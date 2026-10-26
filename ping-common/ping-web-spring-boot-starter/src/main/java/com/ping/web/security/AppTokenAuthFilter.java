package com.ping.web.security;

import com.ping.core.context.OperatorType;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import com.ping.core.security.SecurityConstants;
import com.ping.core.security.StpAppUtil;
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
import java.util.List;
import java.util.Set;

/**
 * AppTokenAuthFilter - App 登录态桥接。
 *
 * <p>使用独立的 {@code StpLogic("app")}，与后台 StpUtil 隔离。
 * 该过滤器是全局的：App 路径会注入 App 登录态，非 App 路径不受影响。</p>
 *
 * @author humdeef
 */
@Slf4j
public class AppTokenAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean appAuthSet = false;
        try {
            if (StpAppUtil.isLogin()) {
                Object loginId = StpAppUtil.getLoginId();
                if (loginId != null) {
                    Long userId = Long.valueOf(String.valueOf(loginId));
                    String nickname = readSessionString(SecurityConstants.SESSION_NICKNAME);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    String.valueOf(userId), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_APP")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // 写入跨线程/跨服务上下文，Dubbo 调用时自动透传
                    UserContextHolder.set(new UserContext(userId, Set.of("app"), Set.of(),
                            OperatorType.APP, nickname));
                    appAuthSet = true;
                }
            }
        } catch (Exception e) {
            log.debug("App 登录态桥接失败: {}", e.getMessage());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (appAuthSet) {
                SecurityContextHolder.clearContext();
                UserContextHolder.clear();
            }
        }
    }

    private String readSessionString(String key) {
        try {
            Object value = StpAppUtil.LOGIC.getTokenSession().get(key);
            return value instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.debug("读取 App token 会话失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
