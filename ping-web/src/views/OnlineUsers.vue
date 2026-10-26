<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>在线用户</span>
          <div class="filters">
            <el-input v-model="username" placeholder="用户名" clearable style="width: 160px" @keyup.enter="fetchData" />
            <el-button type="primary" @click="fetchData">查询</el-button>
            <el-button @click="fetchData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="userAgent" label="User-Agent" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="登录时间" width="180" />
        <el-table-column label="剩余有效期" width="120">
          <template #default="{ row }">{{ formatSeconds(row.timeoutSeconds) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="canKickout" type="danger" link @click="handleKickout(row)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineUsers, kickoutUser } from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const userStore = useUserStore()
const canKickout = computed(() => userStore.hasPerm(PERM.ONLINE_DELETE))

const loading = ref(false)
const tableData = ref([])
const username = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await getOnlineUsers({ username: username.value || undefined })
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleKickout(row) {
  ElMessageBox.confirm(`确认将用户「${row.username || row.userId}」强制下线吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await kickoutUser(row.token)
      ElMessage.success('已强制下线')
      fetchData()
    })
    .catch(() => {})
}

function formatSeconds(seconds) {
  if (!seconds || seconds <= 0) return '已过期'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return h > 0 ? `${h} 小时 ${m} 分` : `${m} 分钟`
}

onMounted(fetchData)
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 8px; }
</style>
