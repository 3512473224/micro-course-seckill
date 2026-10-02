<template>
  <div class="page">
    <div class="error-wrap">
      <el-result :icon="code === 403 ? 'warning' : 'error'"
        :title="code === 403 ? '无权访问' : '页面不存在'"
        :sub-title="hint">
        <template #extra>
          <el-button type="primary" @click="$router.push('/')">返回选课广场</el-button>
          <el-button v-if="!isLoggedIn()" @click="$router.push('/login')">去登录</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { isLoggedIn } from '../store/user'

const props = defineProps({
  code: { type: Number, default: 404 }
})

const hint = computed(() =>
  props.code === 403
    ? '当前账号的角色没有访问该页面的权限。如需访问，请使用对应角色的账号登录。'
    : '你访问的页面不存在或已被移动，请检查链接是否正确。')
</script>

<style scoped>
.error-wrap {
  max-width: 560px;
  margin: 60px auto;
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  padding: 24px;
}
</style>
