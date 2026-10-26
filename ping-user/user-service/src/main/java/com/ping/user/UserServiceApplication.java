package com.ping.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * UserServiceApplication - 用户服务启动类。
 *
 * @author humdeef
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan({"com.ping.user.pc.mapper", "com.ping.user.app.mapper"})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
