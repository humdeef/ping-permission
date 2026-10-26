package com.ping.order.controller.pc;

import com.ping.core.annotation.LoginOnlyApi;
import com.ping.core.context.UserContextHolder;
import com.ping.core.dto.ApiResponse;
import com.ping.core.security.StpAppUtil;
import com.ping.order.client.dto.OrderDTO;
import com.ping.order.model.OrderNameResponse;
import com.ping.order.service.OrderLocalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OrderController - HTTP 对外接口，内部逻辑全部委托给 OrderLocalService
 * @author humdeef
 * @date 2025/11/17
 */
@Tag(name = "订单示例")
@RestController
@RequestMapping("/order/pc")
@Slf4j
@PreAuthorize("hasAuthority('system:order:list')")
public class OrderPcController {

    private final OrderLocalService orderLocalService;
    private final Executor          orderAsyncExecutor;

    public OrderPcController(OrderLocalService orderLocalService, @Qualifier("ttAsyncExecutor") Executor orderAsyncExecutor) {
        this.orderLocalService = orderLocalService;
        this.orderAsyncExecutor = orderAsyncExecutor;
    }

    /**
     * 移动端查询订单：打印当前 App 用户后返回该用户的订单。
     */
    @GetMapping("/queryOrderByUser")
    @LoginOnlyApi
    public ApiResponse<OrderDTO> queryOrderByUser() {
        Long userId = UserContextHolder.getUserId();
        log.info("currentUserId:{}", userId);
        log.info("[trace] bizTid={}", MDC.get("bizTid"));
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        OrderDTO orderDto = orderLocalService.getOrderByUserId(userId);
        log.info("[后台系统订单接口] 当前后台用户 userId={}, orderDto={}", userId,
                orderDto == null ? null : orderDto.getTraNo());
        return ApiResponse.ok(orderDto);
    }

    /**
     * 通过用户ID查询订单（Dubbo 内部调用 user 服务）
     */
    @GetMapping("/{userId}")
    public ApiResponse<OrderDTO> getOrder(@PathVariable Long userId) {
        return ApiResponse.ok(orderLocalService.getOrderByUserId(userId));
    }

    /**
     * 查询订单（无参数版本）
     */
    @RequestMapping("/getOrder")
    public ApiResponse<OrderNameResponse> getOrder() {
        log.info("OrderController getOrder");
        OrderDTO order = orderLocalService.getOrderByUserId(1L);
        return ApiResponse.ok(new OrderNameResponse(order.getTraNo()));
    }

    /**
     * 查询订单 V1
     */
    @RequestMapping("/getOrderV1")
    public ApiResponse<OrderNameResponse> getOrderV1() {
        return ApiResponse.ok(new OrderNameResponse("kkk555"));
    }

    /**
     * 异步查询订单（通过 Dubbo 调 user 服务）
     */
    @RequestMapping("/getOrderAsync")
    public ApiResponse<OrderNameResponse> getOrderAsync() {
        log.info("OrderController getOrderAsync");
        AtomicReference<OrderDTO> orderRef = new AtomicReference<>();
        CompletableFuture.runAsync(() -> {
            orderRef.set(orderLocalService.getOrderByUserId(1L));
        }, orderAsyncExecutor).join();
        return ApiResponse.ok(new OrderNameResponse(orderRef.get().getTraNo()));
    }

    /**
     * 异步查询订单 V1
     */
    @RequestMapping("/getOrderAsync1")
    public ApiResponse<OrderNameResponse> getOrderAsync1() {
        CompletableFuture.runAsync(() -> orderLocalService.getOrderByUserId(1L), orderAsyncExecutor);
        return ApiResponse.ok(new OrderNameResponse("getOrderAsync1"));
    }

    /**
     * 异步查询订单 V2
     */
    @RequestMapping("/getOrderAsync2")
    public ApiResponse<OrderNameResponse> getOrderAsync2() {
        CompletableFuture.runAsync(() -> orderLocalService.getOrderByUserId(1L), orderAsyncExecutor).join();
        return ApiResponse.ok(new OrderNameResponse("Tom"));
    }
}
