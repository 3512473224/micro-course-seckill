<template>
  <div class="page">
    <div class="page-head">
      <h2>我的选课</h2>
      <p>选课单记录你的每一次选课操作，退课后名额会释放回课程池。</p>
    </div>

    <!-- 选课单列表 -->
    <el-card class="section-card">
      <template #header><span class="card-title">选课单</span></template>
      <div v-loading="orderLoading">
        <el-result v-if="orderError" icon="error" title="选课单加载失败" :sub-title="orderError">
          <template #extra><el-button type="primary" @click="loadOrders">重试</el-button></template>
        </el-result>
        <template v-else>
          <el-table v-if="orders.length" :data="orders" style="width: 100%">
            <el-table-column prop="orderNo" label="单号" width="180" show-overflow-tooltip />
            <el-table-column label="课程" min-width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="$router.push('/course/' + row.courseId)">{{ row.courseName }}</el-button>
                <div class="muted small">{{ timeText(row) }} · {{ row.classroom || '' }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="credit" label="学分" width="70" />
            <el-table-column label="订单类型" width="110">
              <template #default="{ row }">
                <el-tag :type="orderTypeTag(row.orderType).type" size="small">{{ orderTypeTag(row.orderType).text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="orderStatusTag(row.status).type" size="small">{{ orderStatusTag(row.status).text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="选课时间" width="170" :formatter="dt" />
            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-button v-if="Number(row.status) === 1" link type="danger"
                  :loading="droppingId === row.id" @click="dropOrder(row)">退课</el-button>
                <span v-else class="muted small">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="还没有选课记录，去选课广场看看吧">
            <el-button type="primary" @click="$router.push('/')">去选课广场</el-button>
          </el-empty>
        </template>
      </div>
    </el-card>

    <!-- 候补列表 -->
    <el-card class="section-card">
      <template #header><span class="card-title">我的候补</span></template>
      <div v-loading="waitLoading">
        <el-result v-if="waitError" icon="error" title="候补列表加载失败" :sub-title="waitError">
          <template #extra><el-button type="primary" @click="loadWaitlist">重试</el-button></template>
        </el-result>
        <template v-else>
          <el-table v-if="waitlist.length" :data="waitlist" style="width: 100%">
            <el-table-column label="课程" min-width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="$router.push('/course/' + row.courseId)">
                  {{ row.courseName || ('课程 #' + row.courseId) }}
                </el-button>
                <div class="muted small">{{ row.teacher || '' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="排队位置" width="130">
              <template #default="{ row }">
                <el-tag type="warning" effect="plain">第 {{ row.position ?? '—' }} 位</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="加入时间" width="170" :formatter="dt" />
            <el-table-column label="操作" width="120" align="right">
              <template #default="{ row }">
                <el-button link type="danger" :loading="cancelId === row.id" @click="cancelWait(row)">取消候补</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无候补，热门课程没抢到时可以先候补排队" />
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { timeText, orderTypeTag, orderStatusTag, formatDateTime } from '../utils/format'

const orders = ref([])
const orderLoading = ref(false)
const orderError = ref('')
const droppingId = ref(null)

const waitlist = ref([])
const waitLoading = ref(false)
const waitError = ref('')
const cancelId = ref(null)

const dt = (row, col, v) => formatDateTime(v)

async function dropOrder(row) {
  try {
    await ElMessageBox.confirm(
      `确定要退掉「${row.courseName}」吗？退课后名额将释放，需要重新选课。`,
      '退课确认',
      { confirmButtonText: '确认退课', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  droppingId.value = row.id
  try {
    await http.post('/enroll/drop/' + row.id)
    ElMessage.success('退课成功')
    await loadOrders()
  } finally {
    droppingId.value = null
  }
}

async function cancelWait(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.courseName || row.courseId}」的候补吗？`,
      '取消候补',
      { confirmButtonText: '取消候补', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  cancelId.value = row.id
  try {
    await http.delete('/enroll/waitlist/' + row.id)
    ElMessage.success('已取消候补')
    await loadWaitlist()
  } finally {
    cancelId.value = null
  }
}

async function loadOrders() {
  orderLoading.value = true
  orderError.value = ''
  try {
    const data = await http.get('/enroll/my')
    orders.value = Array.isArray(data) ? data : []
  } catch (e) {
    orderError.value = e.message || '加载失败'
  } finally {
    orderLoading.value = false
  }
}

async function loadWaitlist() {
  waitLoading.value = true
  waitError.value = ''
  try {
    const data = await http.get('/enroll/waitlist/my')
    waitlist.value = Array.isArray(data) ? data : []
  } catch (e) {
    waitError.value = e.message || '加载失败'
  } finally {
    waitLoading.value = false
  }
}

onMounted(() => {
  loadOrders()
  loadWaitlist()
})
</script>

<style scoped>
.section-card {
  margin-bottom: 20px;
}
.card-title {
  font-size: 15px;
  font-weight: 700;
}
.small {
  font-size: 12px;
}
</style>
