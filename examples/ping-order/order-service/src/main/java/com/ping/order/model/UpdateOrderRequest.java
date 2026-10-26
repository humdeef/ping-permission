package com.ping.order.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * UpdateOrderRequest - 修改订单请求。
 *
 * <p>只允许修改金额和备注；状态变更走支付/取消/完成接口，避免绕过状态机。</p>
 *
 * @author humdeef
 */
@Data
public class UpdateOrderRequest {

    @DecimalMin(value = "0.01", message = "订单金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "订单金额最多 10 位整数、2 位小数")
    private BigDecimal amount;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;

    public boolean isEmpty() {
        return amount == null && remark == null;
    }
}
