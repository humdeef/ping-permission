package com.ping.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BizOrderOperationLog - 订单操作日志。
 *
 * <p>每次创建、修改、支付、取消、完成、删除都追加一条记录，保留操作人类型和名称快照。
 * 订单主表只保存“最后一次修改人”，完整轨迹看这张表。</p>
 *
 * @author humdeef
 */
@Data
@TableName("ping_order_operation_log")
public class BizOrderOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 订单号快照，订单被删除后仍可追溯 */
    private String orderNo;

    /** CREATE / UPDATE / PAY / CANCEL / COMPLETE / DELETE */
    private String operation;

    /** 操作人类型：1后台 2App 3系统 4匿名 */
    private Integer operatorType;

    private Long operatorId;

    private String operatorName;

    private Integer beforeStatus;

    private Integer afterStatus;

    /** 变更摘要，例如 amount: 10.00 -> 20.00; remark: ... */
    private String changeSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
