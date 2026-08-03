<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { deleteAppByAdmin, listAppVoByPageByAdmin, updateAppByAdmin } from '@/api/appController'
import {
  CODE_GEN_TYPE_MAP,
  DEFAULT_APP_PRIORITY,
  GOOD_APP_PRIORITY,
  formatCodeGenType,
} from '@/constants/app'
import { formatDateTime } from '@/utils/time'

// 表格列配置
const columns = [
  { title: 'id', dataIndex: 'id', width: 100 },
  { title: '应用名称', dataIndex: 'appName', width: 150 },
  { title: '封面', dataIndex: 'cover', width: 100 },
  { title: '初始提示词', dataIndex: 'initPrompt', width: 240 },
  { title: '生成类型', dataIndex: 'codeGenType', width: 130 },
  { title: '优先级', dataIndex: 'priority', width: 90 },
  { title: '部署时间', dataIndex: 'deployedTime', width: 170 },
  { title: '创建者', dataIndex: 'user', width: 130 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' },
]

// 表格数据
const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 创建者 id 用字符串输入，避免雪花 id 超出 JS 安全整数范围
const userIdInput = ref<string>('')

// 获取应用列表
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAppVoByPageByAdmin({ ...searchParams })
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
  searchParams.userId = userIdInput.value
    ? (userIdInput.value.trim() as unknown as number)
    : undefined
  searchParams.pageNum = 1
  fetchData()
}

// 编辑应用：新开页面跳转到应用信息修改页
const doEdit = (record: API.AppVO) => {
  window.open(`/app/edit/${record.id}`)
}

// 精选 / 取消精选：本质是更新应用优先级
const doFeature = async (record: API.AppVO) => {
  const isGood = record.priority === GOOD_APP_PRIORITY
  const res = await updateAppByAdmin({
    id: record.id,
    priority: isGood ? DEFAULT_APP_PRIORITY : GOOD_APP_PRIORITY,
  })
  if (res.data.code === 0) {
    message.success(isGood ? '已取消精选' : '设置精选成功')
    fetchData()
  } else {
    message.error('操作失败：' + res.data.message)
  }
}

// 删除应用
const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const res = await deleteAppByAdmin({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败：' + res.data.message)
  }
}
</script>

<template>
  <div id="appManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" allow-clear />
      </a-form-item>
      <a-form-item label="创建者 id">
        <a-input v-model:value="userIdInput" placeholder="输入创建者 id" allow-clear />
      </a-form-item>
      <a-form-item label="生成类型">
        <a-select
          v-model:value="searchParams.codeGenType"
          placeholder="选择生成类型"
          style="width: 160px"
          allow-clear
        >
          <a-select-option v-for="(text, value) in CODE_GEN_TYPE_MAP" :key="value" :value="value">
            {{ text }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <!-- 应用表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :loading="loading"
      :scroll="{ x: 1400 }"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="64" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'initPrompt'">
          <a-tooltip :title="record.initPrompt">
            <span class="init-prompt">{{ record.initPrompt }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          <a-tag color="blue">{{ formatCodeGenType(record.codeGenType) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag v-if="record.priority === GOOD_APP_PRIORITY" color="green">精选</a-tag>
          <span v-else>{{ record.priority ?? 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'deployedTime'">
          {{ record.deployedTime ? formatDateTime(record.deployedTime) : '未部署' }}
        </template>
        <template v-else-if="column.dataIndex === 'user'">
          <a-space>
            <a-avatar :src="record.user?.userAvatar" :size="24" />
            {{ record.user?.userName ?? record.userId }}
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="doEdit(record)">编辑</a-button>
            <a-popconfirm
              :title="
                record.priority === GOOD_APP_PRIORITY
                  ? '确定取消精选该应用吗？'
                  : '确定精选该应用吗？'
              "
              @confirm="doFeature(record)"
            >
              <a-button type="link" size="small">
                {{ record.priority === GOOD_APP_PRIORITY ? '取消精选' : '精选' }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm title="确定要删除该应用吗？" @confirm="doDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
#appManagePage {
  padding: 4px;
}

/* 初始提示词最多展示两行，超出省略 */
.init-prompt {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
