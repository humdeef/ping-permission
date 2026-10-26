package com.ping.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import com.ping.order.support.TableInfoTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单业务权限测试：App 用户只能操作自己的订单，后台用户只做管理。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private BizOrderMapper orderMapper;
    @Mock
    private BizOrderOperationLogMapper operationLogMapper;
    @Mock
    private DistributedIdGenerator idGenerator;

    private OrderServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoTestSupport.init(BizOrder.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderMapper, operationLogMapper, idGenerator);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.set(null);
    }

    @Test
    @DisplayName("App 用户创建订单：归属自己、待支付、记录创建日志")
    void createByAppUserSetsOwnershipAndLogs() {
        loginAsApp(2L, "张三");
        when(idGenerator.nextId()).thenReturn("1000000000000000001");
        when(orderMapper.insert(any(BizOrder.class))).thenAnswer(invocation -> {
            BizOrder inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setRemark("  加急  ");

        OrderResponse response = service.create(request);

        assertEquals(100L, response.getId());
        assertEquals("1000000000000000001", response.getOrderNo());
        assertEquals(2L, response.getUserId());
        assertEquals("张三", response.getUserName());
        assertEquals(0, response.getStatus());
        assertEquals("加急", response.getRemark());

        BizOrderOperationLog log = captureSingleLog();
        assertEquals(100L, log.getOrderId());
        assertEquals("1000000000000000001", log.getOrderNo());
        assertEquals(OrderOperation.CREATE, log.getOperation());
        assertEquals(OperatorType.APP.getCode(), log.getOperatorType());
        assertEquals(2L, log.getOperatorId());
        assertEquals("张三", log.getOperatorName());
        assertEquals(null, log.getBeforeStatus());
        assertEquals(0, log.getAfterStatus());
        assertEquals("创建订单", log.getChangeSummary());
    }

    @Test
    @DisplayName("后台用户不能创建订单")
    void createByPcUserIsRejected() {
        loginAsPc("管理员");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> service.create(request("10.00")));

        assertEquals("订单只能由 App 用户创建", exception.getMessage());
        verifyNoInteractions(orderMapper, operationLogMapper, idGenerator);
    }

    @Test
    @DisplayName("未登录不能创建订单")
    void createByAnonymousIsRejected() {
        assertThrows(AccessDeniedException.class, () -> service.create(request("10.00")));

        verifyNoInteractions(orderMapper, operationLogMapper, idGenerator);
    }

    @Test
    @DisplayName("本人和后台用户可以查看订单")
    void getAllowsOwnerAndPcUser() {
        BizOrder order = order(10L, 2L, OrderStatus.PENDING);
        when(orderMapper.selectById(10L)).thenReturn(order);

        loginAsApp(2L, "张三");
        assertEquals(10L, service.get(10L).getId());

        loginAsPc("管理员");
        assertEquals(10L, service.get(10L).getId());
    }

    @Test
    @DisplayName("App 用户不能查看他人订单，未登录不能查看")
    void getRejectsOtherAppUserAndAnonymous() {
        BizOrder order = order(10L, 2L, OrderStatus.PENDING);
        when(orderMapper.selectById(10L)).thenReturn(order);

        loginAsApp(3L, "李四");
        AccessDeniedException other = assertThrows(AccessDeniedException.class,
                () -> service.get(10L));
        assertEquals("只能访问自己的订单", other.getMessage());

        UserContextHolder.set(null);
        AccessDeniedException anonymous = assertThrows(AccessDeniedException.class,
                () -> service.get(10L));
        assertEquals("请先登录", anonymous.getMessage());
    }

    @Test
    @DisplayName("订单不存在时返回明确错误")
    void getRejectsMissingOrder() {
        loginAsPc("管理员");
        when(orderMapper.selectById(10L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.get(10L));
        assertEquals("订单不存在", exception.getMessage());
    }

    @Test
    @DisplayName("App 用户分页只看自己的订单，并限制单页最大 100 条")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageFiltersByCurrentAppUser() {
        loginAsApp(2L, "张三");
        Page<BizOrder> result = new Page<>(1, 10);
        result.setRecords(List.of(order(10L, 2L, OrderStatus.PENDING)));
        result.setTotal(1);
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);

        PageResult<OrderResponse> page = service.page(0, 1000, null, null, null);

        assertEquals(1, page.getTotal());
        assertEquals(10L, page.getList().get(0).getId());

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(100L, pageCaptor.getValue().getSize());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("user_id ="), sql);
        assertTrue(sql.contains("order by create_time desc"), sql);
    }

    @Test
    @DisplayName("后台分页按筛选条件查询，不追加用户过滤")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageForPcAppliesQueryConditions() {
        loginAsPc("管理员");
        Page<BizOrder> result = new Page<>(1, 10);
        result.setRecords(List.of());
        result.setTotal(0);
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);

        service.page(1, 10, 1, "NO1", "张");

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("status ="), sql);
        assertTrue(sql.contains("order_no like"), sql);
        assertTrue(sql.contains("user_name like"), sql);
        assertTrue(!sql.contains("user_id ="), sql);
    }

    @Test
    @DisplayName("未登录不能分页查询")
    void pageRejectsAnonymous() {
        assertThrows(AccessDeniedException.class,
                () -> service.page(1, 10, null, null, null));

        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("本人可以修改待支付订单，并记录变更摘要")
    void updateModifiesPendingOwnOrder() {
        loginAsApp(2L, "张三");
        BizOrder order = order(10L, 2L, OrderStatus.PENDING);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.updateById(any(BizOrder.class))).thenReturn(1);
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setAmount(new BigDecimal("20.00"));
        request.setRemark("改备注");

        OrderResponse response = service.update(10L, request);

        assertEquals(0, new BigDecimal("20.00").compareTo(response.getAmount()));
        assertEquals("改备注", response.getRemark());
        BizOrderOperationLog log = captureSingleLog();
        assertEquals(OrderOperation.UPDATE, log.getOperation());
        assertEquals(0, log.getBeforeStatus());
        assertEquals(0, log.getAfterStatus());
        assertTrue(log.getChangeSummary().contains("amount: 10.00 -> 20.00"), log.getChangeSummary());
        assertTrue(log.getChangeSummary().contains("remark: 旧备注 -> 改备注"), log.getChangeSummary());
    }

    @Test
    @DisplayName("修改请求没有任何字段时直接拒绝")
    void updateRejectsEmptyRequest() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(10L, new UpdateOrderRequest()));

        assertEquals("至少提供一个需要修改的字段", exception.getMessage());
        verifyNoInteractions(orderMapper, operationLogMapper);
    }

    @Test
    @DisplayName("只有待支付订单可以修改")
    void updateRejectsNonPendingOrder() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PAID));
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setRemark("改备注");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.update(10L, request));

        assertEquals("只有待支付订单可以修改", exception.getMessage());
        verify(orderMapper, never()).updateById(any(BizOrder.class));
    }

    @Test
    @DisplayName("App 用户不能修改他人订单")
    void updateRejectsNonOwner() {
        loginAsApp(3L, "李四");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setRemark("改备注");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> service.update(10L, request));

        assertEquals("只能访问自己的订单", exception.getMessage());
        verify(orderMapper, never()).updateById(any(BizOrder.class));
    }

    @Test
    @DisplayName("乐观锁冲突时提示刷新重试")
    void updateRejectsStaleVersion() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        when(orderMapper.updateById(any(BizOrder.class))).thenReturn(0);
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setRemark("改备注");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.update(10L, request));

        assertEquals("订单已被他人修改，请刷新后重试", exception.getMessage());
        verifyNoInteractions(operationLogMapper);
    }

    @Test
    @DisplayName("支付：待支付 -> 已支付，并记录日志")
    void payMovesPendingToPaid() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        when(orderMapper.updateById(any(BizOrder.class))).thenReturn(1);

        OrderResponse response = service.pay(10L);

        assertEquals(1, response.getStatus());
        BizOrderOperationLog log = captureSingleLog();
        assertEquals(OrderOperation.PAY, log.getOperation());
        assertEquals(0, log.getBeforeStatus());
        assertEquals(1, log.getAfterStatus());
    }

    @Test
    @DisplayName("取消：待支付 -> 已取消，并记录日志")
    void cancelMovesPendingToCancelled() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        when(orderMapper.updateById(any(BizOrder.class))).thenReturn(1);

        OrderResponse response = service.cancel(10L);

        assertEquals(2, response.getStatus());
        BizOrderOperationLog log = captureSingleLog();
        assertEquals(OrderOperation.CANCEL, log.getOperation());
        assertEquals(2, log.getAfterStatus());
    }

    @Test
    @DisplayName("非法状态流转被拒绝")
    void transitionRejectsInvalidStatus() {
        loginAsPc("管理员");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PAID));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.pay(10L));

        assertTrue(exception.getMessage().contains("订单状态不允许从「已支付」变更为「已支付」"),
                exception.getMessage());
        verify(orderMapper, never()).updateById(any(BizOrder.class));
    }

    @Test
    @DisplayName("App 用户不能完成订单")
    void appUserCannotCompleteOrder() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PAID));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> service.complete(10L));

        assertEquals("App 用户不能完成订单", exception.getMessage());
        verify(orderMapper, never()).updateById(any(BizOrder.class));
    }

    @Test
    @DisplayName("后台用户可以完成已支付订单")
    void pcUserCanCompletePaidOrder() {
        loginAsPc("管理员");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PAID));
        when(orderMapper.updateById(any(BizOrder.class))).thenReturn(1);

        OrderResponse response = service.complete(10L);

        assertEquals(3, response.getStatus());
        BizOrderOperationLog log = captureSingleLog();
        assertEquals(OrderOperation.COMPLETE, log.getOperation());
        assertEquals(OperatorType.PC.getCode(), log.getOperatorType());
        assertEquals(1, log.getBeforeStatus());
        assertEquals(3, log.getAfterStatus());
    }

    @Test
    @DisplayName("App 用户不能删除订单")
    void appUserCannotDeleteOrder() {
        loginAsApp(2L, "张三");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> service.delete(10L));

        assertEquals("只有后台用户可以删除订单", exception.getMessage());
        verifyNoInteractions(orderMapper, operationLogMapper);
    }

    @Test
    @DisplayName("后台用户可以删除订单，并保留删除日志")
    void pcUserCanDeleteOrder() {
        loginAsPc("管理员");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.COMPLETED));
        when(orderMapper.deleteById(10L)).thenReturn(1);

        service.delete(10L);

        verify(orderMapper).deleteById(10L);
        BizOrderOperationLog log = captureSingleLog();
        assertEquals(OrderOperation.DELETE, log.getOperation());
        assertEquals(3, log.getBeforeStatus());
        assertEquals(3, log.getAfterStatus());
    }

    @Test
    @DisplayName("删除乐观锁冲突时提示刷新重试")
    void deleteRejectsStaleVersion() {
        loginAsPc("管理员");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        when(orderMapper.deleteById(anyLong())).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.delete(10L));

        assertEquals("订单已被他人删除，请刷新后重试", exception.getMessage());
        verifyNoInteractions(operationLogMapper);
    }

    @Test
    @DisplayName("本人可以查看订单操作日志")
    void logsReturnHistoryForOwner() {
        loginAsApp(2L, "张三");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));
        BizOrderOperationLog log = new BizOrderOperationLog();
        log.setId(1L);
        log.setOrderId(10L);
        when(operationLogMapper.selectList(any())).thenReturn(List.of(log));

        List<BizOrderOperationLog> logs = service.logs(10L);

        assertEquals(1, logs.size());
        assertEquals(10L, logs.get(0).getOrderId());
    }

    @Test
    @DisplayName("App 用户不能查看他人订单日志")
    void logsRejectOtherAppUser() {
        loginAsApp(3L, "李四");
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 2L, OrderStatus.PENDING));

        assertThrows(AccessDeniedException.class, () -> service.logs(10L));

        verifyNoInteractions(operationLogMapper);
    }

    private BizOrderOperationLog captureSingleLog() {
        ArgumentCaptor<BizOrderOperationLog> captor =
                ArgumentCaptor.forClass(BizOrderOperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static BizOrder order(Long id, Long userId, OrderStatus status) {
        BizOrder order = new BizOrder();
        order.setId(id);
        order.setOrderNo("NO" + id);
        order.setUserId(userId);
        order.setUserName("下单人" + userId);
        order.setAmount(new BigDecimal("10.00"));
        order.setStatus(status.getCode());
        order.setRemark("旧备注");
        order.setVersion(0);
        return order;
    }

    private static CreateOrderRequest request(String amount) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private static void loginAsApp(Long id, String name) {
        UserContextHolder.set(new UserContext(id, Set.of("app"), Set.of(),
                OperatorType.APP, name));
    }

    private static void loginAsPc(String name) {
        UserContextHolder.set(new UserContext(1L, Set.of("admin"), Set.of(),
                OperatorType.PC, name));
    }
}
