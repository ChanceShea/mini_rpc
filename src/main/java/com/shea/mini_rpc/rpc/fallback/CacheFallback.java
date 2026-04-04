package com.shea.mini_rpc.rpc.fallback;

import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存降级策略实现
 * 通过缓存最近一次成功的RPC调用结果，在服务不可用时返回缓存数据
 * 适用于对实时性要求不高但需要保证可用性的场景
 * @author : Shea.
 * @since : 2026/4/4 14:05
 */
public class CacheFallback implements Fallback {

    /**
     * 用于表示null值的占位对象，避免Map中存储null导致无法区分"无缓存"和"缓存为null"
     */
    private static final Object NULL_OBJECT = new Object();
    
    /**
     * RPC结果缓存，key为调用方法的唯一标识，value为调用结果
     * 使用ConcurrentHashMap保证线程安全
     */
    private final Map<InvokeKey,Object> rpcResultCache = new ConcurrentHashMap<>();

    /**
     * 执行缓存降级逻辑
     * 从缓存中获取之前成功调用的结果并返回
     * @param metrics RPC调用的指标信息，包含方法、参数等
     * @return 缓存的调用结果，如果缓存中没有则抛出异常
     */
    @Override
    public Object fallback(RpcCallMetrics metrics) {
        // 根据方法和参数生成唯一的缓存键
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
        Object cacheResult = rpcResultCache.get(invokeKey);
        // 如果缓存的是NULL_OBJECT，说明之前成功调用的结果是null，直接返回null
        if (cacheResult == NULL_OBJECT) {
            return null;
        }
        // 如果缓存中没有找到结果，说明从未成功调用过，抛出异常
        if (cacheResult == null) {
            throw new RpcException("缓存降级真不行了!");
        }
        // 返回缓存的成功结果
        return cacheResult;
    }

    /**
     * 记录成功的RPC调用结果到缓存中
     * 在每次RPC调用成功后被调用，用于更新缓存数据
     * @param metrics RPC调用的指标信息，包含方法、参数和结果
     */
    @Override
    public void recordMetrics(RpcCallMetrics metrics) {
        // 根据方法和参数生成唯一的缓存键
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
        Object result = metrics.getResult();
        // 如果结果是null，使用NULL_OBJECT占位符存储，以区分"无缓存"和"缓存为null"
        if (result == null) {
            result = NULL_OBJECT;
        }
        // 将结果存入缓存
        rpcResultCache.put(invokeKey,result);
    }

    /**
     * 缓存键类，用于唯一标识一次RPC调用
     * 由方法和参数组成，确保相同方法和参数的调用能够命中相同的缓存
     */
    @Data
    private class InvokeKey {
        /**
         * 被调用的方法
         */
        final Method method;
        
        /**
         * 方法参数数组
         */
        final Object[] args;

        /**
         * 判断两个调用键是否相等
         * 需要比较方法是否相同以及参数数组是否深度相等
         * @param o 待比较的对象
         * @return 是否相等
         */
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            InvokeKey invokeKey = (InvokeKey) o;
            return Objects.equals(method, invokeKey.method) && Objects.deepEquals(args, invokeKey.args);
        }

        /**
         * 计算哈希码
         * 基于方法和参数的哈希值计算
         * @return 哈希码
         */
        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args));
        }
    }
}
