package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.spi.Spi;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/31 20:01
 */
@Slf4j
@Spi("retrySame")
public class RetrySameRetryPolicy implements RetryPolicy{

    private int retryMax = 3;
    private final Random random = new Random();

    @Override
    public Response retry(RetryContext context) throws Exception {
        long start = System.currentTimeMillis();
        int retryCount = 0;
        while (retryCount < retryMax) {
            long nextDelay = nextDelay(retryCount);
            if (nextDelay >= 1000) {
                nextDelay = 1000;
            }
            long methodTimeoutMs = context.getRequestTimeoutMs() - (System.currentTimeMillis() - start);
            if(methodTimeoutMs <= 0 || nextDelay >= methodTimeoutMs) {
                throw new TimeoutException();
            }
            Thread.sleep(nextDelay);
            try {
                CompletableFuture<Response> future = context.doRpc(context.getFailService());
                return future.get(Math.min(context.getRequestTimeoutMs(),methodTimeoutMs), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("重试发生错误 ",e);
            }
            retryCount++;
        }
        throw new RpcException("重试失败");
    }

    private long nextDelay(int retryCount) {
        return 100L * (1L << retryCount) + random.nextInt(0,50);
    }
}
