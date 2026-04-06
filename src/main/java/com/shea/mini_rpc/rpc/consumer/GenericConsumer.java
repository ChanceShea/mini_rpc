package com.shea.mini_rpc.rpc.consumer;

/**
 * @author : Shea.
 * @since : 2026/4/6 15:27
 */
public interface GenericConsumer {

    Object $invoke(String serviceName, String methodName, String[] paramsType,Object[] params);
}
