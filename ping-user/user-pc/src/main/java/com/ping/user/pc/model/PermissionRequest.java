package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PermissionRequest - 权限请求参数。
 *
 * @author humdeef
 */
@Data
public class PermissionRequest {
    /** 主键 ID */
    private Long id;
    /** 权限名称 */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过64")
    private String name;
    /** 权限编码（创建后不可修改） */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 128, message = "权限编码长度不能超过128")
    private String code;
    /** 类型：1目录 2菜单 3按钮 */
    private Integer type = 2;
    /** 父级 ID */
    private Long parentId = 0L;
    /** 前端路由地址（目录/菜单使用，如 /users） */
    @Size(max = 256, message = "路由地址长度不能超过256")
    private String path;
    /** 前端组件名（对应 ping-web/src/views 下的文件名，不含 .vue） */
    @Size(max = 128, message = "组件名长度不能超过128")
    private String component;
    /** 排序 */
    private Integer sort = 0;
    /** 图标 */
    @Size(max = 128, message = "图标长度不能超过128")
    private String icon;
    /** 是否在侧边菜单显示：1 显示 0 隐藏 */
    private Integer visible = 1;
}
