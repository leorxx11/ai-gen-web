<script setup lang="ts">
import { UserOutlined } from '@ant-design/icons-vue'
import { getDeployUrl } from '@/config/env'

const props = defineProps<{
  app: API.AppVO
}>()

const emit = defineEmits<{
  click: [app: API.AppVO]
}>()

// 查看作品：新页面打开部署地址（注意区别于生成网站的预览地址）
const viewWork = () => {
  if (props.app.deployKey) {
    window.open(getDeployUrl(props.app.deployKey), '_blank')
  }
}
</script>

<template>
  <div class="app-card" @click="emit('click', app)">
    <!-- 封面：无封面时展示应用名首字占位图，悬浮展示操作按钮 -->
    <div class="app-cover">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" loading="lazy" />
      <div v-else class="cover-placeholder">{{ app.appName?.charAt(0) ?? '?' }}</div>
      <div class="cover-mask">
        <a-space>
          <a-button type="primary" @click.stop="emit('click', app)">查看对话</a-button>
          <a-button v-if="app.deployKey" @click.stop="viewWork">查看作品</a-button>
        </a-space>
      </div>
    </div>
    <!-- 应用信息：左侧创建者头像，右侧上应用标题、下创建者昵称 -->
    <div class="app-info">
      <a-avatar :src="app.user?.userAvatar" :size="40">
        <template #icon><UserOutlined /></template>
      </a-avatar>
      <div class="app-meta">
        <div class="app-name">{{ app.appName || '未命名应用' }}</div>
        <div class="app-author">{{ app.user?.userName ?? '匿名用户' }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.app-cover {
  position: relative;
  height: 180px;
  background: #f5f5f5;
}

.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 48px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #a8edea 0%, #5ec8c3 100%);
}

/* 悬浮遮罩：hover 时淡入展示操作按钮 */
.cover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card:hover .cover-mask {
  opacity: 1;
}

.app-info {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
}

/* min-width: 0 保证应用名过长时省略号生效 */
.app-meta {
  min-width: 0;
  flex: 1;
}

.app-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-author {
  margin-top: 4px;
  overflow: hidden;
  font-size: 13px;
  color: #999;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
