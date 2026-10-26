package com.ping.user.pc.service.impl.dubbo;

import com.ping.core.context.UserContextHolder;
import com.ping.user.client.spi.UserDubboClient;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UserDubboServiceImpl
 *
 * <p>注意：Dubbo 提供方线程没有 HTTP 请求上下文，不能调用 {@code StpUtil}
 * 获取当前登录人；调用方需要显式传入 userId。</p>
 *
 * @author humdeef
 * @date 2026/9/4
 **/
@DubboService
@Slf4j
public class UserDubboServiceImpl implements UserDubboClient {

    @Autowired
    private UserService userService;

    /**
     * 根据 id 获取用户名称（昵称优先，否则用户名）。
     */
    @Override
    public String getUserNameById(Long userId) {
        log.info("currentUserId:{}", UserContextHolder.getUserId());
        // 业务链路 ID（由 Dubbo filter 透传）
        log.info("[trace] bizTid={}", MDC.get("bizTid"));
        if (userId == null) {
            return null;
        }
        log.info("getUserNameById userId={}", userId);
        try {
            SysUser sysUser = userService.get(userId);
            if (sysUser.getNickname() != null && !sysUser.getNickname().isBlank()) {
                return sysUser.getNickname();
            }
            return sysUser.getUsername();
        } catch (RuntimeException e) {
            log.warn("用户不存在 userId={}", userId);
            return null;
        }
    }
}
