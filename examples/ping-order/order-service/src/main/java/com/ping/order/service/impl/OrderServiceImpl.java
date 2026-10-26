package com.ping.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.core.context.OperatorType;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import com.ping.core.dto.PageResult;
import com.ping.core.id.DistributedIdGenerator;
import com.ping.order.entity.BizOrder;
import com.ping.order.entity.BizOrderOperationLog;
import com.ping.order.mapper.BizOrderMapper;
import com.ping.order.mapper.BizOrderOperationLogMapper;
import com.ping.order.model.CreateOrderRequest;
import com.ping.order.model.OrderOperation;
import com.ping.order.model.OrderResponse;
import com.ping.order.model.OrderStatus;
import com.ping.order.model.UpdateOrderRequest;
import com.ping.order.service.OrderService;
import com.ping.user.client.spi.AppUserDubboClient;
import com.ping.user.client.spi.UserDubboClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * OrderServiceImpl - 订单业务实现。
 *
 * <p>权限模型：</p>
 * <ul>
 *     <li>App 用户：创建订单，且只能读写自己的订单；不能完成/删除订单；</li>
 *     <li>后台用户：只做管理（查询/改价/支付/取消/完成/删除），不创建订单；
 *         由 Controller 的 {@code @PreAuthorize} 校验 {@code system:order:*}；</li>
 *     <li>审计：所有写操作都记录「操作人类型 + ID + 名称快照」。</li>
 * </ul>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final String USER_NAME_PREFIX = "user#";
    private static final String SYSTEM_NAME = "system";
    private static final String ANONYMOUS_NAME = "anonymous";

    private final BizOrderMapper orderMapper;
    private final BizOrderOperationLogMapper operationLogMapper;
    private final DistributedIdGenerator idGenerator;

    @DubboReference(check = false, timeout = 3000)
    private UserDubboClient userDubboClient;

    @DubboReference(check = false, timeout = 3000)
    private AppUserDubboClient appUserDubboClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse create(CreateOrderRequest request) {
        Operator operator = currentOperator();
        if (operator.type() != OperatorType.APP) {
            throw new AccessDeniedException("订单只能由 App 用户创建");
        }
        BizOrder order = new BizOrder();
        order.setOrderNo(idGenerator.nextId());
        order.setUserId(operator.id());
        order.setUserName(operator.name());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(normalize(request.getRemark()));

        orderMapper.insert(order);
        recordLog(order, OrderOperation.CREATE, null, OrderStatus.PENDING.getCode(), "创建订单", operator);
        return OrderResponse.from(order);
    }

    @Override
    public OrderResponse get(Long id) {
        BizOrder order = requireOrder(id);
        checkReadable(order, currentOperator());
        return OrderResponse.from(order);
    }

    @Override
    public PageResult<OrderResponse> page(long page, long size, Integer status,
                                          String orderNo, String userName) {
        Operator operator = currentOperator();
        Page<BizOrder> pageRequest = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<BizOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(status != null, BizOrder::getStatus, status)
                .like(hasText(orderNo), BizOrder::getOrderNo, orderNo)
                .like(hasText(userName), BizOrder::getUserName, userName);
        if (operator.type() == OperatorType.APP) {
            wrapper.eq(BizOrder::getUserId, operator.id());
        } else if (operator.type() == OperatorType.ANONYMOUS) {
            throw new AccessDeniedException("请先登录");
        }
        wrapper.orderByDesc(BizOrder::getCreateTime).orderByDesc(BizOrder::getId);

        Page<BizOrder> result = orderMapper.selectPage(pageRequest, wrapper);
        List<OrderResponse> list = result.getRecords().stream().map(OrderResponse::from).toList();
        return PageResult.of(list, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        if (request.isEmpty()) {
            throw new IllegalArgumentException("至少提供一个需要修改的字段");
        }
        Operator operator = currentOperator();
        BizOrder order = requireOrder(id);
        checkWritable(order, operator);
        if (!Objects.equals(order.getStatus(), OrderStatus.PENDING.getCode())) {
            throw new IllegalStateException("只有待支付订单可以修改");
        }

        StringBuilder summary = new StringBuilder();
        if (request.getAmount() != null
                && request.getAmount().compareTo(order.getAmount()) != 0) {
            summary.append("amount: ").append(order.getAmount())
                    .append(" -> ").append(request.getAmount()).append("; ");
            order.setAmount(request.getAmount());
        }
        if (request.getRemark() != null
                && !Objects.equals(request.getRemark(), order.getRemark())) {
            summary.append("remark: ").append(order.getRemark())
                    .append(" -> ").append(request.getRemark()).append("; ");
            order.setRemark(normalize(request.getRemark()));
        }
        if (summary.isEmpty()) {
            return OrderResponse.from(order);
        }

        order.setUpdateByName(operator.name());
        int rows = orderMapper.updateById(order);
        if (rows == 0) {
            throw new IllegalStateException("订单已被他人修改，请刷新后重试");
        }
        recordLog(order, OrderOperation.UPDATE, order.getStatus(), order.getStatus(),
                summary.toString(), operator);
        return OrderResponse.from(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse pay(Long id) {
        return changeStatus(id, OrderStatus.PAID, OrderOperation.PAY, "支付订单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancel(Long id) {
        return changeStatus(id, OrderStatus.CANCELLED, OrderOperation.CANCEL, "取消订单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse complete(Long id) {
        return changeStatus(id, OrderStatus.COMPLETED, OrderOperation.COMPLETE, "完成订单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Operator operator = currentOperator();
        if (operator.type() != OperatorType.PC && operator.type() != OperatorType.SYSTEM) {
            throw new AccessDeniedException("只有后台用户可以删除订单");
        }
        BizOrder order = requireOrder(id);
        int rows = orderMapper.deleteById(order.getId());
        if (rows == 0) {
            throw new IllegalStateException("订单已被他人删除，请刷新后重试");
        }
        recordLog(order, OrderOperation.DELETE, order.getStatus(), order.getStatus(),
                "删除订单", operator);
    }

    @Override
    public List<BizOrderOperationLog> logs(Long id) {
        BizOrder order = requireOrder(id);
        checkReadable(order, currentOperator());
        LambdaQueryWrapper<BizOrderOperationLog> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(BizOrderOperationLog::getOrderId, id)
                .orderByDesc(BizOrderOperationLog::getId);
        return operationLogMapper.selectList(wrapper);
    }

    private OrderResponse changeStatus(Long id, OrderStatus target,
                                       String operation, String summary) {
        Operator operator = currentOperator();
        BizOrder order = requireOrder(id);
        checkWritable(order, operator);
        if (operator.type() == OperatorType.APP && target == OrderStatus.COMPLETED) {
            throw new AccessDeniedException("App 用户不能完成订单");
        }

        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("订单状态不允许从「" + current.getDescription()
                    + "」变更为「" + target.getDescription() + "」");
        }
        int before = current.getCode();
        order.setStatus(target.getCode());
        order.setUpdateByName(operator.name());
        int rows = orderMapper.updateById(order);
        if (rows == 0) {
            throw new IllegalStateException("订单已被他人修改，请刷新后重试");
        }
        recordLog(order, operation, before, target.getCode(), summary, operator);
        return OrderResponse.from(order);
    }

    private BizOrder requireOrder(Long id) {
        BizOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private void checkReadable(BizOrder order, Operator operator) {
        if (operator.type() == OperatorType.ANONYMOUS) {
            throw new AccessDeniedException("请先登录");
        }
        if (operator.type() == OperatorType.APP && !isOwner(order, operator)) {
            throw new AccessDeniedException("只能访问自己的订单");
        }
    }

    private void checkWritable(BizOrder order, Operator operator) {
        checkReadable(order, operator);
        if (operator.type() == OperatorType.APP && !isOwner(order, operator)) {
            throw new AccessDeniedException("只能修改自己的订单");
        }
    }

    private boolean isOwner(BizOrder order, Operator operator) {
        return Objects.equals(order.getUserId(), operator.id());
    }

    private void recordLog(BizOrder order, String operation, Integer beforeStatus,
                           Integer afterStatus, String summary, Operator operator) {
        BizOrderOperationLog logEntity = new BizOrderOperationLog();
        logEntity.setOrderId(order.getId());
        logEntity.setOrderNo(order.getOrderNo());
        logEntity.setOperation(operation);
        logEntity.setOperatorType(operator.type().getCode());
        logEntity.setOperatorId(operator.id());
        logEntity.setOperatorName(operator.name());
        logEntity.setBeforeStatus(beforeStatus);
        logEntity.setAfterStatus(afterStatus);
        logEntity.setChangeSummary(truncate(summary));
        operationLogMapper.insert(logEntity);
    }

    private Operator currentOperator() {
        UserContext context = UserContextHolder.get();
        if (context == null) {
            return new Operator(OperatorType.ANONYMOUS, 0L, "anonymous");
        }
        OperatorType type = context.getOperatorType();
        Long id = context.getUserId() == null ? 0L : context.getUserId();
        String name = context.getUserName();
        if (name == null || name.isBlank()) {
            name = resolveName(type, id);
        }
        return new Operator(type, id, name);
    }

    /**
     * 旧 token 没写名称快照时，按用户域查询一次；失败则退化为 user#id，不能因为审计名称影响主流程。
     */
    private String resolveName(OperatorType type, Long id) {
        try {
            if (type == OperatorType.PC) {
                String name = userDubboClient.getUserNameById(id);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } else if (type == OperatorType.APP) {
                String name = appUserDubboClient.getNicknameById(id);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("查询操作人名称失败 type={}, id={}: {}", type, id, e.getMessage());
        }
        if (type == OperatorType.SYSTEM) {
            return SYSTEM_NAME;
        }
        if (type == OperatorType.ANONYMOUS) {
            return ANONYMOUS_NAME;
        }
        return USER_NAME_PREFIX + id;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= SUMMARY_MAX_LENGTH ? value : value.substring(0, SUMMARY_MAX_LENGTH);
    }

    private record Operator(OperatorType type, Long id, String name) {
    }
}
