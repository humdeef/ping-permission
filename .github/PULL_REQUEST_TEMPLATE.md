## 改了什么

<!-- 一两句话说清楚这个 PR 做了什么 -->

## 为什么

<!-- 关联的 issue 或背景：Closes #12 -->

## 改动类型

- [ ] 缺陷修复
- [ ] 新功能
- [ ] 重构 / 性能优化（无对外行为变化）
- [ ] 文档
- [ ] 构建 / CI / 依赖升级

## 破坏性变更

<!-- 有则说明影响范围与迁移方式（接口、配置项、数据库结构、Nacos dataId）；无则写「无」 -->

## 怎么验证的

<!-- 贴关键命令与结果，或复现步骤；界面改动请附截图 -->

- [ ] `mvn verify` 通过（含 `*IT.java` 集成测试）
- [ ] `mvn -Pali-p3c compile pmd:check` 0 违规
- [ ] `cd ping-web && npm run build` 通过（涉及前端时）
- [ ] 手工验证：<!-- 例如 登录 → 打开订单列表 → 403/200 -->

## 自检清单

- [ ] 新增/修改的数据库变更走的是新的 `V{n}__xxx.sql`，没有改已发布的迁移文件
- [ ] 新增表或种子数据后，已同步更新 `*SchemaMigrationIT` 的期望值
- [ ] 新增接口已加到 `sys_api_resource`（否则网关会 fail-closed）
- [ ] 新增接口补了 `@Tag` / `@Operation`
- [ ] 同步更新了对应模块的 `README.md` 与 `README.en.md`
- [ ] 对外行为变更已写入 `CHANGELOG.md` 的 `[Unreleased]`
- [ ] 没有提交 `.env`、`target/`、`node_modules/` 或本地 IDE 个人配置
