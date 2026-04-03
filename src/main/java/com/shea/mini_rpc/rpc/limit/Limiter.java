package com.shea.mini_rpc.rpc.limit;

/**
 * 限流器接口
 * <p>
 * 定义了流量控制的基本操作，用于保护系统免受过载影响
 * 支持并发限流和速率限流两种模式
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/4/1 16:14
 */
public interface Limiter {

    /**
     * 尝试获取许可
     * <p>
     * 如果当前有可用许可，则立即返回 true 并消耗一个许可；否则返回 false
     * </p>
     * @return 是否成功获取许可
     */
    boolean tryAcquire();

    /**
     * 释放一个许可
     * <p>
     * 默认方法，调用 release(1)
     * </p>
     */
    default void release() {
        release(1);
    }

    /**
     * 释放指定数量的许可
     * <p>
     * 将指定数量的许可归还到限流器中
     * </p>
     * @param permits 要释放的许可数量
     */
    void release(int permits);

    // 并发限流：控制当前服务器最多能处理的并发请求数量
    // 速率限流：控制单位时间内可以处理的请求数 (如 100QPS)，防止流量激增对系统造成冲击
}
