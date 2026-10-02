<template>
  <div class="page">
    <div class="page-head">
      <h2>我的课表</h2>
      <p v-if="!loading && !loadError">已选 {{ enrolled.length }} 门 · 共 {{ totalCredit }} 学分 · 悬停课程块查看详情</p>
    </div>

    <div v-loading="loading" class="sched-wrap">
      <el-result v-if="loadError" icon="error" title="课表加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>

      <template v-else-if="!loading">
        <el-empty v-if="enrolled.length === 0" description="还没有选上课程，快去选课广场抢课吧">
          <el-button type="primary" @click="$router.push('/')">去选课广场</el-button>
        </el-empty>

        <!-- 周视图：周一到周日 × 1-12节，课程块按星期/节次定位 -->
        <div v-else class="sched-scroll">
          <div class="sched-grid">
            <div class="corner"></div>
            <div v-for="(d, i) in WEEKDAYS" :key="i" class="day-head" :class="{ today: i + 1 === todayWeekday }">{{ d }}</div>

            <template v-for="s in 12" :key="s">
              <div class="sec-label">第{{ s }}节</div>
              <div v-for="d in 7" :key="d" class="cell"></div>
            </template>

            <el-tooltip v-for="b in blocks" :key="b.id"
              :content="tipText(b)" placement="top" :show-after="200">
              <div class="course-block" :style="blockStyle(b)" @click="$router.push('/course/' + b.courseId)">
                <div class="block-name">{{ b.courseName }}</div>
                <div class="block-room">{{ b.classroom || '' }}</div>
              </div>
            </el-tooltip>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api/http'
import { WEEKDAYS, weekdayName, coverGradient } from '../utils/format'

const enrolled = ref([])
const loading = ref(false)
const loadError = ref('')

// JS getDay(): 0=周日 → 转为 1-7（周一到周日）
const todayWeekday = ((new Date().getDay() + 6) % 7) + 1

const totalCredit = computed(() =>
  enrolled.value.reduce((s, o) => s + (Number(o.credit) || 0), 0))

// 只显示"已选上"的订单；缺星期/节次信息的订单过滤掉
const blocks = computed(() =>
  enrolled.value.filter(o =>
    Number(o.weekday) >= 1 && Number(o.weekday) <= 7 &&
    Number(o.startSection) >= 1 && Number(o.endSection) >= Number(o.startSection)))

function blockStyle(b) {
  return {
    gridColumn: `${b.weekday + 1}`,
    gridRow: `${b.startSection + 1} / ${b.endSection + 2}`,
    background: coverGradient(b.courseId)
  }
}

function tipText(b) {
  return `${b.courseName}｜${b.teacher || ''}｜${weekdayName(b.weekday)}第${b.startSection}-${b.endSection}节｜${b.weeks ? b.weeks + '周' : ''}｜${b.classroom || ''}`
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await http.get('/enroll/my')
    const list = Array.isArray(data) ? data : []
    enrolled.value = list.filter(o => Number(o.status) === 1)
  } catch (e) {
    loadError.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sched-wrap {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  padding: 18px;
  min-height: 300px;
}
.sched-scroll {
  overflow-x: auto;
}
.sched-grid {
  display: grid;
  grid-template-columns: 64px repeat(7, minmax(96px, 1fr));
  grid-template-rows: 38px repeat(12, 56px);
  min-width: 760px;
  gap: 2px;
  background: #eef1f5;
  border: 1px solid #e2e7ee;
  border-radius: 10px;
  overflow: hidden;
}
.corner {
  background: #e8edf3;
}
.day-head {
  background: #e8edf3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--brand-text-2);
}
.day-head.today {
  background: #1e3a5f;
  color: #fff;
}
.sec-label {
  background: #f7f9fb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--brand-text-3);
}
.cell {
  background: #fff;
}
.course-block {
  border-radius: 8px;
  color: #fff;
  padding: 6px 8px;
  margin: 2px;
  cursor: pointer;
  overflow: hidden;
  z-index: 2;
  box-shadow: 0 2px 8px rgba(15, 31, 51, 0.25);
  transition: transform 0.15s;
}
.course-block:hover {
  transform: scale(1.03);
}
.block-name {
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.block-room {
  font-size: 11px;
  opacity: 0.85;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
