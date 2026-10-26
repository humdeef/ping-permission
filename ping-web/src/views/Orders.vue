<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>订单管理</span>
          <div class="filters">
            <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 190px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-input v-model="query.userName" placeholder="下单用户" clearable style="width: 140px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-select v-model="query.status" placeholder="订单状态" clearable style="width: 130px"
                       @change="handleSearch">
              <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="userName" label="下单用户" width="150" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusText || statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updateByName" label="最后修改人" width="120" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px; text-align: right" background
                     layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 30, 50, 100]"
                     :total="total" :page-size="pageSize" v-model:current-page="page"
                     @current-change="fetchData" @size-change="handleSizeChange" />
    </el-card>

    <el-dialog v-model="detailVisible" title="订单详情" width="860px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="订单 ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="下单用户">
          {{ detail.userName }}（App 用户，ID {{ detail.userId }}）
        </el-descriptions-item>
        <el-descriptions-item label="订单金额">¥{{ formatAmount(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="statusType(detail.status)">{{ detail.statusText || statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="乐观锁版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="最后修改人">
          {{ detail.updateByName }}（{{ detail.updateByTypeText || operatorTypeText(detail.updateByType) }}，ID {{ detail.updateById }}）
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">操作日志</el-divider>
      <el-table :data="logs" border size="small" v-loading="logsLoading">
        <el-table-column prop="operation" label="操作" width="90" />
        <el-table-column label="操作人" width="160">
          <template #default="{ row }">
            {{ row.operatorName }}（{{ operatorTypeText(row.operatorType) }}）
          </template>
        </el-table-column>
        <el-table-column label="状态变化" width="180">
          <template #default="{ row }">
            {{ row.beforeStatus == null ? '—' : statusText(row.beforeStatus) }}
            →
            {{ row.afterStatus == null ? '—' : statusText(row.afterStatus) }}
          </template>
        </el-table-column>
        <el-table-column prop="changeSummary" label="变更摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getOrders, getOrder, getOrderLogs } from '../api/index'

const STATUS_OPTIONS = [
  { value: 0, label: '待支付' },
  { value: 1, label: '已支付' },
  { value: 2, label: '已取消' },
  { value: 3, label: '已完成' }
]
const STATUS_TEXT = Object.fromEntries(STATUS_OPTIONS.map(s => [s.value, s.label]))
const OPERATOR_TYPE_TEXT = { 1: '后台用户', 2: 'App用户', 3: '系统', 4: '匿名' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ orderNo: '', userName: '', status: null })

const detailVisible = ref(false)
const detail = ref(null)
const logs = ref([])
const logsLoading = ref(false)

function statusText(code) {
  return STATUS_TEXT[code] || '未知'
}

function statusType(code) {
  return { 0: 'warning', 1: 'success', 2: 'info', 3: 'primary' }[code] || 'info'
}

function operatorTypeText(code) {
  return OPERATOR_TYPE_TEXT[code] || '未知'
}

function formatAmount(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(2) : '0.00'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : ''
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getOrders({
      page: page.value,
      size: pageSize.value,
      orderNo: query.orderNo || undefined,
      userName: query.userName || undefined,
      status: query.status ?? undefined
    })
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

function handleReset() {
  query.orderNo = ''
  query.userName = ''
  query.status = null
  handleSearch()
}

function handleSizeChange(size) {
  pageSize.value = size
  page.value = 1
  fetchData()
}

async function openDetail(row) {
  detailVisible.value = true
  detail.value = row
  logs.value = []
  logsLoading.value = true
  try {
    const [orderRes, logRes] = await Promise.all([getOrder(row.id), getOrderLogs(row.id)])
    detail.value = orderRes.data || row
    logs.value = logRes.data || []
  } finally {
    logsLoading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 8px; }
</style>
