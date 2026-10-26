<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>操作日志</span>
          <div class="filters">
            <el-input v-model="query.username" placeholder="操作人" clearable style="width: 140px" @keyup.enter="handleSearch" />
            <el-input v-model="query.action" placeholder="动作" clearable style="width: 140px" @keyup.enter="handleSearch" />
            <el-select v-model="query.success" placeholder="结果" clearable style="width: 110px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button :loading="exporting" @click="handleExport">导出 CSV</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="130" />
        <el-table-column prop="action" label="动作" width="140" />
        <el-table-column prop="targetType" label="目标类型" width="110" />
        <el-table-column prop="targetId" label="目标ID" width="90" />
        <el-table-column prop="detail" label="详情" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="链路ID" width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
      <el-pagination style="margin-top: 16px; text-align: right" background layout="total, sizes, prev, pager, next"
                     :page-sizes="[10, 20, 30, 50, 100]"
                     :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="fetchData" @size-change="handleSizeChange"/>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationLogs, exportOperationLogs } from '../api/index'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const query = reactive({ username: '', action: '', success: null })

function params() {
  return {
    username: query.username || undefined,
    action: query.action || undefined,
    success: query.success ?? undefined
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getOperationLogs({ page: page.value - 1, size: pageSize.value, ...params() })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportOperationLogs(params())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'operation-logs.csv'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function handleSizeChange(size) {
  pageSize.value = size
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 8px; }
</style>
