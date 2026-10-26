package com.ping.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BizOrder - 订单表（App 用户下单示例）。
 *
 * <p>这张表只建模「App 用户下单」一种场景：</p>
 * <ul>
 *     <li>{@code user_id/user_name}：下单的 App 用户（{@code app_user.id}），
 *         不再区分 PC/App 用户类型；</li>
 *     <li>{@code update_by_*}：最后修改人。App 用户可以支付/取消，
 *         后台管理员可以改价/完成/删除，所以需要保留操作人类型。</li>
 * </ul>
 *
 * <p>创建人就是下单人，因此没有 {@code create_by_*}；部门也不属于订单，
 * 因此没有 {@code dept_id}。需要 PC 代客下单或部门级订单可见性时，再按业务扩展。</p>
 *
 * @author humdeef
 */
@Data
@TableName("ping_order")
public class BizOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号（雪花 ID 字符串） */
    private String orderNo;

    /** 下单 App 用户 ID（app_user.id） */
    private Long userId;

    /** 下单 App 用户名称快照 */
    private String userName;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单状态：0待支付 1已支付 2已取消 3已完成 */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Integer updateByType;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateById;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateByName;

    /** 乐观锁版本号 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    /** 逻辑删除：0否 1是 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
