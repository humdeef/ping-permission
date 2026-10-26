package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysRole - 角色实体。
 *
 * @author humdeef
 */
@TableName("sys_role")
@Data
public class SysRole {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色编码（唯一，创建后不可修改） */
    private String code;
    /** 角色名称 */
    private String name;
    /** 排序 */
    private Integer sort = 0;
    /** 备注 */
    private String remark;

    /** 数据范围：1全部 2本部门 3本部门及下级 4仅本人 5自定义 */
    private Integer dataScope = 1;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
