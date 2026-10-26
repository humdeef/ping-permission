package com.ping.order.service.impl;

import com.ping.order.client.dto.OrderDTO;
import com.ping.order.service.OrderLocalService;
import com.ping.user.client.spi.UserDubboClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * OrderLocalServiceImpl - 内部业务层实现，通过 Dubbo 调用 user 服务。
 *
 * @author humdeef
 */
@Slf4j
@Service
public class OrderLocalServiceImpl implements OrderLocalService {
    /** 用户 Dubbo 客户端 */
    @DubboReference(check = false)
    private UserDubboClient userDubboClient;
    /** {@inheritDoc} */
    @Override
    public OrderDTO getOrderByUserId(Long userId) {
        String userName = userDubboClient.getUserNameById(userId);
        log.info("getOrderByUserId userId={}, userName={}", userId, userName);
        return new OrderDTO(1, "trade-" + userId + "-" + userName);
    }

    @Override
    public OrderDTO getOrderByUserId(Long userId, String userName) {
        log.info("getOrderByUserId userId={}, userName={}", userId, userName);
        return new OrderDTO(1, "trade-" + userId + "-" + userName);
    }
}
