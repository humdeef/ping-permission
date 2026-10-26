package com.ping.user.app.service.dubbo;

import com.ping.core.context.UserContextHolder;
import com.ping.user.app.entity.AppUser;
import com.ping.user.app.service.AppAuthService;
import com.ping.user.client.spi.AppUserDubboClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.MDC;

/**
 * AppUserDubboServiceImpl - C 端用户 Dubbo 提供方。
 *
 * @author humdeef
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class AppUserDubboServiceImpl implements AppUserDubboClient {

    private final AppAuthService appAuthService;

    @Override
    public String getNicknameById(Long userId) {
        log.info("currentUserId:{}", UserContextHolder.getUserId());
        log.info("[trace] bizTid={}", MDC.get("bizTid"));
        if (userId == null) {
            return null;
        }
        AppUser user = appAuthService.findById(userId);
        if (user == null) {
            return null;
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getPhone();
    }
}
