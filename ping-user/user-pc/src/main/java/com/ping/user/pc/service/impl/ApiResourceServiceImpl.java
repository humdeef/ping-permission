package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.ApiResourceService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.model.ApiResourceRequest;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.mapper.SysApiResourceMapper;
import com.ping.user.pc.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * ApiResourceServiceImpl - 接口资源管理
 *
 * <p>接口资源即网关鉴权规则（方法 + 路径 → 权限编码）。变更后无需踢下线，
 * 网关 {@code ApiPermissionRegistry} 每 60 秒刷新一次规则即可生效。</p>
 *
 * @author humdeef
 */
@Service
@RequiredArgsConstructor
public class ApiResourceServiceImpl implements ApiResourceService {
    /** 接口资源数据访问 */
    private final SysApiResourceMapper apiResourceMapper;
    /** 权限数据访问 */
    private final SysPermissionMapper permissionMapper;

    /** 管理页：展示全部记录（含禁用），分页 + 路径/权限编码过滤 */
    @Override
    public IPage<SysApiResource> list(int page, int size, String path, String permissionCode) {
        QueryWrapper<SysApiResource> wrapper = new QueryWrapper<>();
        if (path != null && !path.isBlank()) {
            wrapper.like("path", path);
        }
        if (permissionCode != null && !permissionCode.isBlank()) {
            wrapper.eq("permission_code", permissionCode);
        }
        wrapper.orderByAsc("sort", "id");
        return apiResourceMapper.selectPage(new Page<>((long) Math.max(page, 0) + 1, size), wrapper);
    }

    /** 全部记录（供权限页展示已挂接的接口） */
    @Override
    public List<SysApiResource> listAll() {
        return apiResourceMapper.selectList(
                new QueryWrapper<SysApiResource>().orderByAsc("sort", "id"));
    }

    /**
     * 限定权限编码范围的分页查询（非管理员只能看到自己角色权限挂接的资源）。
     */
    @Override
    public IPage<SysApiResource> list(Collection<String> permissionCodes, int page, int size,
                                      String path, String permissionCode) {
        Page<SysApiResource> p = new Page<>((long) Math.max(page, 0) + 1, size);
        QueryWrapper<SysApiResource> wrapper = scopedWrapper(permissionCodes);
        if (wrapper == null) {
            return p;
        }
        if (path != null && !path.isBlank()) {
            wrapper.like("path", path);
        }
        if (permissionCode != null && !permissionCode.isBlank()) {
            wrapper.eq("permission_code", permissionCode);
        }
        wrapper.orderByAsc("sort", "id");
        return apiResourceMapper.selectPage(p, wrapper);
    }

    /** 限定权限编码范围的全量查询 */
    @Override
    public List<SysApiResource> listAll(Collection<String> permissionCodes) {
        QueryWrapper<SysApiResource> wrapper = scopedWrapper(permissionCodes);
        if (wrapper == null) {
            return List.of();
        }
        return apiResourceMapper.selectList(wrapper.orderByAsc("sort", "id"));
    }

    /** 权限编码为空时不返回任何资源（返回 null 表示无可见数据） */
    private QueryWrapper<SysApiResource> scopedWrapper(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return null;
        }
        return new QueryWrapper<SysApiResource>().in("permission_code", permissionCodes);
    }

    /** 网关用：仅启用的规则 */
    @Override
    public List<SysApiResource> listEnabled() {
        return apiResourceMapper.selectList(
                new QueryWrapper<SysApiResource>()
                        .eq("status", 1)
                        .orderByAsc("sort", "id"));
    }

    /** 按权限编码批量查询挂接的接口资源 */
    @Override
    public List<SysApiResource> listByPermissionCodes(java.util.Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        return apiResourceMapper.selectList(
                new QueryWrapper<SysApiResource>()
                        .in("permission_code", permissionCodes)
                        .orderByAsc("sort", "id"));
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysApiResource save(ApiResourceRequest req) {
        // 校验权限编码存在，避免挂接到不存在的权限
        Long count = permissionMapper.selectCount(
                new QueryWrapper<SysPermission>().eq("code", req.getPermissionCode()));
        if (count == 0) {
            throw new RuntimeException("权限编码不存在: " + req.getPermissionCode());
        }

        SysApiResource r;
        if (req.getId() != null) {
            r = apiResourceMapper.selectById(req.getId());
            if (r == null) {
                throw new RuntimeException("接口资源不存在");
            }
        } else {
            r = new SysApiResource();
        }
        // 路径 + 方法唯一性校验（编辑时排除自身）
        Long dup = apiResourceMapper.selectCount(new QueryWrapper<SysApiResource>()
                .eq("path", req.getPath())
                .eq("method", req.getMethod())
                .ne(req.getId() != null, "id", req.getId()));
        if (dup > 0) {
            throw new RuntimeException("该路径和方法已存在");
        }

        r.setPermissionCode(req.getPermissionCode());
        r.setPath(req.getPath());
        r.setMethod(req.getMethod());
        r.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        r.setDescription(req.getDescription());
        r.setSort(req.getSort() != null ? req.getSort() : 0);
        if (req.getId() != null) {
            apiResourceMapper.updateById(r);
        } else {
            apiResourceMapper.insert(r);
        }
        return r;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (apiResourceMapper.selectById(id) == null) {
            throw new RuntimeException("接口资源不存在");
        }
        apiResourceMapper.deleteById(id);
    }
}
