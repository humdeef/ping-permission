package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysLoginLog;

/**
 * 登录日志。
 *
 * @author humdeef
 */
public interface LoginLogService {

    /**
     * 记录当前请求的登录结果（IP/UA 在请求线程内读取）。
     *
     * @param userId 用户 ID
     * @param username 用户名，可为空
     * @param success 是否成功：0失败 1成功
     * @param message 结果说明
     */
    void recordCurrentRequest(Long userId, String username, boolean success, String message);

    /**
     * 按数据范围分页查询登录日志。
     *
     * @param scope 数据范围
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param username 用户名，可为空
     * @param success 是否成功：0失败 1成功
     * @return 分页结果
     */
    IPage<SysLoginLog> page(DataScopeService.Scope scope, int page, int size,
                            String username, Integer success);
}
