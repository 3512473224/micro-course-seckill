<template>
  <div class="page">
    <!-- Hero：产品介绍 + 三个亮点数据 -->
    <section class="hero">
      <div class="hero-text">
        <div class="hero-kicker">学衡教务 · 智能选课</div>
        <h1>把每一门好课，都选到手。</h1>
        <p class="hero-sub">志愿填报、正选抢课、候补排队全流程线上化——时间冲突自动提醒，热门课程秒杀级并发也稳得住。</p>
      </div>
      <div class="hero-stats" v-loading="statsLoading">
        <div class="hero-stat">
          <div class="stat-num">{{ stats.courseCount }}</div>
          <div class="muted">本学期开设课程</div>
        </div>
        <div class="hero-stat">
          <div class="stat-num">{{ stats.teacherCount }}</div>
          <div class="muted">授课教师</div>
        </div>
        <div class="hero-stat">
          <div class="stat-num">{{ stats.creditSum }}</div>
          <div class="muted">课程总学分</div>
        </div>
      </div>
    </section>

    <!-- 当前阶段横幅 -->
    <section class="phase-banner" v-loading="phaseLoading">
      <el-result v-if="phaseError" icon="error" :title="'阶段信息加载失败'" :sub-title="phaseError">
        <template #extra><el-button type="primary" @click="loadPhase">重试</el-button></template>
      </el-result>
      <div v-else-if="phase" class="phase-active">
        <div class="phase-info">
          <el-tag :type="phaseTagType" size="large" effect="dark">{{ phaseTypeName(phase.type) }}</el-tag>
          <div>
            <div class="phase-name">{{ phase.name }}</div>
            <div class="muted">{{ formatDateTime(phase.startTime) }} 至 {{ formatDateTime(phase.endTime) }}</div>
          </div>
        </div>
        <div class="phase-countdown">
          <span class="muted">{{ countdownLabel }}</span>
          <strong>{{ countdown }}</strong>
        </div>
      </div>
      <div v-else class="phase-idle">
        <span class="idle-dot"></span>
        <span>当前不在选课阶段，请关注教务通知</span>
      </div>
    </section>

    <!-- 筛选 -->
    <section class="filters">
      <el-input v-model="keyword" placeholder="搜索课程名 / 教师 / 教室" clearable class="filter-item" :prefix-icon="Search" />
      <el-select v-model="weekdayFilter" placeholder="星期" clearable class="filter-item narrow">
        <el-option label="全部星期" value="" />
        <el-option v-for="(w, i) in WEEKDAYS" :key="i" :label="w" :value="i + 1" />
      </el-select>
      <el-select v-model="creditFilter" placeholder="学分" clearable class="filter-item narrow">
        <el-option label="全部学分" value="" />
        <el-option v-for="c in creditOptions" :key="c" :label="c + ' 学分'" :value="c" />
      </el-select>
      <span class="muted result-count">共 {{ filtered.length }} 门课程</span>
    </section>

    <!-- 课程卡片流 -->
    <section v-loading="loading">
      <el-result v-if="loadError" icon="error" title="课程加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadAll">重试</el-button></template>
      </el-result>
      <template v-else>
        <el-row :gutter="18" v-if="filtered.length">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="c in filtered" :key="c.id" class="card-col">
            <el-card shadow="hover" class="course-card" :body-style="{ padding: 0 }">
              <div class="cover-block" :style="{ background: coverGradient(c.id) }">{{ coverChar(c.name) }}</div>
              <div class="card-body">
                <div class="card-title-row">
                  <h3 class="card-title" @click="goDetail(c.id)">{{ c.name }}</h3>
                  <el-tag :type="statusTag(c.status).type" size="small">{{ statusTag(c.status).text }}</el-tag>
                </div>
                <p class="card-meta">{{ c.teacher }} · {{ c.credit }} 学分</p>
                <p class="card-meta muted">{{ timeText(c) }}</p>
                <p class="card-meta muted">{{ c.classroom || '教室待定' }}</p>
                <div class="card-actions">
                  <el-button v-if="primaryAction" type="primary" size="small" @click="onPrimary(c)">{{ primaryAction.label }}</el-button>
                  <el-button v-else type="primary" size="small" disabled>不在选课阶段</el-button>
                  <el-button size="small" @click="joinWaitlist(c)" :loading="actingId === 'w' + c.id">候补</el-button>
                  <el-button v-if="canSeckill" size="small" type="warning" @click="doSeckill(c)" :loading="actingId === 's' + c.id">秒杀</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="没有符合条件的课程，换个筛选试试" />
      </template>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../api/http'
