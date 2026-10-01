<template>
  <div class="layout">
    <el-menu mode="horizontal" :router="true" :default-active="$route.path" class="nav">
      <div class="brand">🎓 校园抢课系统</div>
      <el-menu-item index="/">选课首页</el-menu-item>
      <el-menu-item index="/my-courses">我的课程表</el-menu-item>
      <div class="nav-right">
        <span v-if="nickname" class="hello">你好，{{ nickname }}</span>
        <el-button v-if="!nickname" type="primary" link @click="$router.push('/login')">登录</el-button>
        <el-button v-else link @click="logout">退出</el-button>
      </div>
    </el-menu>

    <div class="container">
      <router-view :key="$route.fullPath" />
    </div>

    <div class="footer">
      微服务演示项目：Gateway / user / course / enroll / seat · Nacos · Sentinel · Seata · Redis+Lua
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const nickname = ref(localStorage.getItem('course_nickname') || '')

// 登录/退出后刷新右上角昵称
watch(() => route.fullPath, () => {
  nickname.value = localStorage.getItem('course_nickname') || ''
})

function logout() {
  localStorage.removeItem('course_token')
  localStorage.removeItem('course_user')
  localStorage.removeItem('course_nickname')
  nickname.value = ''
  router.push('/login')
}
</script>

<style>
body { margin: 0; background: #f5f7fa; }
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.nav { align-items: center; padding: 0 24px; }
.brand { font-weight: 700; font-size: 18px; margin-right: 32px; }
.nav-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.hello { font-size: 14px; color: #606266; }
.container { flex: 1; max-width: 1100px; width: 100%; margin: 0 auto; padding: 24px; box-sizing: border-box; }
.footer { text-align: center; color: #909399; font-size: 12px; padding: 16px; }
</style>
