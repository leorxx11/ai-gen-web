<script setup lang="ts">
import { UserOutlined } from '@ant-design/icons-vue'
import { formatCodeGenType } from '@/constants/app'
import { getDeployUrl } from '@/config/env'
import { formatDateTime } from '@/utils/time'

defineProps<{
  app?: API.AppVO
  // 是否展示修改 / 删除操作（创建者或管理员可见）
  showActions?: boolean
}>()

const open = defineModel<boolean>('open', { default: false })

const emit = defineEmits<{
  edit: []
  delete: []
}>()
</script>

<template>
  <a-modal v-model:open="open" title="应用详情" :footer="null">
    <a-descriptions :column="1" bordered size="small">
      <a-descriptions-item label="应用名称">
        {{ app?.appName || '未命名应用' }}
      </a-descriptions-item>
      <a-descriptions-item label="创建者">
        <a-space>
          <a-avatar :src="app?.user?.userAvatar" :size="24">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          {{ app?.user?.userName ?? '匿名用户' }}
        </a-space>
      </a-descriptions-item>
      <a-descriptions-item label="生成类型">
        {{ formatCodeGenType(app?.codeGenType) }}
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">
        {{ formatDateTime(app?.createTime) }}
      </a-descriptions-item>
      <a-descriptions-item v-if="app?.deployKey" label="部署地址">
        <a :href="getDeployUrl(app.deployKey)" target="_blank">
          {{ getDeployUrl(app.deployKey) }}
        </a>
      </a-descriptions-item>
      <a-descriptions-item label="初始提示词">
        {{ app?.initPrompt || '-' }}
      </a-descriptions-item>
    </a-descriptions>
    <div v-if="showActions" class="detail-actions">
      <a-space>
        <a-button type="primary" @click="emit('edit')">修改应用</a-button>
        <a-button danger @click="emit('delete')">删除应用</a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<style scoped>
.detail-actions {
  margin-top: 16px;
  text-align: right;
}
</style>
