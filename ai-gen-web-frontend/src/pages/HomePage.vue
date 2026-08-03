<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowUpOutlined, PaperClipOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import AppListSection from '@/components/AppListSection.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// —— 提示词输入 ——
const prompt = ref('')
const creating = ref(false)

// 快捷提示词，点击后填充到输入框
const quickPrompts = [
  { label: '波普风电商页面', prompt: '做一个波普艺术风格的电商促销页面，包含商品展示和购买按钮' },
  { label: '企业网站', prompt: '做一个简约现代的企业官网，包含首页、服务介绍和联系我们' },
  { label: '电商运营后台', prompt: '做一个电商运营管理后台，包含数据看板、订单和商品管理' },
  { label: '暗黑话题社区', prompt: '做一个暗黑风格的话题讨论社区，包含话题列表和热榜' },
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
  const res = await listMyAppVoByPage({ ...mySearchParams })
  if (res.data.code === 0 && res.data.data) {
    myApps.value = res.data.data.records ?? []
    myTotal.value = res.data.data.totalRow ?? 0
  } else {
    message.error('获取我的应用失败：' + res.data.message)
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
  const res = await listGoodAppVoByPage({ ...goodSearchParams })
  if (res.data.code === 0 && res.data.data) {
    goodApps.value = res.data.data.records ?? []
    goodTotal.value = res.data.data.totalRow ?? 0
  } else {
    message.error('获取精选应用失败：' + res.data.message)
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
      <!-- 网站标题 -->
      <div class="hero">
        <h1 class="hero-title">
          一句话
          <img class="hero-logo" src="@/assets/logo.png" alt="Logo" />
          呈所想
        </h1>
        <p class="hero-desc">与 AI 对话轻松创建应用和网站</p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="prompt-input">
        <a-textarea
          v-model:value="prompt"
          placeholder="使用 NoCode 创建一个高效的小工具，帮我计算……"
          :auto-size="{ minRows: 4, maxRows: 8 }"
          :maxlength="1000"
          @keydown.enter.exact.prevent="doCreateApp"
        />
        <div class="prompt-toolbar">
          <a-space>
            <a-button shape="round" size="small" @click="message.info('功能开发中，敬请期待')">
              <template #icon><PaperClipOutlined /></template>
              上传
            </a-button>
            <a-button shape="round" size="small" @click="message.info('功能开发中，敬请期待')">
              <template #icon><ThunderboltOutlined /></template>
              优化
            </a-button>
          </a-space>
          <a-button
            type="primary"
            shape="circle"
            size="large"
            :loading="creating"
            @click="doCreateApp"
          >
            <template #icon><ArrowUpOutlined /></template>
          </a-button>
        </div>
      </div>

      <!-- 快捷提示词 -->
      <div class="quick-prompts">
        <a-button v-for="item in quickPrompts" :key="item.label" @click="prompt = item.prompt">
          {{ item.label }}
        </a-button>
      </div>

      <!-- 我的应用分页列表 -->
      <AppListSection
        v-model:search-text="mySearchParams.appName"
        v-model:page-num="mySearchParams.pageNum"
        title="我的作品"
        search-placeholder="搜索我的应用"
        :apps="myApps"
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
/* 通过负 margin 抵消布局内边距，让渐变背景铺满内容区 */
#homePage {
  margin: -20px;
  padding: 40px 24px 60px;
  background: linear-gradient(180deg, #fdfdfb 0%, #e9f8f5 45%, #c2eeed 100%);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

/* —— 标题区 —— */
.hero {
  text-align: center;
}

.hero-title {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: center;
  margin: 24px 0 0;
  font-size: 44px;
  font-weight: 700;
  color: #1a1a1a;
}

.hero-logo {
  width: 56px;
  height: 56px;
}

.hero-desc {
  margin: 16px 0 0;
  font-size: 16px;
  color: #666;
}

/* —— 提示词输入框 —— */
.prompt-input {
  max-width: 800px;
  margin: 32px auto 0;
  padding: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.prompt-input :deep(.ant-input) {
  border: none;
  box-shadow: none;
  resize: none;
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

/* —— 快捷提示词 —— */
.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}
</style>
