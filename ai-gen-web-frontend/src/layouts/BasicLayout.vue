<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<template>
  <a-layout class="basic-layout">
    <!-- 上：全局导航栏 -->
    <GlobalHeader />
    <!-- 中：内容区域，根据路由切换页面 -->
    <a-layout-content class="content">
      <RouterView />
    </a-layout-content>
    <!-- 下：全局底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
}

.content {
  padding: 20px;
  /* 底部留出空间，避免内容被固定在底部的 footer 遮挡 */
  margin-bottom: 56px;
  background: linear-gradient(to right, #fefefe, #fff);
}
</style>
