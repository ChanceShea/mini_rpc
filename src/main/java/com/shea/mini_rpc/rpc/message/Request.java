package com.shea.mini_rpc.rpc.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 请求类
 * <p>
 * 封装了 RPC 调用的请求信息，包括服务名、方法名、参数等
 * 用于 Consumer 向 Provider 发起远程调用时传递请求数据
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/22 21:00
 */
@Data
public class Request {

    /**
     * 请求 ID 生成器，线程安全
     * 用于为每个请求生成唯一的标识符
     */
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    /**
     * 请求唯一标识符
     * 用于匹配请求和响应，确保响应能正确返回给对应的调用方
     */
    private int requestId = REQUEST_COUNTER.getAndIncrement();

    /**
     * 服务名称
     * 标识要调用的远程服务接口
     */
    private String serviceName;
    
    /**
     * 方法名称
     * 标识要调用的具体方法
     */
    private String methodName;
    
    /**
     * 参数类型数组
     * 用于 Provider 端反射调用时确定方法签名
     */
    private Class<?>[] paramsClass;
    
    /**
     * 参数值数组
     * 实际传递给远程方法的参数值
     */
    private Object[] params;
}
