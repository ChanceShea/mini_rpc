package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.register.RegistryConfig;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * RPC 服务消费者启动类
 * <p>
 * 演示如何启动一个 RPC 服务消费者，创建代理并发起远程调用
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/23 14:40
 */
public class ConsumerApp {

    /**
     * 主函数 - 启动 RPC 服务消费者
     * <p>
     * 1. 配置注册中心信息（ZooKeeper）
     * 2. 创建消费者代理工厂
     * 3. 获取远程服务代理对象
     * 4. 循环发起 RPC 调用测试
     * </p>
     * @param args 命令行参数
     * @throws Exception 启动或调用异常
     */
    public static void main(String[] args) throws Exception {
        RegistryConfig config = new RegistryConfig();
        config.setRegisterType("zookeeper");
        config.setConnectString("127.0.0.1:2181");
        ConsumerProperties properties = new ConsumerProperties();
        properties.setRegistryConfig(config);
        properties.setRpcPerChannel(10000);
        properties.setRpcPerSecond(10000);
        Add addConsumer = new ConsumerProxyFactory(properties).getConsumerProxy(Add.class);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                while (true) {
                    LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
                    long startTime = System.currentTimeMillis();
                    System.out.println(addConsumer.add(1, 2) + "  " + (System.currentTimeMillis() - startTime) + "ms");
                }
            }).start();
        }
//        while(true) {
//            Thread.sleep(1000);
//            System.out.println(addConsumer.add(1, 2));
//        }
    }
}
