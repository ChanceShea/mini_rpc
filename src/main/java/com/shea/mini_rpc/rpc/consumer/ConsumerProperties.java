package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.register.RegistryConfig;
import lombok.Data;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/26 16:50
 */
@Data
public class ConsumerProperties {

    private Integer workThreadNum = 4;
    private Integer connectTimeoutMs = 3000;
    private Integer requestTimeoutMs = 3000;
    private Integer methodTimeoutMs = 10000;
    private String loadBalancePolicy = "robin";
    private String retryPolicy = "forking";
    private int rpcPerSecond = 100;
    private int rpcPerChannel = 50;
    private double slowRequestBreakRatio = 0.5;
    private long slowRequestMs = 1000;
    private RegistryConfig registryConfig = new RegistryConfig();
}
