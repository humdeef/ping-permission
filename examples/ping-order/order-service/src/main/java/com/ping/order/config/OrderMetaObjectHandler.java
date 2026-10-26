package com.ping.order.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ping.core.context.OperatorType;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * OrderMetaObjectHandler - 订单审计字段自动填充。
 *
 * <p>订单只由 App 用户创建，所以没有 {@code create_by_*}；但后台管理员可以
 * 修改/完成/删除订单，因此保留 {@code update_by_type / update_by_id / update_by_name}，
 * 用类型位区分 App 用户和后台用户，避免两套用户 ID 数值相同时串号。</p>
 *
 * <p>名称优先取 token 会话中的快照；旧 token 没有名称时退化为 {@code user#id}，
 * 业务层可以在插入前用 Dubbo 查询到的昵称覆盖它。</p>
 *
 * @author humdeef
 */
@Component
public class OrderMetaObjectHandler implements MetaObjectHandler {

    private static final String SYSTEM_NAME = "system";
    private static final String ANONYMOUS_NAME = "anonymous";

    @Override
    public void insertFill(MetaObject metaObject) {
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();

        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateByType", Integer.class, operator.type().getCode());
        strictInsertFill(metaObject, "updateById", Long.class, operator.id());
        strictInsertFill(metaObject, "updateByName", String.class, operator.name());
        strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Operator operator = currentOperator();
        // 实体通常是从数据库查出来的，update* 字段已有旧值；strictUpdateFill 不会覆盖旧值，必须强制写。
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        setFieldValByName("updateByType", operator.type().getCode(), metaObject);
        setFieldValByName("updateById", operator.id(), metaObject);
        setFieldValByName("updateByName", operator.name(), metaObject);
    }

    private Operator currentOperator() {
        UserContext context = UserContextHolder.get();
        if (context == null) {
            return new Operator(OperatorType.ANONYMOUS, 0L, ANONYMOUS_NAME);
        }
        OperatorType type = context.getOperatorType();
        Long id = context.getUserId() == null ? 0L : context.getUserId();
        String name = context.getUserName();
        if (name == null || name.isBlank()) {
            if (type == OperatorType.SYSTEM) {
                name = SYSTEM_NAME;
            } else if (type == OperatorType.ANONYMOUS) {
                name = ANONYMOUS_NAME;
            } else {
                name = "user#" + id;
            }
        }
        return new Operator(type, id, name);
    }

    private record Operator(OperatorType type, Long id, String name) {
    }
}