import { isLoggedIn } from '../store/user'
import {
  WEEKDAYS, weekdayName, timeText, coverGradient, coverChar,
  statusTag, phaseTypeName, countdownText, formatDateTime
} from '../utils/format'

const router = useRouter()

const courses = ref([])
const loading = ref(false)
const loadError = ref('')
const statsLoading = ref(false)

const phase = ref(null)
const phaseLoading = ref(false)
const phaseError = ref('')

const keyword = ref('')
const weekdayFilter = ref('')
const creditFilter = ref('')
const actingId = ref(null)

const now = ref(Date.now())
let timer = null

const stats = computed(() => {
  const list = courses.value
  const teachers = new Set(list.map(c => c.teacherId ?? c.teacher))
  return {
    courseCount: list.length,
    teacherCount: teachers.size,
    creditSum: list.reduce((s, c) => s + (Number(c.credit) || 0), 0)
  }
})

const creditOptions = computed(() => {
  const set = new Set(courses.value.map(c => Number(c.credit)).filter(n => !Number.isNaN(n)))
  return [...set].sort((a, b) => a - b)
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return courses.value.filter(c => {
    if (kw && !`${c.name} ${c.teacher} ${c.classroom || ''}`.toLowerCase().includes(kw)) return false
    if (weekdayFilter.value !== '' && Number(c.weekday) !== Number(weekdayFilter.value)) return false
    if (creditFilter.value !== '' && Number(c.credit) !== Number(creditFilter.value)) return false
    return true
  })
})

// 主按钮随阶段变化：志愿阶段→填报志愿；正选/补选→选课
const primaryAction = computed(() => {
  if (!phase.value) return null
  const t = phase.value.type
  if (t === 'WISH') return { label: '填报志愿', kind: 'wish' }
  if (t === 'MAIN' || t === 'ADD') return { label: t === 'ADD' ? '补选' : '选课', kind: 'enroll' }
  return { label: '选课', kind: 'enroll' }
})

const canSeckill = computed(() => phase.value && (phase.value.type === 'MAIN' || phase.value.type === 'ADD'))

const phaseTagType = computed(() => {
  const t = phase.value?.type
  if (t === 'WISH') return 'warning'
  if (t === 'MAIN') return 'danger'
  return 'success'
})

// 倒计时：未开始→距离开始；进行中→距离结束
const countdownLabel = computed(() => {
  if (!phase.value) return ''
  return now.value < new Date(phase.value.startTime).getTime() ? `距离${phaseTypeName(phase.value.type)}开始还有` : `距离本阶段结束还有`
})
const countdown = computed(() => {
  if (!phase.value) return '--'
  const start = new Date(phase.value.startTime).getTime()
  const end = new Date(phase.value.endTime).getTime()
  const target = now.value < start ? start : end
  return countdownText(target - now.value)
})

