<script setup lang="ts">
import { computed } from 'vue'
import { md, mdPlain } from '@/utils/markdown'
import 'highlight.js/styles/github.css'

const props = defineProps<{
  content: string
  // 流式输出中跳过代码高亮，避免高频重渲染阻塞页面
  streaming?: boolean
}>()

const renderedHtml = computed(() => (props.streaming ? mdPlain : md).render(props.content ?? ''))
</script>

<template>
  <div class="markdown-content" v-html="renderedHtml"></div>
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
</style>
