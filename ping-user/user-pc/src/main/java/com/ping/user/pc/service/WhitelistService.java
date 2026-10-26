package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysApiWhitelist;
import com.ping.user.pc.model.WhitelistRequest;

import java.util.List;

/**
 * 接口白名单管理。
 *
 * @author humdeef
 */
public interface WhitelistService {

    /**
     * 管理页分页查询白名单（含禁用）。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param path 接口路径，可为空
     * @return 分页结果
     */
    IPage<SysApiWhitelist> list(int page, int size, String path);

    /**
     * 新增或编辑白名单。
     *
     * @param req 请求参数
     * @return 白名单
     */
    SysApiWhitelist save(WhitelistRequest req);

    /**
     * 删除白名单。
     *
     * @param id 主键 ID
     */
    void delete(Long id);

    /**
     * 查询全部白名单。
     * @return 列表
     */
    List<SysApiWhitelist> listAll();
}
