<template>
  <div class="user-page">
    <!-- 左侧部门树：需要 system:dept:list，且部门加载成功才展示 -->
    <el-card v-if="showDeptTree" class="dept-panel">
      <template #header>
        <span>组织架构</span>
      </template>
      <el-input v-model="treeFilter" placeholder="搜索部门" clearable class="dept-search" />
      <el-tree
        ref="treeRef"
        :data="treeData"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        class="dept-tree"
        @node-click="handleDeptClick"
      />
    </el-card>

    <el-card class="user-main">
      <template #header>
        <div class="header-row">
          <span>用户管理</span>
          <div class="filters">
            <el-input v-model="searchUsername" placeholder="用户名" clearable style="width: 140px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-input v-model="searchNickname" placeholder="昵称" clearable style="width: 140px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-checkbox v-if="selectedDeptId" v-model="includeChildren" @change="handleSearch">
              包含下级
            </el-checkbox>
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd">新增用户</el-button>
          </div>
        </div>
      </template>

      <div v-if="selectedDeptName" style="margin-bottom: 12px">
        <el-tag closable @close="clearDeptFilter">当前部门：{{ selectedDeptName }}</el-tag>
      </div>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="deptName" label="部门" width="140" />
        <el-table-column prop="roleNames" label="角色" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button v-if="canEdit" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canReset" type="warning" link @click="handleResetPassword(row)">重置密码</el-button>
            <el-button v-if="canDelete" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px; text-align: right" background layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 30, 50, 100]" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="fetchData" @size-change="handleSizeChange"/>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="480px" @opened="initForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位，含大小写/数字/特殊字符中的 3 类" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
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

    <!-- 重置密码 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="420px">
      <el-alert title="重置后该用户会被强制下线，并在下次登录时要求修改密码" type="warning" :closable="false" style="margin-bottom: 16px" />
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="80px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password
                    placeholder="至少 8 位，含大小写/数字/特殊字符中的 3 类" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="handleResetSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, getUser, saveUser, updateUser, deleteUser, getAllRoles, getDepts, resetUserPassword } from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'
import { passwordValidator } from '../utils/password'

const userStore = useUserStore()
const isAdmin = computed(() => (userStore.roles || []).includes('admin'))
const canAdd = computed(() => userStore.hasPerm(PERM.USER_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.USER_EDIT))
const canDelete = computed(() => isAdmin.value && userStore.hasPerm(PERM.USER_DELETE))
const canReset = computed(() => userStore.hasPerm(PERM.USER_RESET_PWD))
// 部门树依赖 system:dept:list，没有该权限时保持原来的平铺布局
const canViewDeptTree = computed(() => userStore.hasPerm(PERM.DEPT_LIST))

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const searchUsername = ref('')
const searchNickname = ref('')
const roleOptions = ref([])
const deptList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 部门树
const treeRef = ref(null)
const treeFilter = ref('')
const selectedDeptId = ref(null)
const selectedDeptName = ref('')
const includeChildren = ref(true)
const deptTreeReady = ref(false)
const showDeptTree = computed(() => canViewDeptTree.value && deptTreeReady.value)

const form = reactive({
  userId: null, username: '', password: '', nickname: '', email: '', phone: '',
  deptId: null, roleIds: [], status: 1
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' }
  ]
}

const resetDialogVisible = ref(false)
const resetSubmitting = ref(false)
const resetFormRef = ref(null)
const resetTarget = ref(null)
const resetForm = reactive({ password: '' })
const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' }
  ]
}

const deptTree = computed(() => buildTree(deptList.value))
const treeData = computed(() => [{ id: 0, name: '全部用户', children: deptTree.value }])

function buildTree(list) {
  const nodes = list.map(d => ({ ...d, children: [] }))
  const map = new Map(nodes.map(n => [n.id, n]))
  const roots = []
  nodes.forEach(n => {
    const parent = map.get(n.parentId)
    if (parent && n.parentId !== n.id) {
      parent.children.push(n)
    } else {
      roots.push(n)
    }
  })
  return roots
}

