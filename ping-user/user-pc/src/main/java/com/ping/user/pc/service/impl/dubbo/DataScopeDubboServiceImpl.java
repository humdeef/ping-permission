package com.ping.user.pc.service.impl.dubbo;

import com.ping.user.client.dto.DataScopeDTO;
import com.ping.user.client.spi.DataScopeDubboClient;
import com.ping.user.pc.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * DataScopeDubboServiceImpl - 数据权限解析的 Dubbo 提供者。
 *
 * <p>订单等业务域不直接访问 sys_role / sys_dept，统一通过本接口获取可见范围。</p>
 *
 * @author humdeef
 * @date 2026/10/24
 */
@DubboService
@Slf4j
@RequiredArgsConstructor
public class DataScopeDubboServiceImpl implements DataScopeDubboClient {

    private final DataScopeService dataScopeService;

    @Override
    public DataScopeDTO resolve(Long userId) {
        if (userId == null) {
            return DataScopeDTO.selfOnly(null);
        }
        DataScopeService.Scope scope = dataScopeService.resolve(userId);
        log.debug("解析数据范围 userId={}, all={}, deptIds={}, selfOnly={}",
                userId, scope.all(), scope.deptIds(), scope.selfOnly());
        return new DataScopeDTO(scope.all(), scope.deptIds(), scope.selfOnly(), scope.userId());
    }
}
