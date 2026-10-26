package com.ping.user.pc.service;

import com.ping.user.pc.entity.SysUser;

import java.util.Set;

/**
 * 数据权限解析。
 *
 * <p>支持全部、本部门、本部门及下级、仅本人、自定义部门五种范围。</p>
 *
 * @author humdeef
 */
public interface DataScopeService {

    /**
     * 解析指定用户的数据范围（多角色取并集）。
     *
     * @param userId 用户 ID
     * @return 处理结果
     */
    Scope resolve(Long userId);

    /**
     * 判断当前数据范围是否可以访问目标用户。
     *
     * @param scope 数据范围
     * @param target 目标用户
     * @return 是否满足条件
     */
    boolean canAccessUser(Scope scope, SysUser target);

    /**
     * 部门页可见范围；返回 null 表示全部可见。
     *
     * @param userId 用户 ID
     * @return 集合
     */
    Set<Long> visibleDeptIds(Long userId);

    /**
     * 查询部门及其全部下级部门 ID。
     *
     * @param deptId 部门 ID
     * @return 集合
     */
    Set<Long> deptAndChildren(Long deptId);

    /**
     * 解析后的数据范围。
     *
     * @param all      是否拥有全部数据
     * @param deptIds  可见部门 ID 集合
     * @param selfOnly 是否仅本人
     * @param userId   当前用户 ID
     */
    record Scope(boolean all, Set<Long> deptIds, boolean selfOnly, Long userId) {
    }
}
