<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { md, mdPlain } from '@/utils/markdown'
import 'highlight.js/styles/github.css'

const props = defineProps<{
  content: string
  // 流式输出中跳过代码高亮，避免高频重渲染阻塞页面
  streaming?: boolean
}>()

const renderedHtml = computed(() => (props.streaming ? mdPlain : md).render(props.content ?? ''))
const copyCode = async (event: MouseEvent) => {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-copy-code]')
  if (!button) return
  const code = button.closest('details')!.querySelector('code')!.textContent!
  try {
    await navigator.clipboard.writeText(code)
    message.success('代码已复制')
  } catch {
    message.error('复制失败，请允许浏览器访问剪贴板')
  }
}
</script>

<template>
  <div class="markdown-content" @click="copyCode" v-html="renderedHtml"></div>
</template>

<style scoped>
.markdown-content {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.markdown-content :deep(p) {
  margin: 0 0 8px;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0 0 8px;
  padding-left: 20px;
}

/* 代码块（含流式输出时未高亮的 pre） */
.markdown-content :deep(pre) {
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  font-size: 13px;
  background: #f6f8fa;
  border: 1px solid #eee;
  border-radius: 8px;
}

.markdown-content :deep(pre code) {
  padding: 0;
  background: transparent;
}

/* 行内代码 */
.markdown-content :deep(code) {
  padding: 2px 6px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  background: #f2f2f2;
  border-radius: 4px;
}

.markdown-content :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  color: #666;
  border-left: 3px solid #ddd;
}

.markdown-content :deep(table) {
  margin: 8px 0;
  border-collapse: collapse;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 6px 12px;
  border: 1px solid #eee;
}
.markdown-content :deep(.code-block) {
  margin: 14px 0;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  overflow: hidden;
  background: #f7f9f7;
}
.markdown-content :deep(summary) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  color: var(--app-primary);
  font-size: 12px;
}
.markdown-content :deep(.code-disclosure) {
  color: var(--app-muted);
}
.markdown-content :deep(.code-toolbar) {
  display: flex;
  justify-content: flex-end;
  padding: 0 10px;
}
.markdown-content :deep([data-copy-code]) {
  padding: 5px 8px;
  border: 0;
  border-radius: 5px;
  background: var(--app-soft);
  color: var(--app-primary);
  cursor: pointer;
  font-size: 12px;
}
.markdown-content :deep([data-copy-code]:disabled) {
  cursor: default;
  opacity: 0.5;
}
.markdown-content :deep(.code-block pre) {
  border: 0;
  margin: 0;
  border-radius: 0;
  background: transparent;
}
.markdown-content :deep(a) {
  overflow-wrap: anywhere;
}
.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
}
</style>
