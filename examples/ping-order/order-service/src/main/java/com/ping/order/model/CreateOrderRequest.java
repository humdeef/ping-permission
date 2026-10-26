package com.ping.order.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * CreateOrderRequest - CreateOrderRequest。
 *
 * @author humdeef
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "订单金额最多 10 位整数、2 位小数")
    private BigDecimal amount;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
