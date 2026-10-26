# ping-web：Vue 3 管理后台

`ping-web` 是 ping-permission 的 PC 管理后台，提供登录、用户/角色/权限/部门管理、接口资源与白名单维护、在线用户、审计日志和订单示例页面。

菜单与路由由后端权限树驱动：前端不维护菜单表，新增页面只需要添加一个 `views/*.vue`，再在后台配置对应的权限记录即可。

## 1. 技术栈

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Vue | 3.4 | 组合式 API |
| Vite | 5.2 | 开发服务器与构建 |
| Vue Router | 4.3 | history 模式路由，动态注册 |
| Pinia | 2.1 | 用户、权限、菜单状态 |
| Element Plus | 2.6 | UI 组件与图标 |
| Axios | 1.6 | 请求封装与拦截器 |

## 2. 目录结构

```
ping-web/
├── vite.config.js           # 开发服务器与 /api、/order 代理
├── nginx.conf               # 生产环境静态资源与反向代理
├── Dockerfile               # nginx:alpine，拷入 dist/
└── src/
    ├── api/
    │   ├── request.js       # axios 实例、Token 注入、401/403/400/429 处理
    │   └── index.js         # 所有后端接口
    ├── components/layout/
    │   ├── MainLayout.vue   # 侧边栏 + 顶栏 + 修改密码弹窗
    │   └── SidebarMenu.vue  # 按菜单树递归渲染
    ├── constants/permissions.js  # 权限编码常量
    ├── router/index.js      # 固定路由 + 动态路由注册 + 路由守卫
    ├── stores/user.js       # 登录态、权限、菜单
    ├── utils/password.js    # 密码强度校验（与后端策略一致）
    └── views/               # 业务页面，文件名即菜单的 component
```

## 3. 本地运行

```bash
cd ping-web
npm install
npm run dev
```

- 开发地址：http://localhost:5173
- 需要先启动网关（默认 9999）以及 `user-service`；否则页面能打开，但登录接口会失败。
- Node.js 20.19+ / 22.13+（ESLint 10 要求；Vite 5 本身 18+ 即可）。

开发服务器把请求转发到网关，不做跨域：

| 前端前缀 | 目标 | 重写 |
| --- | --- | --- |
| `/api/**` | `http://localhost:9999` | `/api` → `/user/pc` |
| `/order/**` | `http://localhost:9999` | 无 |

因此 `http.get('/users')` 实际访问网关的 `/user/pc/users`，订单接口用 `orderHttp`（`baseURL=/order/pc`）访问 `/order/pc/orders`。

## 4. 登录、权限与动态路由

### 4.1 登录态

- 登录成功后 token 存在 `localStorage` 的 `ping-token`，每个请求带 `ping-token: Bearer <token>`（与后端 `sa-token.token-name` 保持一致）。
- 登录页先请求 `/auth/config`，按返回的 `captchaEnabled`、`registrationEnabled` 决定是否显示验证码和注册入口；开启验证码时通过 `/auth/captcha` 取题。
- 首次登录或被重置密码时，后端返回 `forceChangePassword=true`，布局会弹出不可关闭的改密窗口，改完才能继续使用。
- 密码策略在前端 `utils/password.js` 校验：至少 8 位，且大写/小写/数字/特殊字符至少满足 3 类，与后端 `PasswordPolicy` 默认值一致。

### 4.2 菜单驱动的动态路由

`router/index.js` 只注册 `/login`、布局壳、`/403` 和通配的 404 页面。登录后（或刷新时）调用 `/auth/me` 拿到 `menus`，再把菜单节点逐条注册到 `Layout` 下：

```js
const name = `menu-${node.id}`
router.addRoute('Layout', {
  path: node.path,
  name,
  component: resolveView(node.component),
  meta: { title: node.name, perm: node.code, icon: node.icon }
})
```

- 只注册 `type` 为 1（目录）或 2（菜单）且同时配置了 `path`、`component` 的节点；`component` 就是 `src/views` 下的文件名，不需要 `.vue` 后缀。
- 菜单变更或切换账号时先移除旧路由再重建，避免残留上一个账号的页面。
- 访问 `/` 时跳到第一个可见菜单；没有可访问菜单则落到 `/403`。
- 刷新页面后 `infoLoaded` 为 `false`，守卫会重新拉取 `/auth/me`，因此权限变更后刷新即可生效。

### 4.3 权限判断

按钮级权限使用 `constants/permissions.js` 中的常量调用 `userStore.hasPerm(code)`，后端返回的权限支持 `system:user:*` 形式的通配。路由守卫也会校验 `meta.perm`，但前端的权限只影响展示：

