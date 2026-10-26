package com.ping.order.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单元测试用的 MyBatis-Plus 元数据初始化。
 *
 * <p>Lambda 条件构造器和 {@code strictInsertFill} 都依赖 {@code TableInfo} 缓存，
 * 正常运行时由 MyBatis 注册 Mapper 时写入；纯单元测试没有容器，需要手动初始化。</p>
 */
public final class TableInfoTestSupport {

    private static final Set<Class<?>> INITIALIZED = ConcurrentHashMap.newKeySet();

    private TableInfoTestSupport() {
    }

    public static void init(Class<?>... entities) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "unit-test");
        for (Class<?> entity : entities) {
            if (INITIALIZED.add(entity)) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
