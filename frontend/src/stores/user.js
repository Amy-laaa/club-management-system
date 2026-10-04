import { defineStore } from 'pinia'

/**
 * 用户会话状态：token + 基本信息
 * 持久化到 localStorage，刷新页面不丢
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: Number(localStorage.getItem('userId')) || null,
    realName: localStorage.getItem('realName') || '',
    roleCode: localStorage.getItem('roleCode') || '',
  }),
  getters: {
    isLogin: (state) => !!state.token,
    // 角色码: STUDENT / LEADER / UNION_ADMIN / SYS_ADMIN
    role: (state) => state.roleCode,
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userId = data.userId
      this.realName = data.realName
      this.roleCode = data.roleCode
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('realName', data.realName)
      localStorage.setItem('roleCode', data.roleCode)
    },
    clear() {
      this.token = ''
      this.userId = null
      this.realName = ''
      this.roleCode = ''
      ;['token', 'userId', 'realName', 'roleCode'].forEach((k) =>
        localStorage.removeItem(k)
      )
    },
  },
})
