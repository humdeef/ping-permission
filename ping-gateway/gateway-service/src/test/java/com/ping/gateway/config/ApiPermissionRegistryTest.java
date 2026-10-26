package com.ping.gateway.config;

import com.ping.user.client.dto.ApiPermissionRuleDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关白名单匹配测试：方法、Ant 路径与空规则的处理。
 */
class ApiPermissionRegistryTest {

    private ApiPermissionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ApiPermissionRegistry();
        setWhitelists(List.of());
    }

    @Test
    @DisplayName("精确路径 + 指定方法命中白名单")
    void matchesExactPathAndMethod() {
        setWhitelists(List.of(rule("GET", "/user/pc/auth/login")));

        assertTrue(registry.isWhitelisted("GET", "/user/pc/auth/login"));
        assertFalse(registry.isWhitelisted("POST", "/user/pc/auth/login"));
        assertFalse(registry.isWhitelisted("GET", "/user/pc/auth/me"));
    }

    @Test
    @DisplayName("方法为 * 时匹配任意 HTTP 方法")
    void wildcardMethodMatchesAnyMethod() {
        setWhitelists(List.of(rule("*", "/actuator/health")));

        assertTrue(registry.isWhitelisted("GET", "/actuator/health"));
        assertTrue(registry.isWhitelisted("POST", "/actuator/health"));
    }

    @Test
    @DisplayName("Ant 风格路径支持 ** 通配")
    void supportsAntPattern() {
        setWhitelists(List.of(rule("*", "/api/public/**")));

        assertTrue(registry.isWhitelisted("GET", "/api/public/info"));
        assertTrue(registry.isWhitelisted("POST", "/api/public/deep/info"));
        assertFalse(registry.isWhitelisted("GET", "/api/private/info"));
    }

    @Test
    @DisplayName("空白路径规则被忽略")
    void ignoresBlankRules() {
        setWhitelists(List.of(rule("GET", ""), rule("GET", null)));

        assertFalse(registry.isWhitelisted("GET", "/any/path"));
    }

    @Test
    @DisplayName("空规则列表不命中任何请求")
    void emptyWhitelistMatchesNothing() {
        assertFalse(registry.isWhitelisted("GET", "/user/pc/auth/login"));
    }

    @Test
    @DisplayName("鉴权规则默认加载为空，避免误放行")
    void rulesStartEmpty() {
        assertEquals(List.of(), registry.getRules());
    }

    private static ApiPermissionRuleDTO rule(String method, String path) {
        return new ApiPermissionRuleDTO(null, method, path);
    }

    @SuppressWarnings("unchecked")
    private void setWhitelists(List<ApiPermissionRuleDTO> rules) {
        AtomicReference<List<ApiPermissionRuleDTO>> reference =
                (AtomicReference<List<ApiPermissionRuleDTO>>) ReflectionTestUtils
                        .getField(registry, "whitelists");
        reference.set(rules);
    }
}
