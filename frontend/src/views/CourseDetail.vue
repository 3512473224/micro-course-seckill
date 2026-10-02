<template>
  <div class="page">
    <el-button link @click="$router.back()" class="back-btn">← 返回</el-button>

    <div v-loading="loading">
      <el-result v-if="loadError" icon="error" title="课程加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadAll">重试</el-button></template>
      </el-result>

      <template v-else-if="course">
        <!-- 课程信息头 -->
        <el-card class="head-card" :body-style="{ padding: 0 }">
          <div class="head-cover" :style="{ background: coverGradient(course.id) }">
            <span class="head-char">{{ coverChar(course.name) }}</span>
            <div class="head-main">
              <div class="head-tags">
                <el-tag :type="statusTag(course.status).type">{{ statusTag(course.status).text }}</el-tag>
                <el-tag type="info">{{ course.credit }} 学分</el-tag>
              </div>
              <h1>{{ course.name }}</h1>
              <p class="teacher-line">授课教师：{{ course.teacher }}</p>
            </div>
          </div>
          <div class="head-body">
            <div class="info-grid">
              <div class="info-item"><span class="muted">上课时间</span><strong>{{ weekdayName(course.weekday) }} 第{{ course.startSection }}-{{ course.endSection }}节</strong></div>
              <div class="info-item"><span class="muted">周次</span><strong>{{ course.weeks ? course.weeks + '周' : '—' }}</strong></div>
              <div class="info-item"><span class="muted">教室</span><strong>{{ course.classroom || '待定' }}</strong></div>
              <div class="info-item"><span class="muted">课程简介</span><span class="desc">{{ course.description || '暂无简介' }}</span></div>
            </div>
            <div class="head-actions">
              <el-button v-if="primaryAction" type="primary" @click="onPrimary" :loading="acting">{{ primaryAction.label }}</el-button>
              <el-button v-else type="primary" disabled>不在选课阶段</el-button>
              <el-button @click="joinWaitlist" :loading="acting">加入候补</el-button>
              <el-button v-if="canSeckill" type="warning" @click="doSeckill" :loading="acting">秒杀抢课</el-button>
              <el-button link @click="goWish">填报志愿 →</el-button>
            </div>
          </div>
        </el-card>

        <!-- 评价区 -->
        <el-card class="review-card">
          <template #header>
            <div class="review-head">
              <span class="review-title">课程评价</span>
              <span v-if="reviewData" class="review-summary">
                <el-rate :model-value="reviewData.avgScore || 0" disabled show-score
                  :score-template="'{value} 分'" allow-half />
                <span class="muted">共 {{ reviewData.count || 0 }} 条评价</span>
              </span>
            </div>
          </template>

          <div v-loading="reviewLoading">
            <el-empty v-if="!reviewLoading && reviewList.length === 0" description="还没有评价，来做第一个评价的人吧" />
            <div v-else class="review-list">
              <div v-for="(r, i) in reviewList" :key="i" class="review-item">
                <div class="review-meta">
                  <span class="review-user">{{ r.nickname || '匿名同学' }}</span>
                  <el-rate :model-value="Number(r.score) || 0" disabled size="small" />
                  <span class="muted review-time">{{ formatDateTime(r.createdAt) }}</span>
                </div>
                <p class="review-comment">{{ r.comment || '（无文字评价）' }}</p>
              </div>
            </div>
          </div>

          <div class="divider"></div>

          <!-- 我的评价表单：只能评一次 -->
          <div v-if="!isLoggedIn()" class="muted">登录后可参与评价。<el-button link type="primary" @click="$router.push('/login')">去登录</el-button></div>
          <div v-else-if="myReviewed" class="reviewed-tip">
            <el-tag type="success">你已评价过本课程，感谢反馈</el-tag>
          </div>
          <div v-else class="review-form">
            <div class="form-row">
              <span>我的评分</span>
              <el-rate v-model="myScore" allow-half />
            </div>
            <el-input v-model="myComment" type="textarea" :rows="3" maxlength="500" show-word-limit
              placeholder="说说这门课的上课体验、作业量、给分情况……" class="form-comment" />
            <el-button type="primary" @click="submitReview" :loading="submitting" :disabled="!myScore">提交评价</el-button>
          </div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { isLoggedIn, userStore } from '../store/user'
import {
  weekdayName, coverGradient, coverChar, statusTag, formatDateTime
} from '../utils/format'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const course = ref(null)
const loading = ref(false)
const loadError = ref('')
const acting = ref(false)

const phase = ref(null)

const reviewData = ref(null)
const reviewLoading = ref(false)
const myScore = ref(0)
const myComment = ref('')
const submitting = ref(false)

const reviewList = computed(() => reviewData.value?.list || [])

