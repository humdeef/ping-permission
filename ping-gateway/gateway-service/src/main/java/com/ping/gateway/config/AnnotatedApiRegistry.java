package com.ping.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AnnotatedApiRegistry - 读取各服务自动注册的接口访问策略。
 *
 * <p>策略来源：</p>
 * <ul>
 *     <li>{@code @PublicApi}：免登录</li>
 *     <li>{@code @LoginOnlyApi}：后台登录域仅登录</li>
 *     <li>{@code @AppLoginOnlyApi}：App 登录域仅登录</li>
 * </ul>
 *
 * <p>登录域由注解决定，不再通过 URL 前缀硬编码判断。</p>
 *
 * @author humdeef
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnotatedApiRegistry {

    private static final String PUBLIC_KEY_PATTERN = "ping:gateway:api:public:*";
    private static final String LOGIN_KEY_PATTERN = "ping:gateway:api:login:*";
    private static final String APP_LOGIN_KEY_PATTERN = "ping:gateway:api:app-login:*";
    private static final String ANY_METHOD = "*";

    private final StringRedisTemplate redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final AtomicReference<List<Rule>> publicRules = new AtomicReference<>(List.of());
    private final AtomicReference<List<Rule>> loginRules = new AtomicReference<>(List.of());
    private final AtomicReference<List<Rule>> appLoginRules = new AtomicReference<>(List.of());

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 定时聚合所有服务的注解策略。
     */
    @Scheduled(fixedDelayString = "${ping.api-annotation.refresh-ms:10000}")
    public void refresh() {
        try {
            List<Rule> publicLoaded = load(PUBLIC_KEY_PATTERN);
            List<Rule> loginLoaded = load(LOGIN_KEY_PATTERN);
            List<Rule> appLoginLoaded = load(APP_LOGIN_KEY_PATTERN);
            publicRules.set(publicLoaded);
            loginRules.set(loginLoaded);
            appLoginRules.set(appLoginLoaded);
            log.debug("[ApiAnnotation] 已加载 public={}, login={}, appLogin={}",
                    publicLoaded.size(), loginLoaded.size(), appLoginLoaded.size());
        } catch (Exception e) {
            log.warn("[ApiAnnotation] 刷新失败: {}", e.getMessage());
        }
    }

    /** 是否命中 @PublicApi（免登录） */
    public boolean isPublic(String method, String path) {
        return matches(publicRules.get(), method, path);
    }

    /** 是否命中 @LoginOnlyApi（后台登录域仅登录） */
    public boolean isLoginOnly(String method, String path) {
        return matches(loginRules.get(), method, path);
    }

    /** 是否命中 @AppLoginOnlyApi（App 登录域仅登录） */
    public boolean isAppLoginOnly(String method, String path) {
        return matches(appLoginRules.get(), method, path);
    }

    private List<Rule> load(String keyPattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(keyPattern).count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        List<Rule> rules = new ArrayList<>();
        for (String key : keys) {
            Set<String> members = redisTemplate.opsForSet().members(key);
            if (members == null) {
                continue;
            }
            for (String member : members) {
                int idx = member.indexOf('|');
                if (idx <= 0 || idx == member.length() - 1) {
                    continue;
                }
                rules.add(new Rule(member.substring(0, idx), member.substring(idx + 1)));
            }
        }
        // 更长的路径模式优先匹配
        rules.sort(Comparator.comparingInt((Rule r) -> -r.path.length()));
        return rules;
    }

    private boolean matches(List<Rule> rules, String method, String path) {
        for (Rule rule : rules) {
            if (matchesMethod(rule.method, method) && pathMatcher.match(rule.path, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        if (ANY_METHOD.equals(ruleMethod)) {
            return true;
        }
        return ruleMethod.equalsIgnoreCase(requestMethod);
    }

    private record Rule(String method, String path) {
    }
}
