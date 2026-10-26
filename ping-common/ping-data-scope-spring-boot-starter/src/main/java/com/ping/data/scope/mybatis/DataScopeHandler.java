package com.ping.data.scope.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.ping.core.context.UserContextHolder;
import com.ping.data.scope.annotation.DataScope;
import com.ping.data.scope.annotation.IgnoreDataScope;
import com.ping.data.scope.config.DataScopeProperties;
import com.ping.data.scope.core.DataScopeContext;
import com.ping.data.scope.core.DataScopeException;
import com.ping.data.scope.core.DataScopeInfo;
import com.ping.data.scope.core.DataScopeProvider;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 数据权限处理器。
 *
 * <p>根据 MappedStatement 对应的 Mapper 方法/接口上的 {@link DataScope}
 * 判断是否改写 SQL，并把当前用户的数据范围拼成：</p>
 *
 * <pre>(alias.dept_id IN (...) OR alias.owner_column = 当前用户)</pre>
 *
 * <p>失败关闭：已登录却拿不到数据范围、SQL 解析失败，都会抛
 * {@link DataScopeException}，不会退化成全量查询。</p>
 *
 * <p>MyBatis-Plus 的 {@code DataPermissionInterceptor} 会对标注了规则的
 * SELECT 调用 {@code beforeQuery}，对 UPDATE / DELETE 调用 {@code beforePrepare}，
 * 因此该处理器返回的条件同样会追加到修改、删除语句上；INSERT 不处理。</p>
 *
 * @author humdeef
 */
public class DataScopeHandler implements MultiDataPermissionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataScopeHandler.class);

    private final DataScopeProperties properties;
    private final ObjectProvider<DataScopeProvider> providerProvider;
    private final Map<String, Rule> ruleCache = new ConcurrentHashMap<>();

    public DataScopeHandler(DataScopeProperties properties,
                            ObjectProvider<DataScopeProvider> providerProvider) {
        this.properties = properties;
        this.providerProvider = providerProvider;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!properties.isEnabled()) {
            return null;
        }
        Rule rule = ruleCache.computeIfAbsent(mappedStatementId, this::loadRule);
        if (rule == Rule.NONE || rule == Rule.IGNORE || !rule.matches(table)) {
            return null;
        }
        // 解析数据范围自身的查询不能再次套数据权限
        if (DataScopeContext.isResolving()) {
            return null;
        }

        DataScopeInfo scope = DataScopeContext.get();
        if (scope == null) {
            // 匿名/内部任务没有数据范围，保持原有查询；登录用户必须能解析出范围
            if (!UserContextHolder.isLogin()) {
                return null;
            }
            DataScopeProvider provider = providerProvider.getIfAvailable();
            if (provider == null) {
                throw new DataScopeException("存在 @DataScope 查询，但容器中没有 DataScopeProvider 实现");
            }
            scope = DataScopeContext.resolve(provider);
            if (scope == null) {
                throw new DataScopeException("无法解析当前用户的数据范围，拒绝执行查询: " + mappedStatementId);
            }
        }
        if (scope.all()) {
            return null;
        }
        if (scope.userId() == null) {
            throw new DataScopeException("数据范围缺少当前用户 ID，拒绝执行查询: " + mappedStatementId);
        }

        String condition = buildCondition(rule, table, scope);
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            throw new DataScopeException("数据权限条件解析失败: " + condition, e);
        }
    }

    private String buildCondition(Rule rule, Table table, DataScopeInfo scope) {
        String qualifier = table.getAlias() == null
                ? normalize(table.getName())
                : normalize(table.getAlias().getName());
        if (!rule.alias.isBlank()) {
            qualifier = rule.alias;
        }

        String self = qualifier + "." + rule.ownerColumn + " = " + scope.userId();
        if (!rule.ownerTypeColumn.isBlank() && rule.ownerTypeValue >= 0) {
            self = "(" + self + " AND " + qualifier + "." + rule.ownerTypeColumn
                    + " = " + rule.ownerTypeValue + ")";
        }

        boolean hasDeptScope = !rule.deptColumn.isBlank()
                && !scope.selfOnly()
                && !scope.deptIds().isEmpty();
        if (!hasDeptScope) {
            return rule.includeSelf ? self : "1 = 0";
        }

        String deptIds = scope.deptIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String dept = qualifier + "." + rule.deptColumn + " IN (" + deptIds + ")";
        if (!rule.includeSelf) {
            return dept;
        }
        return "(" + dept + " OR " + self + ")";
    }

    private Rule loadRule(String mappedStatementId) {
        int split = mappedStatementId.lastIndexOf('.');
        if (split <= 0 || split == mappedStatementId.length() - 1) {
            return Rule.NONE;
        }
        String mapperName = mappedStatementId.substring(0, split);
        String methodName = mappedStatementId.substring(split + 1);
        try {
            Class<?> mapperType = Class.forName(mapperName);
            if (mapperType.isAnnotationPresent(IgnoreDataScope.class)) {
                return Rule.IGNORE;
            }
            for (Method method : mapperType.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (method.isAnnotationPresent(IgnoreDataScope.class)) {
                    return Rule.IGNORE;
                }
                DataScope methodScope = method.getAnnotation(DataScope.class);
                if (methodScope != null) {
                    return Rule.of(methodScope);
                }
            }
            DataScope typeScope = mapperType.getAnnotation(DataScope.class);
            return typeScope == null ? Rule.NONE : Rule.of(typeScope);
        } catch (ClassNotFoundException e) {
            // XML 语句或非 Mapper 接口：没有注解就保持原样
            log.debug("无法加载 Mapper 接口 {}，跳过数据权限", mapperName);
            return Rule.NONE;
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replace("`", "");
    }

    private record Rule(String table, String alias, String deptColumn, String ownerColumn,
                        String ownerTypeColumn, int ownerTypeValue, boolean includeSelf) {

        private static final Rule NONE = new Rule("", "", "", "", "", -1, true);
        private static final Rule IGNORE = new Rule("", "", "", "", "", -1, true);

        private static Rule of(DataScope scope) {
            if (scope.table() == null || scope.table().isBlank()) {
                log.warn("@DataScope 未指定 table，已跳过: {}", scope);
                return NONE;
            }
            return new Rule(scope.table(), scope.alias(), scope.deptColumn(),
                    scope.ownerColumn(), scope.ownerTypeColumn(),
                    scope.ownerTypeValue(), scope.includeSelf());
        }

        private boolean matches(Table table) {
            String tableName = normalize(table.getName());
            if (!this.table.equalsIgnoreCase(tableName)) {
                return false;
            }
            if (alias.isBlank()) {
                return true;
            }
            return table.getAlias() != null
                    && alias.equalsIgnoreCase(normalize(table.getAlias().getName()));
        }
    }
}
