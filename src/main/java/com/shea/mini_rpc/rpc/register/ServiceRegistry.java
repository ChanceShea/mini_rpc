package com.shea.mini_rpc.rpc.register;

import java.util.List;

/**
 * 服务注册中心接口
 * <p>
 * 定义了服务注册与发现的基本操作，支持将服务提供者信息注册到注册中心
 * 以及 Consumer 从注册中心获取服务列表
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/25 19:45
 */
public interface ServiceRegistry {

    /**
     * 初始化注册中心配置
     * @param config 注册中心配置信息，包含连接地址、类型等
     * @throws Exception 初始化异常
     */
   void init(RegistryConfig config) throws Exception;

    /**
     * 注册服务到注册中心
     * <p>
     * 将服务提供者的元数据信息（如主机、端口、服务名）注册到注册中心
     * </p>
     * @param metadata 服务元数据信息
     */
   void registerService(ServiceMetadata metadata);

    /**
     * 从注册中心获取服务列表
     * <p>
     * Consumer 通过该方法发现可用的服务提供者
     * </p>
     * @param serviceName 服务名称
     * @return 服务元数据列表
     * @throws Exception 查询异常
     */
   List<ServiceMetadata> fetchServiceList(String serviceName) throws Exception;
}
