<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deleteApp, deleteAppByAdmin, deployApp, getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VUE_PROJECT_CODE_GEN_TYPE } from '@/constants/app'
import { useLoginUserStore } from '@/stores/loginUser'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import logo from '@/assets/logo.png'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用 id 保持字符串形式，避免雪花 id 超出 JS 安全整数范围导致精度丢失
const appId = String(route.params.id ?? '')

// 应用信息
const appInfo = ref<API.AppVO>()

// 后端仅允许创建者对话和部署
const isOwner = computed(
  () => !!appInfo.value?.userId && appInfo.value.userId === loginUserStore.loginUser.id,
)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// —— 对话消息 ——
interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  // 等待 AI 返回第一个片段前展示加载状态
  loading?: boolean
  // 是否正在流式输出（此阶段渲染跳过代码高亮，降低开销）
  streaming?: boolean
}

const messages = ref<ChatMessage[]>([])
const userInput = ref('')
const isStreaming = ref(false)
const isBuilding = ref(false)
const messageListRef = ref<HTMLDivElement>()
let eventSource: EventSource | null = null
// 流式内容批量刷新的定时器
let flushTimer: number | null = null

// —— 对话历史 ——
const HISTORY_PAGE_SIZE = 10
const historyLoading = ref(false)
// 是否还有更早的历史消息可加载
const hasMoreHistory = ref(false)
// 游标：当前已加载的最早一条消息的创建时间
let historyCursor: string | undefined

// 游标分页加载对话历史：首次加载最近 10 条，加载更多时取游标之前的一页
const loadChatHistory = async (loadMore = false) => {
  if (historyLoading.value) {
    return
  }
  historyLoading.value = true
  try {
    const res = await listAppChatHistory({
      appId: appId as unknown as number,
      pageSize: HISTORY_PAGE_SIZE,
      lastCreateTime: loadMore ? historyCursor : undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      // 接口按创建时间降序返回，反转为升序后插入消息列表头部
      const historyMessages: ChatMessage[] = [...records].reverse().map((record) => ({
        role: record.messageType === 'user' ? 'user' : 'ai',
        content: record.message ?? '',
      }))
      messages.value.unshift(...historyMessages)
      // 更新游标为已加载的最早一条消息的创建时间
      const oldest = records[records.length - 1]
      if (oldest) {
        historyCursor = oldest.createTime
      }
      // 取满一页说明可能还有更早的消息
      hasMoreHistory.value = records.length === HISTORY_PAGE_SIZE
    } else {
      message.error('加载对话历史失败：' + res.data.message)
    }
  } finally {
    historyLoading.value = false
  }
}

// 点击加载更多：加载后保持视口停留在原来阅读的位置
const loadMoreHistory = async () => {
  const el = messageListRef.value
  const prevScrollHeight = el?.scrollHeight ?? 0
  const prevScrollTop = el?.scrollTop ?? 0
  await loadChatHistory(true)
  nextTick(() => {
    if (el) {
      el.scrollTop = el.scrollHeight - prevScrollHeight + prevScrollTop
    }
  })
}

// 权限校验：只有本人能在自己的作品下对话
const inputDisabled = computed(() => !isOwner.value || isStreaming.value || isBuilding.value)
const inputPlaceholder = computed(() =>
  !isOwner.value ? '无法在别人的作品下对话哦~' : '描述越详细，页面越具体，可以一步一步完善生成效果',
)
// 非本人作品时，鼠标悬浮到输入框上给出提示（应用信息加载完成后才判断）
const inputTooltip = computed(() =>
  appInfo.value && !isOwner.value ? '无法在别人的作品下对话哦~' : '',
)

// —— 生成的网页预览 ——
const previewUrl = ref('')
const PREVIEW_POLL_INTERVAL_MS = 1000
const PREVIEW_POLL_MAX_ATTEMPTS = 480

// 生成完成后展示网站效果，附加时间戳强制 iframe 加载最新版本
const updatePreview = () => {
  if (!appInfo.value?.codeGenType) {
    return
  }
  previewUrl.value = getStaticPreviewUrl(appInfo.value.codeGenType, appId) + `?t=${Date.now()}`
}

// 应用此前生成过网站时直接加载预览；未生成则保持占位提示，避免 iframe 展示 404
const showExistingPreview = async (generatedAfter?: number): Promise<boolean> => {
  if (!appInfo.value?.codeGenType) {
    return false
  }
  const url = getStaticPreviewUrl(appInfo.value.codeGenType, appId)
  try {
    // 用 GET 探测（后端 CORS 未放行 HEAD 方法，HEAD 请求会被拦截）
    const resp = await fetch(url, { credentials: 'include', cache: 'no-store' })
    const lastModified = Date.parse(resp.headers.get('Last-Modified') ?? '')
    if (resp.ok && (!generatedAfter || lastModified > generatedAfter)) {
      previewUrl.value = `${url}?t=${Date.now()}`
      return true
    }
  } catch {
    // 探测失败视为未生成
  }
  return false
}

// VUE 工程在流式响应结束后异步构建，构建产物可访问后再刷新预览
const waitForVuePreview = async (generatedAfter: number) => {
  isBuilding.value = true
  for (let attempt = 0; attempt < PREVIEW_POLL_MAX_ATTEMPTS && isBuilding.value; attempt++) {
    if (await showExistingPreview(generatedAfter)) {
      isBuilding.value = false
      message.success('网站生成完成')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, PREVIEW_POLL_INTERVAL_MS))
  }
  if (isBuilding.value) {
    isBuilding.value = false
    message.error('VUE 工程构建超时，请查看后端构建日志')
  }
}

