<template>
  <div class="layout">
    <!-- 顶部导航：深藏青学术风 -->
    <header class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark">衡</span>
          <span class="brand-text">学衡教务 <em>·</em> 选课系统</span>
        </router-link>

        <nav class="nav-links">
          <template v-for="m in menus" :key="m.path">
            <router-link :to="m.path" class="nav-link" active-class="active">{{ m.label }}</router-link>
          </template>
        </nav>

        <div class="nav-right">
          <template v-if="user">
            <span class="nickname">{{ user.nickname || user.username }}</span>
            <el-tag :type="roleTagType" size="small" effect="dark" class="role-badge">{{ roleName(user.role) }}</el-tag>
            <el-button link class="logout-btn" @click="logout">退出</el-button>
          </template>
          <el-button v-else type="warning" size="small" class="login-btn" @click="$router.push('/login')">登录</el-button>
        </div>
      </div>
    </header>

    <main class="main">
      <router-view :key="$route.fullPath" />
    </main>

    <footer class="footer">
      学衡教务选课系统 · 微服务架构演示：Gateway / user / course / enroll / seat · Nacos · Sentinel · Seata · Redis + Lua
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { userStore, clearUser, roleName } from './store/user'

const router = useRouter()
const user = computed(() => userStore.user)

const roleTagType = computed(() => {
  const r = user.value?.role
  if (r === 'admin') return 'danger'
  if (r === 'teacher') return 'warning'
  return 'success'
})

// 按角色展示菜单：学生-广场/课表/志愿/我的选课；教师-工作台；管理员-后台管理
const menus = computed(() => {
  const role = user.value?.role
  if (role === 'teacher') return [{ path: '/teacher', label: '教师工作台' }]
  if (role === 'admin') return [{ path: '/admin', label: '后台管理' }]
  return [
    { path: '/', label: '选课广场' },
    { path: '/schedule', label: '我的课表' },
    { path: '/wish', label: '志愿填报' },
    { path: '/my', label: '我的选课' }
  ]
})

async function logout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  clearUser()
  router.push('/login')
}
</script>

<style>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  background: linear-gradient(180deg, #1e3a5f 0%, #182e4c 100%);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 12px rgba(15, 31, 51, 0.35);
}
.topbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 28px;
  box-sizing: border-box;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: #fff;
  flex-shrink: 0;
}
.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #e8862e;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  font-style: normal;
}
.brand-text em {
  font-style: normal;
  color: #e8862e;
  margin: 0 2px;
}

.nav-links {
  display: flex;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
}
.nav-link {
  color: #b9c6d6;
  text-decoration: none;
  font-size: 14px;
  padding: 8px 14px;
  border-radius: 8px;
  white-space: nowrap;
  transition: all 0.2s;
}
.nav-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}
.nav-link.active {
  color: #fff;
  background: rgba(232, 134, 46, 0.22);
  box-shadow: inset 0 -2px 0 #e8862e;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.nickname {
  font-size: 14px;
  color: #e8edf3;
}
.role-badge {
  border: none;
}
.logout-btn {
  color: #b9c6d6;
}
.logout-btn:hover {
  color: #fff;
}
.login-btn {
  font-weight: 600;
}

.main {
  flex: 1;
}

.footer {
  text-align: center;
  color: #8a97a5;
  font-size: 12px;
  padding: 18px 12px;
  border-top: 1px solid #e2e7ee;
  background: #fff;
}

@media (max-width: 768px) {
  .topbar-inner {
    padding: 0 12px;
    gap: 12px;
  }
  .brand-text {
    display: none;
  }
  .nickname {
    display: none;
  }
}
</style>
