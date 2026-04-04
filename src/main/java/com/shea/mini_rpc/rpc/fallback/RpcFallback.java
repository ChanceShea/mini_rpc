package com.shea.mini_rpc.rpc.fallback;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RPC降级策略注解
 * 用于标注在RPC服务接口上，指定当服务不可用时使用的Mock降级类
 * Mock类必须实现与原接口相同的方法签名
 * 
 * 使用示例：
 * <pre>
 * {@code
 * @RpcFallback(ConsumerAddImpl.class)
 * public interface Add {
 *     int add(int a, int b);
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE})  // 只能用在类或接口上
@Retention(RetentionPolicy.RUNTIME)  // 运行时可见，可以通过反射获取
public @interface RpcFallback {
    /**
     * 指定降级时使用的Mock类
     * Mock类必须有无参构造函数，并且实现与原接口相同的方法
     * @return Mock类的Class对象
     */
    Class<?> value();
}
