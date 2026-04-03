package com.shea.mini_rpc.rpc.breaker;

import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;

/**
 * 熔断器接口
 * <p>
 * 用于保护 RPC 调用，防止级联故障。熔断器包含三种状态：
 * </p>
 * <ul>
 *     <li><strong>关闭（Closed）</strong>：正常状态，请求可以正常通过</li>
 *     <li><strong>开启（Open）</strong>：熔断状态，请求被拒绝，直接返回</li>
 *     <li><strong>半开（Half-Open）</strong>：尝试恢复状态</li>
 * </ul>
 * <p>
 * <strong>状态流转逻辑：</strong>
 * </p>
 * <ul>
 *     <li>当熔断器检测到 provider 异常（失败率过高）时，从"关闭"转为"开启"</li>
 *     <li>在开启状态下，经过一段时间后进入"半开"状态</li>
 *     <li>半开状态下，会尝试发送少量请求：
 *         <ul>
 *             <li>如果请求失败，则重新进入"开启"状态</li>
 *             <li>如果请求成功，则恢复为"关闭"状态</li>
 *         </ul>
 *     </li>
 * </ul>
 * @author Shea.
 * @version 1.0
 * @since 2026/4/3 09:56
 */
public interface CircuitBreaker {

    /**
     * 判断是否允许请求通过
     * @return true 表示允许通过，false 表示熔断拒绝
     */
    boolean allowRequest();

    /**
     * 记录 RPC 调用指标信息
     * <p>
     * 用于统计成功率、响应时间等，作为熔断器状态转换的依据
     * </p>
     * @param metrics RPC 调用指标数据
     */
    void recordRpc(RpcCallMetrics metrics);

    enum State {
        OPEN,CLOSE,HALF_OPEN
    }
}
