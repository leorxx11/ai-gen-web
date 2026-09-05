<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
const route = useRoute()
const isWorkspace = computed(() => route.path.startsWith('/app/chat/'))
</script>

<template>
  <a-layout class="basic-layout" :class="{ 'workspace-layout': isWorkspace }">
    <GlobalHeader v-if="!isWorkspace" />
    <a-layout-content class="content">
      <RouterView :key="route.path" />
    </a-layout-content>
    <GlobalFooter v-if="!isWorkspace" />
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100dvh;
  background: var(--app-bg);
}
.content {
  width: 100%;
  padding: 24px 32px;
}
.workspace-layout {
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
}
.workspace-layout .content {
  padding: 0;
  min-height: 0;
  display: flex;
}
@media (max-width: 720px) {
  .content {
    padding: 16px;
  }
}
</style>
