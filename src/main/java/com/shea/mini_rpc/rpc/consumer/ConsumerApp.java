package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.register.RegistryConfig;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

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
        properties.setRpcPerChannel(10000);
        properties.setRpcPerSecond(10000);
        Add addConsumer = new ConsumerProxyFactory(properties).getConsumerProxy(Add.class);
        CyclicBarrier barrier = new CyclicBarrier(10);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    System.out.println(addConsumer.add(1,2));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }
}
