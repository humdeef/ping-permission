package com.ping.user.pc.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuNode - 当前用户可见的菜单树节点。
 *
 * <p>只包含目录（type=1）与菜单（type=2），按钮权限不会出现在菜单树中。
 * 前端据此动态注册路由并渲染侧边栏：
 * <ul>
 *     <li>{@code path}：前端路由地址</li>
 *     <li>{@code component}：views 目录下的组件名（不含 .vue）</li>
 *     <li>{@code visible}：0 表示注册路由但不在侧边栏展示（如详情页）</li>
 * </ul>
 *
 * @author humdeef
 */
@Data
public class MenuNode {
    /** 权限 ID */
    private Long id;
    /** 父级 ID */
    private Long parentId;
    /** 菜单名称 */
    private String name;
    /** 权限编码 */
    private String code;
    /** 类型：1 目录 2 菜单 */
    private Integer type;
    /** 前端路由地址 */
    private String path;
    /** 前端组件名 */
    private String component;
    /** 图标 */
    private String icon;
    /** 排序 */
    private Integer sort;
    /** 是否在侧边菜单显示：1 显示 0 隐藏 */
    private Integer visible;
    /** 子菜单 */
    private List<MenuNode> children = new ArrayList<>();
}
