import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatHistoryManagePage from '@/pages/admin/ChatHistoryManagePage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
    },
    {
      path: '/user/login',
      name: 'userLogin',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: 'userRegister',
      component: UserRegisterPage,
    },
    {
      path: '/app/chat/:id',
      name: 'appChat',
      component: AppChatPage,
    },
    {
      path: '/app/edit/:id',
      name: 'appEdit',
      component: AppEditPage,
    },
    {
      path: '/admin/userManage',
      name: 'userManage',
      component: UserManagePage,
      // 标记该页面仅管理员可访问，供全局权限校验使用
      meta: {
        access: 'admin',
      },
    },
    {
      path: '/admin/appManage',
      name: 'appManage',
      component: AppManagePage,
      meta: {
        access: 'admin',
      },
    },
    {
      path: '/admin/chatManage',
      name: 'chatManage',
      component: ChatHistoryManagePage,
      meta: {
        access: 'admin',
      },
    },
  ],
})

export default router
