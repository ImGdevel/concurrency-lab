package com.lab.couponfcfs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> issueCouponScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/issue_coupon.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
