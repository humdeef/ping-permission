package com.ping.order.model;

import com.ping.core.context.OperatorType;
import com.ping.order.entity.BizOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderResponse - OrderResponse。
 *
 * @author humdeef
 */
@Data
public class OrderResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private String userName;
    private BigDecimal amount;
    private Integer status;
    private String statusText;
    private String remark;

    private Integer updateByType;
    private String updateByTypeText;
    private Long updateById;
    private String updateByName;

    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OrderResponse from(BizOrder order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setUserName(order.getUserName());
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus());
        response.setStatusText(statusText(order.getStatus()));
        response.setRemark(order.getRemark());
        response.setUpdateByType(order.getUpdateByType());
        response.setUpdateByTypeText(operatorTypeText(order.getUpdateByType()));
        response.setUpdateById(order.getUpdateById());
        response.setUpdateByName(order.getUpdateByName());
        response.setVersion(order.getVersion());
        response.setCreateTime(order.getCreateTime());
        response.setUpdateTime(order.getUpdateTime());
        return response;
    }

    private static String statusText(Integer code) {
        try {
            return OrderStatus.fromCode(code).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    private static String operatorTypeText(Integer code) {
        return OperatorType.fromCode(code).getDescription();
    }
}
