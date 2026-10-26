package com.ping.data.scope.mybatis;

import com.ping.core.context.OperatorType;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import com.ping.data.scope.annotation.DataScope;
import com.ping.data.scope.annotation.IgnoreDataScope;
import com.ping.data.scope.config.DataScopeProperties;
import com.ping.data.scope.core.DataScopeContext;
import com.ping.data.scope.core.DataScopeException;
import com.ping.data.scope.core.DataScopeInfo;
import com.ping.data.scope.core.DataScopeProvider;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据权限 SQL 改写测试：验证部门范围、仅本人、归属人类型和失败关闭。
 */
class DataScopeHandlerTest {

    private DataScopeProperties properties;
    private ObjectProvider<DataScopeProvider> providerProvider;
    private DataScopeHandler handler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        properties = new DataScopeProperties();
        providerProvider = mock(ObjectProvider.class);
        handler = new DataScopeHandler(properties, providerProvider);
        loginAsPc(9L);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        UserContextHolder.set(null);
    }

    @Test
    @DisplayName("部门范围 + 本人数据：生成 dept IN (...) OR owner = 当前用户")
    void buildsDeptAndSelfCondition() throws Exception {
        DataScopeContext.set(new DataScopeInfo(false, new LinkedHashSet<>(List.of(4L, 5L)), false, 9L));

        Expression expression = handler.getSqlSegment(
                new Table("sys_user"), null, statement(SysUserMapper.class, "selectById"));

        assertEquals(parse("(sys_user.dept_id IN (4,5) OR sys_user.id = 9)"), expression.toString());
    }

    @Test
    @DisplayName("存在表别名时使用别名限定列")
    void usesTableAlias() throws Exception {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L), false, 9L));
        Table table = new Table("sys_user");
        table.setAlias(new Alias("u"));

        Expression expression = handler.getSqlSegment(
                table, null, statement(SysUserMapper.class, "selectById"));

        assertEquals(parse("(u.dept_id IN (4) OR u.id = 9)"), expression.toString());
    }

    @Test
    @DisplayName("仅本人：只追加 owner = 当前用户")
    void buildsSelfOnlyCondition() throws Exception {
        DataScopeContext.set(DataScopeInfo.selfOnly(9L));

        Expression expression = handler.getSqlSegment(
                new Table("sys_user"), null, statement(SysUserMapper.class, "selectById"));

        assertEquals(parse("sys_user.id = 9"), expression.toString());
    }

    @Test
    @DisplayName("全部数据范围不追加条件")
    void allScopeSkipsCondition() {
        DataScopeContext.set(DataScopeInfo.all(9L));

        assertNull(handler.getSqlSegment(
                new Table("sys_user"), null, statement(SysUserMapper.class, "selectById")));
    }

    @Test
    @DisplayName("归属人类型列用于区分 PC 与 App 用户")
    void appendsOwnerTypeCondition() throws Exception {
        DataScopeContext.set(DataScopeInfo.selfOnly(9L));

        Expression expression = handler.getSqlSegment(
                new Table("biz_order"), null, statement(TypedMapper.class, "selectById"));

        assertEquals(parse("(biz_order.user_id = 9 AND biz_order.update_by_type = 2)"),
                expression.toString());
    }

    @Test
    @DisplayName("无部门列时只按归属人过滤")
    void ownerOnlyTableIgnoresDeptScope() throws Exception {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L, 5L), false, 9L));

        Expression expression = handler.getSqlSegment(
                new Table("biz_order"), null, statement(OwnerOnlyMapper.class, "selectById"));

        assertEquals(parse("biz_order.create_by_id = 9"), expression.toString());
    }

    @Test
    @DisplayName("includeSelf=false 时部门范围不叠加本人条件")
    void includeSelfFalseKeepsDeptConditionOnly() throws Exception {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L), false, 9L));

        Expression expression = handler.getSqlSegment(
                new Table("sys_user"), null,
                statement(DeptOnlyMapper.class, "selectById"));

        assertEquals(parse("sys_user.dept_id IN (4)"), expression.toString());
    }

    @Test
    @DisplayName("无部门范围且 includeSelf=false 时生成永假条件")
    void noScopeAndIncludeSelfFalseDeniesAll() throws Exception {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(), true, 9L));

        Expression expression = handler.getSqlSegment(
                new Table("sys_user"), null,
                statement(DeptOnlyMapper.class, "selectById"));

        assertEquals(parse("1 = 0"), expression.toString());
    }

    @Test
    @DisplayName("表名不匹配时不改写 SQL")
    void differentTableIsIgnored() {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L), false, 9L));

        assertNull(handler.getSqlSegment(
                new Table("ping_order"), null, statement(SysUserMapper.class, "selectById")));
    }

    @Test
    @DisplayName("关闭开关后不改写 SQL")
    void disabledScopeSkipsCondition() {
        properties.setEnabled(false);
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L), false, 9L));

        assertNull(handler.getSqlSegment(
                new Table("sys_user"), null, statement(SysUserMapper.class, "selectById")));
    }

    @Test
    @DisplayName("@IgnoreDataScope 优先级最高")
    void ignoreAnnotationSkipsCondition() {
        DataScopeContext.set(new DataScopeInfo(false, Set.of(4L), false, 9L));

        assertNull(handler.getSqlSegment(
                new Table("sys_user"), null, statement(IgnoredMapper.class, "selectById")));
    }

    @Test
    @DisplayName("方法级 @DataScope 覆盖接口级配置")
    void methodAnnotationOverridesInterface() throws Exception {
        DataScopeContext.set(DataScopeInfo.selfOnly(9L));

        Expression expression = handler.getSqlSegment(
                new Table("biz_order"), null, statement(MethodScopedMapper.class, "selectScoped"));

        assertEquals(parse("biz_order.user_id = 9"), expression.toString());
    }

    @Test
    @DisplayName("匿名请求保持原 SQL")
    void anonymousRequestSkipsCondition() {
        UserContextHolder.set(null);

        assertNull(handler.getSqlSegment(
                new Table("sys_user"), null, statement(SysUserMapper.class, "selectById")));
    }

    @Test
    @DisplayName("已登录但没有 DataScopeProvider 时失败关闭")
    void loggedInWithoutProviderFailsClosed() {
        when(providerProvider.getIfAvailable()).thenReturn(null);

        DataScopeException exception = assertThrows(DataScopeException.class,
                () -> handler.getSqlSegment(new Table("sys_user"), null,
                        statement(SysUserMapper.class, "selectById")));
        assertEquals("存在 @DataScope 查询，但容器中没有 DataScopeProvider 实现", exception.getMessage());
    }

    @Test
    @DisplayName("Provider 返回 null 时失败关闭")
    void providerReturningNullFailsClosed() {
        DataScopeProvider provider = mock(DataScopeProvider.class);
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.resolve()).thenReturn(null);

        assertThrows(DataScopeException.class,
                () -> handler.getSqlSegment(new Table("sys_user"), null,
                        statement(SysUserMapper.class, "selectById")));
    }

    @Test
    @DisplayName("同一请求只解析一次数据范围")
    void scopeIsResolvedOncePerRequest() throws Exception {
        DataScopeProvider provider = mock(DataScopeProvider.class);
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.resolve()).thenReturn(DataScopeInfo.selfOnly(9L));

        String statementId = statement(SysUserMapper.class, "selectById");
        handler.getSqlSegment(new Table("sys_user"), null, statementId);
        Expression second = handler.getSqlSegment(new Table("sys_user"), null, statementId);

        assertEquals(parse("sys_user.id = 9"), second.toString());
        verify(provider, times(1)).resolve();
    }

    private void loginAsPc(Long userId) {
        UserContextHolder.set(new UserContext(userId, Set.of(), Set.of(),
                OperatorType.PC, "tester"));
    }

    private static String statement(Class<?> mapper, String method) {
        return mapper.getName() + "." + method;
    }

    private static String parse(String expression) throws JSQLParserException {
        return CCJSqlParserUtil.parseCondExpression(expression).toString();
    }

    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    interface SysUserMapper {
        void selectById();
    }

    @DataScope(table = "biz_order", deptColumn = "", ownerColumn = "user_id",
            ownerTypeColumn = "update_by_type", ownerTypeValue = 2)
    interface TypedMapper {
        void selectById();
    }

    @DataScope(table = "biz_order", deptColumn = "", ownerColumn = "create_by_id")
    interface OwnerOnlyMapper {
        void selectById();
    }

    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id",
            includeSelf = false)
    interface DeptOnlyMapper {
        void selectById();
    }

    @IgnoreDataScope
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    interface IgnoredMapper {
        void selectById();
    }

    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    interface MethodScopedMapper {
        @DataScope(table = "biz_order", deptColumn = "", ownerColumn = "user_id")
        void selectScoped();

        void selectUnscoped();
    }
}
