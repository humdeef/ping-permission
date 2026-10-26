package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.entity.SysApiWhitelist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SysApiWhitelistMapper - 接口白名单 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysApiWhitelistMapper extends BaseMapper<SysApiWhitelist> {

    /**
     * 分页查询接口白名单。
     *
     * @param page 分页参数
     * @param path 路径关键字，可为空
     * @return 白名单分页结果
     */
    IPage<SysApiWhitelist> search(Page<SysApiWhitelist> page, @Param("path") String path);
}
