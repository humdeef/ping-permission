package com.ping.user.app.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AppUser - C 端用户。
 *
 * @author humdeef
 */
@TableName("app_user")
@Data
public class AppUser {
    /** 主键 ID（雪花 ID，应用层生成） */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 手机号（唯一） */
    private String phone;

    /** BCrypt 密码哈希，禁止序列化 */
    @JsonIgnore
    private String passwordHash;
    /** 昵称 */
    private String nickname;
    /** 头像 */
    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender = 0;

    /** 0禁用 1正常 2注销中 */
    private Integer status = 1;
    /** 注册来源 */
    private String registerSource;
    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
