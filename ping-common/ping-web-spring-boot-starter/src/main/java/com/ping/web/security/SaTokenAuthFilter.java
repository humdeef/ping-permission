package com.ping.web.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import com.ping.core.context.OperatorType;
import com.ping.core.security.PermissionProvider;
import com.ping.core.security.SecurityConstants;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * SaTokenAuthFilter - 将 Sa-Token 登录态桥接到 Spring Security 上下文。
 *
 * <p>处理顺序：</p>
 * <ol>
 *     <li>优先读取 token 会话（共享 Redis）中登录时缓存的 ROLES / PERMS，零 RPC；</li>
 *     <li>缓存缺失时，若容器中存在 {@link PermissionProvider}，调用其兜底并回写会话。</li>
 * </ol>
 *
 * <p>注入后即可在 Controller 上使用
 * {@code @PreAuthorize("hasAuthority('system:user:list')")}。</p>
 *
 * @author humdeef
 */
@Slf4j
public class SaTokenAuthFilter extends OncePerRequestFilter {

    private final PermissionProvider permissionProvider;

    public SaTokenAuthFilter(PermissionProvider permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (StpUtil.isLogin()) {
                Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
                List<String> roles = readSessionList("ROLES");
                List<String> perms = readSessionList("PERMS");
                String nickname = readSessionString(SecurityConstants.SESSION_NICKNAME);
                Long deptId = parseLong(readSessionString(SecurityConstants.SESSION_DEPT_ID));

                // 写入跨线程用户上下文（含角色/权限/操作人类型），供线程池与 Dubbo 透传
                UserContextHolder.set(new UserContext(userId, new LinkedHashSet<>(roles),
                        new LinkedHashSet<>(perms), OperatorType.PC, nickname, deptId));

                // 缓存缺失（旧 token 未写会话）时走 SPI 兜底并回写
                if (roles.isEmpty() && perms.isEmpty() && permissionProvider != null) {
                    roles = safeList(permissionProvider.roles(userId));
                    perms = safeList(permissionProvider.permissions(userId));
                    StpUtil.getTokenSession().set("ROLES", String.join(",", roles));
                    StpUtil.getTokenSession().set("PERMS", String.join(",", perms));
                    UserContextHolder.set(new UserContext(userId, new LinkedHashSet<>(roles),
                            new LinkedHashSet<>(perms), OperatorType.PC, nickname, deptId));
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                roles.forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
                perms.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 未登录或 token 无效时不注入认证信息，由后续 @PreAuthorize 处理
            log.debug("Sa-Token 登录态桥接失败: {}", e.getMessage());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private List<String> readSessionList(String key) {
        try {
            Object value = StpUtil.getTokenSession().get(key);
            if (value instanceof String s && !s.isEmpty()) {
                return SaFoxUtil.convertStringToList(s);
            }
        } catch (Exception e) {
            log.debug("读取 token 会话失败 key={}: {}", key, e.getMessage());
        }
        return Collections.emptyList();
    }

    private String readSessionString(String key) {
        try {
            Object value = StpUtil.getTokenSession().get(key);
            return value instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.debug("读取 token 会话失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> safeList(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
