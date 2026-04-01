package com.shea.mini_rpc.rpc.limit;

/**
 * @author : Shea.
 * @since : 2026/4/1 16:14
 */
public interface Limiter {

    boolean tryAcquire();

    void release();

    // 并发限流：当前服务器最多能承受多少个并发请求
    // 速率限流：一定时间内可以承受的并发请求 100QPS 防止流量激增
}
