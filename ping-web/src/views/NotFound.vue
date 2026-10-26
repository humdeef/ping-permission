<template>
  <div class="error-page">
    <el-result icon="info" title="404" sub-title="页面不存在或已被移除">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { firstMenuPath } from '../router'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  const first = firstMenuPath(userStore.menus)
  router.push(first || '/login')
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
