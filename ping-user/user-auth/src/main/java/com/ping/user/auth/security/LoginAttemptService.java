package com.ping.user.auth.security;

/**
 * 登录失败计数与账号锁定。
 *
 * @author humdeef
 */
public interface LoginAttemptService {

    /**
     * 账号是否处于锁定状态。
     *
     * @param username 用户名，可为空
     * @return 是否满足条件
     */
    boolean isLocked(String username);

    /**
     * 剩余锁定秒数，未锁定返回 0。
     *
     * @param username 用户名，可为空
     * @return 数值结果
     */
    long remainingLockSeconds(String username);

    /**
     * 记录一次失败，返回剩余可尝试次数；达到上限时锁定账号。
     *
     * @param username 用户名，可为空
     * @return 数值结果
     */
    int recordFailure(String username);

    /**
     * 登录成功后清理失败计数和锁定标记。
     *
     * @param username 用户名，可为空
     */
    void clear(String username);
}
