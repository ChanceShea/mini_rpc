package com.shea.mini_rpc.rpc.fallback;

import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock降级策略实现
 * 通过反射调用用户指定的Mock对象来提供降级服务
 * 需要配合 @RpcFallback 注解使用，指定降级时使用的Mock类
 * @author : Shea.
 * @since : 2026/4/4 14:05
 */
public class MockFallback implements Fallback {

    /**
     * Mock对象缓存，key为Mock类的Class对象，value为Mock实例
     * 使用ConcurrentHashMap保证线程安全，避免重复创建Mock对象
     */
    private final Map<Class<?>,Object> mockObjectCache = new ConcurrentHashMap<>();

    /**
     * 执行Mock降级逻辑
     * 根据 @RpcFallback 注解指定的Mock类，创建或获取Mock实例并调用相应方法
     * @param metrics RPC调用的指标信息，包含方法、参数等
     * @return Mock方法的调用结果
     * @throws Exception 当未配置降级策略或Mock对象创建失败时抛出异常
     */
    @Override
    public Object fallback(RpcCallMetrics metrics) throws Exception {
        Method method = metrics.getMethod();
        // 从方法声明的类上获取 @RpcFallback 注解
        RpcFallback annotation = method.getDeclaringClass().getAnnotation(RpcFallback.class);
        // 如果没有配置降级策略，抛出异常
        if (annotation == null) {
            throw new RpcException("你不设置降级策略，我是真没招了");
        }
        // 获取注解中指定的Mock类
        Class<?> methodClass = annotation.value();
        // 验证Mock类是否实现了原接口，确保类型兼容性
        if (!method.getDeclaringClass().isAssignableFrom(methodClass)) {
            throw new RpcException(String.format("你调用了%s,但是降级策略是%s",method,methodClass));
        }
        // 从缓存中获取或创建Mock对象
        Object mockObject = mockObjectCache.computeIfAbsent(methodClass, this::createMockObject);
        // 反射调用Mock对象的对应方法
        return method.invoke(mockObject, metrics.getParams());
    }

    /**
     * 创建Mock对象实例
     * 通过反射调用无参构造函数创建Mock类的实例
     * @param methodClass Mock类的Class对象
     * @return 新创建的Mock对象实例
     * @throws RpcException 当创建Mock对象失败时抛出异常
     */
    private Object createMockObject(Class<?> methodClass) {
        try {
            // 通过无参构造函数创建实例
            return methodClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RpcException("创建Mock对象失败",e);
        }
    }
}
