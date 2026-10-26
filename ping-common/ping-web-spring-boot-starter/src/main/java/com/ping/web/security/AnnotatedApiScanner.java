package com.ping.web.security;

import com.ping.core.annotation.AppLoginOnlyApi;
import com.ping.core.annotation.LoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AnnotatedApiScanner - 扫描接口访问策略注解并自动注册到网关。
 *
 * <p>三种策略：</p>
 * <ul>
 *     <li>{@link PublicApi}：免登录</li>
 *     <li>{@link LoginOnlyApi}：后台登录域仅登录（StpUtil）</li>
 *     <li>{@link AppLoginOnlyApi}：App 登录域仅登录（StpLogic("app")）</li>
 * </ul>
 *
 * <p>方法级注解优先于类级注解。扫描结果按服务名写入共享 Redis，网关按注解策略校验，
 * 不再依赖 URL 前缀判断登录域。</p>
 *
 * <p>Redis Key：</p>
 * <ul>
 *     <li>{@code ping:gateway:api:public:{serviceName}}：免登录</li>
 *     <li>{@code ping:gateway:api:login:{serviceName}}：后台仅登录</li>
 *     <li>{@code ping:gateway:api:app-login:{serviceName}}：App 仅登录</li>
 * </ul>
 *
 * @author humdeef
 */
@Slf4j
public class AnnotatedApiScanner {

    private static final String KEY_PREFIX = "ping:gateway:api:";
    private static final long TTL_SECONDS = 120;
    private static final long REFRESH_SECONDS = 30;

    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final String serviceName;

    private ScheduledExecutorService scheduler;
    private Set<String> lastPublic = Set.of();
    private Set<String> lastLogin = Set.of();
    private Set<String> lastAppLogin = Set.of();

    public AnnotatedApiScanner(ObjectProvider<RequestMappingHandlerMapping> mappingProvider,
                               ObjectProvider<StringRedisTemplate> redisProvider,
                               String serviceName) {
        this.mappingProvider = mappingProvider;
        this.redisProvider = redisProvider;
        this.serviceName = serviceName;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        sync();
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "api-annotation-sync");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sync, REFRESH_SECONDS, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                redis.delete(publicKey());
                redis.delete(loginKey());
                redis.delete(appLoginKey());
            }
        } catch (Exception e) {
            log.debug("清理接口策略注册失败: {}", e.getMessage());
        }
    }

    private void sync() {
        RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (mapping == null || redis == null) {
            return;
        }
        try {
            Set<String> publicEntries = new HashSet<>();
            Set<String> loginEntries = new HashSet<>();
            Set<String> appLoginEntries = new HashSet<>();

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                HandlerMethod handler = entry.getValue();
                Policy policy = resolvePolicy(handler);
                if (policy == Policy.NONE) {
                    continue;
                }
                for (String pattern : entry.getKey().getPatternValues()) {
                    String normalized = normalizePattern(pattern);
                    for (String method : methodNames(entry.getKey())) {
                        String item = method + "|" + normalized;
                        if (policy == Policy.PUBLIC) {
                            publicEntries.add(item);
                        } else if (policy == Policy.APP_LOGIN) {
                            appLoginEntries.add(item);
                        } else if (policy == Policy.LOGIN) {
                            loginEntries.add(item);
                        }
                    }
                }
            }

            write(redis, publicKey(), publicEntries);
            write(redis, loginKey(), loginEntries);
            write(redis, appLoginKey(), appLoginEntries);

            if (!publicEntries.equals(lastPublic)
                    || !loginEntries.equals(lastLogin)
                    || !appLoginEntries.equals(lastAppLogin)) {
                lastPublic = publicEntries;
                lastLogin = loginEntries;
                lastAppLogin = appLoginEntries;
                log.info("[ApiAnnotation] 已注册 service={}, public={}, login={}, appLogin={}",
                        serviceName, publicEntries, loginEntries, appLoginEntries);
            }
        } catch (Exception e) {
            log.warn("[ApiAnnotation] 扫描/注册失败 service={}: {}", serviceName, e.getMessage());
        }
    }

    /**
     * 方法级注解优先于类级注解；{@link PublicApi} 优先级最高。
     */
    private Policy resolvePolicy(HandlerMethod handler) {
        boolean methodPublic = handler.hasMethodAnnotation(PublicApi.class);
        boolean methodLoginOnly = handler.hasMethodAnnotation(LoginOnlyApi.class);
        boolean methodAppLoginOnly = handler.hasMethodAnnotation(AppLoginOnlyApi.class);

        boolean classPublic = handler.getBeanType().isAnnotationPresent(PublicApi.class);
        boolean classLoginOnly = handler.getBeanType().isAnnotationPresent(LoginOnlyApi.class);
        boolean classAppLoginOnly = handler.getBeanType().isAnnotationPresent(AppLoginOnlyApi.class);

        if (methodPublic) {
            return Policy.PUBLIC;
        }
        if (methodAppLoginOnly) {
            return Policy.APP_LOGIN;
        }
        if (methodLoginOnly) {
            return Policy.LOGIN;
        }
        if (classPublic) {
            return Policy.PUBLIC;
        }
        if (classAppLoginOnly) {
            return Policy.APP_LOGIN;
        }
        if (classLoginOnly) {
            return Policy.LOGIN;
        }
        return Policy.NONE;
    }

    private Set<String> methodNames(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return Set.of("*");
        }
        return methods.stream().map(Enum::name).collect(Collectors.toSet());
    }

    /** 把 /order/app/{id} 转成网关可匹配的 /order/app/* */
    private String normalizePattern(String pattern) {
        return pattern.replaceAll("\\{[^/]+}", "*");
    }

    private void write(StringRedisTemplate redis, String key, Set<String> entries) {
        redis.delete(key);
        if (entries.isEmpty()) {
            return;
        }
        redis.opsForSet().add(key, entries.toArray(new String[0]));
        redis.expire(key, Duration.ofSeconds(TTL_SECONDS));
    }

    private String publicKey() {
        return KEY_PREFIX + "public:" + serviceName;
    }

    private String loginKey() {
        return KEY_PREFIX + "login:" + serviceName;
    }

    private String appLoginKey() {
        return KEY_PREFIX + "app-login:" + serviceName;
    }

    private enum Policy {
        /** 未标注访问策略。 */
        NONE,
        /** 免登录。 */
        PUBLIC,
        /** 后台登录域仅登录。 */
        LOGIN,
        /** App 登录域仅登录。 */
        APP_LOGIN
    }
}
