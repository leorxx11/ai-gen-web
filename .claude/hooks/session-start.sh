#!/bin/bash
# Prepare a Claude Code cloud session: frontend deps, backend deps,
# plus a local MariaDB (MySQL-compatible) and Redis for the backend.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"

DB_NAME=ai_gen_web
DB_PASS=12345678   # matches src/main/resources/application.yaml

# --- Frontend (npm install rather than ci: lockfile is currently out of sync) ---
(cd ai-gen-web-frontend && npm install --no-audit --no-fund)

# --- Backend: resolve Maven dependencies into the cached ~/.m2 ---
mvn -q -B dependency:go-offline

# --- MariaDB ---
if ! command -v mariadbd >/dev/null 2>&1; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq mariadb-server
fi

if ! mysqladmin ping --silent >/dev/null 2>&1; then
  mkdir -p /run/mysqld && chown mysql:mysql /run/mysqld
  nohup mysqld_safe --user=mysql >/dev/null 2>&1 &
  for _ in $(seq 1 30); do
    mysqladmin ping --silent >/dev/null 2>&1 && break
    sleep 1
  done
fi

# Root is socket-auth on a fresh install; switch to the password the app expects.
MYSQL="mysql -uroot"
$MYSQL -e "SELECT 1" >/dev/null 2>&1 || MYSQL="mysql -uroot -p${DB_PASS}"
$MYSQL -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_PASS}';" 2>/dev/null || true
MYSQL="mysql -uroot -p${DB_PASS}"
$MYSQL -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4;"
if [ -z "$($MYSQL -N -e "SHOW TABLES FROM ${DB_NAME}")" ]; then
  $MYSQL "${DB_NAME}" < sql/create_table.sql
fi

# --- Redis ---
redis-cli ping >/dev/null 2>&1 || redis-server --daemonize yes >/dev/null

# --- LLM config: application-local.yaml is gitignored, so generate it from a secret ---
# Set DEEPSEEK_API_KEY in the cloud environment's secrets (optionally LLM_BASE_URL / LLM_MODEL_NAME).
LOCAL_CFG=src/main/resources/application-local.yaml
if [ -n "${DEEPSEEK_API_KEY:-}" ] && [ ! -f "$LOCAL_CFG" ]; then
  LLM_URL="${LLM_BASE_URL:-https://api.deepseek.com/v1}"
  LLM_MODEL="${LLM_MODEL_NAME:-deepseek-v4-flash}"
  cat > "$LOCAL_CFG" <<CFG
langchain4j:
  open-ai:
    chat-model:
      base-url: ${LLM_URL}
      api-key: ${DEEPSEEK_API_KEY}
      model-name: ${LLM_MODEL}
    streaming-chat-model:
      base-url: ${LLM_URL}
      api-key: ${DEEPSEEK_API_KEY}
      model-name: ${LLM_MODEL}
CFG
  chmod 600 "$LOCAL_CFG"
elif [ -z "${DEEPSEEK_API_KEY:-}" ] && [ ! -f "$LOCAL_CFG" ]; then
  echo "NOTE: DEEPSEEK_API_KEY not set; skipping application-local.yaml (use run-backend-mock.sh for a mock LLM)." >&2
fi

# --- Helper for starting the backend without an application-local.yaml / real LLM key ---
cat > .claude/hooks/run-backend-mock.sh <<'RUN'
#!/bin/bash
# Start the backend against a mock OpenAI-compatible endpoint (override MOCK_LLM_URL as needed).
U="${MOCK_LLM_URL:-http://localhost:18080/v1}"
exec java -jar "$(dirname "$0")/../../target/ai-gen-web-0.0.1-SNAPSHOT.jar" \
  --langchain4j.open-ai.chat-model.base-url="$U" \
  --langchain4j.open-ai.chat-model.api-key=mock \
  --langchain4j.open-ai.chat-model.model-name=mock \
  --langchain4j.open-ai.streaming-chat-model.base-url="$U" \
  --langchain4j.open-ai.streaming-chat-model.api-key=mock \
  --langchain4j.open-ai.streaming-chat-model.model-name=mock "$@"
RUN
chmod +x .claude/hooks/run-backend-mock.sh
