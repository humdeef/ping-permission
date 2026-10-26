package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysPermission - 权限实体。
 *
 * @author humdeef
 */
@TableName("sys_permission")
@Data
public class SysPermission {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 权限名称 */
    private String name;
    /** 权限编码（唯一，创建后不可修改） */
    private String code;

    /** 类型: 1目录 2菜单 3按钮 */
    private Integer type = 1;
    /** 父级 ID */
    private Long parentId = 0L;
    /** 前端路由地址（菜单类型使用，如 /users） */
    private String path;
    /** 前端组件名（对应 ping-web/src/views 下的文件名，不含 .vue） */
    private String component;
    /** 排序 */
    private Integer sort = 0;
    /** 图标 */
    private String icon;
    /** 是否在侧边菜单显示：1 显示 0 隐藏 */
    private Integer visible = 1;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
