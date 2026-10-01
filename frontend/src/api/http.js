import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 所有请求统一走 /api（开发环境 vite 代理到网关 :8080，线上 nginx 转发到 gateway 容器）
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截：把 localStorage 的 token 塞进 Authorization 头，网关靠它鉴权
http.interceptors.request.use(config => {
  const token = localStorage.getItem('course_token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截：后端统一返回 {code, msg, data}
// code=200 才算成功，直接把 data 抛给调用方；401 踢回登录页
http.interceptors.response.use(
  res => {
    const { code, msg, data } = res.data
    if (code === 200) {
      return data
    }
    if (code === 401) {
      localStorage.removeItem('course_token')
      localStorage.removeItem('course_user')
      router.push('/login')
    }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg || '请求失败'))
  },
  err => {
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http
