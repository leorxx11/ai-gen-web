<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeOutlined, LogoutOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message, type MenuProps } from 'ant-design-vue'
import { logout } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 菜单配置项，key 为路由路径，新增菜单只需在此追加配置
const originItems: MenuProps['items'] = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
    title: '用户管理',
  },
]

// 根据登录用户权限过滤菜单：管理员页面仅对 admin 展示
const menuItems = computed<MenuProps['items']>(() =>
  originItems?.filter((item) => {
    const key = String(item?.key ?? '')
    if (key.startsWith('/admin')) {
      return loginUserStore.loginUser.userRole === 'admin'
    }
    return true
  }),
)

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

// 用户注销
const doLogout = async () => {
  const res = await logout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('退出登录成功')
    router.push('/user/login')
  } else {
    message.error('退出登录失败：' + res.data.message)
  }
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
      <!-- 右侧：用户操作区 -->
      <a-col flex="120px" class="header-right">
        <div v-if="loginUserStore.loginUser.id">
          <a-dropdown>
            <a-space class="user-info">
              <a-avatar :src="loginUserStore.loginUser.userAvatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              {{ loginUserStore.loginUser.userName ?? '无名' }}
            </a-space>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div v-else>
          <a-button type="primary" href="/user/login">登录</a-button>
        </div>
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

.user-info {
  cursor: pointer;
}

/* 小屏幕下隐藏网站标题，保证菜单和按钮有足够空间 */
@media (max-width: 768px) {
  .site-title {
    display: none;
  }
}
</style>
