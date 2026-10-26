package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.model.ApiResourceRequest;

import java.util.Collection;
import java.util.List;

/**
 * 接口资源（网关鉴权规则）管理。
 *
 * @author humdeef
 */
public interface ApiResourceService {

    /**
     * 管理页分页查询全部接口资源（含禁用）。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param path 接口路径，可为空
     * @param permissionCode 权限编码，可为空
     * @return 分页结果
     */
    IPage<SysApiResource> list(int page, int size, String path, String permissionCode);

    /**
     * 查询全部接口资源。
     * @return 列表
     */
    List<SysApiResource> listAll();

    /**
     * 按权限编码范围分页查询（非管理员使用）。
     *
     * @param permissionCodes 权限编码集合
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param path 接口路径，可为空
     * @param permissionCode 权限编码，可为空
     * @return 分页结果
     */
    IPage<SysApiResource> list(Collection<String> permissionCodes, int page, int size, String path, String permissionCode);

    /**
     * 按权限编码范围查询全部（非管理员使用）。
     *
     * @param permissionCodes 权限编码集合
     * @return 列表
     */
    List<SysApiResource> listAll(Collection<String> permissionCodes);

    /**
     * 网关加载用：仅返回启用状态的规则。
     * @return 列表
     */
    List<SysApiResource> listEnabled();

    /**
     * 按权限编码批量查询挂接的接口资源。
     *
     * @param permissionCodes 权限编码集合
     * @return 列表
     */
    List<SysApiResource> listByPermissionCodes(Collection<String> permissionCodes);

    /**
     * 新增或编辑接口资源。
     *
     * @param req 请求参数
     * @return 接口资源
     */
    SysApiResource save(ApiResourceRequest req);

    /**
     * 删除接口资源。
     *
     * @param id 主键 ID
     */
    void delete(Long id);
}
