package com.shea.mini_rpc.rpc.fallback;

import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认降级策略实现
 * 组合了缓存降级和Mock降级两种策略，优先使用缓存降级，如果缓存降级失败则使用Mock降级
 * 提供了多级降级保障，提高系统的可用性
 * @author : Shea.
 * @since : 2026/4/4 14:05
 */
@Slf4j
public class DefaultFallback implements Fallback {

    /**
     * 缓存降级策略实例
     */
    private final CacheFallback cacheFallback;
    
    /**
     * Mock降级策略实例
     */
    private final MockFallback mockFallback;

    /**
     * 构造函数，初始化两种降级策略
     * @param cacheFallback 缓存降级策略
     * @param mockFallback Mock降级策略
     */
    public DefaultFallback(CacheFallback cacheFallback, MockFallback mockFallback) {
        this.cacheFallback = cacheFallback;
        this.mockFallback = mockFallback;
    }

    /**
     * 记录RPC调用成功的结果到所有降级策略中
     * 同时更新缓存降级和Mock降级所需的数据
     * @param metrics RPC调用的指标信息
     */
    @Override
    public void recordMetrics(RpcCallMetrics metrics) {
        // 记录到Mock降级策略
        this.mockFallback.recordMetrics(metrics);
        // 记录到缓存降级策略
        this.cacheFallback.recordMetrics(metrics);
    }

    /**
     * 执行降级逻辑，采用多级降级策略
     * 首先尝试缓存降级，如果缓存降级失败则尝试Mock降级
     * @param metrics RPC调用的指标信息
     * @return 降级后的返回结果
     * @throws Exception 当所有降级策略都失败时抛出异常
     */
    @Override
    public Object fallback(RpcCallMetrics metrics) throws Exception {
        try {
            // 优先尝试缓存降级
            return cacheFallback.fallback(metrics);
        }catch (Exception e) {
            // 缓存降级失败，记录警告日志
            log.warn("缓存降级没有生效");
            // 尝试Mock降级作为备选方案
            return mockFallback.fallback(metrics);
        }
    }
}
