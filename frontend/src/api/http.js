import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearUser } from '../store/user'

// 所有请求统一走 /api（开发环境 vite 代理到网关 :8080，线上 nginx 转发到 gateway 容器）
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：把 localStorage 的 token 塞进 Authorization 头，网关靠它鉴权
http.interceptors.request.use(config => {
  const token = localStorage.getItem('course_token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 跳转到登录页（加锁避免多个 401 并发时反复 push）
let redirectingToLogin = false
function goLogin() {
  clearUser()
  if (redirectingToLogin) return
  redirectingToLogin = true
  router.push('/login').finally(() => {
    redirectingToLogin = false
  })
}

// 响应拦截：后端统一返回 {code, msg, data}
// code=200 才算成功，直接把 data 抛给调用方；后端 msg 原样展示，不吞错
http.interceptors.response.use(
  res => {
    const { code, msg, data } = res.data || {}
    if (code === 200) {
      return data
    }
    if (code === 401) {
      goLogin()
      return Promise.reject(new Error(msg || '登录已过期，请重新登录'))
    }
    // 403 / 业务失败：把后端 msg 直接展示给用户
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg || '请求失败'))
  },
  err => {
    const status = err?.response?.status
    if (status === 401) {
      goLogin()
      ElMessage.error('登录已过期，请重新登录')
    } else if (status === 403) {
      ElMessage.error('无权访问：' + (err.response?.data?.msg || '权限不足'))
    } else if (err.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
