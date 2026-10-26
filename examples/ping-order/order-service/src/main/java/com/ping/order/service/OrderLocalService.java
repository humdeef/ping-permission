package com.ping.order.service;

import com.ping.order.client.dto.OrderDTO;

/**
 * 订单内部业务层。
 *
 * @author humdeef
 */
public interface OrderLocalService {

    /**
     * 根据用户 ID 查询订单信息（含用户名称）。
     *
     * @param userId 用户 ID
     * @return 处理结果
     */
    OrderDTO getOrderByUserId(Long userId);

    /**
     * 使用调用方传入的用户名称查询订单（App 用户场景）。
     *
     * @param userId   用户 ID
     * @param userName 用户名称
     * @return 订单信息
     */
    OrderDTO getOrderByUserId(Long userId, String userName);
}
