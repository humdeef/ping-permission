<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>白名单管理</span>
          <div class="filters">
            <el-input v-model="searchPath" placeholder="接口路径" clearable style="width: 200px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd">新增白名单</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="path" label="接口路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="method" label="请求方法" width="100" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="canEdit" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; text-align: right"
        background
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 30, 50, 100]"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
        @current-change="fetchData"
      @size-change="handleSizeChange"/>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑白名单' : '新增白名单'" width="480px" @opened="initForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="接口路径" prop="path">
          <el-input v-model="form.path" placeholder="如 /user/pc/public/** 或 /user/pc/auth/login" />
        </el-form-item>
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="form.method" style="width: 100%">
            <el-option label="任意 (*)" value="*" />
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="接口描述（可选）" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWhitelists, saveWhitelist, updateWhitelist, deleteWhitelist } from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const userStore = useUserStore()
const canAdd = computed(() => userStore.hasPerm(PERM.WHITELIST_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.WHITELIST_EDIT))
const canDelete = computed(() => userStore.hasPerm(PERM.WHITELIST_DELETE))

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const searchPath = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({ id: null, path: '', method: '*', description: '', status: 1 })
const rules = {
  path: [{ required: true, message: '请输入接口路径', trigger: 'blur' }],
  method: [{ required: true, message: '请选择请求方法', trigger: 'change' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getWhitelists({
      page: page.value - 1,
      size: pageSize.value,
      path: searchPath.value || undefined
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
  searchPath.value = ''
  page.value = 1
  fetchData()
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: null, path: '', method: '*', description: '', status: 1 })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, path: row.path, method: row.method, description: row.description || '', status: row.status !== null ? row.status : 1 })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateWhitelist(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await saveWhitelist(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除白名单「${row.path}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteWhitelist(row.id)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

function initForm() {
  formRef.value?.clearValidate()
}

function handleSizeChange(size) {
  pageSize.value = size
  page.value = 1
  fetchData()
}

onMounted(() => fetchData())
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
