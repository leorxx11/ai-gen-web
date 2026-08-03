<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { formatDateTime } from '@/utils/time'

// 表格列配置
const columns = [
  { title: 'id', dataIndex: 'id', width: 100 },
  { title: '消息内容', dataIndex: 'message', width: 320 },
  { title: '消息类型', dataIndex: 'messageType', width: 100 },
  { title: '应用 id', dataIndex: 'appId', width: 180 },
  { title: '用户 id', dataIndex: 'userId', width: 180 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
]

// 表格数据
const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 应用 id / 用户 id 用字符串输入，避免雪花 id 超出 JS 安全整数范围
const appIdInput = ref<string>('')
const userIdInput = ref<string>('')

// 获取对话历史列表（后端默认按创建时间降序）
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({ ...searchParams })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)

// 分页配置
const pagination = computed(() => ({
  current: searchParams.pageNum,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
}))

// 翻页 / 修改每页条数
const handleTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索（重置页码到第一页）
const doSearch = () => {
  searchParams.appId = appIdInput.value ? (appIdInput.value.trim() as unknown as number) : undefined
  searchParams.userId = userIdInput.value
    ? (userIdInput.value.trim() as unknown as number)
    : undefined
  searchParams.pageNum = 1
  fetchData()
}
</script>

<template>
  <div id="chatHistoryManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="消息内容">
        <a-input v-model:value="searchParams.message" placeholder="输入消息内容" allow-clear />
      </a-form-item>
      <a-form-item label="消息类型">
        <a-select
          v-model:value="searchParams.messageType"
          placeholder="选择消息类型"
          style="width: 140px"
          allow-clear
        >
          <a-select-option value="user">用户消息</a-select-option>
          <a-select-option value="ai">AI 消息</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="应用 id">
        <a-input v-model:value="appIdInput" placeholder="输入应用 id" allow-clear />
      </a-form-item>
      <a-form-item label="用户 id">
        <a-input v-model:value="userIdInput" placeholder="输入用户 id" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <!-- 对话历史表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :loading="loading"
      :scroll="{ x: 1150 }"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'message'">
          <a-tooltip :title="record.message">
            <span class="message-text">{{ record.message }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag v-if="record.messageType === 'user'" color="blue">用户消息</a-tag>
          <a-tag v-else color="green">AI 消息</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
#chatHistoryManagePage {
  padding: 4px;
}

/* 消息内容最多展示两行，超出省略 */
.message-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
