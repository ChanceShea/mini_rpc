package com.shea.mini_rpc.rpc.api;

import com.shea.mini_rpc.rpc.fallback.RpcFallback;

/**
 * RPC 服务接口示例 - 加法服务
 * <p>
 * 该接口定义了基础的数学运算方法，用于演示 RPC 调用功能
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/23 15:35
 */
@RpcFallback(ConsumerAddImpl.class)
public interface Add {
    int add(int a, int b);

    int minus(int a, int b);
}
