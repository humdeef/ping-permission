package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.model.RoleRequest;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 角色管理。
 *
 * @author humdeef
 */
public interface RoleService {

    /**
     * 管理页分页查询全部角色。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param name 名称，可为空
     * @param code 编码，可为空
     * @return 分页结果
     */
    IPage<SysRole> list(int page, int size, String name, String code);

    /**
     * 按角色范围分页查询（非管理员只能看到自己的角色）。
     *
     * @param roleIds 角色 ID 集合
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param name 名称，可为空
     * @param code 编码，可为空
     * @return 分页结果
     */
    IPage<SysRole> list(Collection<Long> roleIds, int page, int size, String name, String code);

    /**
     * 按主键查询角色。
     *
     * @param id 主键 ID
     * @return 角色信息
     */
    SysRole get(Long id);

    /**
     * 新增或编辑角色，同时维护权限、数据范围和自定义部门。
     *
     * @param req 请求参数
     * @return 角色信息
     */
    SysRole save(RoleRequest req);

    /**
     * 删除角色，并清理权限、部门和用户关联。
     *
     * @param id 主键 ID
     */
    void delete(Long id);

    /**
     * 查询全部角色。
     * @return 列表
     */
    List<SysRole> listAll();

    /**
     * 按 ID 集合批量查询角色。
     *
     * @param ids ID 集合
     * @return 列表
     */
    List<SysRole> listByIds(Collection<Long> ids);

    /**
     * 查询角色拥有的权限 ID。
     *
     * @param roleId 角色 ID
     * @return 集合
     */
    Set<Long> getPermissionIds(Long roleId);

    /**
     * 查询角色自定义数据范围的部门 ID。
     *
     * @param roleId 角色 ID
     * @return 集合
     */
    Set<Long> getDeptIds(Long roleId);
}
