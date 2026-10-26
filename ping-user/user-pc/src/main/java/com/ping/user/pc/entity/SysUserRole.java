package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SysUserRole - 用户角色关联实体。
 *
 * @author humdeef
 */
@TableName("sys_user_role")
@Data
public class SysUserRole {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 角色 ID */
    private Long roleId;
}
