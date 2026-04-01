package com.shea.mini_rpc.rpc.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.Collection;
import java.util.List;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/25 19:59
 */
@Slf4j
public class ZkServiceRegistry implements ServiceRegistry {

    private static final String BASE_PATH = "/Shea";
    private CuratorFramework client;
    private ServiceDiscovery<ServiceMetadata> discovery;


    @Override
    public void init(RegistryConfig config) throws Exception {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(config.getConnectString())
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();

        this.discovery = ServiceDiscoveryBuilder.builder(ServiceMetadata.class)
                .client(client)
                .basePath(BASE_PATH)
                .serializer(new JsonInstanceSerializer<>(ServiceMetadata.class))
                .build();
        discovery.start();
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        try {
            ServiceInstance<ServiceMetadata> instance = ServiceInstance.<ServiceMetadata>builder()
                    .address(metadata.getHost())
                    .port(metadata.getPort())
                    .name(metadata.getServiceName())
                    .payload(metadata)
                    .build();
            this.discovery.registerService(instance);
        }catch (Exception e) {
            log.error("{}注册失败",metadata,e);
            throw new RuntimeException(metadata + "注册失败");
        }
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String serviceName) throws Exception {
        Collection<ServiceInstance<ServiceMetadata>> collection = discovery.queryForInstances(serviceName);
        return collection.stream().map(ServiceInstance::getPayload).toList();
    }
}
