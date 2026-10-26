-- ============================================================
-- 订单示例模块初始化（order-service 迁移基线）
-- 仅包含 ping_order / ping_order_operation_log 两张示例业务表。
--
-- 约定：
--   * V1 为幂等基线（CREATE TABLE IF NOT EXISTS + INSERT IGNORE），
--     空库全新安装与已有库升级都会执行；后续变更请新增 V2__xxx.sql。
--   * 已完成迁移的文件视为只读，不要修改，改结构请追加新版本。
-- ============================================================
/*!50503 SET NAMES utf8mb4 */;

-- ------------------------------------------------------------
-- ping_order
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ping_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '下单用户 ID（App 用户 ID）',
  `user_name` varchar(64) NOT NULL DEFAULT '' COMMENT '下单用户名称快照',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态：0待支付 1已支付 2已取消 3已完成',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `update_by_type` tinyint NOT NULL DEFAULT '4' COMMENT '最后修改人类型：1后台 2App 3系统 4匿名',
  `update_by_id` bigint NOT NULL DEFAULT '0' COMMENT '最后修改人ID',
  `update_by_name` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人名称快照',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status_create_time` (`status`,`create_time`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

INSERT IGNORE INTO `ping_order` VALUES (1,'1999999999990000001',2,'张三',99.00,0,'App 用户下单：待支付测试订单',2,2,'张三',0,0,'2026-10-24 10:00:00','2026-10-24 10:00:00'),(2,'1999999999990000002',2,'张三',199.50,1,'App 用户下单：已支付测试订单',2,2,'张三',1,0,'2026-10-24 11:00:00','2026-10-24 11:05:00');

-- ------------------------------------------------------------
-- ping_order_operation_log
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ping_order_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号快照',
  `operation` varchar(32) NOT NULL COMMENT '操作：CREATE/UPDATE/PAY/CANCEL/COMPLETE/DELETE',
  `operator_type` tinyint NOT NULL COMMENT '操作人类型：1后台 2App 3系统 4匿名',
  `operator_id` bigint NOT NULL DEFAULT '0' COMMENT '操作人ID',
  `operator_name` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人名称快照',
  `before_status` tinyint DEFAULT NULL COMMENT '变更前状态',
  `after_status` tinyint DEFAULT NULL COMMENT '变更后状态',
  `change_summary` varchar(1000) NOT NULL DEFAULT '' COMMENT '变更摘要',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_operator` (`operator_type`,`operator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单操作日志表';

INSERT IGNORE INTO `ping_order_operation_log` VALUES (1,1,'1999999999990000001','CREATE',2,2,'张三',NULL,0,'创建订单','2026-10-24 10:00:00','2026-10-24 10:00:00'),(2,2,'1999999999990000002','CREATE',2,2,'张三',NULL,0,'创建订单','2026-10-24 11:00:00','2026-10-24 11:00:00'),(3,2,'1999999999990000002','PAY',2,2,'张三',0,1,'支付订单','2026-10-24 11:05:00','2026-10-24 11:05:00');
