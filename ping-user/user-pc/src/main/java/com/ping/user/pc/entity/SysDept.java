package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysDept - 部门。
 *
 * @author humdeef
 */
@TableName("sys_dept")
@Data
public class SysDept {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 父级 ID */
    private Long parentId = 0L;

    /** 祖级列表，如 0,1,2 */
    private String ancestors = "";
    /** 部门名称 */
    private String name;
    /** 部门编码 */
    private String code;
    /** 负责人 */
    private String leader;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 排序 */
    private Integer sort = 0;
    /** 状态：0禁用 1启用 */
    private Integer status = 1;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
