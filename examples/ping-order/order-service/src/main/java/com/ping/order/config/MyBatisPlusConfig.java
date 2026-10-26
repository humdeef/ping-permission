package com.ping.order.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

import static com.baomidou.mybatisplus.annotation.IdType.AUTO;

/**
 * MyBatis-Plus 手动配置（与 user-service 保持一致，兼容 Spring Boot 4 自动配置顺序）。
 *
 * @author humdeef
 */
@Configuration
@MapperScan("com.ping.order.mapper")
public class MyBatisPlusConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrderMetaObjectHandler orderMetaObjectHandler;

    @Value("${mybatis-plus.mapper-locations:classpath*:mapper/*.xml}")
    private String mapperLocations;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // @Version 乐观锁：并发修改时 update ... where version = ?，影响 0 行即冲突
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
        factory.setConfiguration(mybatisConfiguration());
        factory.setPlugins(mybatisPlusInterceptor());
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(AUTO);
        globalConfig.setDbConfig(dbConfig);
        // 手工构建 SqlSessionFactory 时必须显式挂载填充器
        globalConfig.setMetaObjectHandler(orderMetaObjectHandler);
        factory.setGlobalConfig(globalConfig);
        return factory.getObject();
    }

    private MybatisConfiguration mybatisConfiguration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        return configuration;
    }
}
