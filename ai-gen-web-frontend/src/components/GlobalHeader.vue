<script setup lang="ts">
import { h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import type { MenuProps } from 'ant-design-vue'

const route = useRoute()
const router = useRouter()

// 菜单配置项，key 为路由路径，新增菜单只需在此追加配置
const menuItems = ref<MenuProps['items']>([
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/about',
    icon: () => h(InfoCircleOutlined),
    label: '关于',
    title: '关于',
  },
])

// 当前选中的菜单项，跟随路由变化
const selectedKeys = ref<string[]>([route.path])
watch(
  () => route.path,
  (path) => {
    selectedKeys.value = [path]
  },
)

// 点击菜单跳转到对应路由
const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(String(key))
}
</script>

<template>
  <a-layout-header class="global-header">
    <a-row :wrap="false" align="middle">
      <!-- 左侧：Logo 和网站标题 -->
      <a-col flex="200px">
        <RouterLink to="/" class="header-left">
          <img class="logo" src="@/assets/logo.png" alt="Logo" />
          <h1 class="site-title">AI 应用生成</h1>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto" class="header-menu">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区（暂用登录按钮替代用户头像和昵称） -->
      <a-col flex="120px" class="header-right">
        <a-button type="primary">登录</a-button>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  height: 64px;
  padding-inline: 20px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.header-left {
  display: flex;
  align-items: center;
  height: 64px;
}

.logo {
  width: 40px;
  height: 40px;
}

.site-title {
  margin: 0 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
}

/* min-width: 0 让菜单在窄屏下可收缩，超出的菜单项自动折叠为省略号 */
.header-menu {
  min-width: 0;
}

.header-menu :deep(.ant-menu-horizontal) {
  border-bottom: none;
}

.header-right {
  text-align: right;
}

/* 小屏幕下隐藏网站标题，保证菜单和按钮有足够空间 */
@media (max-width: 768px) {
  .site-title {
    display: none;
  }
}
</style>
