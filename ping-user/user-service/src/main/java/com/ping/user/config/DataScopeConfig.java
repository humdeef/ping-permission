package com.ping.user.config;

import com.ping.core.context.UserContextHolder;
import com.ping.data.scope.core.DataScopeException;
import com.ping.data.scope.core.DataScopeInfo;
import com.ping.data.scope.core.DataScopeProvider;
import com.ping.user.pc.service.DataScopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service 的数据范围来源：直接复用现有 {@link DataScopeService}。
 *
 * <p>解析结果按请求缓存在 DataScopeContext，后续所有 SQL 共享。</p>
 *
 * @author humdeef
 */
@Configuration
public class DataScopeConfig {

    @Bean
    public DataScopeProvider dataScopeProvider(DataScopeService dataScopeService) {
        return () -> {
            Long userId = UserContextHolder.getUserId();
            if (userId == null) {
                return null;
            }
            // 后台数据权限只针对 sys_user；App 用户 ID 与 sys_user 无关，不能套用
            if (UserContextHolder.isAppUser()) {
                throw new DataScopeException("App 用户不支持后台数据权限过滤");
            }
            DataScopeService.Scope scope = dataScopeService.resolve(userId);
            return new DataScopeInfo(scope.all(), scope.deptIds(), scope.selfOnly(), scope.userId());
        };
    }
}
