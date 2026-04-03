package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.message.Response;

/**
 * 重试策略接口
 * <p>
 * 定义了 RPC 调用失败时的重试机制，支持多种重试策略（如故障转移、Forking 等）
 * 用于提高 RPC 调用的可靠性和成功率
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/31 19:41
 */
public interface RetryPolicy {

    /**
     * 执行重试逻辑
     * <p>
     * 根据具体的重试策略，在 RPC 调用失败时进行重试
     * </p>
     * @param context 重试上下文，包含请求信息、已尝试次数等
     * @return 重试后的响应结果
     * @throws Exception 重试过程中的异常
     */
    Response retry(RetryContext context) throws Exception;
}
