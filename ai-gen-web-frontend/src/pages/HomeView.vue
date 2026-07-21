<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { healthCheck } from '@/api/healthController'

// 后端健康检查结果
const health = ref<string>('检测中...')

onMounted(async () => {
  try {
    const res = await healthCheck()
    health.value = res.data.data ?? '无返回数据'
    message.success('后端连接成功')
  } catch (e) {
    health.value = '连接失败'
    message.error('后端连接失败，请确认后端服务已启动')
    console.error('healthCheck error:', e)
  }
})
</script>

<template>
  <div class="home-page">
    <h2>首页</h2>
    <p>欢迎使用零代码生成平台</p>
    <p>后端健康状态：{{ health }}</p>
  </div>
</template>

<style scoped>
.home-page {
  padding: 24px;
}
</style>
