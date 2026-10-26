package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.pc.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * SysRolePermissionMapper - 角色权限关联 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 查询多个角色关联的权限 ID。
     *
     * @param roleIds 角色 ID 列表
     * @return 权限 ID 集合
     */
    Set<Long> findPermissionIdsByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 删除角色的全部权限关联。
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);
}
