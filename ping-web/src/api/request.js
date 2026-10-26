import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

function createHttp(baseURL) {
  const instance = axios.create({
    baseURL,
    timeout: 15000
  })

  // 请求拦截：自动带 Token（Sa-Token header 名为 ping-token）
  instance.interceptors.request.use(config => {
    const token = localStorage.getItem('ping-token')
    if (token) config.headers['ping-token'] = `Bearer ${token}`
    return config
  }, err => Promise.reject(err))

  // 响应拦截：统一处理 401
  instance.interceptors.response.use(
    res => res.data,
    err => {
      if (err.response?.status === 401) {
        // 登录接口的 401 是账号/密码错误，由登录页处理，不按「登录过期」跳转
        if ((err.config?.url || '').includes('/auth/login')) {
          return Promise.reject(err)
        }
        localStorage.removeItem('ping-token')
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (err.response?.status === 403) {
        // 登录接口的 403（账号禁用）由登录页展示
        if (!(err.config?.url || '').includes('/auth/login')) {
          const msg = err.response?.data?.message || err.response?.data?.msg || err.response?.data?.error || '没有权限执行该操作'
          ElMessage.error(msg)
        }
      } else if (err.response?.status === 400) {
        // 业务校验失败（参数、密码策略等）；登录/注册页自行展示，避免重复提示
        const url = err.config?.url || ''
        if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
          const msg = err.response?.data?.message || err.response?.data?.msg || '请求参数有误'
          ElMessage.error(msg)
        }
      } else if (err.response?.status === 429) {
        // 登录失败锁定等限流场景；登录页自行展示错误，避免重复提示
        if (!(err.config?.url || '').includes('/auth/login')) {
          ElMessage.error(err.response?.data?.message || '操作过于频繁，请稍后再试')
        }
      }
      return Promise.reject(err)
    }
  )

  return instance
}

// 用户/权限等后台接口：/api -> 网关 /user/pc
const http = createHttp('/api')

// 订单接口：网关 /order/pc（nginx 与 Vite 都直接转发 /order）
export const orderHttp = createHttp('/order/pc')

export default http
