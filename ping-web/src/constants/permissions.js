/**
 * 权限编码常量：必须与后端 sys_permission.code 保持一致。
 *
 * 前端权限只用于菜单/按钮显示，真正的安全边界在网关和微服务端；
 * 前端即使被篡改，接口依然会被拦截。
 */
export const PERM = {
  // 用户
  USER_MENU: 'system:user',
  USER_LIST: 'system:user:list',
  USER_ADD: 'system:user:add',
  USER_EDIT: 'system:user:edit',
  USER_DELETE: 'system:user:delete',

  // 角色
  ROLE_MENU: 'system:role',
  ROLE_LIST: 'system:role:list',
  ROLE_ADD: 'system:role:add',
  ROLE_EDIT: 'system:role:edit',
  ROLE_DELETE: 'system:role:delete',

  // 权限
  PERMISSION_MENU: 'system:permission',
  PERMISSION_LIST: 'system:permission:list',
  PERMISSION_ADD: 'system:permission:add',
  PERMISSION_EDIT: 'system:permission:edit',
  PERMISSION_DELETE: 'system:permission:delete',

  // 接口资源
  API_RESOURCE_MENU: 'system:apiresource',
  API_RESOURCE_LIST: 'system:apiresource:list',
  API_RESOURCE_ADD: 'system:apiresource:add',
  API_RESOURCE_EDIT: 'system:apiresource:edit',
  API_RESOURCE_DELETE: 'system:apiresource:delete',

  // 白名单
  WHITELIST_MENU: 'system:whitelist',
  WHITELIST_LIST: 'system:whitelist:list',
  WHITELIST_ADD: 'system:whitelist:add',
  WHITELIST_EDIT: 'system:whitelist:edit',
  WHITELIST_DELETE: 'system:whitelist:delete',

  // 订单
  ORDER_MENU: 'system:order',
  ORDER_LIST: 'system:order:list',
  ORDER_EDIT: 'system:order:edit',
  ORDER_DELETE: 'system:order:delete',

  // 部门
  DEPT_MENU: 'system:dept',
  DEPT_LIST: 'system:dept:list',
  DEPT_ADD: 'system:dept:add',
  DEPT_EDIT: 'system:dept:edit',
  DEPT_DELETE: 'system:dept:delete',

  // 用户密码
  USER_RESET_PWD: 'system:user:resetpwd',

  // 登录日志
  LOGIN_LOG_MENU: 'system:loginlog',
  LOGIN_LOG_LIST: 'system:loginlog:list',

  // 在线用户
  ONLINE_MENU: 'system:online',
  ONLINE_LIST: 'system:online:list',
  ONLINE_DELETE: 'system:online:delete',

  // 操作日志
  AUDIT_LOG_MENU: 'system:auditlog',
  AUDIT_LOG_LIST: 'system:auditlog:list'
}