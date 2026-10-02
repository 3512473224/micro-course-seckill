<template>
  <div class="page">
    <div class="page-head">
      <h2>志愿填报</h2>
      <p>第一志愿优先录取，第二志愿作为调剂。每门课程每个志愿位只能填报一次，提交前请确认。</p>
    </div>

    <!-- 两个志愿选择器 -->
    <el-card class="wish-form-card" v-loading="courseLoading">
      <div class="wish-selectors">
        <div class="wish-selector">
          <div class="wish-label"><el-tag type="warning" effect="dark" size="large">第一志愿</el-tag></div>
          <el-select v-model="firstId" placeholder="选择心仪的课程" filterable clearable class="wish-select">
            <el-option v-for="c in courses" :key="c.id" :label="`${c.name}（${c.teacher} · ${c.credit}学分）`" :value="c.id" />
          </el-select>
          <el-button type="warning" @click="submitWish(1)" :loading="submitting === 1" :disabled="!firstId">提交第一志愿</el-button>
        </div>
        <div class="wish-selector">
          <div class="wish-label"><el-tag type="info" effect="dark" size="large">第二志愿</el-tag></div>
          <el-select v-model="secondId" placeholder="选择备选课程" filterable clearable class="wish-select">
            <el-option v-for="c in courses" :key="c.id" :label="`${c.name}（${c.teacher} · ${c.credit}学分）`" :value="c.id" />
          </el-select>
          <el-button type="primary" @click="submitWish(2)" :loading="submitting === 2" :disabled="!secondId">提交第二志愿</el-button>
        </div>
      </div>
      <p class="muted tip">同一门课不能同时占两个志愿位；重复提交会被后端拒绝并提示。</p>
    </el-card>

    <!-- 我的志愿列表 -->
    <el-card class="wish-list-card">
      <template #header><span class="card-title">我的志愿</span></template>
      <div v-loading="listLoading">
        <el-result v-if="listError" icon="error" title="志愿列表加载失败" :sub-title="listError">
          <template #extra><el-button type="primary" @click="loadWishes">重试</el-button></template>
        </el-result>
        <template v-else>
          <el-table v-if="wishes.length" :data="wishes" style="width: 100%">
            <el-table-column label="志愿" width="110">
              <template #default="{ row }">
                <el-tag :type="Number(row.priority) === 1 ? 'warning' : 'info'" effect="dark">
                  {{ Number(row.priority) === 1 ? '第一志愿' : '第二志愿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="课程">
              <template #default="{ row }">
                <el-button link type="primary" @click="$router.push('/course/' + row.courseId)">
                  {{ row.courseName || ('课程 #' + row.courseId) }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="填报时间" width="180" :formatter="dt" />
            <el-table-column label="操作" width="110" align="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeWish(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="还没有填报志愿，先选两门心仪的课程吧" />
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatDateTime } from '../utils/format'

const route = useRoute()

const courses = ref([])
const courseLoading = ref(false)
const wishes = ref([])
const listLoading = ref(false)
const listError = ref('')

const firstId = ref(route.query.courseId ? Number(route.query.courseId) : null)
const secondId = ref(null)
const submitting = ref(0)

const dt = (row, col, v) => formatDateTime(v)

async function submitWish(priority) {
  const courseId = priority === 1 ? firstId.value : secondId.value
  if (!courseId) return
  submitting.value = priority
  try {
    await http.post('/enroll/wish', { courseId: Number(courseId), priority })
    ElMessage.success(`第${priority === 1 ? '一' : '二'}志愿提交成功`)
    if (priority === 1) firstId.value = null
    else secondId.value = null
    await loadWishes()
  } finally {
    submitting.value = 0
  }
}

async function removeWish(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除${Number(row.priority) === 1 ? '第一' : '第二'}志愿「${row.courseName || row.courseId}」吗？`,
      '删除志愿',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete('/enroll/wish/' + row.id)
    ElMessage.success('志愿已删除')
    await loadWishes()
  } catch {
    // 错误已由拦截器展示
  }
}

async function loadCourses() {
  courseLoading.value = true
  try {
    const data = await http.get('/course/list')
    courses.value = Array.isArray(data) ? data : []
  } finally {
    courseLoading.value = false
  }
}

async function loadWishes() {
  listLoading.value = true
  listError.value = ''
  try {
    const data = await http.get('/enroll/wish/my')
    wishes.value = Array.isArray(data) ? data : []
  } catch (e) {
    listError.value = e.message || '加载失败'
  } finally {
    listLoading.value = false
  }
}

onMounted(() => {
  loadCourses()
  loadWishes()
})
</script>

<style scoped>
.wish-form-card {
  margin-bottom: 20px;
}
.wish-selectors {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.wish-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  background: #f7f9fb;
  border: 1px solid var(--brand-border);
  border-radius: 10px;
  padding: 18px;
}
.wish-select {
  width: 100%;
}
.tip {
  margin: 14px 0 0;
  font-size: 12.5px;
}
.card-title {
  font-size: 15px;
  font-weight: 700;
}

@media (max-width: 768px) {
  .wish-selectors {
    grid-template-columns: 1fr;
  }
}
</style>
