package com.shea.mini_rpc.rpc.breaker;

import com.shea.mini_rpc.rpc.consumer.ConsumerProperties;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : Shea.
 * @since : 2026/4/3 09:58
 */
public class CircuitBreakerManager {

    private final Map<ServiceMetadata,CircuitBreaker> breakerMap = new ConcurrentHashMap<ServiceMetadata,CircuitBreaker>();
    private final ConsumerProperties consumerProperties;

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public CircuitBreaker createOrGetBreaker(ServiceMetadata serviceMetadata) {
        return breakerMap.computeIfAbsent(serviceMetadata, this::createBreaker);
    }

    private CircuitBreaker createBreaker(ServiceMetadata serviceMetadata) {
        return new ResponseTimeCircuitBreaker(consumerProperties.getSlowRequestBreakRatio(),consumerProperties.getSlowRequestMs());
    }
}
