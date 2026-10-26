package com.ping.user.client.spi;

import com.ping.user.client.dto.DataScopeDTO;

/**
 * DataScopeDubboClient - 数据权限 Dubbo 契约。
 *
 * <p>数据权限规则（角色 -> 部门范围）集中在用户域维护，其他服务通过该接口获取
 * 当前用户的数据范围，避免各自重复实现。</p>
 *
 * @author humdeef
 * @date 2026/10/24
 */
public interface DataScopeDubboClient {

    /**
     * 解析指定后台用户的数据范围（多角色取并集）。
     *
     * @param userId sys_user.id
     * @return 数据范围；用户不存在时返回「仅本人」
     */
    DataScopeDTO resolve(Long userId);
}