// 获取应用信息
const fetchAppInfo = async () => {
  const res = await getAppVoById({ id: appId as unknown as number })
  if (res.data.code === 0 && res.data.data) {
    appInfo.value = res.data.data
  } else {
    message.error('获取应用信息失败：' + res.data.message)
  }
}

// 进入页面：先加载应用信息和对话历史，再决定是否自动触发生成
onMounted(async () => {
  await fetchAppInfo()
  if (!appInfo.value) {
    return
  }
  // 对话历史仅应用创建者和管理员可见
  if (isOwner.value || isAdmin.value) {
    await loadChatHistory()
    scrollToBottom()
    // 自己的应用且没有任何对话历史，才自动发送初始提示词触发生成
    if (isOwner.value && messages.value.length === 0 && appInfo.value.initPrompt) {
      sendMessage(appInfo.value.initPrompt)
      return
    }
    // 已有至少 2 条对话记录（一问一答），说明生成过网站，直接展示
    if (messages.value.length >= 2) {
      showExistingPreview()
    }
  } else {
    // 非本人应用看不到历史，直接尝试展示已生成的网站
    showExistingPreview()
  }
})

// 消息更新后滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const closeEventSource = () => {
  eventSource?.close()
  eventSource = null
  if (flushTimer !== null) {
    window.clearInterval(flushTimer)
    flushTimer = null
  }
}

// 离开页面时断开 SSE 连接
onBeforeUnmount(() => {
  closeEventSource()
  isBuilding.value = false
})

