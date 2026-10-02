import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import CourseDetail from '../views/CourseDetail.vue'
import Schedule from '../views/Schedule.vue'
import Wish from '../views/Wish.vue'
import MyEnroll from '../views/MyEnroll.vue'
import Teacher from '../views/Teacher.vue'
import Admin from '../views/Admin.vue'
import Forbidden from '../views/Forbidden.vue'
import { isLoggedIn, homePathByRole } from '../store/user'

const routes = [
  { path: '/', name: 'Home', component: Home, meta: { title: '选课广场' } },
  { path: '/login', name: 'Login', component: Login, meta: { title: '登录', guest: true } },
  { path: '/course/:id', name: 'CourseDetail', component: CourseDetail, meta: { title: '课程详情' } },
  { path: '/schedule', name: 'Schedule', component: Schedule, meta: { title: '我的课表', auth: true, roles: ['student'] } },
  { path: '/wish', name: 'Wish', component: Wish, meta: { title: '志愿填报', auth: true, roles: ['student'] } },
  { path: '/my', name: 'MyEnroll', component: MyEnroll, meta: { title: '我的选课', auth: true, roles: ['student'] } },
  { path: '/teacher', name: 'Teacher', component: Teacher, meta: { title: '教师工作台', auth: true, roles: ['teacher'] } },
  { path: '/admin', name: 'Admin', component: Admin, meta: { title: '管理后台', auth: true, roles: ['admin'] } },
  { path: '/403', name: 'Forbidden', component: Forbidden, props: { code: 403 }, meta: { title: '无权访问' } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: Forbidden, props: { code: 404 }, meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

function currentRole() {
  try {
    const raw = localStorage.getItem('course_user_info')
    return raw ? JSON.parse(raw).role : null
  } catch {
    return null
  }
}

// 路由守卫：
// 1. 需要登录的页面没 token → 踢到登录页
// 2. 角色不符（如学生进 /admin）→ 403 友好页
// 3. 已登录访问 /login → 按角色跳首页
router.beforeEach(to => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '学衡教务选课系统'
  const logged = isLoggedIn()

  if (to.meta.guest && logged) {
    return homePathByRole(currentRole())
  }
  if ((to.meta.auth || to.meta.roles) && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && logged) {
    const role = currentRole()
    if (!to.meta.roles.includes(role)) {
      return '/403'
    }
  }
})

export default router
