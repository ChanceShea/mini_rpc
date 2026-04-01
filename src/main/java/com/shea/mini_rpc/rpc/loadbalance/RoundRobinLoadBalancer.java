package com.shea.mini_rpc.rpc.loadbalance;

import com.shea.mini_rpc.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/26 19:53
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    final AtomicInteger index = new AtomicInteger(0);

    @Override
    public ServiceMetadata select(List<ServiceMetadata> services) {
        int i = index.getAndIncrement() % services.size();
        return services.get(i);
    }
}
