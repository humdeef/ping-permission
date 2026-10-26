package com.ping.gateway.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SaStpInterfaceImpl - Sa-Token 权限接口实现。
 *
 * <p>只读取 token 会话（共享 Redis）中登录时缓存的 ROLES / PERMS，纯内存/Redis 操作。
 * 缓存缺失说明是登录流程之外的旧 token，直接返回空并提示重新登录，
 * 不再在 WebFlux 事件循环线程上阻塞等待 Dubbo（避免高并发下拖垮 Netty）。</p>
 *
 * @author humdeef
 * @date 2026/10/19
 */
@Slf4j
@Component
public class SaStpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return readSession("PERMS", loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return readSession("ROLES", loginId);
    }

    private List<String> readSession(String key, Object loginId) {
        try {
            Object value = StpUtil.getTokenSession().get(key);
            if (value instanceof String s && !s.isEmpty()) {
                return SaFoxUtil.convertStringToList(s);
            }
        } catch (Exception e) {
            log.debug("读取 token 会话失败 key={}: {}", key, e.getMessage());
        }
        log.warn("token 会话缺少 {}，请重新登录 loginId={}", key, loginId);
        return List.of();
    }
}
