<template>
  <div>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>部门管理</span>
          <div class="filters">
            <el-input v-model="searchText" placeholder="部门名称/编码" clearable style="width: 180px"
                      @keyup.enter="handleSearch" @clear="handleReset" />
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd(null)">新增部门</el-button>
          </div>
        </div>
      </template>
      <el-table :data="deptTree" row-key="id" border default-expand-all
                :tree-props="{ children: 'children' }" v-loading="loading">
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-if="canAdd" type="primary" link @click="handleAdd(row)">新增下级</el-button>
            <el-button v-if="canEdit" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑部门' : '新增部门'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="selectTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="form.code" placeholder="如 RD" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
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
import { getDepts, saveDept, updateDept, deleteDept } from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const userStore = useUserStore()
const canAdd = computed(() => userStore.hasPerm(PERM.DEPT_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.DEPT_EDIT))
const canDelete = computed(() => userStore.hasPerm(PERM.DEPT_DELETE))

const loading = ref(false)
const deptList = ref([])
const searchText = ref('')
const appliedSearch = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null, parentId: 0, name: '', code: '', leader: '',
  phone: '', email: '', sort: 0, status: 1
})
const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const deptTreeAll = computed(() => buildTree(deptList.value))
const filteredDepts = computed(() => {
  const keyword = appliedSearch.value.trim().toLowerCase()
  if (!keyword) {
    return deptList.value
  }
  const byId = new Map(deptList.value.map(d => [d.id, d]))
  const keep = new Set()
  for (const dept of deptList.value) {
    const name = (dept.name || '').toLowerCase()
    const code = (dept.code || '').toLowerCase()
    if (name.includes(keyword) || code.includes(keyword)) {
      keep.add(dept.id)
      // 补全祖先节点，保证树形结构可以展示
      let parentId = dept.parentId
      while (parentId && parentId !== 0 && byId.has(parentId)) {
        keep.add(parentId)
        parentId = byId.get(parentId).parentId
      }
    }
  }
  return deptList.value.filter(d => keep.has(d.id))
})
const deptTree = computed(() => buildTree(filteredDepts.value))
const selectTree = computed(() => [{ id: 0, name: '根部门', children: deptTreeAll.value }])

function handleSearch() {
  appliedSearch.value = searchText.value
}

function handleReset() {
  searchText.value = ''
  appliedSearch.value = ''
}

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

async function fetchData() {
  loading.value = true
  try {
    const res = await getDepts()
    deptList.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd(parent) {
  Object.assign(form, {
    id: null, parentId: parent?.id || 0, name: '', code: '', leader: '',
    phone: '', email: '', sort: 0, status: 1
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId || 0,
    name: row.name,
    code: row.code || '',
    leader: row.leader || '',
    phone: row.phone || '',
    email: row.email || '',
    sort: row.sort || 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateDept(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await saveDept(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除部门「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDept(row.id)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

onMounted(fetchData)
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
