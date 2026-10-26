package com.ping.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadPoolConfig - 线程池配置
 *
 * @author humdeef
 * @date 2026/10/19
 */
@Configuration
public class ThreadPoolConfig {

    private final BlockingQueue<Runnable> asyncSenderThreadPoolQueue = new LinkedBlockingQueue<>(50000);

    /**
     * 用户角色权限获取线程池
     */
    @Bean("userRolePermThreadPool")
    public ExecutorService userRolePermThreadPool() {
        return new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2,
                60L, TimeUnit.SECONDS,
                this.asyncSenderThreadPoolQueue,
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "UserRolePermExecutor_" + this.threadIndex.incrementAndGet());
                    }
                }
        );
    }
}
