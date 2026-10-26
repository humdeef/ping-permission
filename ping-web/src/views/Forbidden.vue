<template>
  <div class="error-page">
    <el-result
      icon="warning"
      title="403"
      sub-title="当前账号没有可访问的菜单，请联系管理员分配角色或权限"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { firstMenuPath } from '../router'
import { logout } from '../api/index'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  const first = firstMenuPath(userStore.menus)
  if (first) router.push(first)
}

async function handleLogout() {
  try {
    await logout()
  } catch {
    // token 已失效时忽略
  } finally {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f5f5;
}
</style>
