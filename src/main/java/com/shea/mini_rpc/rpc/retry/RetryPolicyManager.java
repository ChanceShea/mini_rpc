package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.spi.Spi;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author : Shea.
 * @since : 2026/4/6 14:53
 */
@Slf4j
public class RetryPolicyManager {

    private final Map<String,RetryPolicy> nameMap = new HashMap<>();

    public RetryPolicyManager() {
        init();
    }

    public RetryPolicy getRetryPolicy(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<RetryPolicy> load = ServiceLoader.load(RetryPolicy.class);
        for (RetryPolicy retry : load) {
            Class<? extends RetryPolicy> retryClass = retry.getClass();
            Spi spi = retryClass.getAnnotation(Spi.class);
            if (spi == null) {
                log.warn("{} 没有spi注解，无法被管理", retryClass.getName());
                continue;
            }
            if (nameMap.put(spi.value().toUpperCase(Locale.ROOT), retry) != null) {
                throw new IllegalArgumentException("重试策略的name不能重复 " + spi.value());
            }
        }
    }
}