> 真正的安全边界在网关与微服务端，前端代码被篡改后接口依然会被拦截。

### 4.4 统一错误处理

`api/request.js` 的响应拦截器把 `res.data` 直接返回，并按状态码提示：

| 状态码 | 行为 |
| --- | --- |
| 401 | 清除 token 并跳转登录页（登录接口自身的 401 交给登录页展示） |
| 403 | 提示后端返回的 `message`，例如「接口未配置权限规则」 |
| 400 | 参数或密码策略校验失败，登录/注册页自行处理 |
| 429 | 登录失败锁定等限流场景，提示稍后再试 |

操作日志导出使用 `responseType: 'blob'`，直接下载后端生成的表格。

## 5. 页面

| 页面 | 文件 | 说明 |
| --- | --- | --- |
| 登录 / 注册 | `Login.vue` | 验证码、注册开关、强制改密引导 |
| 用户管理 | `Users.vue` | 增删改查、角色分配、重置密码 |
| 角色管理 | `Roles.vue` | 角色维护与权限授权 |
| 权限管理 | `Permissions.vue` | 菜单/按钮/接口权限树，决定前端路由 |
| 部门管理 | `Depts.vue` | 部门树，数据权限按部门过滤 |
| 接口资源 | `ApiResources.vue` | 网关鉴权规则（权限码 + 方法 + 路径） |
| 白名单 | `Whitelist.vue` | 免登录或跳过鉴权的路径 |
| 在线用户 | `OnlineUsers.vue` | 查看会话并强制下线 |
| 登录日志 | `LoginLogs.vue` | 登录成功/失败记录 |
| 操作日志 | `OperationLogs.vue` | 审计日志查询与导出 |
| 订单管理 | `Orders.vue` | 示例业务，走网关 `/order/pc` |
| 403 / 404 | `Forbidden.vue`、`NotFound.vue` | 无权限与页面不存在 |

## 6. 构建与部署

### 6.1 本地构建

```bash
npm run lint      # ESLint 检查（CI 门禁，配置见 eslint.config.js）
npm run lint:fix  # 自动修复可修复项
npm run build     # 输出到 dist/
npm run preview   # 本地预览构建结果
```

ESLint 只启用能挡住运行时错误的规则（未定义变量、漏 `:key`、直接改 props 等），不检查排版，避免与尚未引入的 Prettier 冲突。

### 6.2 Docker

`Dockerfile` 基于 `nginx:alpine`，只负责运行宿主机构建好的 `dist/`（`start.sh` 会先执行 `npm run build`）。`nginx.conf` 的行为：

| 路径 | 行为 |
| --- | --- |
| `/api/` | 转发到 `http://gateway:9999/user/pc/` |
| `/user/`、`/order/` | 转发到网关对应路由，便于浏览器直接调试 |
| 其他路径 | `try_files` 回退到 `index.html`，支持 history 路由 |
| `/index.html` | 不缓存，保证发布后立即生效 |

开启 gzip，压缩 JS/CSS/JSON/SVG 等文本资源。

## 7. 常见坑

### 7.1 新增页面后菜单点不开

控制台出现「菜单「xxx」的组件 yyy.vue 不存在」说明 `component` 与 `views` 下的文件名不一致（大小写敏感）。补上 `views/yyy.vue`，或把权限记录里的 `component` 改成实际文件名。

### 7.2 改了权限但页面没变化

菜单和权限只在登录或刷新时从 `/auth/me` 拉取。授权后让用户重新登录或刷新页面；路由守卫会清理并按新菜单重建路由。

### 7.3 直接访问 `/api/...` 返回 404

开发代理只匹配 `/api/`，并重写为 `/user/pc`。如果网关没有启动、端口不是 9999，或接口实际不在 `/user/pc` 下，就会 404。订单接口请使用 `/order/pc`（`orderHttp`），不要拼到 `/api` 上。

### 7.4 403：接口未配置权限规则

这是网关 fail-closed 的结果：该「方法 + 路径」没有对应的 `sys_api_resource` 记录，或当前用户没有该权限码。先在后台上传/维护接口资源，再给角色授权。

### 7.5 刷新后跳回登录页

`ping-token` 缺失或已失效（被下线、过期、Redis 中会话被清理）。浏览器 `localStorage` 里的 token 不会自动续期，需要重新登录；线上通常由网关的会话超时配置决定。

## 8. 依赖方式

前端独立构建，不被任何后端模块依赖。后端接口或权限码变更时同步更新 `src/api/index.js`、`src/constants/permissions.js` 和对应页面。
