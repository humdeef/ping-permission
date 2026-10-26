# ping-web: Vue 3 admin console

`ping-web` is the PC admin console for ping-permission. It covers login, user/role/permission/department management, API resource and whitelist maintenance, online sessions, audit logs and the sample order pages.

Menus and routes are driven by the backend permission tree: the frontend keeps no menu table, so adding a page only means adding a `views/*.vue` file and configuring the matching permission record in the console.

## 1. Tech stack

| Dependency | Version | Purpose |
| --- | --- | --- |
| Vue | 3.4 | Composition API |
| Vite | 5.2 | Dev server and build |
| Vue Router | 4.3 | History-mode routing with dynamic registration |
| Pinia | 2.1 | User, permission and menu state |
| Element Plus | 2.6 | UI components and icons |
| Axios | 1.6 | Request wrapper and interceptors |

## 2. Directory layout

```
ping-web/
├── vite.config.js           # Dev server and /api, /order proxies
├── nginx.conf               # Static assets and reverse proxy in production
├── Dockerfile               # nginx:alpine, copies dist/
└── src/
    ├── api/
    │   ├── request.js       # Axios instances, token injection, 401/403/400/429 handling
    │   └── index.js         # Every backend endpoint
    ├── components/layout/
    │   ├── MainLayout.vue   # Sidebar + header + change-password dialog
    │   └── SidebarMenu.vue  # Renders the menu tree recursively
    ├── constants/permissions.js  # Permission code constants
    ├── router/index.js      # Fixed routes + dynamic registration + navigation guards
    ├── stores/user.js       # Login state, permissions, menus
    ├── utils/password.js    # Password strength check (matches the backend policy)
    └── views/               # Business pages; the file name is the menu's component
```

## 3. Running locally

```bash
cd ping-web
npm install
npm run dev
```

- Dev URL: http://localhost:5173
- The gateway (default 9999) and `user-service` must already be running; otherwise the page loads but login fails.
- Node.js 20.19+ / 22.13+ (required by ESLint 10; Vite 5 alone works on 18+).

The dev server proxies to the gateway, so there is no CORS:

| Frontend prefix | Target | Rewrite |
| --- | --- | --- |
| `/api/**` | `http://localhost:9999` | `/api` → `/user/pc` |
| `/order/**` | `http://localhost:9999` | none |

So `http.get('/users')` really hits the gateway's `/user/pc/users`, and order endpoints use `orderHttp` (`baseURL=/order/pc`) to reach `/order/pc/orders`.

## 4. Login, permissions and dynamic routes

### 4.1 Login state

