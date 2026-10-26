package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.pc.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/**
 * SysUserRoleMapper - 用户角色关联 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询用户关联的角色 ID。
     *
     * @param userId 用户 ID
     * @return 角色 ID 集合
     */
    Set<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的全部角色关联。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") Long userId);
}
