import http, { orderHttp } from './request'

export function login(data) {
  return http.post('/auth/login', data)
}

export function getAuthConfig() {
  return http.get('/auth/config')
}

export function getCaptcha() {
  return http.get('/auth/captcha')
}

export function logout() {
  return http.post('/auth/logout')
}

export function changePassword(data) {
  return http.post('/auth/password', data)
}

export function getMe() {
  return http.get('/auth/me')
}

export function register(data) {
  return http.post('/auth/register', data)
}

export function getUsers(params) {
  return http.get('/users', { params })
}

export function getUser(id) {
  return http.get(`/users/${id}`)
}

export function saveUser(data) {
  return http.post('/users', data)
}

export function updateUser(id, data) {
  return http.put(`/users/${id}`, data)
}

export function deleteUser(id) {
  return http.delete(`/users/${id}`)
}

export function getAllRoles() {
  return http.get('/users/roles/all')
}

export function getRoles(params) {
  return http.get('/roles', { params })
}

export function getRole(id) {
  return http.get(`/roles/${id}`)
}

export function saveRole(data) {
  return http.post('/roles', data)
}

export function updateRole(id, data) {
  return http.put(`/roles/${id}`, data)
}

export function deleteRole(id) {
  return http.delete(`/roles/${id}`)
}

export function getAllPermissions() {
  return http.get('/permissions/tree')
}

export function getPermissions(params) {
  return http.get('/permissions', { params })
}

export function savePermission(data) {
  return http.post('/permissions', data)
}

export function updatePermission(id, data) {
  return http.put(`/permissions/${id}`, data)
}

export function deletePermission(id) {
  return http.delete(`/permissions/${id}`)
}

export function getWhitelists(params) {
  return http.get('/whitelist', { params })
}

export function saveWhitelist(data) {
  return http.post('/whitelist', data)
}

export function updateWhitelist(id, data) {
  return http.put(`/whitelist/${id}`, data)
}

export function deleteWhitelist(id) {
  return http.delete(`/whitelist/${id}`)
}

export function getApiResources(params) {
  return http.get('/api-resources', { params })
}

export function getAllApiResources() {
  return http.get('/api-resources/all')
}

export function saveApiResource(data) {
  return http.post('/api-resources', data)
}

export function updateApiResource(id, data) {
  return http.put(`/api-resources/${id}`, data)
}

export function deleteApiResource(id) {
  return http.delete(`/api-resources/${id}`)
}

export function resetUserPassword(id, data) {
  return http.post(`/users/${id}/reset-password`, data)
}

// ---------------- 部门 ----------------
export function getDepts() {
  return http.get('/depts')
}

export function getDept(id) {
  return http.get(`/depts/${id}`)
}

export function saveDept(data) {
  return http.post('/depts', data)
}

export function updateDept(id, data) {
  return http.put(`/depts/${id}`, data)
}

export function deleteDept(id) {
  return http.delete(`/depts/${id}`)
}

// ---------------- 登录日志 ----------------
export function getLoginLogs(params) {
  return http.get('/login-logs', { params })
}

// ---------------- 在线用户 ----------------
export function getOnlineUsers(params) {
  return http.get('/online', { params })
}

export function kickoutUser(token) {
  return http.delete(`/online/${encodeURIComponent(token)}`)
}

// ---------------- 操作日志 ----------------
export function getOperationLogs(params) {
  return http.get('/operation-logs', { params })
}

export function exportOperationLogs(params) {
  return http.get('/operation-logs/export', { params, responseType: 'blob' })
}

// ---------------- 订单 ----------------
export function getOrders(params) {
  return orderHttp.get('/orders', { params })
}

export function getOrder(id) {
  return orderHttp.get(`/orders/${id}`)
}

export function getOrderLogs(id) {
  return orderHttp.get(`/orders/${id}/logs`)
}
