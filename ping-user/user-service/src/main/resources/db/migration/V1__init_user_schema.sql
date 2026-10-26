-- ============================================================
-- ping 用户域初始化（user-service 迁移基线）
-- 包含 RBAC、双登录域账号、接口资源与审计表结构及种子数据。
--
-- 约定：
--   * V1 为幂等基线（CREATE TABLE IF NOT EXISTS + INSERT IGNORE），
--     空库全新安装与已有库升级都会执行；后续变更请新增 V2__xxx.sql。
--   * 已完成迁移的文件视为只读，不要修改，改结构请追加新版本。
-- ============================================================
/*!50503 SET NAMES utf8mb4 */;

-- ------------------------------------------------------------
-- app_user
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint NOT NULL COMMENT 'C端用户ID（雪花ID，应用层生成）',
  `phone` varchar(32) NOT NULL COMMENT '手机号',
  `password_hash` varchar(256) DEFAULT NULL COMMENT 'BCrypt密码哈希',
  `nickname` varchar(64) DEFAULT '' COMMENT '昵称',
  `avatar` varchar(512) DEFAULT '' COMMENT '头像',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '0未知 1男 2女',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1正常 2注销中',
  `register_source` varchar(32) DEFAULT '' COMMENT '注册来源',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_phone` (`phone`),
  KEY `idx_app_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C端用户';

INSERT IGNORE INTO `app_user` VALUES (2,'13000000001','$2a$10$mpBgvaqbc8oRUXJcxU9YRupsOdr5Agba0CdGG21pHg3HzELIWq/km','张三','',0,1,'phone','2026-10-23 16:09:05','2026-10-23 11:55:50','2026-10-23 11:55:50');

