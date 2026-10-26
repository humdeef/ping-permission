package com.ping.order.client.spi;

import com.ping.order.client.dto.OrderDTO;

/**
 * OrderDubboClient - 供其他服务通过 Dubbo 调用 order 服务
 * @author humdeef
 * @date 2026/9/8
 */
public interface OrderDubboClient {

    /**
     * 根据订单 ID 查询订单。
     *
     * @param id 订单 ID
     * @return 订单信息
     */
    OrderDTO getOrderById(Long id);
}
