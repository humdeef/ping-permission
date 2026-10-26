package com.ping.order.service;

import com.ping.order.client.spi.OrderDubboClient;
import com.ping.order.client.dto.OrderDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * OrderDubboServiceImpl - order 服务对外暴露的 Dubbo 实现
 * @author humdeef
 * @date 2026/9/8
 */
@DubboService
public class OrderDubboServiceImpl implements OrderDubboClient {

    @Override
    public OrderDTO getOrderById(Long id) {
        return new OrderDTO(Math.toIntExact(id), "trade-" + id);
    }
}
