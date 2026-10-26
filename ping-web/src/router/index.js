import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMe } from '../api/index'

// 所有页面组件按文件名索引：菜单数据里的 component 即 views 下的文件名
const viewModules = import.meta.glob('../views/*.vue')

// 固定路由：登录页、布局壳、错误页；业务页面全部由后端菜单树动态注册
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../components/layout/MainLayout.vue'),
    children: []
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/Forbidden.vue'),
    meta: { title: '无权访问' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({ history: createWebHistory(), routes })

let dynamicRouteNames = []
let routesRegistered = false

function flattenMenus(menus) {
  const result = []
  const walk = list => {
    (list || []).forEach(node => {
      result.push(node)
      walk(node.children)
    })
  }
  walk(menus)
  return result
}

function resolveView(component) {
  const name = String(component || '').replace(/\.vue$/, '')
  return viewModules[`../views/${name}.vue`] || null
}

function clearDynamicRoutes() {
  dynamicRouteNames.forEach(name => {
    if (router.hasRoute(name)) router.removeRoute(name)
  })
  dynamicRouteNames = []
  routesRegistered = false
}

/**
 * 根据后端菜单树注册业务路由。
 * 权限变更或切换账号后会先清理旧路由，再按新菜单重建。
 */
export function registerDynamicRoutes(menus) {
  clearDynamicRoutes()
  for (const node of flattenMenus(menus)) {
    // 目录通常不承载页面；只有配置了组件与路由地址的节点才注册路由
    if (node.type !== 1 && node.type !== 2) continue
    if (!node.path || !node.component) continue

    const component = resolveView(node.component)
    if (!component) {
      console.warn(`[router] 菜单「${node.name}」的组件 ${node.component}.vue 不存在，已跳过`)
      continue
    }
    const name = `menu-${node.id}`
    router.addRoute('Layout', {
      path: node.path,
      name,
      component,
      meta: {
        title: node.name,
        perm: node.code,
        icon: node.icon
      }
    })
    dynamicRouteNames.push(name)
  }
  routesRegistered = true
}

/** 第一个可访问的菜单地址，用于登录后落地、无权限时兜底 */
export function firstMenuPath(menus) {
  const flat = flattenMenus(menus).filter(node => node.type === 2 && node.path)
  const visible = flat.find(node => node.visible !== 0)
  return (visible || flat[0])?.path || ''
}

router.beforeEach(async (to, _from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 管理后台` : '管理后台'

  if (to.path === '/login') {
    next()
    return
  }
  if (!localStorage.getItem('ping-token')) {
    next('/login')
    return
  }

  const store = useUserStore()
  // 首次进入/刷新/切换账号：拉取当前用户的权限与菜单，并动态注册路由
  if (!store.infoLoaded) {
    try {
      const res = await getMe()
      if (res && res.code === 200 && res.data) {
        store.setAuthInfo(res.data)
        registerDynamicRoutes(store.menus)
        // 路由是刚注册的，按地址重新解析；不能展开 to，否则会按旧路由名（如 NotFound）重定向
        next({ path: to.fullPath, replace: true })
      } else {
        store.logout()
        next('/login')
      }
    } catch {
      store.logout()
      next('/login')
    }
    return
  }

  if (!routesRegistered) {
    registerDynamicRoutes(store.menus)
    next({ path: to.fullPath, replace: true })
    return
  }

  if (to.path === '/') {
    const first = firstMenuPath(store.menus)
    next(first || '/403')
    return
  }

  // 路由级权限校验（菜单编码即权限编码）
  if (to.meta.perm && !store.hasPerm(to.meta.perm)) {
    const fallback = firstMenuPath(store.menus)
    next(fallback && fallback !== to.path ? fallback : '/403')
    return
  }

  next()
})

export default router
