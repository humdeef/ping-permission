package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysRoleDept - 角色自定义数据范围部门。
 *
 * @author humdeef
 */
@TableName("sys_role_dept")
@Data
public class SysRoleDept {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色 ID */
    private Long roleId;
    /** 部门 ID */
    private Long deptId;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
