<template>
  <div class="page">
    <div class="page-head">
      <h2>管理后台</h2>
      <p>排课管理、选课阶段开关与数据看板。</p>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="admin-tabs">
      <!-- ============ 排课管理 ============ -->
      <el-tab-pane label="排课管理" name="courses">
        <div class="toolbar">
          <el-button type="primary" @click="openCourseDialog()">＋ 新增课程</el-button>
          <el-button @click="loadCourses">刷新</el-button>
        </div>
        <div v-loading="courseLoading">
          <el-result v-if="courseError" icon="error" title="课程加载失败" :sub-title="courseError">
            <template #extra><el-button type="primary" @click="loadCourses">重试</el-button></template>
          </el-result>
          <template v-else>
            <el-table v-if="courses.length" :data="courses" style="width: 100%">
              <el-table-column prop="name" label="课程" min-width="150" />
              <el-table-column prop="teacher" label="教师" width="100" />
              <el-table-column label="时间" min-width="170">
                <template #default="{ row }">{{ timeText(row) }}</template>
              </el-table-column>
              <el-table-column prop="classroom" label="教室" width="110" />
              <el-table-column prop="credit" label="学分" width="65" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).text }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="230" align="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openCourseDialog(row)">编辑</el-button>
                  <el-button link type="success" :loading="pubId === row.id" @click="publish(row)">发布</el-button>
                  <el-button link type="warning" :loading="pubId === row.id" @click="offline(row)">下架</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="还没有课程，点击「新增课程」创建" />
          </template>
        </div>
      </el-tab-pane>

      <!-- ============ 阶段管理 ============ -->
      <el-tab-pane label="阶段管理" name="phases">
        <div class="toolbar">
          <el-button type="primary" @click="openPhaseDialog()">＋ 新增阶段</el-button>
          <el-button @click="loadPhases">刷新</el-button>
        </div>
        <div v-loading="phaseLoading">
          <el-result v-if="phaseError" icon="error" title="阶段加载失败" :sub-title="phaseError">
            <template #extra><el-button type="primary" @click="loadPhases">重试</el-button></template>
          </el-result>
          <template v-else>
            <el-table v-if="phases.length" :data="phases" style="width: 100%">
              <el-table-column prop="name" label="阶段名称" min-width="150" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }"><el-tag>{{ phaseTypeName(row.type) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="开始时间" width="170"><template #default="{ row }">{{ formatDateTime(row.startTime) }}</template></el-table-column>
              <el-table-column label="结束时间" width="170"><template #default="{ row }">{{ formatDateTime(row.endTime) }}</template></el-table-column>
              <el-table-column label="启用" width="90">
                <template #default="{ row }">
                  <el-switch :model-value="isPhaseOn(row)" @change="togglePhase(row)" :loading="toggleId === row.id" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="right">
                <template #default="{ row }">
                  <el-button
                    v-if="String(row.type).toUpperCase() === 'WISH'"
                    link type="warning"
                    :loading="settleId === row.id"
                    @click="settleWish(row)">结算志愿</el-button>
                  <el-button link type="primary" @click="openPhaseDialog(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="还没有选课阶段，点击「新增阶段」创建（如：志愿填报 / 正选 / 补退选）" />
          </template>
        </div>
      </el-tab-pane>

      <!-- ============ 数据看板 ============ -->
      <el-tab-pane label="数据看板" name="dashboard">
        <div v-loading="dashLoading">
          <el-result v-if="dashError" icon="error" title="看板加载失败" :sub-title="dashError">
            <template #extra><el-button type="primary" @click="loadDashboard">重试</el-button></template>
          </el-result>
          <template v-else>
            <div class="dash-cards">
              <div class="dash-card" v-for="d in dashboard" :key="d.courseId">
                <div class="dash-card-head">
                  <strong>{{ d.name }}</strong>
                  <span class="muted small">{{ d.enrolled }}/{{ d.total }} 人</span>
                </div>
                <el-progress :percentage="rateOf(d)" :stroke-width="10" :color="barColor(d)" />
                <div class="dash-card-foot">
                  <span class="muted small">选课率 {{ rateOf(d) }}%</span>
                  <span class="small score">★ {{ avgOf(d) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="dashboard.length === 0" description="暂无看板数据" />

            <!-- 热门排行：纯 div 条形图，不引入图表库 -->
            <h3 v-if="ranking.length" class="rank-title">热门课程排行 <span class="muted small">（按选课人数）</span></h3>
            <div v-if="ranking.length" class="rank-list">
              <div v-for="(d, i) in ranking" :key="d.courseId" class="rank-row">
                <span class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                <span class="rank-name">{{ d.name }}</span>
                <div class="rank-bar"><div class="rank-fill" :style="{ width: (maxEnrolled ? (d.enrolled / maxEnrolled * 100) : 0) + '%' }"></div></div>
                <span class="rank-num">{{ d.enrolled }} 人</span>
              </div>
            </div>
          </template>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 课程新增/编辑弹窗 -->
    <el-dialog v-model="courseDialog" :title="editingCourse ? '编辑课程' : '新增课程'" width="560px">
      <el-form :model="courseForm" label-width="90px" :rules="courseRules" ref="courseFormRef">
        <el-form-item label="课程名称" prop="name"><el-input v-model="courseForm.name" /></el-form-item>
        <el-form-item label="授课教师" prop="teacher"><el-input v-model="courseForm.teacher" placeholder="教师姓名" /></el-form-item>
        <el-form-item label="学分" prop="credit"><el-input-number v-model="courseForm.credit" :min="0.5" :max="10" :step="0.5" /></el-form-item>
        <el-form-item label="课程简介"><el-input v-model="courseForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="上课星期" prop="weekday">
          <el-select v-model="courseForm.weekday" placeholder="选择星期">
            <el-option v-for="(w, i) in WEEKDAYS" :key="i" :label="w" :value="i + 1" />
          </el-select>
        </el-form-item>
        <el-form-item label="节次">
          <el-col :span="11"><el-input-number v-model="courseForm.startSection" :min="1" :max="12" style="width: 100%" /></el-col>
          <el-col :span="2" class="center">—</el-col>
          <el-col :span="11"><el-input-number v-model="courseForm.endSection" :min="1" :max="12" style="width: 100%" /></el-col>
        </el-form-item>
        <el-form-item label="周次"><el-input v-model="courseForm.weeks" placeholder="如：1-16" /></el-form-item>
        <el-form-item label="教室"><el-input v-model="courseForm.classroom" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="courseForm.status" placeholder="选择状态">
            <el-option label="可选（发布）" :value="1" />
            <el-option label="未发布（草稿）" :value="0" />
            <el-option label="已下架" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialog = false">取消</el-button>
        <el-button type="primary" :loading="courseSaving" @click="saveCourse">保存</el-button>
      </template>
    </el-dialog>

    <!-- 阶段新增/编辑弹窗 -->
    <el-dialog v-model="phaseDialog" :title="editingPhase ? '编辑阶段' : '新增阶段'" width="520px">
      <el-form :model="phaseForm" label-width="90px" :rules="phaseRules" ref="phaseFormRef">
        <el-form-item label="阶段名称" prop="name"><el-input v-model="phaseForm.name" placeholder="如：2026秋季正选" /></el-form-item>
        <el-form-item label="阶段类型" prop="type">
          <el-select v-model="phaseForm.type" placeholder="选择类型">
            <el-option label="志愿填报" value="WISH" />
            <el-option label="正选" value="MAIN" />
            <el-option label="补退选" value="ADD" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="phaseForm.startTime" type="datetime" placeholder="选择开始时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="phaseForm.endTime" type="datetime" placeholder="选择结束时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="phaseDialog = false">取消</el-button>
        <el-button type="primary" :loading="phaseSaving" @click="savePhase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { WEEKDAYS, timeText, statusTag, phaseTypeName, formatDateTime } from '../utils/format'

const activeTab = ref('courses')

/* ---------- 排课管理 ---------- */
const courses = ref([])
const courseLoading = ref(false)
const courseError = ref('')
const pubId = ref(null)

const courseDialog = ref(false)
const editingCourse = ref(null)
const courseSaving = ref(false)
const courseFormRef = ref(null)
const courseForm = reactive({
  name: '', teacher: '', credit: 2, description: '',
  weekday: null, startSection: 1, endSection: 2,
  weeks: '1-16', classroom: '', status: 0
})
const courseRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  teacher: [{ required: true, message: '请输入授课教师', trigger: 'blur' }],
  credit: [{ required: true, message: '请设置学分', trigger: 'blur' }],
  weekday: [{ required: true, message: '请选择上课星期', trigger: 'change' }]
}

function openCourseDialog(row) {
  editingCourse.value = row || null
  Object.assign(courseForm, row
    ? { ...row }
    : { name: '', teacher: '', credit: 2, description: '', weekday: null, startSection: 1, endSection: 2, weeks: '1-16', classroom: '', status: 0 })
  courseDialog.value = true
}

async function saveCourse() {
  try {
    await courseFormRef.value.validate()
  } catch {
    return
  }
  if (courseForm.endSection < courseForm.startSection) {
    ElMessage.warning('结束节不能小于开始节')
    return
  }
  courseSaving.value = true
  try {
    if (editingCourse.value) {
      await http.put('/course/admin/' + editingCourse.value.id, { ...courseForm })
      ElMessage.success('课程已更新')
    } else {
      await http.post('/course/admin', { ...courseForm })
      ElMessage.success('课程已创建')
    }
    courseDialog.value = false
    await loadCourses()
  } finally {
    courseSaving.value = false
  }
}

async function publish(row) {
  pubId.value = row.id
  try {
    await http.post(`/course/admin/${row.id}/publish`)
    ElMessage.success(`「${row.name}」已发布，学生可见`)
    await loadCourses()
  } finally {
    pubId.value = null
  }
}

async function offline(row) {
  try {
    await ElMessageBox.confirm(`确定下架「${row.name}」吗？下架后学生将无法再选该课程。`, '下架确认',
      { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  pubId.value = row.id
  try {
    await http.post(`/course/admin/${row.id}/offline`)
    ElMessage.success(`「${row.name}」已下架`)
    await loadCourses()
  } finally {
    pubId.value = null
  }
}

async function loadCourses() {
  courseLoading.value = true
  courseError.value = ''
  try {
    const data = await http.get('/course/list')
    courses.value = Array.isArray(data) ? data : []
  } catch (e) {
    courseError.value = e.message || '加载失败'
  } finally {
    courseLoading.value = false
  }
}

/* ---------- 阶段管理 ---------- */
const phases = ref([])
const phaseLoading = ref(false)
const phaseError = ref('')
const toggleId = ref(null)
const settleId = ref(null)

const phaseDialog = ref(false)
const editingPhase = ref(null)
const phaseSaving = ref(false)
const phaseFormRef = ref(null)
const phaseForm = reactive({ name: '', type: 'MAIN', startTime: '', endTime: '' })
const phaseRules = {
  name: [{ required: true, message: '请输入阶段名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择阶段类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

function isPhaseOn(p) {
  const s = String(p.status ?? '').toUpperCase()
  return s === '1' || s === 'ON' || s === 'ENABLED' || s === 'OPEN' || p.enabled === true
}

function openPhaseDialog(row) {
  editingPhase.value = row || null
  Object.assign(phaseForm, row
    ? { name: row.name, type: row.type, startTime: row.startTime, endTime: row.endTime }
    : { name: '', type: 'MAIN', startTime: '', endTime: '' })
  phaseDialog.value = true
}

async function savePhase() {
  try {
    await phaseFormRef.value.validate()
  } catch {
    return
  }
  if (new Date(phaseForm.endTime) <= new Date(phaseForm.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  phaseSaving.value = true
  try {
    if (editingPhase.value) {
      await http.put('/course/admin/phase/' + editingPhase.value.id, { ...phaseForm })
      ElMessage.success('阶段已更新')
    } else {
      await http.post('/course/admin/phase', { ...phaseForm })
      ElMessage.success('阶段已创建')
    }
    phaseDialog.value = false
    await loadPhases()
  } finally {
    phaseSaving.value = false
  }
}

async function togglePhase(row) {
  toggleId.value = row.id
  try {
    await http.post(`/course/admin/phase/${row.id}/toggle`)
    ElMessage.success(`阶段「${row.name}」状态已切换`)
    await loadPhases()
  } finally {
    toggleId.value = null
  }
}

async function settleWish(row) {
  try {
    await ElMessageBox.confirm(
      `结算「${row.name}」的全部志愿？将按"志愿优先级→填报时间"自动录取，录满的进入候补名单，结算后该阶段关闭。`,
      '结算志愿', { type: 'warning', confirmButtonText: '开始结算' })
  } catch {
    return
  }
  settleId.value = row.id
  try {
    const data = await http.post(`/enroll/admin/wish/settle?phaseId=${row.id}`)
    ElMessage.success(`结算完成：录取 ${data?.admitted ?? 0} 人，进入候补 ${data?.waitlisted ?? 0} 人`)
    await loadPhases()
  } finally {
    settleId.value = null
  }
}

async function loadPhases() {
  phaseLoading.value = true
  phaseError.value = ''
  try {
    const data = await http.get('/course/admin/phase')
    phases.value = Array.isArray(data) ? data : []
  } catch (e) {
    phaseError.value = e.message || '加载失败'
  } finally {
    phaseLoading.value = false
  }
}

/* ---------- 数据看板 ---------- */
const dashboard = ref([])
const dashLoading = ref(false)
const dashError = ref('')

const ranking = computed(() =>
  [...dashboard.value].sort((a, b) => (b.enrolled || 0) - (a.enrolled || 0)).slice(0, 5))
const maxEnrolled = computed(() =>
  ranking.value.length ? Math.max(...ranking.value.map(d => d.enrolled || 0)) : 0)

function rateOf(d) {
  const r = Number(d.rate)
  if (!Number.isNaN(r)) return Math.min(100, Math.round(r > 1 ? r : r * 100))
  if (d.total) return Math.min(100, Math.round((d.enrolled / d.total) * 100))
  return 0
}
function avgOf(d) {
  const a = Number(d.avgScore)
  return Number.isNaN(a) ? '—' : a.toFixed(1)
}
function barColor(d) {
  const r = rateOf(d)
  if (r >= 90) return '#c96f1e'
  if (r >= 60) return '#e8862e'
  return '#1e3a5f'
}

async function loadDashboard() {
  dashLoading.value = true
  dashError.value = ''
  try {
    const data = await http.get('/course/admin/dashboard')
    dashboard.value = Array.isArray(data) ? data : []
  } catch (e) {
    dashError.value = e.message || '加载失败'
  } finally {
    dashLoading.value = false
  }
}

loadCourses()
loadPhases()
loadDashboard()
</script>

<style scoped>
.admin-tabs {
  border-radius: var(--brand-radius);
  overflow: hidden;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.center {
  text-align: center;
  line-height: 32px;
}

/* 看板卡片 */
.dash-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}
.dash-card {
  border: 1px solid var(--brand-border);
  border-radius: 10px;
  padding: 16px;
  background: #fbfcfe;
}
.dash-card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
  gap: 8px;
}
.dash-card-head strong {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dash-card-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}
.score {
  color: #e8862e;
  font-weight: 700;
}
.small {
  font-size: 12px;
}

/* 热门排行：纯 div 条形图 */
.rank-title {
  font-size: 16px;
  margin: 0 0 14px;
}
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 720px;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rank-no {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #eef1f5;
  color: var(--brand-text-2);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rank-no.top {
  background: #e8862e;
  color: #fff;
}
.rank-name {
  width: 180px;
  font-size: 13.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}
.rank-bar {
  flex: 1;
  height: 10px;
  background: #eef1f5;
  border-radius: 6px;
  overflow: hidden;
}
.rank-fill {
  height: 100%;
  background: linear-gradient(90deg, #1e3a5f, #e8862e);
  border-radius: 6px;
  transition: width 0.4s;
}
.rank-num {
  font-size: 13px;
  color: var(--brand-text-2);
  width: 56px;
  text-align: right;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .rank-name {
    width: 110px;
  }
}
</style>
