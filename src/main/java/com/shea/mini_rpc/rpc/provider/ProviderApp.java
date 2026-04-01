package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.register.RegistryConfig;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 14:41
 */
public class ProviderApp {

    public static void main(String[] args) {
        RegistryConfig config = new RegistryConfig();
        config.setConnectString("127.0.0.1:2181");
        config.setRegisterType("zookeeper");
        ProviderProperties properties = new ProviderProperties();
        properties.setRegistryConfig(config);
        properties.setHost("127.0.0.1");
        properties.setPort(8891);
        ProviderServer server = new ProviderServer(properties);
        server.register(Add.class,new AddImpl());
        server.start();
    }
}
