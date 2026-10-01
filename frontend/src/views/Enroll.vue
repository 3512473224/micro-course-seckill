<template>
  <el-card class="enroll-card">
    <h2>📝 确认选课</h2>
    <el-skeleton v-if="!course" :rows="4" animated />
    <div v-else>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="课程">{{ course.name }}</el-descriptions-item>
        <el-descriptions-item label="教师">{{ course.teacher }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ course.credit }}</el-descriptions-item>
        <el-descriptions-item label="简介">{{ course.description }}</el-descriptions-item>
        <el-descriptions-item label="剩余名额">{{ remain }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button @click="$router.back()">返回</el-button>
        <!-- 普通选课：走 Seata 分布式事务（建单 + 扣名额要么都成功要么都回滚） -->
        <el-button type="primary" :loading="submitting" @click="submit(false)">确认选课（Seata 事务）</el-button>
        <!-- 对比演示：无 Seata，名额不足时选课单会残留 -->
        <el-button type="warning" :loading="submitting" @click="submit(true)">对比：无事务选课</el-button>
      </div>
      <p class="hint">提示：两个按钮调不同接口，演示"分布式事务 vs 无事务"的差异，详见 README 演示步骤。</p>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const route = useRoute()
const router = useRouter()
const courseId = route.params.courseId
const course = ref(null)
const remain = ref('-')
const submitting = ref(false)

onMounted(async () => {
  course.value = await http.get('/course/' + courseId)
  try {
    remain.value = await http.get('/seat/' + courseId)
  } catch { remain.value = '未知' }
})

async function submit(noSeata) {
  submitting.value = true
  try {
    const url = noSeata ? '/enroll/create-no-seata' : '/enroll/create'
    const enrollId = await http.post(url, { courseId: Number(courseId), quantity: 1 })
    ElMessage.success('选课成功！选课单 id=' + enrollId)
    router.push('/my-courses')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.enroll-card { max-width: 640px; margin: 40px auto; }
.actions { margin-top: 20px; display: flex; gap: 12px; }
.hint { color: #909399; font-size: 12px; margin-top: 12px; }
</style>
