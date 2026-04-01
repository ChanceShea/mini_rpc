package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.loadbalance.LoadBalancer;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/31 20:00
 */
@Data
public class RetryContext {

    private ServiceMetadata failService;
    private List<ServiceMetadata> serviceMetadataList;
    private long methodTimeoutMs;
    private long requestTimeoutMs;
    private Function<ServiceMetadata,CompletableFuture<Response>> doRpcFunction;
    private LoadBalancer loadBalancer;

    public CompletableFuture<Response> doRpc(ServiceMetadata serviceMetadata) {
        return doRpcFunction.apply(serviceMetadata);
    }
}
