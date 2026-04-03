package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.register.RegistryConfig;

/**
 * RPC 服务提供者启动类
 * <p>
 * 演示如何启动一个 RPC 服务提供者，注册服务实例并开启服务器监听
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/23 14:41
 */
public class ProviderApp {

    /**
     * 主函数 - 启动 RPC 服务提供者
     * <p>
     * 1. 配置注册中心信息（ZooKeeper）
     * 2. 配置服务器地址和端口
     * 3. 注册服务实例
     * 4. 启动服务器
     * </p>
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        RegistryConfig config = new RegistryConfig();
        config.setConnectString("127.0.0.1:2181");
        config.setRegisterType("zookeeper");
        ProviderProperties properties = new ProviderProperties();
        properties.setRegistryConfig(config);
        properties.setHost("127.0.0.1");
        properties.setPort(8893);
        ProviderServer server = new ProviderServer(properties);
        server.register(Add.class,new AddImpl());
        server.start();
    }
}
