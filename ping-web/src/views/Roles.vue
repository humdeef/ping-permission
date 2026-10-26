<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>角色管理</span>
          <div class="filters">
            <el-input v-model="searchName" placeholder="角色名称" clearable style="width: 150px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-input v-model="searchCode" placeholder="角色编码" clearable style="width: 150px"
                      @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd">新增角色</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="角色名称" />
        <el-table-column prop="code" label="角色编码" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="数据范围" width="140">
          <template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="描述" show-overflow-tooltip />
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="520px"
      @opened="initForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="如 admin、user" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="角色描述（可选）" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="s in scopeOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin && form.dataScope === 5 && canViewDepts" label="自定义部门">
          <el-tree-select
            v-model="form.deptIds"
            :data="deptTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            node-key="id"
            multiple
            show-checkbox
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="canAssignPerms" label="权限分配">
          <div class="perm-tree-box">
            <el-tree
              ref="permTreeRef"
              :data="permissionTreeData"
              :props="{ children: 'children', label: 'name', value: 'id' }"
              node-key="id"
              show-checkbox
              default-expand-all
              :check-strictly="false"
            />
          </div>
          <div style="color: #999; font-size: 12px; margin-top: 4px">
            选中父节点会自动选中所有子节点，反之亦然
          </div>
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
import { getRoles, getRole, saveRole, updateRole, deleteRole, getAllPermissions, getDepts } from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const userStore = useUserStore()
const isAdmin = computed(() => (userStore.roles || []).includes('admin'))
const canAdd = computed(() => userStore.hasPerm(PERM.ROLE_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.ROLE_EDIT))
const canDelete = computed(() => userStore.hasPerm(PERM.ROLE_DELETE))
const canAssignPerms = computed(() => isAdmin.value && userStore.hasPerm(PERM.PERMISSION_LIST))
const canViewDepts = computed(() => userStore.hasPerm(PERM.DEPT_LIST))

const scopeOptions = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '本部门' },
  { value: 3, label: '本部门及下级' },
  { value: 4, label: '仅本人' },
  { value: 5, label: '自定义部门' }
]

const deptList = ref([])
const deptTree = computed(() => {
  const nodes = deptList.value.map(d => ({ ...d, children: [] }))
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
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const searchName = ref('')
const searchCode = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const permTreeRef = ref(null)
const permissionList = ref([])

const form = reactive({
  id: null, name: '', code: '', sort: 0, remark: '',
  dataScope: 4, deptIds: [], permissionIds: []
})
const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

// 将平铺的权限列表转为树形结构
const permissionTreeData = computed(() => {
  const buildTree = (list, parentId) => {
    return list
      .filter(item => item.parentId === parentId)
      .map(item => ({
        ...item,
        children: buildTree(list, item.id)
      }))
  }
  return buildTree(permissionList.value, 0)
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getRoles({
      page: page.value - 1,
      size: pageSize.value,
      name: searchName.value || undefined,
      code: searchCode.value || undefined
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
  searchName.value = ''
  searchCode.value = ''
  page.value = 1
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

function handleAdd() {
  isEdit.value = false
  Object.assign(form, {
    id: null, name: '', code: '', sort: 0, remark: '',
    dataScope: 4, deptIds: [], permissionIds: []
  })
  dialogVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    sort: row.sort,
    remark: row.remark || '',
    dataScope: row.dataScope ?? 4,
    deptIds: [],
    permissionIds: []
  })
  dialogVisible.value = true
  try {
    const res = await getRole(row.id)
    form.permissionIds = res.data?.permissionIds || []
    form.deptIds = res.data?.deptIds || []
  } catch {
    form.permissionIds = []
    form.deptIds = []
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 获取勾选的节点（包含半选状态的父节点）
  if (canAssignPerms.value && permTreeRef.value) {
    const checkedKeys = permTreeRef.value.getCheckedKeys()
    // 未勾选的节点（半选父节点不在此列表中）
    const halfCheckedKeys = permTreeRef.value.getHalfCheckedKeys()
    // 合并：父节点如果被半选说明有子节点被选中，也需要加入
    form.permissionIds = [...new Set([...checkedKeys, ...halfCheckedKeys])]
  } else {
    // 无权限分配能力时不修改权限
    form.permissionIds = null
  }
  // 非自定义数据范围时清空自定义部门
  form.deptIds = form.dataScope === 5 ? form.deptIds : []
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateRole(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await saveRole(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteRole(row.id)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

function scopeLabel(scope) {
  return scopeOptions.find(s => s.value === scope)?.label || '仅本人'
}

async function loadDepts() {
  try {
    const res = await getDepts()
    deptList.value = res.data || []
  } catch {
    // 部门列表加载失败时保留空列表，不影响其他操作
  }
}

function initForm() {
  formRef.value?.clearValidate()
  // 延迟设置半选状态，等树渲染完成
  setTimeout(() => {
    if (permTreeRef.value && form.permissionIds?.length) {
      // 父节点 ID 更小，先设置父节点再设置子节点，避免父节点被子节点反推为全选
      ;[...form.permissionIds].sort((a, b) => a - b)
        .forEach(id => permTreeRef.value.setChecked(id, true, false))
    }
  }, 50)
}

function handleSizeChange(size) {
  pageSize.value = size
  page.value = 1
  fetchData()
}

onMounted(() => {
  fetchData()
  loadPermissions()
  if (canViewDepts.value) loadDepts()
})
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.perm-tree-box {
  width: 100%;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 4px 8px;
  box-sizing: border-box;
}
</style>
