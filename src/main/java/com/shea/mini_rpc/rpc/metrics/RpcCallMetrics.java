package com.shea.mini_rpc.rpc.metrics;

import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import lombok.Data;

import java.lang.reflect.Method;

/**
 * @author : Shea.
 * @since : 2026/4/3 09:57
 */
@Data
public class RpcCallMetrics {

    private boolean complete;
    private Throwable throwable;
    private long duration;
    private long startTime;
    private Method method;
    private ServiceMetadata provider;
    private Object[] params;

    private RpcCallMetrics() {

    }

    public static RpcCallMetrics createRpcCallMetrics(Method method,ServiceMetadata provider,Object[] params) {
        RpcCallMetrics metrics = new RpcCallMetrics();
        metrics.startTime = System.currentTimeMillis();
        metrics.method = method;
        metrics.provider = provider;
        metrics.params = params;
        return metrics;
    }

    public void complete() {
        this.complete = true;
        this.duration = System.currentTimeMillis() - startTime;
    }

    public void errorComplete(Throwable throwable) {
        this.duration = System.currentTimeMillis() - startTime;
        this.throwable = throwable;
    }
}
