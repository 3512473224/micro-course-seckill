<template>
  <el-card class="login-card">
    <h2>🎓 学生登录</h2>
    <p class="tip">测试账号：student01 / 123456（教务：admin / admin123）</p>
    <el-form :model="form" label-width="70px" @submit.prevent="onLogin">
      <el-form-item label="用户名">
        <el-input v-model="form.username" placeholder="student01" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" placeholder="123456" show-password />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="onLogin" style="width: 100%">登录</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: 'student01', password: '123456' })

async function onLogin() {
  loading.value = true
  try {
    // http 拦截器已解包 Result，这里 data = {token, userId, username, nickname}
    const data = await http.post('/user/login', form.value)
    localStorage.setItem('course_token', data.token)
    localStorage.setItem('course_user', String(data.userId))
    localStorage.setItem('course_nickname', data.nickname)
    ElMessage.success('登录成功，欢迎 ' + data.nickname)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-card { max-width: 420px; margin: 80px auto; padding: 12px; }
.login-card h2 { text-align: center; }
.tip { text-align: center; color: #909399; font-size: 13px; }
</style>
