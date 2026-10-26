package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysOperationLog - 操作审计日志。
 *
 * @author humdeef
 */
@TableName("sys_operation_log")
@Data
public class SysOperationLog {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人用户 ID（未登录操作可为空） */
    private Long userId;
    /** 用户名 */
    private String username;

    /** 动作，如 新增用户、编辑角色、登录 */
    private String action;

    /** 目标类型，如 用户、角色、权限 */
    private String targetType;

    /** 目标 ID */
    private String targetId;

    /** 链路追踪 ID（bizTid） */
    private String traceId;

    /** 详情（方法签名等，禁止记录密码等敏感信息） */
    private String detail;
    /** IP 地址 */
    private String ip;

    /** 是否成功：0失败 1成功 */
    private Integer success = 1;
    /** 创建时间 */
    private LocalDateTime createTime = LocalDateTime.now();
}
