package com.ping.order.controller.pc;

import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.order.entity.BizOrderOperationLog;
import com.ping.order.model.OrderResponse;
import com.ping.order.model.UpdateOrderRequest;
import com.ping.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * OrderPcOrderController - 后台订单管理接口。
 *
 * <p>订单只能由 App 用户创建；后台负责查询、改价、支付、取消、完成和删除。
 * 网关按 {@code sys_api_resource} 的规则校验权限码，服务内再用
 * {@code @authz.has(...)} 做二次校验。管理员角色会直接通过。</p>
 *
 * @author humdeef
 */
@Tag(name = "订单管理")
@RestController
@RequestMapping("/order/pc/orders")
@RequiredArgsConstructor
public class OrderPcOrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("@authz.has('system:order:list')")
    public ApiResponse<PageResult<OrderResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String userName) {
        return ApiResponse.ok(orderService.page(page, size, status, orderNo, userName));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('system:order:list')")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:order:edit')")
    public ApiResponse<OrderResponse> update(@PathVariable Long id,
                                             @RequestBody @Valid UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("@authz.has('system:order:edit')")
    public ApiResponse<OrderResponse> pay(@PathVariable Long id) {
        return ApiResponse.ok(orderService.pay(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authz.has('system:order:edit')")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@authz.has('system:order:edit')")
    public ApiResponse<OrderResponse> complete(@PathVariable Long id) {
        return ApiResponse.ok(orderService.complete(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:order:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("@authz.has('system:order:list')")
    public ApiResponse<List<BizOrderOperationLog>> logs(@PathVariable Long id) {
        return ApiResponse.ok(orderService.logs(id));
    }
}
