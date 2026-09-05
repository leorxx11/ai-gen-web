<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  BulbOutlined,
  LayoutOutlined,
  ShopOutlined,
  DashboardOutlined,
  MessageOutlined,
} from '@ant-design/icons-vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import AppListSection from '@/components/AppListSection.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// —— 提示词输入 ——
const prompt = ref('')
const creating = ref(false)
const myLoading = ref(false)
const goodLoading = ref(false)
const myError = ref(false)
const goodError = ref(false)

const handlePromptKeydown = (event: KeyboardEvent) => {
  if (
    event.key === 'Enter' &&
    !event.shiftKey &&
    !event.ctrlKey &&
    !event.altKey &&
    !event.metaKey &&
    !event.isComposing
  ) {
    event.preventDefault()
    if (!creating.value) void doCreateApp()
  }
}

// 快捷提示词，点击后填充到输入框
const quickPrompts = [
  {
    icon: ShopOutlined,
    label: '创意电商',
    prompt: '做一个波普艺术风格的电商促销页面，包含商品展示和购买按钮',
  },
  {
    icon: LayoutOutlined,
    label: '企业官网',
    prompt: '做一个简约现代的企业官网，包含首页、服务介绍和联系我们',
  },
  {
    icon: DashboardOutlined,
    label: '数据看板',
    prompt: '做一个电商运营管理后台，包含数据看板、订单和商品管理',
  },
  {
    icon: MessageOutlined,
    label: '话题社区',
    prompt: '做一个暗黑风格的话题讨论社区，包含话题列表和热榜',
  },
]

// 创建应用：跳转到对话页（不带 view 参数），由对话页自动发送初始提示词开始生成
const doCreateApp = async () => {
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning('请先描述你想生成的应用')
    return
  }
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt })
    if (res.data.code === 0 && res.data.data) {
      router.push(`/app/chat/${res.data.data}`)
    } else {
      message.error('创建应用失败：' + res.data.message)
    }
  } finally {
    creating.value = false
  }
}

// —— 我的应用列表 ——
const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const mySearchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  appName: '',
  // 最新创建的排在前面
  sortField: 'createTime',
  sortOrder: 'descend',
})

const fetchMyApps = async () => {
  // 未登录时不请求，避免触发登录跳转
  if (!loginUserStore.loginUser.id) {
    return
  }
  myLoading.value = true
  myError.value = false
  try {
    const res = await listMyAppVoByPage({ ...mySearchParams })
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records ?? []
      myTotal.value = res.data.data.totalRow ?? 0
    } else {
      myError.value = true
      message.error('获取我的应用失败：' + res.data.message)
    }
  } catch {
    myError.value = true
  } finally {
    myLoading.value = false
  }
}

// —— 精选应用列表 ——
const goodApps = ref<API.AppVO[]>([])
const goodTotal = ref(0)
const goodSearchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  appName: '',
})

const fetchGoodApps = async () => {
  goodLoading.value = true
  goodError.value = false
  try {
    const res = await listGoodAppVoByPage({ ...goodSearchParams })
    if (res.data.code === 0 && res.data.data) {
      goodApps.value = res.data.data.records ?? []
      goodTotal.value = res.data.data.totalRow ?? 0
    } else {
      goodError.value = true
      message.error('获取精选应用失败：' + res.data.message)
    }
  } catch {
    goodError.value = true
  } finally {
    goodLoading.value = false
  }
}

onMounted(() => {
  fetchMyApps()
  fetchGoodApps()
})

// 按名称搜索（重置页码到第一页）
const doSearchMyApps = () => {
  mySearchParams.pageNum = 1
  fetchMyApps()
}

const doSearchGoodApps = () => {
  goodSearchParams.pageNum = 1
  fetchGoodApps()
}

// 点击卡片进入对话页（是否自动触发生成由对话页根据对话历史判断）
const goToChat = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  router.push(`/app/chat/${app.id}`)
}
</script>

