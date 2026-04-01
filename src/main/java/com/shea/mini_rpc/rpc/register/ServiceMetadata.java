package com.shea.mini_rpc.rpc.register;

import lombok.Data;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/25 19:47
 */
@Data
public class ServiceMetadata {

    private String host;
    private int port;
    private String serviceName;
}
