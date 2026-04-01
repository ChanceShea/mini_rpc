package com.shea.mini_rpc.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/25 20:16
 */
@Slf4j
public class RedisRegistry implements ServiceRegistry {
    @Override
    public void init(RegistryConfig config) throws Exception {
        log.info("Redis注册中心未实现");
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        throw new RuntimeException();
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String serviceName) {
        throw new RuntimeException();
    }
}