// 是否已评价：用当前登录昵称匹配评价列表
const myReviewed = computed(() => {
  const me = userStore.user?.nickname || userStore.user?.username
  if (!me) return false
  return reviewList.value.some(r => r.nickname === me)
})

const primaryAction = computed(() => {
  if (!phase.value) return null
  const t = phase.value.type
  if (t === 'WISH') return { label: '填报志愿', kind: 'wish' }
  return { label: t === 'ADD' ? '补选' : '选课', kind: 'enroll' }
})
const canSeckill = computed(() => phase.value && (phase.value.type === 'MAIN' || phase.value.type === 'ADD'))

function requireLogin() {
  if (!isLoggedIn()) {
    ElMessage.warning('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

function goWish() {
  router.push({ path: '/wish', query: { courseId } })
}

async function onPrimary() {
  if (!requireLogin()) return
  if (primaryAction.value?.kind === 'wish') {
    goWish()
    return
  }
  acting.value = true
  try {
    const data = await http.post('/enroll/create', { courseId: Number(courseId), quantity: 1 })
    const orderId = data && typeof data === 'object' ? (data.orderId ?? data.id ?? '') : data
    ElMessage.success(`选课成功${orderId ? '，订单号 ' + orderId : ''}`)
  } finally {
    acting.value = false
  }
}

async function joinWaitlist() {
  if (!requireLogin()) return
  acting.value = true
  try {
    await http.post('/enroll/waitlist', { courseId: Number(courseId) })
    ElMessage.success('已加入候补队列，可在「我的选课」查看排队位置')
  } finally {
    acting.value = false
  }
}

async function doSeckill() {
  if (!requireLogin()) return
  acting.value = true
  try {
    const data = await http.post('/seckill/' + courseId)
    const msg = data && typeof data === 'object' ? (data.message || '抢课成功') : '抢课成功'
    ElMessage.success(msg)
  } finally {
    acting.value = false
  }
}

async function submitReview() {
  if (!myScore.value) {
    ElMessage.warning('请先打个分')
    return
  }
  submitting.value = true
  try {
    await http.post('/course/review', { courseId: Number(courseId), score: myScore.value, comment: myComment.value.trim() })
    ElMessage.success('评价提交成功')
    myScore.value = 0
    myComment.value = ''
    await loadReviews()
  } finally {
    submitting.value = false
  }
}

async function loadReviews() {
  reviewLoading.value = true
  try {
    reviewData.value = await http.get('/course/review/' + courseId)
  } catch {
    // 无评价时后端可能返回空，由空状态兜底；错误已由拦截器展示
    reviewData.value = null
  } finally {
    reviewLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  try {
    const [c, p] = await Promise.all([
      http.get('/course/' + courseId),
      http.get('/course/phase/current').catch(() => null)
    ])
    course.value = c
    phase.value = p
    await loadReviews()
  } catch (e) {
    loadError.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.back-btn {
  margin-bottom: 12px;
  color: var(--brand-text-2);
}
.head-card {
  overflow: hidden;
  margin-bottom: 20px;
}
.head-cover {
  padding: 32px 28px;
  display: flex;
  align-items: center;
  gap: 20px;
  color: #fff;
}
.head-char {
  width: 84px;
  height: 84px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42px;
  font-weight: 700;
  flex-shrink: 0;
}
.head-main h1 {
  margin: 10px 0 6px;
  font-size: 26px;
  letter-spacing: 1px;
}
.head-tags {
  display: flex;
  gap: 8px;
}
.teacher-line {
  margin: 0;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
}
.head-body {
  padding: 20px 24px;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.info-item strong {
  font-size: 15px;
}
.info-item .desc {
  color: var(--brand-text-2);
  line-height: 1.7;
  font-size: 13.5px;
}
.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  border-top: 1px solid var(--brand-border);
  padding-top: 18px;
}

.review-card {
  margin-bottom: 24px;
}
.review-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.review-title {
  font-size: 16px;
  font-weight: 700;
}
.review-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}
.review-list {
  display: flex;
  flex-direction: column;
}
.review-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--brand-border);
}
.review-item:last-child {
  border-bottom: none;
}
.review-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.review-user {
  font-weight: 600;
  font-size: 14px;
}
.review-time {
  font-size: 12px;
  margin-left: auto;
}
.review-comment {
  margin: 0;
  color: var(--brand-text-2);
  font-size: 13.5px;
  line-height: 1.7;
}
.review-form .form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 14px;
}
.form-comment {
  margin-bottom: 12px;
}
.reviewed-tip {
  padding: 8px 0;
}

@media (max-width: 768px) {
  .head-cover {
    padding: 24px 18px;
  }
  .head-char {
    width: 60px;
    height: 60px;
    font-size: 30px;
  }
  .head-main h1 {
    font-size: 20px;
  }
}
</style>