<template>
  <div id="homePage">
    <div class="container">
      <section class="creation-area" aria-labelledby="creation-title">
        <div class="hero">
          <span class="hero-eyebrow"><BulbOutlined /> 从一个想法开始</span>
          <h1 id="creation-title">一句话，<span>呈所想。</span></h1>
          <p>描述、生成、打磨，让你的网站在对话中成形。</p>
        </div>
        <div class="prompt-input">
          <label for="creation-prompt">你想创建什么？</label>
          <a-textarea
            id="creation-prompt"
            v-model:value="prompt"
            placeholder="描述你想做的网站，比如：一家咖啡店的官网，展示品牌故事与招牌饮品……"
            :auto-size="{ minRows: 3, maxRows: 8 }"
            :maxlength="1000"
            :disabled="creating"
            @keydown="handlePromptKeydown"
          />
          <div class="prompt-toolbar">
            <span class="input-hint"
              >Enter 发送 · Shift + Enter 换行
              <span class="character-count">{{ prompt.length }}/1000</span></span
            >
            <a-button
              type="primary"
              size="large"
              :loading="creating"
              :disabled="!prompt.trim()"
              @click="doCreateApp"
              >开始创作 <ArrowUpOutlined
            /></a-button>
          </div>
        </div>
        <div class="quick-prompts" aria-label="试试这些创意">
          <a-button
            v-for="item in quickPrompts"
            :key="item.label"
            shape="round"
            :disabled="creating"
            @click="prompt = item.prompt"
            ><component :is="item.icon" /> {{ item.label }}</a-button
          >
        </div>
      </section>

      <!-- 我的应用分页列表 -->
      <AppListSection
        v-model:search-text="mySearchParams.appName"
        v-model:page-num="mySearchParams.pageNum"
        title="我的作品"
        search-placeholder="搜索我的应用"
        :apps="myApps"
        :loading="myLoading"
        :error="myError"
        description="继续打磨，让每个想法更进一步"
        :total="myTotal"
        :page-size="mySearchParams.pageSize"
        :empty-text="
          loginUserStore.loginUser.id
            ? '暂无作品，快去创建你的第一个应用吧'
            : '登录后可查看我的作品'
        "
        @search="doSearchMyApps"
        @page-change="fetchMyApps"
        @card-click="goToChat"
      >
        <template v-if="!loginUserStore.loginUser.id" #empty-extra>
          <a-button type="primary" @click="router.push('/user/login')">去登录</a-button>
        </template>
      </AppListSection>

      <!-- 精选应用分页列表 -->
      <AppListSection
        v-model:search-text="goodSearchParams.appName"
        v-model:page-num="goodSearchParams.pageNum"
        title="精选案例"
        search-placeholder="搜索精选应用"
        :apps="goodApps"
        featured
        :loading="goodLoading"
        :error="goodError"
        description="看看其他创作者，把灵感变成了什么"
        :total="goodTotal"
        :page-size="goodSearchParams.pageSize"
        empty-text="暂无精选应用"
        @search="doSearchGoodApps"
        @page-change="fetchGoodApps"
        @card-click="goToChat"
      />
    </div>
  </div>
</template>

<style scoped>
#homePage {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: 44px;
}
.creation-area {
  padding: 40px 0 10px;
  background: radial-gradient(ellipse at 50% 15%, #e9f3ec 0%, transparent 66%);
}
.hero {
  text-align: center;
}
.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--app-primary);
  letter-spacing: 0.06em;
}
.hero h1 {
  margin: 16px 0 14px;
  font-size: clamp(32px, 5vw, 52px);
  letter-spacing: -0.06em;
  line-height: 1.3;
  font-weight: 650;
  color: var(--app-text);
}
.hero h1 span {
  color: var(--app-primary);
}
.hero p {
  margin: 0;
  color: var(--app-muted);
  font-size: 16px;
}
.prompt-input {
  max-width: 760px;
  margin: 30px auto 0;
  padding: 22px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow);
  transition:
    border-color 0.18s,
    box-shadow 0.18s;
}
.prompt-input:focus-within {
  border-color: #80ad92;
  box-shadow:
    0 0 0 4px rgb(33 107 80 / 6%),
    var(--app-shadow);
}
.prompt-input label {
  display: block;
  margin-bottom: 8px;
  color: var(--app-muted);
  font-size: 13px;
}
.prompt-input :deep(.ant-input) {
  padding: 4px 0;
  border: 0;
  box-shadow: none;
  background: transparent;
  font-size: 16px;
  line-height: 1.8;
  resize: none;
}
.prompt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}
.input-hint {
  font-size: 12px;
  color: var(--app-muted);
}
.character-count {
  margin-left: 12px;
  font-variant-numeric: tabular-nums;
}
.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 18px;
}
.quick-prompts .ant-btn {
  color: var(--app-muted);
  background: transparent;
}
@media (max-width: 720px) {
  .creation-area {
    padding-top: 22px;
  }
  .prompt-input {
    padding: 16px;
    margin-top: 24px;
  }
  .prompt-toolbar {
    flex-wrap: wrap;
    gap: 12px;
  }
  .prompt-toolbar .ant-btn {
    margin-left: auto;
  }
  .character-count {
    display: none;
  }
  .hero p {
    max-width: 280px;
    margin: 0 auto;
  }
}
</style>
