package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysLoginLog - 登录日志。
 *
 * @author humdeef
 */
@TableName("sys_login_log")
@Data
public class SysLoginLog {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** IP 地址 */
    private String ip;
    /** User-Agent */
    private String userAgent;

    /** 是否成功：0 失败 1 成功 */
    private Integer success = 1;
    /** 说明/结果信息 */
    private String message;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
