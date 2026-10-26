-- 普通用户角色（role_id=2）补齐查询类权限，消除 lisa 登录后部分菜单 403 报错。
--
-- 本次迁移仅增加查询类权限（*list）及对应父级目录权限，不增加增删改权限，
-- 保持普通用户的最小权限原则。
--
-- 缺失的权限：
--   permission_id=3   system:role           角色管理（父级目录）
--   permission_id=23  system:role:list      查询角色
--   permission_id=24  system:permission:list 查询权限
--
-- WHERE 条件确保幂等：仅当对应关联记录不存在时才插入。

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`)
VALUES
    (2, 3,  NOW(), NOW()),
    (2, 23, NOW(), NOW()),
    (2, 24, NOW(), NOW());
