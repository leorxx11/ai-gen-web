<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const theme = {
  token: {
    colorPrimary: '#216b50',
    colorInfo: '#216b50',
    colorSuccess: '#216b50',
    colorText: '#20362c',
    colorTextSecondary: '#6a7b71',
    colorBorder: '#e0e8e2',
    colorBgLayout: '#f7f9f7',
    borderRadius: 10,
    controlHeight: 38,
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
}
</script>

<template>
  <a-config-provider :theme="theme" :locale="zhCN">
    <BasicLayout />
  </a-config-provider>
</template>