// 通过 SSE 与 AI 对话生成代码，流式展示回复
const sendMessage = (content: string) => {
  if (isStreaming.value) {
    return
  }
  const generationStartedAt = Math.floor(Date.now() / 1000) * 1000
  messages.value.push({ role: 'user', content })
  // 占位的 AI 消息，收到流式内容后逐段追加
  const aiMessage = reactive<ChatMessage>({
    role: 'ai',
    content: '',
    loading: true,
    streaming: true,
  })
  messages.value.push(aiMessage)
  isStreaming.value = true
  scrollToBottom()

  // SSE 片段到达速度极快（每秒可达上百条），若每条都触发 Markdown 重渲染会阻塞主线程
  // 导致页面卡死。这里先把片段累积到普通变量，每 150ms 批量刷入响应式消息
  let buffer = ''
  const flushBuffer = () => {
    if (!buffer) {
      return
    }
    // 刷入前记录是否贴近底部：用户主动上翻阅读时不强制拉回
    const el = messageListRef.value
    const shouldStick = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 120
    aiMessage.content += buffer
    buffer = ''
    aiMessage.loading = false
    if (shouldStick) {
      scrollToBottom()
    }
  }
  flushTimer = window.setInterval(flushBuffer, 150)

  // 结束流式输出：停止定时器，把剩余内容一次性刷入，并触发一次完整的高亮渲染
  const finishStream = () => {
    closeEventSource()
    flushBuffer()
    aiMessage.loading = false
    aiMessage.streaming = false
    isStreaming.value = false
  }

  const url = `${API_BASE_URL}/app/chat/gen/code?appId=${appId}&message=${encodeURIComponent(content)}`
  eventSource = new EventSource(url, { withCredentials: true })
  eventSource.onmessage = (event) => {
    if (!event.data) {
      return
    }
    try {
      // 后端将内容包装为 {"d": "片段"}，防止流式传输丢失空格
      const chunk = JSON.parse(event.data)
      buffer += chunk.d ?? ''
    } catch (e) {
      console.error('解析 SSE 数据失败:', e)
    }
  }
  // 后端发送 done 事件表示网站文件全部生成完成
  eventSource.addEventListener('done', () => {
    finishStream()
    if (appInfo.value?.codeGenType === VUE_PROJECT_CODE_GEN_TYPE) {
      void waitForVuePreview(generationStartedAt)
    } else {
      updatePreview()
      message.success('网站生成完成')
    }
  })
  eventSource.onerror = () => {
    finishStream()
    if (!aiMessage.content) {
      aiMessage.content = '抱歉，本次生成失败，请稍后重试'
      message.error('对话失败，请稍后重试')
    }
  }
}

// 发送输入框中的消息
const doSend = () => {
  const content = userInput.value.trim()
  if (!content || inputDisabled.value) {
    return
  }
  userInput.value = ''
  sendMessage(content)
}

// —— 部署应用 ——
const deploying = ref(false)
const deployModalOpen = ref(false)
const deployUrl = ref('')

