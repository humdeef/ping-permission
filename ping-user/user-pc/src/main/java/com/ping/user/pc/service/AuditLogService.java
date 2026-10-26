package com.ping.user.pc.service;

/**
 * 操作审计日志写入。
 *
 * @author humdeef
 */
public interface AuditLogService {

    /**
     * 记录当前登录人的操作。
     *
     * @param action 操作动作
     * @param targetType 目标类型
     * @param targetId 目标 ID
     * @param detail 详情
     * @param success 是否成功：0失败 1成功
     */
    void record(String action, String targetType, Object targetId, String detail, boolean success);

    /**
     * 记录指定用户的操作（登录成功/失败等场景）。
     *
     * @param userId 用户 ID
     * @param username 用户名，可为空
     * @param action 操作动作
     * @param targetType 目标类型
     * @param targetId 目标 ID
     * @param detail 详情
     * @param success 是否成功：0失败 1成功
     */
    void record(Long userId, String username, String action, String targetType,
                Object targetId, String detail, boolean success);
}
