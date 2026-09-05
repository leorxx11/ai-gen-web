<script setup lang="ts">
import { CheckOutlined, ExportOutlined } from '@ant-design/icons-vue'
defineProps<{ url: string }>()
const open = defineModel<boolean>('open', { default: false })
</script>
<template>
  <a-modal v-model:open="open" title="部署成功" :footer="null">
    <div class="deploy-success">
      <div class="success-icon"><CheckOutlined /></div>
      <h2>你的作品，已经上线。</h2>
      <p>复制链接，分享这一次创作。</p>
      <div class="deploy-link">
        <a-typography-paragraph copyable>{{ url }}</a-typography-paragraph>
      </div>
      <a-button type="primary" size="large" :href="url" target="_blank" rel="noopener noreferrer"
        >访问网站 <ExportOutlined
      /></a-button>
    </div>
  </a-modal>
</template>
<style scoped>
.deploy-success {
  text-align: center;
  padding: 12px 0 20px;
}
.success-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin: 0 auto 22px;
  border-radius: 50%;
  background: var(--app-soft);
  color: var(--app-primary);
  font-size: 24px;
}
h2 {
  margin: 0 0 8px;
  font-size: 23px;
  color: var(--app-text);
}
p {
  color: var(--app-muted);
}
.deploy-link {
  padding: 16px;
  margin: 24px 0;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-bg);
  overflow-wrap: anywhere;
}
.deploy-link :deep(.ant-typography) {
  margin: 0;
}
</style>