const doDeploy = async () => {
  deploying.value = true
  try {
    const res = await deployApp({ appId: appId as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalOpen.value = true
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } finally {
    deploying.value = false
  }
}

// —— 应用详情 / 修改 / 删除 ——
const detailModalOpen = ref(false)

const goToEdit = () => {
  router.push(`/app/edit/${appId}`)
}

const doDelete = () => {
  Modal.confirm({
    title: '删除应用',
    content: '确定要删除该应用吗？删除后不可恢复',
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      // 管理员可删除任意应用，创建者删除自己的应用
      const api = isOwner.value ? deleteApp : deleteAppByAdmin
      const res = await api({ id: appId as unknown as number })
      if (res.data.code === 0) {
        message.success('删除成功')
        router.push('/')
      } else {
        message.error('删除失败：' + res.data.message)
      }
    },
  })
}
</script>

<template>
  <div id="appChatPage">
    <!-- 顶部栏：左侧应用名称，右侧部署按钮 -->
    <div class="chat-header">
      <a-dropdown>
        <a-space class="app-title">
          <a-avatar :src="logo" :size="28" shape="square" />
          <span class="app-name">{{ appInfo?.appName || '未命名应用' }}</span>
          <DownOutlined class="app-title-arrow" />
        </a-space>
        <template #overlay>
          <a-menu>
            <a-menu-item key="detail" @click="detailModalOpen = true">
              <InfoCircleOutlined />
              应用详情
            </a-menu-item>
            <template v-if="isOwner || isAdmin">
              <a-menu-item key="edit" @click="goToEdit">
                <EditOutlined />
                修改应用
              </a-menu-item>
              <a-menu-item key="delete" danger @click="doDelete">
                <DeleteOutlined />
                删除应用
              </a-menu-item>
            </template>
          </a-menu>
        </template>
      </a-dropdown>
      <a-button
        v-if="isOwner"
        type="primary"
        :loading="deploying"
        :disabled="isStreaming || isBuilding"
        @click="doDeploy"
      >
        <template #icon><CloudUploadOutlined /></template>
        部署
      </a-button>
    </div>

    <!-- 核心内容区：左侧对话，右侧网页展示 -->
    <div class="chat-body">
      <!-- 对话区域 -->
      <div class="chat-panel">
        <div ref="messageListRef" class="message-list">
          <!-- 加载更多历史消息 -->
          <div v-if="hasMoreHistory" class="load-more">
            <a-button type="link" size="small" :loading="historyLoading" @click="loadMoreHistory">
              加载更多历史消息
            </a-button>
          </div>
          <a-empty
            v-if="messages.length === 0"
            description="暂无对话，快来和 AI 一起创作吧"
            class="message-empty"
          />
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role === 'user' ? 'message-user' : 'message-ai'"
          >
            <a-avatar v-if="msg.role === 'ai'" :src="logo" :size="36" class="message-avatar" />
            <div class="message-content">
              <template v-if="msg.loading"><LoadingOutlined /> AI 正在生成中...</template>
              <!-- AI 回复使用 Markdown 渲染，用户消息保持纯文本 -->
              <MarkdownRenderer
                v-else-if="msg.role === 'ai'"
                :content="msg.content"
                :streaming="msg.streaming"
              />
              <template v-else>{{ msg.content }}</template>
            </div>
            <a-avatar
              v-if="msg.role === 'user'"
              :src="appInfo?.user?.userAvatar ?? loginUserStore.loginUser.userAvatar"
              :size="36"
              class="message-avatar"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
          </div>
        </div>
        <!-- 用户消息输入框：非本人作品禁用并悬浮提示 -->
        <a-tooltip :title="inputTooltip" placement="topLeft">
          <div class="input-area">
            <a-textarea
              v-model:value="userInput"
              :placeholder="inputPlaceholder"
              :auto-size="{ minRows: 2, maxRows: 5 }"
              :disabled="inputDisabled"
              :maxlength="1000"
              @keydown.enter.exact.prevent="doSend"
            />
            <a-button
              type="primary"
              shape="circle"
              class="send-btn"
              :disabled="inputDisabled || !userInput.trim()"
              @click="doSend"
            >
              <template #icon><SendOutlined /></template>
            </a-button>
          </div>
        </a-tooltip>
      </div>

      <!-- 网页展示区域 -->
      <div class="preview-panel">
        <div class="preview-header">
          <span>生成后的网页展示</span>
          <a v-if="previewUrl" :href="previewUrl" target="_blank">
            在新窗口打开
            <ExportOutlined />
          </a>
        </div>
        <iframe v-if="previewUrl" :src="previewUrl" class="preview-frame" title="网站预览"></iframe>
        <div v-else class="preview-placeholder">
          <a-empty
            :description="
              isStreaming
                ? '网站正在生成中，请稍候…'
                : isBuilding
                  ? 'VUE 工程正在构建中，请稍候…'
                  : '网站文件生成完成后将在此展示效果'
            "
          />
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="detailModalOpen"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="goToEdit"
      @delete="doDelete"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal v-model:open="deployModalOpen" :url="deployUrl" />
  </div>
</template>

<style scoped>
/* 视口高度减去顶部导航 64px、内容区上下内边距 40px、底部页脚 56px */
#appChatPage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
}

/* —— 顶部栏 —— */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.app-title {
  cursor: pointer;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.app-title-arrow {
  font-size: 12px;
  color: #999;
}

/* —— 核心内容区 —— */
/* min-height: 0 保证子元素可以在 flex 布局中正常滚动 */
.chat-body {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
  padding-top: 16px;
}

.chat-panel {
  display: flex;
  flex: 4;
  flex-direction: column;
  min-width: 320px;
}

.message-list {
  flex: 1;
  padding-right: 8px;
  overflow-y: auto;
}

.load-more {
  margin-bottom: 8px;
  text-align: center;
}

.message-empty {
  margin-top: 80px;
}

.message-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
}

/* 用户消息靠右展示 */
.message-user {
  justify-content: flex-end;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 80%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.message-user .message-content {
  white-space: pre-wrap;
  background: #e6f4ff;
  border-color: #bae0ff;
}

/* —— 输入框 —— */
.input-area {
  position: relative;
  margin-top: 12px;
}

.input-area :deep(.ant-input) {
  padding-right: 52px;
  border-radius: 12px;
}

.send-btn {
  position: absolute;
  right: 10px;
  bottom: 10px;
}

/* —— 网页展示区域 —— */
.preview-panel {
  display: flex;
  flex: 6;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  font-weight: 500;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.preview-frame {
  flex: 1;
  width: 100%;
  border: none;
}

.preview-placeholder {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}
</style>
