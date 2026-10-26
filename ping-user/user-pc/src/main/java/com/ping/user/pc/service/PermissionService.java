package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.model.MenuNode;
import com.ping.user.pc.model.PermissionRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限（菜单/按钮）管理。
 *
 * @author humdeef
 */
public interface PermissionService {

    /**
     * 管理页分页查询全部权限。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param name 名称，可为空
     * @param code 编码，可为空
     * @return 分页结果
     */
    IPage<SysPermission> list(int page, int size, String name, String code);

    /**
     * 按角色范围分页查询权限（非管理员使用）。
     *
     * @param roleIds 角色 ID 集合
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param name 名称，可为空
     * @param code 编码，可为空
     * @return 分页结果
     */
    IPage<SysPermission> list(Collection<Long> roleIds, int page, int size, String name, String code);

    /**
     * 查询全部权限（平铺，前端组装树）。
     * @return 列表
     */
    List<SysPermission> listAllTree();

    /**
     * 查询角色拥有的权限 ID 集合。
     *
     * @param roleIds 角色 ID 集合
     * @return 集合
     */
    Set<Long> permissionIdsOfRoles(Collection<Long> roleIds);

    /**
     * 查询角色可见的权限树，并补全祖先节点。
     *
     * @param roleIds 角色 ID 集合
     * @return 列表
     */
    List<SysPermission> listTreeByRoleIds(Collection<Long> roleIds);

    /**
     * 查询当前用户可见的菜单树（目录 + 菜单，不含按钮）。
     *
     * <p>非管理员只返回自己角色拥有的菜单及其祖先目录；
     * 管理员返回全部菜单。前端用该结构动态注册路由、渲染侧边栏。</p>
     *
     * @param userId 用户 ID
     * @param admin  是否超级管理员
     * @return 菜单树
     */
    List<MenuNode> menuTree(Long userId, boolean admin);

    /**
     * 按角色批量查询权限，返回 角色ID -> 权限列表。
     *
     * @param roleIds 角色 ID 集合
     * @return 映射结果
     */
    Map<Long, List<SysPermission>> permissionsByRoleIds(Collection<Long> roleIds);

    /**
     * 多个角色的权限去重合并。
     *
     * @param roleIds 角色 ID 集合
     * @return 列表
     */
    List<SysPermission> permissionsOfRoles(Collection<Long> roleIds);

    /**
     * 新增或编辑权限；编码创建后不可修改。
     *
     * @param req 请求参数
     * @return 权限信息
     */
    SysPermission save(PermissionRequest req);

    /**
     * 删除权限及其子权限、角色绑定和挂接的接口资源。
     *
     * @param id 主键 ID
     */
    void delete(Long id);
}
