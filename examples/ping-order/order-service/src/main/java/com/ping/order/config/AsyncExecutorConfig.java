package com.ping.order.config;

import com.alibaba.ttl.TtlRunnable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AsyncExecutorConfig - 异步线程池配置。
 *
 * @author humdeef
 */
@Configuration
public class AsyncExecutorConfig {

    private static final String THREAD_NAME_PREFIX = "tt-async-";
    private static final int MAX_CONCURRENCY = 8;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Bean
    @Primary
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> workerContext = MDC.getCopyOfContextMap();
                try {
                    setMdcContext(callerContext);
                    runnable.run();
                } finally {
                    setMdcContext(workerContext);
                }
            };
        };
    }

    @Bean("ttAsyncExecutor")
    public AsyncTaskExecutor orderAsyncExecutor(
            @Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator,
            @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
        if (virtualThreadsEnabled) {
            return virtualThreadExecutor(mdcTaskDecorator);
        }
        return platformThreadExecutor(mdcTaskDecorator);
    }

    private ThreadPoolTaskExecutor platformThreadExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(MAX_CONCURRENCY);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setTaskDecorator(runnable -> taskDecorator.decorate(TtlRunnable.get(runnable)));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
        return executor;
    }

    private SimpleAsyncTaskExecutor virtualThreadExecutor(TaskDecorator taskDecorator) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(THREAD_NAME_PREFIX);
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(MAX_CONCURRENCY);
        executor.setTaskDecorator(runnable -> taskDecorator.decorate(TtlRunnable.get(runnable)));
        executor.setTaskTerminationTimeout(SHUTDOWN_TIMEOUT_SECONDS * 1000L);
        return executor;
    }

    private static void setMdcContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
