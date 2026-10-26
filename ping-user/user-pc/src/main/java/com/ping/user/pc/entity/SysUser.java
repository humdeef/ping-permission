package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysUser - 后台用户实体。
 *
 * @author humdeef
 */
@TableName("sys_user")
@Data
public class SysUser {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户名（唯一） */
    private String username;

    /** 密码哈希，禁止序列化到响应中 */
    @JsonIgnore
    private String password;
    /** 昵称 */
    private String nickname;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 状态：0禁用 1启用 */
    private Integer status = 1;

    /** 部门 ID */
    private Long deptId;

    /** 是否强制修改密码：0 否 1 是 */
    private Integer forceChangePassword = 0;

    /** 密码最后修改时间 */
    private LocalDateTime passwordUpdatedAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
