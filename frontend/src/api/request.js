import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 统一请求实例：对接后端 /api 前缀（Vite 代理到 localhost:8080）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截：自动携带 JWT
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理 { code, message, data }，code=0 为成功
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    // 业务错误（400 参数 / 403 越权 / 404 不存在 / 409 冲突 ...）
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    // HTTP 层或网络层错误
    const res = error.response?.data
    if (error.response?.status === 401) {
      // 未登录 / token 过期：清场并跳登录页
      localStorage.removeItem('token')
      ElMessage.warning('登录已过期，请重新登录')
      router.push('/login')
    } else {
      ElMessage.error(res?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
