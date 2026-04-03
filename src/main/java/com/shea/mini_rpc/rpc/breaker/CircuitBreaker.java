package com.shea.mini_rpc.rpc.breaker;

import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;

/**
 * @author : Shea.
 * @since : 2026/4/3 09:56
 */
public interface CircuitBreaker {

    boolean allowRequest();

    void recordRpc(RpcCallMetrics metrics);

    enum State {
        OPEN,CLOSE,HALF_OPEN
    }
}
