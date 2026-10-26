package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.WhitelistService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.model.WhitelistRequest;
import com.ping.user.pc.entity.SysApiWhitelist;
import com.ping.user.pc.mapper.SysApiWhitelistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WhitelistServiceImpl - 接口白名单服务实现。
 *
 * @author humdeef
 */
@Service
@RequiredArgsConstructor
public class WhitelistServiceImpl implements WhitelistService {
    /** 白名单数据访问 */
    private final SysApiWhitelistMapper whitelistMapper;
    /** {@inheritDoc} */
    @Override
    public IPage<SysApiWhitelist> list(int page, int size, String path) {
        // 管理页展示全部记录（含禁用），是否启用由 status 字段标识；
        // 网关鉴权使用的 listEnabledWhitelists 才过滤 status=1。
        QueryWrapper<SysApiWhitelist> wrapper = new QueryWrapper<>();
        Page<SysApiWhitelist> p = new Page<>((long) Math.max(page, 0) + 1, size);
        if (path != null) {
            wrapper.like("path", path);
        }
        return whitelistMapper.selectPage(p, wrapper);
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysApiWhitelist save(WhitelistRequest req) {
        SysApiWhitelist w;
        if (req.getId() != null) {
            w = whitelistMapper.selectById(req.getId());
            if (w == null) {
                throw new RuntimeException("白名单不存在");
            }
        } else {
            Long count = whitelistMapper.selectCount(new QueryWrapper<SysApiWhitelist>()
                    .eq("path", req.getPath()).eq("method", req.getMethod()));
            if (count > 0) {
                throw new RuntimeException("该路径和方法已存在");
            }
            w = new SysApiWhitelist();
            w.setPath(req.getPath());
            w.setMethod(req.getMethod());
        }
        w.setDescription(req.getDescription());
        w.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        if (req.getId() != null) {
            whitelistMapper.updateById(w);
        } else {
            whitelistMapper.insert(w);
        }
        return w;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (whitelistMapper.selectById(id) == null) {
            throw new RuntimeException("白名单不存在");
        }
        whitelistMapper.deleteById(id);
    }
    /** {@inheritDoc} */
    @Override
    public List<SysApiWhitelist> listAll() {
        return whitelistMapper.selectList(new QueryWrapper<>());
    }
}
