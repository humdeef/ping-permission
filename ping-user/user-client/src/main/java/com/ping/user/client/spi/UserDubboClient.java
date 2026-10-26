package com.ping.user.client.spi;

/**
 * UserDubboClient
 * @author humdeef
 * @date 2026/9/4
 * @desc UserDubboClient
 **/
public interface UserDubboClient {

    /**
     * 根据 ID 获取用户名称。
     *
     * @param userId 用户 ID
     * @return 用户名称
     */
    String getUserNameById(Long userId);
}
