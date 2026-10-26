<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>登录日志</span>
          <div class="filters">
            <el-input v-model="query.username" placeholder="用户名" clearable style="width: 160px" @keyup.enter="handleSearch" />
            <el-select v-model="query.success" placeholder="结果" clearable style="width: 120px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="userAgent" label="User-Agent" show-overflow-tooltip />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" width="160" />
        <el-table-column prop="createTime" label="登录时间" width="180" />
      </el-table>
      <el-pagination style="margin-top: 16px; text-align: right" background layout="total, sizes, prev, pager, next"
                     :page-sizes="[10, 20, 30, 50, 100]"
                     :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="fetchData" @size-change="handleSizeChange"/>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLoginLogs } from '../api/index'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const query = reactive({ username: '', success: null })

async function fetchData() {
  loading.value = true
  try {
    const res = await getLoginLogs({
      page: page.value - 1,
      size: pageSize.value,
      username: query.username || undefined,
      success: query.success ?? undefined
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
