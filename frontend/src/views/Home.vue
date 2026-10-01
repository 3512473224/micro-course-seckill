<template>
  <div>
    <!-- 秒杀抢课专区 -->
    <el-card v-if="seckill" class="seckill-card" shadow="hover">
      <div class="seckill-head">
        <span class="seckill-title">🔥 限时抢课 · {{ seckillCourseName }}</span>
        <el-tag :type="seckillPhase === 'ing' ? 'danger' : 'info'">{{ phaseText }}</el-tag>
      </div>
      <div class="seckill-body">
        <div class="countdown">
          <span v-if="seckillPhase === 'wait'">距离开抢</span>
          <span v-else-if="seckillPhase === 'ing'">距离结束</span>
          <span v-else>本场已结束</span>
          <strong>{{ countdownText }}</strong>
        </div>
        <div class="stock">剩余名额：<strong>{{ remainStock }}</strong></div>
        <el-button
          type="danger"
          size="large"
          :disabled="seckillPhase !== 'ing' || seckilling"
          :loading="seckilling"
          @click="doSeckill">
          {{ seckillPhase === 'ing' ? '立即抢课' : '未到开抢时间' }}
        </el-button>
      </div>
      <div class="seckill-hint">同一学生限抢 1 个名额 · Redis+Lua 原子扣减，超卖是不可能的</div>
    </el-card>

    <!-- 普通课程列表 -->
    <h2>📚 可选课程</h2>
    <el-row :gutter="20">
      <el-col :span="8" v-for="c in courses" :key="c.id" class="course-col">
        <el-card shadow="hover" class="course-card">
          <div class="course-emoji">{{ c.image || '📖' }}</div>
          <h3>{{ c.name }}</h3>
          <p class="teacher">{{ c.teacher }} · {{ c.credit }} 学分</p>
          <p class="desc">{{ c.description }}</p>
          <el-button type="primary" @click="$router.push('/enroll/' + c.id)">去选课</el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="courses.length === 0" description="暂无课程" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

const router = useRouter()
const courses = ref([])
const seckill = ref(null)          // seckill_course 行
const seckillCourseName = ref('')
const remainStock = ref(-1)
const seckilling = ref(false)
const now = ref(Date.now())
let timer = null

const seckillPhase = computed(() => {
  if (!seckill.value) return 'end'
  const t = now.value
  const start = new Date(seckill.value.startTime).getTime()
  const end = new Date(seckill.value.endTime).getTime()
  if (t < start) return 'wait'
  if (t <= end) return 'ing'
  return 'end'
})

const phaseText = computed(() =>
  seckillPhase.value === 'ing' ? '抢课进行中' : seckillPhase.value === 'wait' ? '即将开抢' : '已结束')

const countdownText = computed(() => {
  if (!seckill.value) return '--:--:--'
  const target = seckillPhase.value === 'wait'
    ? new Date(seckill.value.startTime).getTime()
    : new Date(seckill.value.endTime).getTime()
  let s = Math.max(0, Math.floor((target - now.value) / 1000))
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const sec = String(s % 60).padStart(2, '0')
  return `${h}:${m}:${sec}`
})

async function loadCourses() {
  courses.value = (await http.get('/course/list')).filter(c => c.id !== 1001)
}

async function loadSeckill() {
  const list = await http.get('/seckill/active')
  if (list && list.length > 0) {
    seckill.value = list[0]
    // 取课程名展示
    try {
      const course = await http.get('/course/' + seckill.value.courseId)
      seckillCourseName.value = course.name
    } catch { seckillCourseName.value = '秒杀课程' }
    refreshStock()
  }
}

async function refreshStock() {
  if (!seckill.value) return
  try {
    remainStock.value = await http.get('/seckill/stock/' + seckill.value.courseId)
  } catch { /* 忽略轮询失败 */ }
}

async function doSeckill() {
  seckilling.value = true
  try {
    const result = await http.post('/seckill/' + seckill.value.courseId)
    await ElMessageBox.alert(result.message, '抢课结果', { confirmButtonText: '查看我的课程表' })
    router.push('/my-courses')
  } catch {
    // 失败信息已由 http 拦截器 ElMessage 展示（如"名额已抢光"）
    refreshStock()
  } finally {
    seckilling.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadSeckill()])
  timer = setInterval(() => {
    now.value = Date.now()
    refreshStock()
  }, 3000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.seckill-card { margin-bottom: 28px; border: 2px solid #f56c6c; }
.seckill-head { display: flex; justify-content: space-between; align-items: center; }
.seckill-title { font-size: 18px; font-weight: 700; }
.seckill-body { display: flex; align-items: center; gap: 32px; margin: 16px 0; }
.countdown { font-size: 15px; color: #606266; }
.countdown strong { font-size: 28px; color: #f56c6c; margin-left: 8px; font-variant-numeric: tabular-nums; }
.stock strong { font-size: 22px; color: #e6a23c; }
.seckill-hint { font-size: 12px; color: #909399; }
.course-col { margin-bottom: 20px; }
.course-card { text-align: center; min-height: 250px; }
.course-emoji { font-size: 44px; }
.course-card h3 { margin: 8px 0; font-size: 16px; }
.teacher { color: #909399; font-size: 13px; margin: 4px 0; }
.desc { color: #606266; font-size: 13px; min-height: 38px; }
h2 { margin: 8px 0 16px; }
</style>
