import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Enroll from '../views/Enroll.vue'
import MyCourses from '../views/MyCourses.vue'

const routes = [
  { path: '/', name: 'Home', component: Home, meta: { title: '选课首页' } },
  { path: '/login', name: 'Login', component: Login, meta: { title: '登录' } },
  { path: '/enroll/:courseId', name: 'Enroll', component: Enroll, meta: { title: '确认选课', auth: true } },
  { path: '/my-courses', name: 'MyCourses', component: MyCourses, meta: { title: '我的课程表', auth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：需要登录的页面没 token 就踢到登录页（网关也会二次校验，双保险）
router.beforeEach(to => {
  if (to.meta.auth && !localStorage.getItem('course_token')) {
    return '/login'
  }
})

export default router
