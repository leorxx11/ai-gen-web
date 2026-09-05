<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getAppVoById, getAppVoByIdByAdmin, updateApp, updateAppByAdmin } from '@/api/appController'
import { GOOD_APP_PRIORITY, formatCodeGenType } from '@/constants/app'
import { formatDateTime } from '@/utils/time'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用 id 保持字符串形式，避免雪花 id 精度丢失
const appId = String(route.params.id ?? '')

const appInfo = ref<API.AppVO>()
const submitting = ref(false)

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// 编辑表单：普通用户仅可修改名称，管理员可额外修改封面和优先级
const formData = reactive<API.AppAdminUpdateRequest>({})

const fetchApp = async () => {
  const getApi = isAdmin.value ? getAppVoByIdByAdmin : getAppVoById
  const res = await getApi({ id: appId as unknown as number })
  if (res.data.code === 0 && res.data.data) {
    const app = res.data.data
    // 普通用户只能编辑自己的应用
    if (!isAdmin.value && app.userId !== loginUserStore.loginUser.id) {
      message.error('无权限编辑该应用')
      router.push('/')
      return
    }
    appInfo.value = app
    Object.assign(formData, {
      id: app.id,
      appName: app.appName,
      cover: app.cover,
      priority: app.priority,
    })
  } else {
    message.error('获取应用信息失败：' + res.data.message)
  }
}

onMounted(fetchApp)

const doSubmit = async () => {
  if (!formData.appName?.trim()) {
    message.warning('请输入应用名称')
    return
  }
  submitting.value = true
  try {
    // 管理员走管理接口，可修改封面和优先级；普通用户仅能修改名称
    const res = isAdmin.value
      ? await updateAppByAdmin({ ...formData })
      : await updateApp({ id: formData.id, appName: formData.appName })
    if (res.data.code === 0) {
      message.success('修改成功')
      fetchApp()
    } else {
      message.error('修改失败：' + res.data.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div id="appEditPage">
    <a-card class="edit-card" title="应用信息修改">
      <a-form :model="formData" :label-col="{ span: 5 }" @finish="doSubmit">
        <a-form-item label="应用名称" required>
          <a-input v-model:value="formData.appName" placeholder="请输入应用名称" allow-clear />
        </a-form-item>
        <!-- 仅管理员可修改封面和优先级 -->
        <template v-if="isAdmin">
          <a-form-item label="应用封面">
            <a-input v-model:value="formData.cover" placeholder="请输入封面图片链接" allow-clear />
            <a-image
              v-if="formData.cover"
              :src="formData.cover"
              :width="200"
              class="cover-preview"
            />
          </a-form-item>
          <a-form-item label="优先级" :help="`设置为 ${GOOD_APP_PRIORITY} 即为精选应用`">
            <a-input-number v-model:value="formData.priority" :min="0" :max="GOOD_APP_PRIORITY" />
          </a-form-item>
        </template>
        <a-form-item :wrapper-col="{ offset: 5 }">
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">保存</a-button>
            <a-button @click="router.push(`/app/chat/${appId}`)">进入对话页</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-divider />
      <!-- 应用基本信息（只读） -->
      <a-descriptions :column="1" size="small" title="应用信息">
        <a-descriptions-item label="创建者">
          {{ appInfo?.user?.userName ?? '匿名用户' }}
        </a-descriptions-item>
        <a-descriptions-item label="生成类型">
          {{ formatCodeGenType(appInfo?.codeGenType) }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">
          {{ formatDateTime(appInfo?.createTime) }}
        </a-descriptions-item>
        <a-descriptions-item label="初始提示词">
          {{ appInfo?.initPrompt || '-' }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<style scoped>
#appEditPage {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.edit-card {
  width: 100%;
  max-width: 680px;
  border-color: var(--app-border);
  border-radius: 20px;
  box-shadow: var(--app-shadow);
}

.cover-preview {
  margin-top: 12px;
}
</style>
