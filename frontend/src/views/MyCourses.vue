<template>
  <el-card>
    <h2>🗓️ 我的课程表</h2>
    <el-table :data="orders" stripe style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="选课单" width="90" />
      <el-table-column prop="orderNo" label="单号" width="170" />
      <el-table-column prop="courseName" label="课程" />
      <el-table-column prop="credit" label="学分" width="80" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="row.orderType === 1 ? 'danger' : 'success'">
            {{ row.orderType === 1 ? '秒杀抢课' : '普通选课' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ row.status === 1 ? '已选上' : row.status === 2 ? '已取消' : '待确认' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="选课时间" width="180" />
    </el-table>
    <el-empty v-if="!loading && orders.length === 0" description="还没有选课，快去首页抢课吧" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api/http'

const orders = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    orders.value = await http.get('/enroll/my')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
h2 { margin: 0 0 16px; }
</style>
