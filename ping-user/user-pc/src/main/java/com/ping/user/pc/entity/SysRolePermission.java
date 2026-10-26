package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SysRolePermission - 角色权限关联实体。
 *
 * @author humdeef
 */
@TableName("sys_role_permission")
@Data
public class SysRolePermission {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色 ID */
    private Long roleId;
    /** 权限 ID */
    private Long permissionId;
}
