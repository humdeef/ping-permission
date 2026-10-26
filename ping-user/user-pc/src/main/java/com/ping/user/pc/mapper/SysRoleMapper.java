package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SysRoleMapper - 角色 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 分页查询角色。
     *
     * @param page 分页参数
     * @param name 角色名称，可为空
     * @param code 角色编码，可为空
     * @return 角色分页结果
     */
    IPage<SysRole> search(Page<SysRole> page, @Param("name") String name, @Param("code") String code);
}
