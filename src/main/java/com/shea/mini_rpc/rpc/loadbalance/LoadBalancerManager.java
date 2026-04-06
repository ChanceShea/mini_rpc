package com.shea.mini_rpc.rpc.loadbalance;

import com.shea.mini_rpc.rpc.spi.Spi;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author : Shea.
 * @since : 2026/4/6 15:07
 */
@Slf4j
public class LoadBalancerManager {

    private final Map<String, LoadBalancer> nameMap = new HashMap<>();

    public LoadBalancerManager() {
        init();
    }

    public LoadBalancer getLoadBalancer(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        for (LoadBalancer loadBalancer : ServiceLoader.load(LoadBalancer.class)) {
            Class<? extends LoadBalancer> loadBalancerClass = loadBalancer.getClass();
            Spi annotation = loadBalancerClass.getAnnotation(Spi.class);
            if (annotation == null) {
                log.warn("{} 没有spi注解，无法被管理", loadBalancerClass.getName());
                continue;
            }
            if (nameMap.put(annotation.value().toUpperCase(Locale.ROOT), loadBalancer) != null) {
                throw new IllegalArgumentException("负载均衡器name不能重复 " + annotation.value());
            }
        }
    }
}
