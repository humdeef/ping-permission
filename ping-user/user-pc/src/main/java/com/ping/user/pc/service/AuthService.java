package com.ping.user.pc.service;

import com.ping.user.pc.entity.SysUser;

/**
 * 后台认证与密码管理。
 *
 * @author humdeef
 */
public interface AuthService {

    /**
     * 自助注册：校验密码策略、创建用户并绑定默认角色。
     *
     * @param username 用户名，可为空
     * @param password 密码
     * @param nickname 昵称，可为空
     * @param email 邮箱
     * @param phone 手机号
     * @return 用户信息
     */
    SysUser register(String username, String password, String nickname, String email, String phone);

    /**
     * 按用户名查询用户，不存在返回 null（登录场景使用）。
     *
     * @param username 用户名，可为空
     * @return 用户信息
     */
    SysUser findByUsernameOrNull(String username);

    /**
     * 按主键查询用户。
     *
     * @param id 主键 ID
     * @return 用户信息
     */
    SysUser findById(Long id);

    /**
     * 当前用户修改密码：校验旧密码和新密码策略。
     *
     * @param userId 用户 ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 管理员重置用户密码，并设置强制改密标记。
     *
     * @param userId 用户 ID
     * @param newPassword 新密码
     */
    void resetPassword(Long userId, String newPassword);
}
