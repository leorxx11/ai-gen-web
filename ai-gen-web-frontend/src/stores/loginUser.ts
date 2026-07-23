import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUser } from '@/api/userController'

/**
 * 登录用户全局状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 默认为未登录状态
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  // 从后端获取当前登录用户信息
  async function fetchLoginUser() {
    const res = await getLoginUser()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    }
  }

  // 设置登录用户信息（登录成功 / 注销后调用）
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchLoginUser, setLoginUser }
})
