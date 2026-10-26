<template>
  <div class="main-layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Ping 管理后台</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
      >
        <SidebarMenu :items="userStore.menus" />
      </el-menu>
    </el-aside>

    <div class="right">
      <el-header class="header">
        <el-breadcrumb separator="/" class="breadcrumb">
          <el-breadcrumb-item :to="{ path: homePath }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="`${item.id}-${index}`">
            {{ item.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            {{ userStore.userInfo.nickname || userStore.userInfo.username || '用户' }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <!-- 多页签：点击左侧菜单时自动追加，关闭当前页签后回到相邻页签 -->
      <div class="tabs-bar">
        <el-tabs v-model="activeTab" type="card" @tab-click="handleTabClick" @tab-remove="handleTabRemove">
          <el-tab-pane
            v-for="tab in tabs"
            :key="tab.path"
            :name="tab.path"
            :label="tab.title"
            :closable="tabs.length > 1"
          />
        </el-tabs>
      </div>
      <el-main class="main">
        <router-view />
      </el-main>
    </div>

    <!-- 修改密码 / 强制改密 -->
    <el-dialog
      v-model="passwordDialogVisible"
      :title="userStore.forceChangePassword ? '请修改初始密码' : '修改密码'"
      width="420px"
      :close-on-click-modal="false"
      :show-close="!userStore.forceChangePassword"
      :close-on-press-escape="!userStore.forceChangePassword"
    >
      <el-alert
        v-if="userStore.forceChangePassword"
        title="首次登录或密码已被重置，修改密码后才能继续使用系统"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password
                    placeholder="至少 8 位，含大小写/数字/特殊字符中的 3 类" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="!userStore.forceChangePassword" @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import SidebarMenu from './SidebarMenu.vue'
import { logout, changePassword } from '../../api/index'
import { passwordValidator } from '../../utils/password'
import { firstMenuPath } from '../../router'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const homePath = computed(() => firstMenuPath(userStore.menus) || '/')

// ---------------- 面包屑 ----------------
// 动态路由是平铺注册的，route.matched 拿不到目录层级，因此从后端菜单树反查路径
function findMenuTrail(menus, path, trail = []) {
  for (const item of menus || []) {
    const next = [...trail, { id: item.id, name: item.name }]
    if (item.path === path) return next
    const found = findMenuTrail(item.children, path, next)
    if (found.length) return found
  }
  return []
}

const breadcrumbs = computed(() => findMenuTrail(userStore.menus, route.path))

// ---------------- 多页签 ----------------
const tabs = ref([])
const activeTab = ref(route.path)

watch(
  () => route.fullPath,
  () => {
    activeTab.value = route.path
    // 只记录由后端菜单注册的业务页面，登录页、403/404 不进页签
    if (!String(route.name || '').startsWith('menu-') || !route.meta.title) return
    const existing = tabs.value.find(tab => tab.path === route.path)
    if (existing) {
      // 同一页面带不同 query（如接口资源筛选）时，切回页签恢复最后一次地址
      existing.fullPath = route.fullPath
    } else {
      tabs.value.push({ path: route.path, fullPath: route.fullPath, title: route.meta.title })
    }
  },
  { immediate: true }
)

function handleTabClick(pane) {
  const tab = tabs.value.find(item => item.path === pane.paneName)
  if (tab && tab.fullPath !== route.fullPath) {
    router.push(tab.fullPath)
  }
}

function handleTabRemove(path) {
  const index = tabs.value.findIndex(tab => tab.path === path)
  if (index === -1) return
  const wasActive = route.path === path
  tabs.value.splice(index, 1)
  if (!wasActive) return
  const next = tabs.value[index] || tabs.value[index - 1]
  router.push(next ? next.fullPath : homePath.value)
}

// ---------------- 修改密码 ----------------
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function handleCommand(command) {
  if (command === 'password') {
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    passwordDialogVisible.value = true
  } else if (command === 'logout') {
    doLogout()
  }
}

async function doLogout() {
  try {
    await logout()
  } catch {
    // token 已失效时忽略
  } finally {
    userStore.logout()
    router.push('/login')
  }
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  passwordSubmitting.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    userStore.setForceChangePassword(false)
    userStore.logout()
    router.push('/login')
  } catch {
    // 错误提示由 request.js 全局拦截器统一处理
  } finally {
    passwordSubmitting.value = false
  }
}

onMounted(() => {
  if (userStore.forceChangePassword) {
    passwordDialogVisible.value = true
  }
})
</script>

<style scoped>
.main-layout { display: flex; height: 100vh; }
.aside { background: #001529; flex-shrink: 0; }
.logo { height: 60px; line-height: 60px; color: #fff; font-size: 18px; font-weight: bold; text-align: center; border-bottom: 1px solid #ffffff1a; }
.right { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e8e8e8; background: #fff; padding: 0 20px; }
.breadcrumb { font-size: 14px; }
.tabs-bar { background: #fff; padding: 6px 16px 0; border-bottom: 1px solid #e8e8e8; }
.tabs-bar :deep(.el-tabs__header) { margin: 0; }
.tabs-bar :deep(.el-tabs__content) { display: none; }
.user-info { display: flex; align-items: center; cursor: pointer; color: #666; font-size: 14px; }
.main { flex: 1; background: #f5f5f5; overflow-y: auto; padding: 20px; }
</style>
