package com.shea.mini_rpc.rpc.loadbalance;

import com.shea.mini_rpc.rpc.register.ServiceMetadata;

import java.util.List;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/26 19:51
 */
public interface LoadBalancer {

    ServiceMetadata select(List<ServiceMetadata> services);
}
