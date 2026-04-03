package com.shea.mini_rpc.rpc.loadbalance;

import com.shea.mini_rpc.rpc.register.ServiceMetadata;

import java.util.List;

/**
 * 负载均衡器接口
 * <p>
 * 定义了服务选择策略，用于在多个服务提供者中选择一个进行处理
 * 支持多种负载均衡算法（如随机、轮询等）
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/26 19:51
 */
public interface LoadBalancer {

    /**
     * 从服务列表中选择一个服务提供者
     * <p>
     * 根据具体的负载均衡策略（随机、轮询等）选择一个可用的服务
     * </p>
     * @param services 可用的服务元数据列表
     * @return 被选中的服务元数据
     */
    ServiceMetadata select(List<ServiceMetadata> services);
}
