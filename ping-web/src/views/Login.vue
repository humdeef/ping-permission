<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">Ping 管理后台</h2>
      <el-form ref="formRef" :model="form" :rules="rules" class="form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" size="large" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item v-if="authConfig.captchaEnabled" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="验证码" size="large" @keyup.enter="handleLogin" />
            <div class="captcha-question" title="点击刷新" @click="loadCaptcha">
              {{ captcha.question || '加载中...' }}
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="handleLogin">登录</el-button>
        </el-form-item>
        <el-form-item v-if="authConfig.registrationEnabled">
          <div style="text-align: center">
            <span style="color: #999; font-size: 13px">还没有账号？</span>
            <el-button type="text" size="small" @click="showRegister = true">立即注册</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 注册弹窗 -->
    <el-dialog v-model="showRegister" title="用户注册" width="420px">
      <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="至少 8 位，含大小写/数字/特殊字符中的 3 类" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="regForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="handleRegister">注册</el-button>
      </template>
    </el-dialog>

    <footer class="copyright">© {{ currentYear }} ping team 版权所有</footer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register, getAuthConfig, getCaptcha } from '../api/index'
import { useUserStore } from '../stores/user'
import { passwordValidator } from '../utils/password'

const router = useRouter()
const userStore = useUserStore()
const currentYear = new Date().getFullYear()
const formRef = ref(null)
const regFormRef = ref(null)
const loading = ref(false)
const regLoading = ref(false)
const showRegister = ref(false)
const authConfig = reactive({ captchaEnabled: false, registrationEnabled: true })
const captcha = reactive({ captchaId: '', question: '' })

const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

async function loadAuthConfig() {
  try {
    const res = await getAuthConfig()
    authConfig.captchaEnabled = !!res.data?.captchaEnabled
    authConfig.registrationEnabled = res.data?.registrationEnabled !== false
    if (authConfig.captchaEnabled) {
      await loadCaptcha()
    }
  } catch {
    // 配置加载失败时按默认值处理
  }
}

async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captcha.captchaId = res.data?.captchaId || ''
    captcha.question = res.data?.question || ''
    form.captchaId = captcha.captchaId
    form.captchaCode = ''
  } catch {
    captcha.question = '验证码加载失败，点击重试'
  }
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (authConfig.captchaEnabled && !form.captchaCode) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    userStore.setLogin(res.data)
    if (res.data?.forceChangePassword) {
      ElMessage.warning('首次登录/密码已被重置，请先修改密码')
    } else {
      ElMessage.success('登录成功')
    }
    router.push('/')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '登录失败，请检查用户名和密码')
    if (authConfig.captchaEnabled) {
      await loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const valid = await regFormRef.value.validate().catch(() => false)
  if (!valid) return
  regLoading.value = true
  try {
    await register(regForm)
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册失败')
  } finally {
    regLoading.value = false
  }
}

onMounted(loadAuthConfig)
</script>

<style scoped>
.login-page { position: relative; display: flex; align-items: center; justify-content: center; height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.copyright { position: absolute; bottom: 20px; left: 0; right: 0; margin: 0; text-align: center; font-size: 13px; color: rgba(255, 255, 255, 0.75); }
.login-card { width: 380px; padding: 20px; }
.title { text-align: center; margin-bottom: 24px; font-size: 24px; color: #303133; }
.form { padding-top: 8px; }
.captcha-row { display: flex; gap: 8px; width: 100%; }
.captcha-question { flex-shrink: 0; min-width: 110px; height: 40px; line-height: 40px; text-align: center; background: #f5f7fa; border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; font-size: 15px; letter-spacing: 1px; user-select: none; }
</style>
