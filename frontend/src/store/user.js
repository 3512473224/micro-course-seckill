import { reactive } from 'vue'

const TOKEN_KEY = 'course_token'
const USER_KEY = 'course_user_info'

// 全局登录态：token 存 course_token，用户信息存 course_user_info
export const userStore = reactive({
  user: null // {userId, username, nickname, role}
})

export function loadUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    userStore.user = raw ? JSON.parse(raw) : null
  } catch {
    userStore.user = null
  }
}

export function saveUser(info) {
  localStorage.setItem(USER_KEY, JSON.stringify(info))
  userStore.user = info
}

export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearUser() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  userStore.user = null
}

export function isLoggedIn() {
  return !!localStorage.getItem(TOKEN_KEY)
}

export const ROLE_NAMES = {
  student: '学生',
  teacher: '教师',
  admin: '管理员'
}

export function roleName(role) {
  return ROLE_NAMES[role] || role || '未知'
}

// 登录后按角色跳转：admin→/admin，teacher→/teacher，student→/
export function homePathByRole(role) {
  if (role === 'admin') return '/admin'
  if (role === 'teacher') return '/teacher'
  return '/'
}
