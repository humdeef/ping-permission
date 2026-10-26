package com.ping.order.controller.app;

import com.ping.core.annotation.AppLoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import com.ping.core.context.UserContextHolder;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.order.client.dto.OrderDTO;
import com.ping.order.entity.BizOrderOperationLog;
import com.ping.order.model.CreateOrderRequest;
import com.ping.order.model.OrderResponse;
import com.ping.order.model.UpdateOrderRequest;
import com.ping.order.service.OrderService;
import com.ping.user.client.spi.AppUserDubboClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OrderAppController - App 端订单接口。
 *
 * <p>标注 {@link AppLoginOnlyApi}：网关按 App 登录域（StpLogic("app")）校验，
 * 不校验后台权限编码。无需改网关配置或权限表。</p>
 *
 * @author humdeef
 */
@Slf4j
@Tag(name = "订单（App）")
@RestController
@RequestMapping("/order/app")
@AppLoginOnlyApi
@RequiredArgsConstructor
public class OrderAppController {

    private final OrderService orderService;

    /** App 用户查询（避免与后台 sys_user 的 ID 串号） */
    @DubboReference(check = false, timeout = 3000)
    private AppUserDubboClient appUserDubboClient;

    /**
     * App 端查询订单：打印当前 App 用户后返回该用户的订单。
     */
    @GetMapping("/queryOrderByUser")
    public ApiResponse<OrderDTO> queryOrderByUser() {
        Long userId = UserContextHolder.getUserId();
        log.info("currentUserId:{}", userId);
        log.info("[trace] bizTid={}", MDC.get("bizTid"));
        String username = "anonymous";
        if (userId != null) {
            username = appUserDubboClient.getNicknameById(userId);
        }
        log.info("[App 端订单接口] 当前 App 用户 userId={}, username={}", userId, username);
        OrderDTO dto = new OrderDTO();
        dto.setTraNo("tracId-1-" + username);
        return ApiResponse.ok(dto);
    }

    /**
     * App 端公开查询：允许不带 token，登录时按当前 App 用户处理，未登录按匿名处理。
     */
    @GetMapping("/queryOrderByUser/v1")
    @PublicApi
    public ApiResponse<OrderDTO> queryOrderByUserV1() {
        Long userId = UserContextHolder.getUserId();
        String username = "anonymous";
        if (userId != null) {
            username = appUserDubboClient.getNicknameById(userId);
        }
        log.info("[App 端公开接口] 当前 App 用户 userId={}, username={}", userId, username);
        OrderDTO order = new OrderDTO();
        order.setId(-1);
        order.setTraNo("trace-no-login");
        return ApiResponse.ok(order);
    }

    // ---------------- 订单读写：只能操作自己的订单 ----------------

    @GetMapping("/orders")
    public ApiResponse<PageResult<OrderResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo) {
        return ApiResponse.ok(orderService.page(page, size, status, orderNo, null));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.get(id));
    }

    @PostMapping("/orders")
    public ApiResponse<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @PutMapping("/orders/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable Long id,
                                             @RequestBody @Valid UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request));
    }

    @PostMapping("/orders/{id}/pay")
    public ApiResponse<OrderResponse> pay(@PathVariable Long id) {
        return ApiResponse.ok(orderService.pay(id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    @GetMapping("/orders/{id}/logs")
    public ApiResponse<List<BizOrderOperationLog>> logs(@PathVariable Long id) {
        return ApiResponse.ok(orderService.logs(id));
    }
}
