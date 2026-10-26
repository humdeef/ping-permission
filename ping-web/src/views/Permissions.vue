<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
          <span>权限管理</span>
          <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
            <el-input
              v-model="searchName"
              placeholder="权限名称"
              clearable
              style="width: 160px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-input
              v-model="searchCode"
              placeholder="权限编码"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-button @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="canAdd" type="primary" @click="handleAdd">新增权限</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" row-key="id" border v-loading="loading">
        <el-table-column prop="name" label="权限名称" min-width="140" />
        <el-table-column prop="code" label="权限编码" min-width="180" />
        <el-table-column label="路由 / 组件" min-width="200">
          <template #default="{ row }">
            <template v-if="row.type !== 3">
              <div v-if="row.path"><code>{{ row.path }}</code></div>
              <div v-if="row.component" style="color: #999; font-size: 12px">{{ row.component }}.vue</div>
              <span v-if="!row.path && !row.component" style="color: #bbb">—</span>
              <el-tag v-if="row.visible === 0" size="small" type="info" style="margin-left: 4px">隐藏</el-tag>
            </template>
            <span v-else style="color: #bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.type === 1" type="primary">目录</el-tag>
            <el-tag v-else-if="row.type === 2" type="success">菜单</el-tag>
            <el-tag v-else type="info">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="挂接接口" min-width="260">
          <template #default="{ row }">
            <template v-if="pathsByCode[row.code] && pathsByCode[row.code].length">
              <el-tag
                v-for="(p, i) in pathsByCode[row.code]"
                :key="i"
                size="small"
                type="warning"
                style="margin: 2px 4px 2px 0"
              >
                {{ p }}
              </el-tag>
            </template>
            <span v-else style="color: #bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-if="canViewResources" type="success" link @click="goResources(row)">接口</el-button>
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
      <div style="color: #999; font-size: 12px; margin-top: 8px">
        接口路径与请求方法在「接口资源」页面维护，一个权限可挂接多个接口。
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑权限' : '新增权限'" width="480px" @opened="initForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="上级权限">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            render-after-expand="false"
            placeholder="请选择上级权限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="如 system:user:add" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.type !== 3">
          <el-form-item label="路由地址" prop="path">
            <el-input v-model="form.path" placeholder="如 /users；目录可留空" />
          </el-form-item>
          <el-form-item label="组件名" prop="component">
            <el-input v-model="form.component" placeholder="如 Users，对应 views/Users.vue" />
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" />
          </el-form-item>
          <el-form-item label="菜单显示">
            <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
            <span style="margin-left: 8px; color: #999; font-size: 12px">关闭后仍注册路由，但不在侧边栏展示</span>
          </el-form-item>
        </template>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPermissions, getAllPermissions, savePermission, updatePermission, deletePermission, getAllApiResources
} from '../api/index'
import { useUserStore } from '../stores/user'
import { PERM } from '../constants/permissions'

const router = useRouter()
const userStore = useUserStore()

const canAdd = computed(() => userStore.hasPerm(PERM.PERMISSION_ADD))
const canEdit = computed(() => userStore.hasPerm(PERM.PERMISSION_EDIT))
const canDelete = computed(() => userStore.hasPerm(PERM.PERMISSION_DELETE))
const canViewResources = computed(() => userStore.hasPerm(PERM.API_RESOURCE_LIST))

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageSize = ref(10)
const page = ref(1)
const searchName = ref('')
const searchCode = ref('')

const allPermissions = ref([])
const apiResources = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null, parentId: 0, name: '', code: '', type: 3,
  path: '', component: '', icon: '', visible: 1, sort: 0
})
const rules = {
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  path: [{
    validator: (_rule, value, callback) => {
      if (form.type === 2 && !value) {
        callback(new Error('菜单类型必须填写路由地址'))
      } else if (value && !value.startsWith('/')) {
        callback(new Error('路由地址必须以 / 开头'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }],
  component: [{
    validator: (_rule, value, callback) => {
      if (form.type === 2 && !value) {
        callback(new Error('菜单类型必须填写组件名'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }]
}

// 权限编码 -> 已启用的接口规则（方法 + 路径）
const pathsByCode = computed(() => {
  const map = {}
  for (const r of apiResources.value) {
    if (r.status !== 1) continue
    const label = `${r.method || '*'} ${r.path}`
    ;(map[r.permissionCode] ||= []).push(label)
  }
  return map
})

// 上级权限下拉使用完整列表（不受分页/搜索影响）
const parentOptions = computed(() => {
  const build = (list, pid) => {
    return list
      .filter(item => item.parentId === pid)
      .map(item => ({ ...item, children: build(list, item.id) }))
  }
  return build(allPermissions.value, 0)
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getPermissions({
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

async function loadAllPermissions() {
  try {
    const res = await getAllPermissions()
    allPermissions.value = res.data || []
  } catch {
    // 权限列表加载失败时保留空列表，不影响其他操作
  }
}

async function loadApiResources() {
  try {
    const res = await getAllApiResources()
    apiResources.value = res.data || []
  } catch {
    // 接口资源加载失败时保留空列表，不影响其他操作
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

function handlePageChange(p) {
  page.value = p
  fetchData()
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, {
    id: null, parentId: 0, name: '', code: '', type: 3,
    path: '', component: '', icon: '', visible: 1, sort: 0
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    name: row.name,
    code: row.code,
    type: row.type,
    path: row.path || '',
    component: row.component || '',
    icon: row.icon || '',
    visible: row.visible === 0 ? 0 : 1,
    sort: row.sort
  })
  dialogVisible.value = true
}

async function refreshAll() {
  await Promise.all([fetchData(), loadAllPermissions(), loadApiResources()])
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePermission(form.id, form)
    } else {
      await savePermission(form)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    refreshAll()
  } finally {
    submitting.value = false
  }
}

function goResources(row) {
  router.push({ path: '/api-resources', query: { code: row.code, name: row.name } })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除权限「${row.name}」吗？其子权限和挂接的接口资源也会一并删除。`, '提示', { type: 'warning' })
    .then(async () => {
      await deletePermission(row.id)
      ElMessage.success('删除成功')
      refreshAll()
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
  refreshAll()
})
</script>
