package com.ping.user.app.service;

import com.ping.user.app.entity.AppUser;

/**
 * C 端注册与登录。
 *
 * @author humdeef
 */
public interface AppAuthService {

    /**
     * 手机号注册：校验密码策略并创建用户与手机号身份。
     *
     * @param phone 手机号
     * @param password 密码
     * @param nickname 昵称，可为空
     * @return App 用户信息
     */
    AppUser register(String phone, String password, String nickname);

    /**
     * 手机号 + 密码登录。
     *
     * @param phone 手机号
     * @param password 密码
     * @return App 用户信息
     */
    AppUser login(String phone, String password);

    /**
     * 按手机号查询用户，不存在返回 null。
     *
     * @param phone 手机号
     * @return App 用户信息
     */
    AppUser findByPhone(String phone);

    /**
     * 按主键查询用户。
     *
     * @param id 主键 ID
     * @return App 用户信息
     */
    AppUser findById(Long id);
}