watch(treeFilter, value => {
  treeRef.value?.filter(value)
})

function filterNode(value, data) {
  if (!value) {
    return true
  }
  const keyword = value.toLowerCase()
  const selfMatch = (data.name || '').toLowerCase().includes(keyword)
      || (data.code || '').toLowerCase().includes(keyword)
  if (selfMatch) {
    return true
  }
  return (data.children || []).some(child => filterNode(value, child))
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getUsers({
      page: page.value - 1,
      size: pageSize.value,
      username: searchUsername.value || undefined,
      nickname: searchNickname.value || undefined,
      deptId: selectedDeptId.value || undefined,
      includeChildren: includeChildren.value
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
  searchUsername.value = ''
  searchNickname.value = ''
  selectedDeptId.value = null
  selectedDeptName.value = ''
  includeChildren.value = true
  page.value = 1
  nextTick(() => treeRef.value?.setCurrentKey(0))
  fetchData()
}

function handleDeptClick(data) {
  if (data.id === 0) {
    selectedDeptId.value = null
    selectedDeptName.value = ''
  } else {
    selectedDeptId.value = data.id
    selectedDeptName.value = data.name
  }
  page.value = 1
  fetchData()
}

function clearDeptFilter() {
  selectedDeptId.value = null
  selectedDeptName.value = ''
  page.value = 1
  nextTick(() => treeRef.value?.setCurrentKey(0))
  fetchData()
}

async function loadRoles() {
  try {
    const res = await getAllRoles()
    roleOptions.value = res.data || []
  } catch {
    // 角色列表加载失败时保留空列表，不影响其他操作
  }
}

async function loadDepts() {
  try {
    const res = await getDepts()
    deptList.value = res.data || []
    deptTreeReady.value = true
    await nextTick()
    treeRef.value?.setCurrentKey(0)
  } catch {
    // 没有部门权限或接口失败时降级为平铺列表
    deptTreeReady.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, {
    userId: null, username: '', password: '', nickname: '', email: '', phone: '',
    deptId: null,
    // 非管理员默认授予自己拥有的角色（后端会校验不能超过自身角色范围）
    roleIds: isAdmin.value ? [] : roleOptions.value.map(r => r.id),
    status: 1
  })
  dialogVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    userId: row.userId,
    username: row.username,
    nickname: row.nickname,
    password: '',
    email: '',
    phone: '',
    deptId: row.deptId ?? null,
    roleIds: row.roleIds || [],
    status: row.status ?? 1
  })
  dialogVisible.value = true
  try {
    const res = await getUser(row.userId)
    const detail = res.data || {}
    form.email = detail.email || ''
    form.phone = detail.phone || ''
    form.nickname = detail.nickname || form.nickname
    form.deptId = detail.deptId ?? form.deptId
    form.status = detail.status ?? form.status
    form.roleIds = detail.roleIds || form.roleIds
  } catch {
    // 详情加载失败时保留列表数据
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.userId, form)
      ElMessage.success('更新成功')
    } else {
      await saveUser(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function handleResetPassword(row) {
  resetTarget.value = row
  resetForm.password = ''
  resetDialogVisible.value = true
}

async function handleResetSubmit() {
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetTarget.value.userId, { password: resetForm.password })
    ElMessage.success('密码已重置')
    resetDialogVisible.value = false
    fetchData()
  } finally {
    resetSubmitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteUser(row.userId)
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
  fetchData()
  loadRoles()
  if (canViewDeptTree.value) {
    loadDepts()
  }
})
</script>

<style scoped>
.user-page { display: flex; gap: 16px; align-items: flex-start; }
.dept-panel { width: 260px; flex-shrink: 0; }
.dept-search { margin-bottom: 10px; }
.dept-tree { max-height: calc(100vh - 230px); overflow-y: auto; }
.user-main { flex: 1; min-width: 0; }
.header-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
@media (max-width: 960px) {
  .user-page { flex-direction: column; }
  .dept-panel { width: 100%; }
  .dept-tree { max-height: 260px; }
}
</style>
