package com.shea.mini_rpc.rpc.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/22 21:00
 */
@Data
public class Request {

    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    private int requestId = REQUEST_COUNTER.getAndIncrement();

    private String serviceName;
    private String methodName;
    private Class<?>[] paramsClass;
    private Object[] params;
}
