package com.booooo.BIbackend.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class RedisLimiterManagerTest {

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Test
    void doRateLimiter() throws InterruptedException {
        String userId = "1";
        // 瞬间执行2次,每成功一次,就打印'成功'
        for (int i = 0; i < 2; i++) {
            redisLimiterManager.doRateLimiter(userId);
            System.out.println("成功");

        }
        Thread.sleep(1000);
        for (int i = 0; i < 5; i++) {
            redisLimiterManager.doRateLimiter(userId);
            System.out.println("成功");

        }

    }
}