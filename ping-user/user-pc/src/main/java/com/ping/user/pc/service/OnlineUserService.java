package com.ping.user.pc.service;

import com.ping.user.pc.model.OnlineUserDTO;

import java.util.List;

/**
 * 在线会话查询与强制下线。
 *
 * @author humdeef
 */
public interface OnlineUserService {

    /**
     * 查询在线用户，可按用户名模糊过滤。
     *
     * @param username 用户名，可为空
     * @return 列表
     */
    List<OnlineUserDTO> list(String username);

    /**
     * 按 token 强制下线。
     *
     * @param token 登录 token
     */
    void kickout(String token);
}
