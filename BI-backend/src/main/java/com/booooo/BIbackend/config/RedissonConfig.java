package com.booooo.BIbackend.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;




@Configuration
// 从application.yml文件中读取前缀为"spring.redis"的配置项
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {

    private Integer database;

    private String host;

    private Integer port;

    @Bean
    public RedissonClient getRedissonClient(){
// 1.创建配置对象
        Config config = new Config();
        // 添加单机Redisson配置
        config.useSingleServer()
                // 设置数据库
                .setDatabase(database)
                // 设置redis的地址
                .setAddress("redis://" + host + ":" + port);
        // 设置redis的密码(redis有密码才设置)
        //                .setPassword(password);

        // 2.创建Redisson实例
        return Redisson.create(config);

    }
}
