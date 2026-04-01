package com.shea.mini_rpc.rpc.loadbalance;

import com.shea.mini_rpc.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.Random;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/26 19:54
 */
public class RandomLoadBalancer implements LoadBalancer {

    private Random random = new Random();

    @Override
    public ServiceMetadata select(List<ServiceMetadata> services) {
        int i = random.nextInt(0, services.size());
        return services.get(i);
    }
}
