<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>接口资源</span>
          <div>
            <el-input
              v-model="searchPath"
              placeholder="按路径筛选"
              clearable
              style="width: 220px; margin-right: 8px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd">新增接口资源</el-button>
          </div>
        </div>
      </template>
      <div v-if="filterCode" style="margin-bottom: 12px">
        <el-tag type="info">当前权限：{{ filterName || filterCode }}（{{ filterCode }}）</el-tag>
        <el-button type="primary" link @click="clearFilter">清除筛选</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="permissionCode" label="权限编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="path" label="接口路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="method" label="请求方法" width="100" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
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
        @current-change="handlePageChange"
      @size-change="handleSizeChange"/>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑接口资源' : '新增接口资源'" width="520px" @opened="initForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="权限编码" prop="permissionCode">
          <el-select v-model="form.permissionCode" filterable style="width: 100%" placeholder="选择该接口需要的权限">
            <el-option
              v-for="p in permissionOptions"
              :key="p.code"
              :label="`${p.name} (${p.code})`"
              :value="p.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="接口路径" prop="path">
          <el-input v-model="form.path" placeholder="如 /user/pc/users/**" />
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
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="接口描述（可选）" />
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getApiResources, saveApiResource, updateApiResource, deleteApiResource, getAllPermissions
} from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const userStore = useUserStore()
const canAdd = computed(() => userStore.hasPerm(PERM.API_RESOURCE_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.API_RESOURCE_EDIT))
const canDelete = computed(() => userStore.hasPerm(PERM.API_RESOURCE_DELETE))

const loading = ref(false)
const route = useRoute()
const router = useRouter()
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const searchPath = ref('')
const filterCode = ref('')
const filterName = ref('')
const permissionList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null, permissionCode: '', path: '', method: '*', status: 1, description: '', sort: 0
})
const rules = {
  permissionCode: [{ required: true, message: '请选择权限编码', trigger: 'change' }],
  path: [{ required: true, message: '请输入接口路径', trigger: 'blur' }],
  method: [{ required: true, message: '请选择请求方法', trigger: 'change' }]
}

// 权限列表接口返回平铺数组，下拉展示全部
const permissionOptions = computed(() => permissionList.value)

async function fetchData() {
  loading.value = true
  try {
    const res = await getApiResources({
      page: page.value - 1,
      size: pageSize.value,
      path: searchPath.value || undefined,
      permissionCode: filterCode.value || undefined
    })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function clearFilter() {
  filterCode.value = ''
  filterName.value = ''
  page.value = 1
  router.replace({ path: '/api-resources' })
  fetchData()
}

async function loadPermissions() {
  try {
    const res = await getAllPermissions()
    permissionList.value = res.data || []
  } catch {
    // 权限列表加载失败时保留空列表，不影响其他操作
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchPath.value = ''
  filterCode.value = ''
  filterName.value = ''
  page.value = 1
  if (route.query && route.query.permissionCode) {
    router.replace({ path: '/api-resources' })
  }
  fetchData()
}

function handlePageChange(p) {
  page.value = p
  fetchData()
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: null, permissionCode: '', path: '', method: '*', status: 1, description: '', sort: 0 })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    permissionCode: row.permissionCode,
    path: row.path,
    method: row.method || '*',
    status: row.status !== null ? row.status : 1,
    description: row.description || '',
    sort: row.sort || 0
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateApiResource(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await saveApiResource(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除接口资源「${row.method} ${row.path}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteApiResource(row.id)
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

onMounted(() => {
  if (route.query.code) {
    filterCode.value = String(route.query.code)
    filterName.value = route.query.name ? String(route.query.name) : ''
  }
  fetchData()
  loadPermissions()
})
</script>
