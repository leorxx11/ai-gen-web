<script setup lang="ts">
import { ArrowUpOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { getDeployUrl } from '@/config/env'
import { formatCodeGenType } from '@/constants/app'
import { formatDateTime } from '@/utils/time'
defineProps<{ app: API.AppVO; featured?: boolean }>()
const emit = defineEmits<{ click: [app: API.AppVO] }>()
</script>

<template>
  <article class="app-card">
    <button
      class="app-cover"
      type="button"
      :aria-label="`查看${app.appName || '未命名应用'}`"
      @click="emit('click', app)"
    >
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" loading="lazy" />
      <div v-else class="cover-placeholder">
        <span class="cover-label">{{ formatCodeGenType(app.codeGenType) }}</span>
        <span class="cover-letter" aria-hidden="true">{{ app.appName?.charAt(0) ?? 'A' }}</span>
        <span class="cover-name">{{ app.appName || '未命名应用' }}</span>
      </div>
      <span class="cover-action"><ArrowUpOutlined /></span>
    </button>
    <div class="app-info">
      <div class="app-title-row">
        <h3>{{ app.appName || '未命名应用' }}</h3>
        <span class="deploy-status" :class="{ published: app.deployKey }">{{
          app.deployKey ? '已部署' : '未部署'
        }}</span>
      </div>
      <div class="app-meta">
        <span>{{ featured ? (app.user?.userName ?? '创作者') : '更新于' }}</span
        ><time>{{ formatDateTime(app.updateTime) }}</time>
      </div>
      <div class="app-actions">
        <a-button
          v-if="!featured || !app.deployKey"
          size="small"
          type="text"
          @click="emit('click', app)"
          >{{ featured ? '查看效果' : '继续创作' }} <ArrowUpOutlined
        /></a-button>
        <a-button
          v-if="app.deployKey"
          size="small"
          :type="featured ? 'primary' : 'text'"
          :href="getDeployUrl(app.deployKey)"
          target="_blank"
          rel="noopener noreferrer"
          >查看作品 <ExportOutlined
        /></a-button>
        <a-button
          v-if="featured && app.deployKey"
          type="text"
          size="small"
          @click="emit('click', app)"
          >应用详情</a-button
        >
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: var(--app-surface);
  transition:
    border-color 0.18s,
    box-shadow 0.18s,
    transform 0.18s;
}
.app-card:hover {
  border-color: #a9c6b3;
  box-shadow: var(--app-shadow);
  transform: translateY(-3px);
}
.app-cover {
  display: block;
  position: relative;
  width: 100%;
  height: 190px;
  padding: 0;
  border: 0;
  background: var(--app-soft);
  cursor: pointer;
  text-align: left;
}
.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  height: 100%;
  position: relative;
  padding: 22px;
  overflow: hidden;
  background: linear-gradient(125deg, #eaf3ed, #d6e7db);
}
.cover-label {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: var(--app-primary);
}
.cover-letter {
  position: absolute;
  right: 18px;
  top: -30px;
  font-size: 164px;
  font-weight: 650;
  line-height: 1.5;
  color: rgb(33 107 80 / 9%);
}
.cover-name {
  position: absolute;
  bottom: 26px;
  left: 22px;
  right: 24px;
  color: var(--app-text);
  font-size: 23px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cover-action {
  position: absolute;
  right: 14px;
  top: 14px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #fff;
  color: var(--app-primary);
  transform: rotate(45deg);
}
.app-info {
  padding: 18px;
}
.app-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.app-title-row h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.deploy-status {
  flex-shrink: 0;
  color: var(--app-muted);
  font-size: 12px;
}
.deploy-status.published {
  color: var(--app-primary);
}
.published::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 5px;
  vertical-align: middle;
}
.app-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--app-muted);
  margin: 9px 0 16px;
  overflow-wrap: anywhere;
}
.app-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
}
.app-actions .ant-btn {
  font-size: 13px;
}
@media (pointer: coarse) {
  .app-actions .ant-btn {
    min-height: 40px;
  }
}
</style>
