package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.spi.Spi;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author : Shea.
 * @since : 2026/3/31 21:39
 */
@Slf4j
@Spi("forking")
public class ForkingRetryPolicy implements RetryPolicy{
    @Override
    public Response retry(RetryContext context) throws Exception {
        List<ServiceMetadata> metadataArrayList = new ArrayList<>(context.getServiceMetadataList());
        metadataArrayList.remove(context.getFailService());
        log.info("重试开始，失败的 provider: {}, 可用的 provider: {}",
                context.getFailService().getPort(),
                metadataArrayList.stream().map(ServiceMetadata::getPort).toList());
        if (metadataArrayList.isEmpty()) {
            throw new RpcException("没有可重试的provider");
        }
        CompletableFuture[] allFuture = new CompletableFuture[metadataArrayList.size()];
        for (int i = 0; i < metadataArrayList.size(); i++) {
            allFuture[i] = context.doRpc(metadataArrayList.get(i));
        }
        CompletableFuture<Object> mainFuture = CompletableFuture.anyOf(allFuture);
        return (Response) mainFuture.get(Math.min(context.getRequestTimeoutMs(), context.getMethodTimeoutMs()), TimeUnit.MILLISECONDS);
    }
}
