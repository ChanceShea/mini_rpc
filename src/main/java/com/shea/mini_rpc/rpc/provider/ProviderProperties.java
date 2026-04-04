package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.register.RegistryConfig;
import lombok.Data;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/26 16:59
 */
@Data
public class ProviderProperties {

    private String host;
    private int port;
    private int globalMaxRequest = 10;
    private int preConsumerMaxRequest = 5;
    private RegistryConfig registryConfig;
    private int workThreadNum = 4;
    private String serializer = "hessian";
    private String compress = "none";
}
