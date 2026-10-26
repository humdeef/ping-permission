package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.pc.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * SysPermissionMapper - 权限 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询全部权限，按排序值升序。
     *
     * @return 权限列表
     */
    List<SysPermission> findAllOrdered();

    /**
     * 查询指定父节点的子权限，按排序值升序。
     *
     * @param parentId 父权限 ID
     * @return 子权限列表
     */
    List<SysPermission> findByParentIdOrderBySort(Long parentId);
}
