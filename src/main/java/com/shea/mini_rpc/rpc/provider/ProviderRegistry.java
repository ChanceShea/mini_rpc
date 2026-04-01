package com.shea.mini_rpc.rpc.provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : Shea.
 * @description: 服务注册表，表示Provider有哪些函数是可以被远程调用的
 * @since : 2026/3/23 15:34
 */
public class ProviderRegistry {

    private final Map<String, Invocation<?>> instanceMap = new ConcurrentHashMap<>();

    public <I>void register(Class<I> interfaceClass,I instance) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("注册的类型必须是一个接口");
        }
        if (instanceMap.putIfAbsent(interfaceClass.getName(), new Invocation<>(interfaceClass,instance))!=null) {
            throw new IllegalArgumentException(interfaceClass.getName() + "重复注册了");
        }
    }

    public Invocation<?> findInstance(String serviceName) {
        return instanceMap.get(serviceName);
    }

    public List<String> allServiceName(){
        return new ArrayList<>(instanceMap.keySet());
    }

    public static class Invocation<I>{
        final I serviceInstance;
        final Class<I> interfaceClass;

        public Invocation(Class<I> interfaceClass, I serviceInstance) {
            this.serviceInstance = serviceInstance;
            this.interfaceClass = interfaceClass;
        }

        public Object invoke(String methodName,Class<?>[] paramsClass, Object... params) throws Exception {
            // 获得接口定义的方法，防止反射调用类的私有方法
            Method invokeMethod = interfaceClass.getDeclaredMethod(methodName, paramsClass);
            return invokeMethod.invoke(serviceInstance,params);
        }
    }

}
