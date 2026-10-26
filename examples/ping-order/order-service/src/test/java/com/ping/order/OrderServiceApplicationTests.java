package com.ping.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 完整上下文启动测试。
 *
 * <p>需要 Nacos / MySQL / Redis 等外部依赖，默认跳过；本地启动基础设施后执行：
 * {@code PING_IT=true mvn test -pl examples/ping-order/order-service -am}</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PING_IT", matches = "true")
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
