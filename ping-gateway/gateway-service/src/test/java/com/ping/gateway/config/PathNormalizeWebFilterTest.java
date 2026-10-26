package com.ping.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 网关路径规范化测试：防止通过重复斜杠、.. 或末尾斜杠绕过鉴权规则。
 */
class PathNormalizeWebFilterTest {

    @Test
    @DisplayName("空路径归一为根路径")
    void normalizesEmptyPath() {
        assertEquals("/", PathNormalizeWebFilter.normalize(null));
        assertEquals("/", PathNormalizeWebFilter.normalize(""));
        assertEquals("/", PathNormalizeWebFilter.normalize("/"));
    }

    @Test
    @DisplayName("折叠重复斜杠与末尾斜杠")
    void collapsesRepeatedAndTrailingSlashes() {
        assertEquals("/user/pc/users", PathNormalizeWebFilter.normalize("//user///pc/users/"));
        assertEquals("/order/app/orders", PathNormalizeWebFilter.normalize("/order//app/orders//"));
    }

    @Test
    @DisplayName("解析 . 与 .. 路径段")
    void resolvesDotSegments() {
        assertEquals("/user/pc", PathNormalizeWebFilter.normalize("/user/./pc"));
        assertEquals("/user/pc", PathNormalizeWebFilter.normalize("/user/app/../pc"));
        assertEquals("/", PathNormalizeWebFilter.normalize("/../../"));
        assertEquals("/", PathNormalizeWebFilter.normalize("/../user/.."));
    }

    @Test
    @DisplayName("已规范化的路径保持不变")
    void keepsNormalizedPathUnchanged() {
        assertEquals("/user/pc/auth/login",
                PathNormalizeWebFilter.normalize("/user/pc/auth/login"));
    }
}
