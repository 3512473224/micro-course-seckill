<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>教师工作台</h2>
        <p>开设课程、查看选课名单、导出点名册，一站式搞定。</p>
      </div>
      <el-button type="primary" @click="openCreate">＋ 开设课程</el-button>
    </div>

    <!-- 我的开课列表 -->
    <el-card>
      <div v-loading="loading">
        <el-result v-if="loadError" icon="error" title="课程列表加载失败" :sub-title="loadError">
          <template #extra><el-button type="primary" @click="loadCourses">重试</el-button></template>
        </el-result>
        <template v-else>
          <el-table v-if="courses.length" :data="courses" style="width: 100%">
            <el-table-column prop="name" label="课程" min-width="160" />
            <el-table-column label="时间" min-width="180">
              <template #default="{ row }">{{ timeText(row) }}</template>
            </el-table-column>
            <el-table-column prop="classroom" label="教室" width="120" />
            <el-table-column prop="credit" label="学分" width="70" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRoster(row)">选课名单</el-button>
                <el-button link type="warning" :loading="exportingId === row.id" @click="exportCsv(row)">导出 CSV</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="还没有开设课程，点击右上角「开设课程」创建第一门课" />
        </template>
      </div>
    </el-card>

    <!-- 开课表单弹窗 -->
    <el-dialog v-model="dialogVisible" title="开设课程" width="560px">
      <el-form :model="form" label-width="90px" :rules="rules" ref="formRef">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" placeholder="如：操作系统" />
        </el-form-item>
        <el-form-item label="学分" prop="credit">
          <el-input-number v-model="form.credit" :min="0.5" :max="10" :step="0.5" />
        </el-form-item>
        <el-form-item label="课程简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="课程内容、考核方式……" />
        </el-form-item>
        <el-form-item label="上课星期" prop="weekday">
          <el-select v-model="form.weekday" placeholder="选择星期">
            <el-option v-for="(w, i) in WEEKDAYS" :key="i" :label="w" :value="i + 1" />
          </el-select>
        </el-form-item>
        <el-form-item label="节次" prop="startSection">
          <el-col :span="11"><el-input-number v-model="form.startSection" :min="1" :max="12" placeholder="开始节" style="width: 100%" /></el-col>
          <el-col :span="2" class="center">—</el-col>
          <el-col :span="11"><el-input-number v-model="form.endSection" :min="1" :max="12" placeholder="结束节" style="width: 100%" /></el-col>
        </el-form-item>
        <el-form-item label="周次" prop="weeks">
          <el-input v-model="form.weeks" placeholder="如：1-16" />
        </el-form-item>
        <el-form-item label="教室" prop="classroom">
          <el-input v-model="form.classroom" placeholder="如：教3-201" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="500" placeholder="选课人数上限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 选课名单抽屉 -->
    <el-drawer v-model="drawerVisible" :title="`选课名单 · ${currentCourse?.name || ''}`" size="520px">
      <div v-loading="rosterLoading">
        <el-empty v-if="!rosterLoading && roster.length === 0" description="还没有学生选这门课" />
        <el-table v-else :data="roster" style="width: 100%">
          <el-table-column prop="orderNo" label="单号" width="150" show-overflow-tooltip />
          <el-table-column prop="nickname" label="姓名" />
          <el-table-column prop="username" label="学号" width="120" />
          <el-table-column prop="createdAt" label="选课时间" width="160" :formatter="dt" />
        </el-table>
      </div>
      <template #footer>
        <el-button type="warning" :loading="exportingId === currentCourse?.id" @click="exportCsv(currentCourse)">导出 CSV</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import http from '../api/http'
import { WEEKDAYS, timeText, statusTag, formatDateTime } from '../utils/format'

const courses = ref([])
const loading = ref(false)
const loadError = ref('')

const dialogVisible = ref(false)
const creating = ref(false)
const formRef = ref(null)
const form = reactive({
  name: '',
  credit: 2,
  description: '',
  weekday: null,
  startSection: 1,
  endSection: 2,
  weeks: '1-16',
  classroom: '',
  capacity: 60
})
const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  credit: [{ required: true, message: '请设置学分', trigger: 'blur' }],
  weekday: [{ required: true, message: '请选择上课星期', trigger: 'change' }],
  classroom: [{ required: true, message: '请输入教室', trigger: 'blur' }],
  capacity: [{ required: true, message: '请设置容量', trigger: 'blur' }]
}

const drawerVisible = ref(false)
const currentCourse = ref(null)
const roster = ref([])
const rosterLoading = ref(false)
const exportingId = ref(null)

const dt = (row, col, v) => formatDateTime(v)

function openCreate() {
  Object.assign(form, {
    name: '', credit: 2, description: '', weekday: null,
    startSection: 1, endSection: 2, weeks: '1-16', classroom: '', capacity: 60
  })
  dialogVisible.value = true
}

async function submitCreate() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (form.endSection < form.startSection) {
    ElMessage.warning('结束节不能小于开始节')
    return
  }
  creating.value = true
  try {
    await http.post('/course/teacher', { ...form })
    ElMessage.success(`课程「${form.name}」创建成功，待管理员发布后学生可见`)
    dialogVisible.value = false
    await loadCourses()
  } finally {
    creating.value = false
  }
}

async function openRoster(course) {
  currentCourse.value = course
  drawerVisible.value = true
  rosterLoading.value = true
  try {
    const data = await http.get(`/course/teacher/${course.id}/roster`)
    roster.value = Array.isArray(data) ? data : []
  } finally {
    rosterLoading.value = false
  }
}

// 真实下载 CSV：用原生 axios 拿 blob（http 拦截器会解包 JSON，不适用文件下载）
async function exportCsv(course) {
  if (!course) return
  exportingId.value = course.id
  try {
    const token = localStorage.getItem('course_token')
    const res = await axios.get(`/api/course/teacher/${course.id}/roster/export`, {
      responseType: 'blob',
      headers: token ? { Authorization: 'Bearer ' + token } : {}
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `选课名单_${course.name}_${course.id}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('名单已开始下载')
  } catch {
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exportingId.value = null
  }
}

async function loadCourses() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await http.get('/course/teacher/my')
    courses.value = Array.isArray(data) ? data : []
  } catch (e) {
    loadError.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

loadCourses()
</script>

<style scoped>
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.head-row h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.head-row p {
  margin: 0;
  color: var(--brand-text-2);
  font-size: 13px;
}
.center {
  text-align: center;
  line-height: 32px;
}
</style>
