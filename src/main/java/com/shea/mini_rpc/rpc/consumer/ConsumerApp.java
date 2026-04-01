package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.register.RegistryConfig;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 14:40
 */
public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        RegistryConfig config = new RegistryConfig();
        config.setRegisterType("zookeeper");
        config.setConnectString("127.0.0.1:2181");
        ConsumerProperties properties = new ConsumerProperties();
        properties.setRegistryConfig(config);
        Add addConsumer = new ConsumerProxyFactory(properties).getConsumerProxy(Add.class);
        System.out.println(addConsumer.add(1,2));
    }
}