- After a successful login the token is stored in `localStorage` as `ping-token`, and every request sends `ping-token: Bearer <token>` (matching the backend's `sa-token.token-name`).
- The login page first calls `/auth/config`; `captchaEnabled` and `registrationEnabled` decide whether the captcha and the register link are shown. When enabled, the captcha challenge comes from `/auth/captcha`.
- On first login or after a password reset the backend returns `forceChangePassword=true`; the layout then opens a non-dismissible change-password dialog and the user cannot continue until it is completed.
- Password strength is validated in `utils/password.js`: at least 8 characters with at least 3 of uppercase, lowercase, digits and special characters, matching the backend's default `PasswordPolicy`.

### 4.2 Menu-driven dynamic routes

`router/index.js` only registers `/login`, the layout shell, `/403` and a catch-all 404 page. After login (or on refresh) it calls `/auth/me` for `menus` and registers each menu node under `Layout`:

```js
const name = `menu-${node.id}`
router.addRoute('Layout', {
  path: node.path,
  name,
  component: resolveView(node.component),
  meta: { title: node.name, perm: node.code, icon: node.icon }
})
```

- Only `type` 1 (directory) or 2 (menu) nodes that have both `path` and `component` are registered; `component` is the file name under `src/views`, without the `.vue` suffix.
- When menus change or the account switches, old routes are removed first and then rebuilt, so pages from the previous account never linger.
- `/` redirects to the first visible menu; if there is no accessible menu the user lands on `/403`.
- After a page refresh `infoLoaded` is `false`, so the guard fetches `/auth/me` again; a permission change therefore takes effect on refresh.

### 4.3 Permission checks

Button-level permissions use the constants in `constants/permissions.js` together with `userStore.hasPerm(code)`, and the backend-issued permissions support wildcards such as `system:user:*`. The navigation guard also checks `meta.perm`, but frontend permissions only affect what is displayed:

> The real security boundary is the gateway and the microservices; tampering with the frontend code does not get past the API checks.

### 4.4 Unified error handling

The response interceptor in `api/request.js` returns `res.data` directly and reports errors by status code:

| Status | Behaviour |
| --- | --- |
| 401 | Clear the token and redirect to the login page (a 401 from the login endpoint itself is shown by the login page) |
| 403 | Show the backend `message`, e.g. "no permission rule configured for this endpoint" |
| 400 | Parameter or password-policy validation failure; the login/register pages handle it themselves |
| 429 | Rate limiting such as login lockout; ask the user to retry later |

Operation-log export uses `responseType: 'blob'` and downloads the file generated by the backend.

## 5. Pages

| Page | File | Description |
| --- | --- | --- |
| Login / register | `Login.vue` | Captcha, registration switch, forced password change |
| Users | `Users.vue` | CRUD, role assignment, password reset |
| Roles | `Roles.vue` | Role maintenance and permission grants |
| Permissions | `Permissions.vue` | Menu/button/API permission tree that drives frontend routes |
| Departments | `Depts.vue` | Department tree; data permissions filter by department |
| API resources | `ApiResources.vue` | Gateway rules (permission code + method + path) |
| Whitelist | `Whitelist.vue` | Paths that skip login or authorization |
| Online users | `OnlineUsers.vue` | Inspect sessions and force logout |
| Login logs | `LoginLogs.vue` | Successful and failed login attempts |
| Operation logs | `OperationLogs.vue` | Audit log search and export |
| Orders | `Orders.vue` | Sample business module through the gateway `/order/pc` |
| 403 / 404 | `Forbidden.vue`, `NotFound.vue` | No permission and page not found |

## 6. Build and deploy

### 6.1 Local build

```bash
npm run lint      # ESLint check (CI gate, config in eslint.config.js)
npm run lint:fix  # fix what can be fixed automatically
npm run build     # outputs to dist/
npm run preview   # preview the build locally
```

ESLint only enables rules that catch runtime mistakes (undefined variables, missing `:key`, mutating props) and skips formatting rules, so it does not clash with a future Prettier setup.

### 6.2 Docker

`Dockerfile` is based on `nginx:alpine` and only serves the `dist/` built on the host (`start.sh` runs `npm run build` first). `nginx.conf` behaves as follows:

| Path | Behaviour |
| --- | --- |
| `/api/` | Proxy to `http://gateway:9999/user/pc/` |
| `/user/`, `/order/` | Proxy to the corresponding gateway routes for direct browser debugging |
| Any other path | `try_files` falls back to `index.html`, supporting history routing |
| `/index.html` | Not cached, so a deploy takes effect immediately |

gzip is enabled for JS/CSS/JSON/SVG and other text resources.

## 7. Pitfalls

### 7.1 A new page cannot be opened from the menu

A console warning like "component yyy.vue for menu "xxx" does not exist" means `component` does not match a file name under `views` (case-sensitive). Add `views/yyy.vue`, or change `component` in the permission record to the real file name.

### 7.2 Permissions changed but the page did not

Menus and permissions are only fetched from `/auth/me` on login or refresh. Ask the user to log in again or refresh the page; the guard clears and rebuilds the routes from the new menus.

### 7.3 `/api/...` returns 404

The dev proxy only matches `/api/` and rewrites it to `/user/pc`. If the gateway is not running, is not on 9999, or the endpoint is not actually under `/user/pc`, the call 404s. Order endpoints use `/order/pc` (`orderHttp`) and must not be appended to `/api`.

### 7.4 403: no permission rule configured for the endpoint

This is the gateway failing closed: there is no `sys_api_resource` row for that method + path, or the current user lacks the permission code. Maintain the API resource in the console first, then grant it to the role.

### 7.5 Refresh sends the user back to the login page

The `ping-token` is missing or invalid (forced logout, expiry, session cleared from Redis). The token in `localStorage` is not renewed automatically, so the user must log in again; the gateway session timeout configuration controls how long it stays valid in production.

## 8. Dependency notes

The frontend is built independently and no backend module depends on it. When backend endpoints or permission codes change, update `src/api/index.js`, `src/constants/permissions.js` and the affected pages together.
