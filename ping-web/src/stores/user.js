import { defineStore } from 'pinia'
import { ref } from 'vue'

const parse = (key, fallback) => {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('ping-token') || '')
  const userInfo = ref(parse('userInfo', {}))
  const roles = ref(parse('roles', []))
  const permissions = ref(parse('permissions', []))
  const menus = ref([])
  const infoLoaded = ref(false)
  const forceChangePassword = ref(false)

  function setLogin(data) {
    token.value = data.token
    userInfo.value = { userId: data.userId, username: data.username, nickname: data.nickname }
    permissions.value = []
    roles.value = []
    menus.value = []
    infoLoaded.value = false
    forceChangePassword.value = !!data.forceChangePassword
    localStorage.setItem('ping-token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.removeItem('permissions')
    localStorage.removeItem('roles')
  }

  function setAuthInfo(data) {
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      deptId: data.deptId,
      deptName: data.deptName
    }
    roles.value = data.roles || []
    permissions.value = data.permissions || []
    menus.value = data.menus || []
    infoLoaded.value = true
    forceChangePassword.value = !!data.forceChangePassword
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.setItem('roles', JSON.stringify(roles.value))
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  function setForceChangePassword(value) {
    forceChangePassword.value = !!value
  }

  function hasPerm(code) {
    if (!code) return true
    return (permissions.value || []).some(p => {
      if (p === code) return true
      if (p.endsWith(':*')) return code.startsWith(p.slice(0, -1))
      return false
    })
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    permissions.value = []
    menus.value = []
    infoLoaded.value = false
    forceChangePassword.value = false
    localStorage.removeItem('ping-token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('roles')
    localStorage.removeItem('permissions')
  }

  return {
    token, userInfo, roles, permissions, menus, infoLoaded, forceChangePassword,
    setLogin, setAuthInfo, setForceChangePassword, hasPerm, logout
  }
})
