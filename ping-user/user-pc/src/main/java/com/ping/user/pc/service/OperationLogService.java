package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysOperationLog;

import java.util.List;

/**
 * 操作审计日志查询。
 *
 * @author humdeef
 */
public interface OperationLogService {

    /**
     * 按数据范围分页查询操作日志。
     *
     * @param scope 数据范围
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param username 用户名，可为空
     * @param action 操作动作
     * @param success 是否成功：0失败 1成功
     * @return 分页结果
     */
    IPage<SysOperationLog> page(DataScopeService.Scope scope, int page, int size,
                                String username, String action, Integer success);

    /**
     * 导出用查询，最多返回 1 万条。
     *
     * @param scope 数据范围
     * @param username 用户名，可为空
     * @param action 操作动作
     * @param success 是否成功：0失败 1成功
     * @return 列表
     */
    List<SysOperationLog> listForExport(DataScopeService.Scope scope, String username,
                                        String action, Integer success);
}