-- ------------------------------------------------------------
-- app_user_identity
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_user_identity` (
  `id` bigint NOT NULL COMMENT '雪花ID，应用层生成',
  `user_id` bigint NOT NULL COMMENT 'app_user.id',
  `provider` varchar(32) NOT NULL COMMENT 'phone/wechat/apple/alipay',
  `identifier` varchar(128) NOT NULL COMMENT '手机号/OpenID/UnionID',
  `credential` varchar(256) DEFAULT '' COMMENT '第三方凭证',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_identity` (`provider`,`identifier`),
  KEY `idx_app_identity_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C端用户第三方身份';

INSERT IGNORE INTO `app_user_identity` VALUES (2,2,'phone','13000000001','','2026-10-23 11:55:50','2026-10-23 11:55:50');

-- ------------------------------------------------------------
-- sys_api_resource
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_api_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '接口资源ID',
  `permission_code` varchar(128) NOT NULL COMMENT '关联权限编码(sys_permission.code)',
  `path` varchar(256) NOT NULL COMMENT '接口路径(Ant 风格，如 /user/users/**)',
  `method` varchar(16) NOT NULL DEFAULT '*' COMMENT '请求方法: GET/POST/PUT/DELETE，* 表示任意',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `description` varchar(256) DEFAULT '' COMMENT '描述',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path_method` (`path`,`method`),
  KEY `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口资源表（网关鉴权规则）';

INSERT IGNORE INTO `sys_api_resource` VALUES (1,'system:user:list','/user/pc/users/**','GET',1,'用户管理',1,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(2,'system:role:list','/user/pc/roles/**','GET',1,'角色管理',2,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(3,'system:permission:list','/user/pc/permissions/**','GET',1,'权限管理',3,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(4,'system:whitelist:list','/user/pc/whitelist/**','GET',1,'白名单管理',4,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(5,'system:user:add','/user/pc/users','POST',1,'新增用户',1,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(6,'system:user:edit','/user/pc/users/**','PUT',1,'编辑用户',2,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(7,'system:user:delete','/user/pc/users/**','DELETE',1,'删除用户',3,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(8,'system:role:add','/user/pc/roles','POST',1,'新增角色',1,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(9,'system:role:edit','/user/pc/roles/**','PUT',1,'编辑角色',2,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(10,'system:role:delete','/user/pc/roles/**','DELETE',1,'删除角色',3,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(11,'system:permission:add','/user/pc/permissions','POST',1,'新增权限',1,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(12,'system:permission:edit','/user/pc/permissions/**','PUT',1,'编辑权限',2,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(13,'system:permission:delete','/user/pc/permissions/**','DELETE',1,'删除权限',3,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(14,'system:whitelist:add','/user/pc/whitelist','POST',1,'新增白名单',1,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(15,'system:whitelist:edit','/user/pc/whitelist/**','PUT',1,'编辑白名单',2,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(16,'system:whitelist:delete','/user/pc/whitelist/**','DELETE',1,'删除白名单',3,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(33,'system:apiresource:list','/user/pc/api-resources/**','GET',1,'查询接口资源',17,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(34,'system:apiresource:add','/user/pc/api-resources','POST',1,'新增接口资源',18,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(35,'system:apiresource:edit','/user/pc/api-resources/**','PUT',1,'编辑接口资源',19,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(36,'system:apiresource:delete','/user/pc/api-resources/**','DELETE',1,'删除接口资源',20,'2026-10-21 08:40:37','2026-10-23 03:24:55'),(37,'system:order:list','/order/**','GET',1,'查询订单',21,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(39,'system:order:edit','/order/**','PUT',1,'编辑订单',23,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(40,'system:order:delete','/order/**','DELETE',1,'删除订单',24,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(41,'system:user:list','/user/pc/users','GET',1,'用户列表',0,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(42,'system:role:list','/user/pc/roles','GET',1,'角色列表',0,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(43,'system:permission:list','/user/pc/permissions','GET',1,'权限列表',0,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(44,'system:whitelist:list','/user/pc/whitelist','GET',1,'白名单列表',0,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(45,'system:apiresource:list','/user/pc/api-resources','GET',1,'接口资源列表',0,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(46,'system:dept:list','/user/pc/depts','GET',1,'部门列表',1,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(47,'system:dept:list','/user/pc/depts/**','GET',1,'查询部门',1,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(48,'system:dept:add','/user/pc/depts','POST',1,'新增部门',1,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(49,'system:dept:edit','/user/pc/depts/**','PUT',1,'编辑部门',1,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(50,'system:dept:delete','/user/pc/depts/**','DELETE',1,'删除部门',1,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(51,'system:user:resetpwd','/user/pc/users/*/reset-password','POST',1,'重置用户密码',2,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(52,'system:loginlog:list','/user/pc/login-logs','GET',1,'登录日志列表',3,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(53,'system:loginlog:list','/user/pc/login-logs/**','GET',1,'登录日志查询',3,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(54,'system:online:list','/user/pc/online','GET',1,'在线用户列表',3,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(55,'system:online:list','/user/pc/online/**','GET',1,'在线用户查询',3,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(56,'system:online:delete','/user/pc/online/**','DELETE',1,'强制下线',3,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(57,'system:auditlog:list','/user/pc/operation-logs','GET',1,'操作日志列表',4,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(58,'system:auditlog:list','/user/pc/operation-logs/**','GET',1,'操作日志导出',4,'2026-10-23 01:28:41','2026-10-23 03:24:55'),(59,'system:order:list','/order/pc/orders/**','GET',1,'后台订单列表/详情/日志',21,'2026-10-24 06:26:04','2026-10-24 06:26:04'),(61,'system:order:edit','/order/pc/orders/**','PUT',1,'后台修改订单',23,'2026-10-24 06:26:04','2026-10-24 06:26:04'),(62,'system:order:edit','/order/pc/orders/*/pay','POST',1,'后台支付订单',24,'2026-10-24 06:26:04','2026-10-24 06:26:04'),(63,'system:order:edit','/order/pc/orders/*/cancel','POST',1,'后台取消订单',25,'2026-10-24 06:26:04','2026-10-24 06:26:04'),(64,'system:order:edit','/order/pc/orders/*/complete','POST',1,'后台完成订单',26,'2026-10-24 06:26:04','2026-10-24 06:26:04'),(65,'system:order:delete','/order/pc/orders/**','DELETE',1,'后台删除订单',27,'2026-10-24 06:26:04','2026-10-24 06:26:04');

-- ------------------------------------------------------------
-- sys_api_whitelist
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_api_whitelist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `path` varchar(256) NOT NULL COMMENT '接口路径',
  `method` varchar(16) NOT NULL DEFAULT 'GET' COMMENT '请求方法 GET/POST/PUT/DELETE',
  `description` varchar(256) DEFAULT '' COMMENT '描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1启用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path_method` (`path`,`method`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口白名单表';

INSERT IGNORE INTO `sys_api_whitelist` VALUES (1,'/user/pc/auth/login','POST','用户登录','2026-10-20 06:16:36','2026-10-23 03:24:55',1),(2,'/user/pc/auth/register','POST','用户注册','2026-10-20 06:16:36','2026-10-23 03:24:55',1),(3,'/user/pc/public/**','*','公共接口','2026-10-20 06:16:36','2026-10-23 03:24:55',1);

-- ------------------------------------------------------------
-- sys_dept
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID，0 表示根',
  `ancestors` varchar(512) NOT NULL DEFAULT '' COMMENT '祖级列表，如 0,1,2',
  `name` varchar(64) NOT NULL COMMENT '部门名称',
  `code` varchar(64) DEFAULT '' COMMENT '部门编码',
  `leader` varchar(64) DEFAULT '' COMMENT '负责人',
  `phone` varchar(32) DEFAULT '' COMMENT '联系电话',
  `email` varchar(128) DEFAULT '' COMMENT '邮箱',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

INSERT IGNORE INTO `sys_dept` VALUES (1,0,'0','总公司','HQ','','','',1,1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(2,1,'0,1','研发中心','RD','','','',1,1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(3,1,'0,1','运营中心','OPS','','','',2,1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(4,2,'0,1,2','开发部','DEVELOP','','','',0,1,'2026-10-23 09:37:46','2026-10-23 09:37:46'),(5,2,'0,1,2','测试部','TEST','','','',0,1,'2026-10-23 09:38:02','2026-10-23 09:38:02'),(6,4,'0,1,2,4','开发一组','DEVE_1','','','',0,1,'2026-10-23 10:12:36','2026-10-23 10:12:36');

-- ------------------------------------------------------------
-- sys_login_log
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `ip` varchar(64) DEFAULT '' COMMENT '登录IP',
  `user_agent` varchar(512) DEFAULT '' COMMENT 'User-Agent',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功: 0失败 1成功',
  `message` varchar(256) DEFAULT '' COMMENT '结果说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_user_id` (`user_id`),
  KEY `idx_login_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';

-- ------------------------------------------------------------
-- sys_operation_log
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作人用户ID',
  `username` varchar(64) DEFAULT '' COMMENT '操作人用户名',
  `action` varchar(64) NOT NULL COMMENT '动作，如 新增用户/登录',
  `target_type` varchar(64) DEFAULT '' COMMENT '目标类型',
  `target_id` varchar(64) DEFAULT '' COMMENT '目标ID',
  `trace_id` varchar(64) DEFAULT '' COMMENT '链路追踪ID',
  `detail` varchar(512) DEFAULT '' COMMENT '详情（不含敏感信息）',
  `ip` varchar(64) DEFAULT '' COMMENT '操作IP',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功: 0失败 1成功',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB AUTO_INCREMENT=173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志';

-- ------------------------------------------------------------
-- sys_permission
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `name` varchar(64) NOT NULL COMMENT '权限名称',
  `code` varchar(128) NOT NULL COMMENT '权限编码',
  `type` int NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级ID',
  `path` varchar(256) DEFAULT '' COMMENT '前端路由地址(菜单类型使用)',
  `component` varchar(128) NOT NULL DEFAULT '' COMMENT '前端组件名(对应 ping-web/src/views 下的文件名)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `icon` varchar(128) DEFAULT '' COMMENT '图标',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否在侧边菜单显示: 1显示 0隐藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统权限表';

INSERT IGNORE INTO `sys_permission` VALUES (1,'系统管理','system',1,0,'','',1,'Setting',1,'2026-10-20 06:16:36','2026-10-20 06:16:36'),(2,'用户管理','system:user',2,1,'/users','Users',1,'User',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(3,'角色管理','system:role',2,1,'/roles','Roles',2,'UserFilled',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(4,'权限管理','system:permission',2,1,'/permissions','Permissions',3,'Key',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(5,'白名单管理','system:whitelist',2,1,'/whitelist','Whitelist',4,'List',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(6,'新增用户','system:user:add',3,2,'','',1,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(7,'编辑用户','system:user:edit',3,2,'','',2,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(8,'删除用户','system:user:delete',3,2,'','',3,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(9,'新增角色','system:role:add',3,3,'','',1,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(10,'编辑角色','system:role:edit',3,3,'','',2,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(11,'删除角色','system:role:delete',3,3,'','',3,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(12,'新增权限','system:permission:add',3,4,'','',1,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(13,'编辑权限','system:permission:edit',3,4,'','',2,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(14,'删除权限','system:permission:delete',3,4,'','',3,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(15,'新增白名单','system:whitelist:add',3,5,'','',1,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(16,'编辑白名单','system:whitelist:edit',3,5,'','',2,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(17,'删除白名单','system:whitelist:delete',3,5,'','',3,'',1,'2026-10-20 06:16:36','2026-10-24 07:29:43'),(18,'接口资源','system:apiresource',2,1,'/api-resources','ApiResources',5,'Connection',1,'2026-10-21 08:40:37','2026-10-24 07:29:43'),(19,'新增接口资源','system:apiresource:add',3,18,'','',1,'',1,'2026-10-21 08:40:37','2026-10-21 08:46:08'),(20,'编辑接口资源','system:apiresource:edit',3,18,'','',2,'',1,'2026-10-21 08:40:37','2026-10-21 08:46:08'),(21,'删除接口资源','system:apiresource:delete',3,18,'','',3,'',1,'2026-10-21 08:40:37','2026-10-21 08:46:08'),(22,'查询用户','system:user:list',3,2,'','',0,'',1,'2026-10-21 10:00:50','2026-10-21 10:00:50'),(23,'查询角色','system:role:list',3,3,'','',0,'',1,'2026-10-21 10:00:51','2026-10-21 10:00:51'),(24,'查询权限','system:permission:list',3,4,'','',0,'',1,'2026-10-21 10:00:51','2026-10-21 10:00:51'),(25,'查询白名单','system:whitelist:list',3,5,'','',0,'',1,'2026-10-21 10:00:51','2026-10-21 10:00:51'),(26,'查询接口资源','system:apiresource:list',3,18,'','',0,'',1,'2026-10-21 10:00:51','2026-10-21 10:00:51'),(27,'订单管理','system:order',2,0,'/orders','Orders',2,'List',1,'2026-10-21 10:15:21','2026-10-26 01:55:41'),(28,'查询订单','system:order:list',3,27,'','',0,'',1,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(30,'编辑订单','system:order:edit',3,27,'','',0,'',1,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(31,'删除订单','system:order:delete',3,27,'','',0,'',1,'2026-10-21 10:15:21','2026-10-21 10:15:21'),(32,'部门管理','system:dept',2,1,'/depts','Depts',6,'OfficeBuilding',1,'2026-10-23 01:28:41','2026-10-24 07:29:43'),(33,'查询部门','system:dept:list',3,32,'','',0,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(34,'新增部门','system:dept:add',3,32,'','',1,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(35,'编辑部门','system:dept:edit',3,32,'','',2,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(36,'删除部门','system:dept:delete',3,32,'','',3,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(37,'重置密码','system:user:resetpwd',3,2,'','',4,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(38,'登录日志','system:loginlog',2,1,'/login-logs','LoginLogs',8,'Document',1,'2026-10-23 01:28:41','2026-10-24 07:29:43'),(39,'查询登录日志','system:loginlog:list',3,38,'','',0,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(40,'在线用户','system:online',2,1,'/online','OnlineUsers',9,'Monitor',1,'2026-10-23 01:28:41','2026-10-24 07:29:43'),(41,'查询在线用户','system:online:list',3,40,'','',0,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(42,'强制下线','system:online:delete',3,40,'','',1,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(43,'操作日志','system:auditlog',2,1,'/operation-logs','OperationLogs',10,'Tickets',1,'2026-10-23 01:28:41','2026-10-24 07:29:43'),(44,'查询操作日志','system:auditlog:list',3,43,'','',0,'',1,'2026-10-23 01:28:41','2026-10-23 01:28:41');

-- ------------------------------------------------------------
-- sys_role
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `code` varchar(64) NOT NULL COMMENT '角色编码',
  `name` varchar(64) NOT NULL COMMENT '角色名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(256) DEFAULT '' COMMENT '备注',
  `data_scope` tinyint NOT NULL DEFAULT '1' COMMENT '数据范围: 1全部 2本部门 3本部门及下级 4仅本人 5自定义',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';

INSERT IGNORE INTO `sys_role` VALUES (1,'admin','超级管理员',1,'',1,'2026-10-20 06:16:36','2026-10-20 06:16:36'),(2,'user','普通用户',2,'',4,'2026-10-20 06:16:36','2026-10-23 01:28:41'),(3,'guest','访客',3,'自助注册默认角色，仅可查看本人信息',4,'2026-10-23 01:28:41','2026-10-23 01:28:41'),(4,'BU_LEADER','BU中心负责人',0,'',3,'2026-10-23 09:40:57','2026-10-23 09:40:57'),(5,'DEPT_LEADER','部门负责人',0,'',2,'2026-10-23 09:45:33','2026-10-23 09:45:33');

-- ------------------------------------------------------------
-- sys_role_dept
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_dept` (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色自定义数据范围部门';

-- ------------------------------------------------------------
-- sys_role_permission
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=686 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

INSERT IGNORE INTO `sys_role_permission` VALUES (455,1,2,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(456,1,22,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(457,1,6,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(458,1,7,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(459,1,8,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(460,1,37,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(461,1,4,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(462,1,24,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(463,1,12,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(464,1,13,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(465,1,14,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(466,1,5,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(467,1,25,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(468,1,15,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(469,1,16,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(470,1,17,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(471,1,18,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(472,1,26,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(473,1,19,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(474,1,20,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(475,1,21,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(476,1,32,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(477,1,33,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(478,1,34,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(479,1,35,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(480,1,36,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(481,1,27,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(482,1,28,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(484,1,30,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(485,1,31,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(486,1,38,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(487,1,39,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(488,1,40,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(489,1,41,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(490,1,42,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(491,1,43,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(492,1,44,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(493,1,1,'2026-10-23 08:56:16','2026-10-23 08:56:16'),(494,1,3,'2026-10-23 08:58:02','2026-10-23 08:58:02'),(495,1,9,'2026-10-23 08:58:02','2026-10-23 08:58:02'),(496,1,11,'2026-10-23 08:58:02','2026-10-23 08:58:02'),(497,1,10,'2026-10-23 08:58:02','2026-10-23 08:58:02'),(498,1,23,'2026-10-23 08:58:02','2026-10-23 08:58:02'),(520,3,37,'2026-10-23 09:00:25','2026-10-23 09:00:25'),(521,3,1,'2026-10-23 09:00:25','2026-10-23 09:00:25'),(522,3,2,'2026-10-23 09:00:25','2026-10-23 09:00:25'),(611,4,22,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(612,4,37,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(613,4,23,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(614,4,24,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(615,4,18,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(616,4,26,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(617,4,19,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(618,4,20,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(619,4,21,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(620,4,33,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(622,4,38,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(623,4,39,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(624,4,43,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(625,4,44,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(626,4,1,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(627,4,2,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(628,4,3,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(629,4,4,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(630,4,32,'2026-10-23 09:05:57','2026-10-23 09:05:57'),(632,5,22,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(633,5,37,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(634,5,23,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(635,5,24,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(636,5,18,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(637,5,26,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(638,5,19,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(639,5,20,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(640,5,21,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(641,5,33,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(643,5,38,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(644,5,39,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(645,5,43,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(646,5,44,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(647,5,1,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(648,5,2,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(649,5,3,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(650,5,4,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(651,5,32,'2026-10-23 09:06:02','2026-10-23 09:06:02'),(672,2,22,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(673,2,6,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(674,2,7,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(675,2,37,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(676,2,33,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(678,2,38,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(679,2,39,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(680,2,43,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(681,2,44,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(682,2,1,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(683,2,2,'2026-10-23 09:26:40','2026-10-23 09:26:40'),(684,2,32,'2026-10-23 09:26:40','2026-10-23 09:26:40');

-- ------------------------------------------------------------
-- sys_user
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(256) NOT NULL COMMENT 'BCrypt加密密码',
  `nickname` varchar(64) DEFAULT '' COMMENT '昵称',
  `email` varchar(128) DEFAULT '' COMMENT '邮箱',
  `phone` varchar(32) DEFAULT '' COMMENT '手机号',
  `status` int NOT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `force_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '是否强制修改密码: 0否 1是',
  `password_updated_at` datetime DEFAULT NULL COMMENT '密码最后修改时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

INSERT IGNORE INTO `sys_user` VALUES (1,'admin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi','管理员','','',1,1,0,NULL,'2026-10-20 06:16:36','2026-10-23 01:28:41'),(2,'tom','$2a$10$JQPQ/BahWVopDbF4IvNRZ.MZqAD0RmkAWBy37Nla2x9HLrJpsk.K6','tom','','',1,2,0,NULL,'2026-10-20 18:21:28','2026-10-23 01:28:41'),(3,'jerry','$2a$10$pQ4biJ.B0VfPyyyWub.CyuCf2mS5QimlKN7VGHUBoMXqm6K4b/Z3q','jerry','','',1,4,0,NULL,'2026-10-22 19:47:50','2026-10-23 01:28:41'),(4,'lisa','$2a$10$l5eYlhqLHRan2cfXGCh.FODRO5ZefY/DVHmlXhvr/DvKU13A4p8S.','lisa','','',1,3,0,'2026-10-23 09:59:34','2026-10-23 09:57:54','2026-10-23 09:57:54');

-- ------------------------------------------------------------
-- sys_user_role
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

INSERT IGNORE INTO `sys_user_role` VALUES (1,1,1,'2026-10-22 11:43:47','2026-10-22 11:43:47'),(6,2,4,'2026-10-23 01:46:19','2026-10-23 01:46:19'),(7,3,5,'2026-10-23 01:46:30','2026-10-23 01:46:30'),(8,4,2,'2026-10-23 01:57:54','2026-10-23 01:57:54');
