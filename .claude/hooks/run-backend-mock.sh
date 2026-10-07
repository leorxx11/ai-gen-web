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
