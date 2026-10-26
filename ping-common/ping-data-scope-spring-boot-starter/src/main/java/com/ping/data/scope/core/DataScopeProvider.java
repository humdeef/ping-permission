package com.ping.data.scope.core;

/**
 * 数据范围解析 SPI。
 *
 * <p>由各服务提供实现：</p>
 * <ul>
 *     <li>user-service：直接查 sys_user / sys_role / sys_dept；</li>
 *     <li>order-service 等业务服务：通过 Dubbo 调 user-service，或读取网关下发的数据范围声明。</li>
 * </ul>
 *
 * <p>返回 {@code null} 表示当前请求没有登录用户（匿名/内部任务），
 * 拦截器不会注入条件；已登录但解析失败时应抛出异常，绝不能静默放行。</p>
 *
 * @author humdeef
 */
@FunctionalInterface
public interface DataScopeProvider {

    /**
     * 解析当前登录用户的数据范围。
     *
     * @return 数据范围；匿名请求返回 {@code null}
     */
    DataScopeInfo resolve();
}
