package com.ping.order.config;

import com.ping.core.security.PermissionProvider;
import com.ping.user.client.dto.UserInfoDTO;
import com.ping.user.client.spi.PermissionDubboClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * DubboPermissionProvider - order-service 的权限数据来源实现。
 *
 * <p>token 会话缓存缺失时，通过 Dubbo 调用 user-service 获取角色与权限。</p>
 *
 * @author humdeef
 */
@Slf4j
@Component
public class DubboPermissionProvider implements PermissionProvider {

    @DubboReference(check = false, timeout = 3000)
    private PermissionDubboClient permissionDubboClient;

    @Override
    public List<String> roles(Long userId) {
        UserInfoDTO info = load(userId);
        return info == null || info.getRoles() == null ? Collections.emptyList() : info.getRoles();
    }

    @Override
    public List<String> permissions(Long userId) {
        UserInfoDTO info = load(userId);
        return info == null || info.getPermissions() == null ? Collections.emptyList() : info.getPermissions();
    }

    private UserInfoDTO load(Long userId) {
        try {
            return permissionDubboClient.getUserInfoById(userId.intValue());
        } catch (Exception e) {
            log.warn("Dubbo 获取用户权限失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
