package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.exception.LimitException;
import com.shea.mini_rpc.rpc.limit.ConcurrencyLimiter;
import com.shea.mini_rpc.rpc.limit.Limiter;
import com.shea.mini_rpc.rpc.limit.RateLimiter;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : Shea.
 * @since : 2026/4/1 18:45
 */
@Slf4j
public class InFlightRequestManager {

    private final Map<Integer, CompletableFuture<Response>> inflightRequestTable;
    private final Map<ServiceMetadata,Limiter> channelLimiterMap;
    private final HashedWheelTimer timer;
    private final Limiter globalLimiter;
    private final ConsumerProperties properties;

    public InFlightRequestManager(ConsumerProperties properties) {
        this.properties = properties;
        this.globalLimiter = new ConcurrencyLimiter(properties.getRpcPerSecond());
        this.inflightRequestTable = new ConcurrentHashMap<>();
        this.timer = new HashedWheelTimer(100,TimeUnit.MILLISECONDS,256);
        this.channelLimiterMap = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Response> inFlightRequest(Request request, long timeoutMs, ServiceMetadata metadata) {
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();
        if (!globalLimiter.tryAcquire()) {
            responseFuture.completeExceptionally(new LimitException("全局限流，当前在途请求超过阈值"));
            return responseFuture;
        }
        Limiter channelLimiter = channelLimiterMap.computeIfAbsent(metadata, k -> new RateLimiter(properties.getRpcPerChannel()));
        if (!channelLimiter.tryAcquire()) {
            responseFuture.completeExceptionally(new LimitException("Channel限流，当前在途请求超过阈值"));
            return responseFuture;
        }
        inflightRequestTable.put(request.getRequestId(), responseFuture);
        // 每发送一个请求，就加一个定时任务，超时则进行异常结束
        Timeout timeout = timer.newTimeout(
                (t) -> responseFuture.completeExceptionally(new TimeoutException()),
                timeoutMs, TimeUnit.MILLISECONDS
        );
        responseFuture.whenComplete((r, e) -> {
            inflightRequestTable.remove(request.getRequestId());
            globalLimiter.release();
            channelLimiter.release();
            timeout.cancel();
        });
        return responseFuture;
    }

    public boolean completeRequest(int requestId,Response response) {
        CompletableFuture<Response> future = inflightRequestTable.remove(requestId);
        if (future == null) {
            log.warn("request Id:{}，空闲返回",requestId);
            return false;
        }
        return future.complete(response);
    }

    public boolean completeExceptionallyRequest(int requestId,Exception e) {
        CompletableFuture<Response> future = inflightRequestTable.remove(requestId);
        if (future == null) {
            log.warn("request Id:{}，空闲异常",requestId,e);
            return false;
        }
        return future.completeExceptionally(e);
    }

    public void clearChannel(ServiceMetadata metadata) {
        channelLimiterMap.remove(metadata);
    }
}
