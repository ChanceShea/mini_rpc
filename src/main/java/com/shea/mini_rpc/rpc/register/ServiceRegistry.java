package com.shea.mini_rpc.rpc.register;

import java.util.List;

/**
 * @author : Shea.
 * @description : TODO
 * @since : 2026/3/25 19:45
 */
public interface ServiceRegistry {

   void init(RegistryConfig config) throws Exception;

   void registerService(ServiceMetadata metadata);

   List<ServiceMetadata> fetchServiceList(String serviceName) throws Exception;
}
