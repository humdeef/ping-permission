package com.ping.order.service;

import com.ping.core.dto.PageResult;
import com.ping.order.entity.BizOrderOperationLog;
import com.ping.order.model.CreateOrderRequest;
import com.ping.order.model.OrderResponse;
import com.ping.order.model.UpdateOrderRequest;

import java.util.List;

/**
 * 订单业务接口。
 *
 * @author humdeef
 */
public interface OrderService {

    /**
     * 创建订单。
     *
     * @param request 创建请求
     * @return 新建的订单
     */
    OrderResponse create(CreateOrderRequest request);

    /**
     * 查询订单详情。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    OrderResponse get(Long id);

    /**
     * 分页查询订单。
     *
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @param status    订单状态，可为空
     * @param orderNo   订单号，可为空
     * @param userName  下单人名称，可为空
     * @return 订单分页结果
     */
    PageResult<OrderResponse> page(long page, long size, Integer status, String orderNo, String userName);

    /**
     * 修改订单。
     *
     * @param id      订单 ID
     * @param request 修改请求
     * @return 修改后的订单
     */
    OrderResponse update(Long id, UpdateOrderRequest request);

    /**
     * 支付订单。
     *
     * @param id 订单 ID
     * @return 支付后的订单
     */
    OrderResponse pay(Long id);

    /**
     * 取消订单。
     *
     * @param id 订单 ID
     * @return 取消后的订单
     */
    OrderResponse cancel(Long id);

    /**
     * 完成订单。
     *
     * @param id 订单 ID
     * @return 完成后的订单
     */
    OrderResponse complete(Long id);

    /**
     * 删除订单。
     *
     * @param id 订单 ID
     */
    void delete(Long id);

    /**
     * 查询订单操作日志。
     *
     * @param id 订单 ID
     * @return 操作日志列表
     */
    List<BizOrderOperationLog> logs(Long id);
}