function requireLogin() {
  if (!isLoggedIn()) {
    ElMessage.warning('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: '/' } })
    return false
  }
  return true
}

function goDetail(id) {
  router.push('/course/' + id)
}

async function onPrimary(c) {
  if (!requireLogin()) return
  const kind = primaryAction.value?.kind
  if (kind === 'wish') {
    router.push({ path: '/wish', query: { courseId: c.id } })
    return
  }
  // 普通选课
  actingId.value = 'e' + c.id
  try {
    const data = await http.post('/enroll/create', { courseId: Number(c.id), quantity: 1 })
    const orderId = data && typeof data === 'object' ? (data.orderId ?? data.id ?? '') : data
    ElMessage.success(`选课成功${orderId ? '，订单号 ' + orderId : ''}`)
  } finally {
    actingId.value = null
  }
}

async function joinWaitlist(c) {
  if (!requireLogin()) return
  actingId.value = 'w' + c.id
  try {
    await http.post('/enroll/waitlist', { courseId: Number(c.id) })
    ElMessage.success(`已加入《${c.name}》候补队列，可在「我的选课」查看排队位置`)
  } finally {
    actingId.value = null
  }
}

async function doSeckill(c) {
  if (!requireLogin()) return
  actingId.value = 's' + c.id
  try {
    const data = await http.post('/seckill/' + c.id)
    const msg = data && typeof data === 'object' ? (data.message || '抢课成功') : '抢课成功'
    ElMessage.success(msg)
  } finally {
    actingId.value = null
  }
}

async function loadCourses() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await http.get('/course/list')
    courses.value = Array.isArray(data) ? data : []
  } catch (e) {
    loadError.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadPhase() {
  phaseLoading.value = true
  phaseError.value = ''
  try {
    phase.value = await http.get('/course/phase/current')
  } catch (e) {
    phaseError.value = e.message || '加载失败'
  } finally {
    phaseLoading.value = false
  }
}

async function loadAll() {
  statsLoading.value = true
  try {
    await Promise.all([loadCourses(), loadPhase()])
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => {
  loadAll()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
/* Hero */
.hero {
  background: linear-gradient(120deg, #1e3a5f 0%, #2c4f7c 60%, #3d5f8a 100%);
  border-radius: 16px;
  color: #fff;
  padding: 36px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;
}
.hero::after {
  content: '衡';
  position: absolute;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 160px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.05);
  pointer-events: none;
}
.hero-kicker {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 2px;
  color: #f4c298;
  border: 1px solid rgba(232, 134, 46, 0.6);
  border-radius: 20px;
  padding: 4px 12px;
  margin-bottom: 12px;
}
.hero h1 {
  margin: 0 0 10px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
}
.hero-sub {
  margin: 0;
  color: #b9c6d6;
  font-size: 14px;
  max-width: 520px;
  line-height: 1.7;
}
.hero-stats {
  display: flex;
  gap: 36px;
  flex-shrink: 0;
  z-index: 1;
}
.hero-stat {
  text-align: center;
  min-width: 90px;
}
.hero-stat .stat-num {
  color: #fff;
}
.hero-stat .stat-num::after {
  content: '';
}
.hero-stat .muted {
  color: #b9c6d6;
  font-size: 12px;
  margin-top: 4px;
}

/* 阶段横幅 */
.phase-banner {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  padding: 18px 22px;
  margin-bottom: 20px;
  min-height: 60px;
}
.phase-active {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.phase-info {
  display: flex;
  align-items: center;
  gap: 14px;
}
.phase-name {
  font-size: 17px;
  font-weight: 700;
}
.phase-countdown {
  text-align: right;
}
.phase-countdown strong {
  display: block;
  font-size: 24px;
  color: #e8862e;
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}
.phase-idle {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--brand-text-2);
  font-size: 14px;
  justify-content: center;
  padding: 8px 0;
}
.idle-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #b9c6d6;
}

/* 筛选 */
.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.filter-item {
  width: 260px;
}
.filter-item.narrow {
  width: 140px;
}
.result-count {
  margin-left: auto;
  font-size: 13px;
}

/* 课程卡片 */
.card-col {
  margin-bottom: 18px;
}
.course-card {
  height: 100%;
  overflow: hidden;
  transition: transform 0.2s;
}
.course-card:hover {
  transform: translateY(-3px);
}
.card-body {
  padding: 14px 16px 16px;
}
.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  flex: 1;
  line-height: 1.4;
}
.card-title:hover {
  color: #1e3a5f;
  text-decoration: underline;
}
.card-meta {
  margin: 3px 0;
  font-size: 12.5px;
  color: var(--brand-text-2);
}
.card-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px 20px;
  }
  .hero h1 {
    font-size: 24px;
  }
  .hero-stats {
    gap: 24px;
  }
  .filter-item {
    width: 100%;
  }
  .filter-item.narrow {
    width: calc(50% - 6px);
  }
  .result-count {
    margin-left: 0;
  }
}
</style>
