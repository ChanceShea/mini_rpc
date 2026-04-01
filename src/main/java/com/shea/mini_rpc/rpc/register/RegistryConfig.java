package com.shea.mini_rpc.rpc.register;

import lombok.Data;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/25 20:01
 */
@Data
public class RegistryConfig {

    private String registerType = "zookeeper";
    private String connectString;
}
