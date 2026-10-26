package com.ping.user.client.spi;

/**
 * AppUserDubboClient - C 端用户查询契约。
 *
 * <p>与 {@link UserDubboClient} 区分：后者查询后台 {@code sys_user}，
 * 本接口查询 C 端 {@code app_user}，避免两套用户 ID 串号。</p>
 *
 * @author humdeef
 */
public interface AppUserDubboClient {

    /**
     * 根据 App 用户 ID 获取昵称；昵称为空时返回手机号。
     *
     * @param userId App 用户 ID
     * @return 昵称或手机号，不存在返回 null
     */
    String getNicknameById(Long userId);
}
