<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-brand">
        <span class="brand-mark">衡</span>
        <h2>学衡教务 · 选课系统</h2>
        <p class="muted">格物致知，衡以选之</p>
      </div>

      <el-form :model="form" label-width="0" @submit.prevent="onLogin" class="login-form">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock"
            show-password size="large" autocomplete="current-password" @keyup.enter="onLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="onLogin" class="login-btn">登 录</el-button>
        </el-form-item>
      </el-form>

      <div class="divider"></div>
      <div class="accounts">
        <div class="muted">测试账号</div>
        <div class="account-row" v-for="a in accounts" :key="a.u">
          <span>{{ a.label }}：<code>{{ a.u }} / {{ a.p }}</code></span>
          <el-button link type="primary" size="small" @click="fill(a)">填入</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import http from '../api/http'
import { saveToken, saveUser, homePathByRole } from '../store/user'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = ref({ username: 'student01', password: '123456' })

const accounts = [
  { label: '学生', u: 'student01', p: '123456' },
  { label: '教师', u: 'teacher01', p: '123456' },
  { label: '管理员', u: 'admin', p: '123456' }
]

function fill(a) {
  form.value = { username: a.u, password: a.p }
}

async function onLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // 登录返回 {token, userId, username, nickname, role}
    const data = await http.post('/user/login', { ...form.value })
    saveToken(data.token)
    saveUser({
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      role: data.role
    })
    ElMessage.success('登录成功，欢迎 ' + (data.nickname || data.username))
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : homePathByRole(data.role))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 60px - 57px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  background:
    radial-gradient(600px 300px at 20% 10%, rgba(30, 58, 95, 0.08), transparent),
    radial-gradient(500px 260px at 85% 90%, rgba(232, 134, 46, 0.08), transparent),
    var(--brand-bg);
  box-sizing: border-box;
}
.login-card {
  width: 400px;
  max-width: 100%;
  border-top: 4px solid #e8862e;
}
.login-brand {
  text-align: center;
  margin-bottom: 24px;
}
.brand-mark {
  display: inline-flex;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1e3a5f, #2c4f7c);
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  box-shadow: 0 6px 16px rgba(30, 58, 95, 0.3);
}
.login-brand h2 {
  margin: 0 0 6px;
  font-size: 20px;
  letter-spacing: 1px;
}
.login-form {
  margin-top: 8px;
}
.login-btn {
  width: 100%;
  letter-spacing: 6px;
  font-size: 16px;
}
.accounts {
  font-size: 13px;
}
.account-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  color: var(--brand-text-2);
}
.account-row code {
  background: #eef1f5;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12.5px;
}
</style>
