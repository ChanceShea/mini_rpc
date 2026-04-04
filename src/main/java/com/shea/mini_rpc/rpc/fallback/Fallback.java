package com.shea.mini_rpc.rpc.fallback;

import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;

/**
 * 降级策略接口，定义了在RPC调用失败时的降级处理机制
 * 当远程服务不可用或出现异常时，可以通过实现此接口提供备用方案
 * @author : Shea.
 * @since : 2026/4/4 14:01
 */
public interface Fallback {

    /**
     * 执行降级逻辑，当RPC调用失败时被调用
     * @param metrics RPC调用的指标信息，包含方法、参数、异常等信息
     * @return 降级后的返回结果
     * @throws Exception 降级过程中可能抛出的异常
     */
    Object fallback(RpcCallMetrics metrics) throws Exception;

    /**
     * 记录RPC调用成功的结果，用于缓存降级等场景
     * 默认实现为空，子类可根据需要重写
     * @param metrics RPC调用的指标信息
     */
    default void recordMetrics(RpcCallMetrics metrics){}

}
