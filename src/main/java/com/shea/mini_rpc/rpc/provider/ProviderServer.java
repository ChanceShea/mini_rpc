package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.codec.ResponseEncoder;
import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/21 14:40
 */
@Slf4j
public class ProviderServer {

    private EventLoopGroup bossEventLoopGroup;
    private EventLoopGroup workerEventLoopGroup;
    private final ProviderRegistry registry;
    private final ServiceRegistry serviceRegistry;
    private final ProviderProperties properties;

    public ProviderServer(ProviderProperties properties) {
        this.properties = properties;
        this.serviceRegistry = new DefaultServiceRegistry();
        this.registry = new ProviderRegistry();
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public void start() {
        bossEventLoopGroup = new NioEventLoopGroup();
        workerEventLoopGroup = new NioEventLoopGroup(properties.getWorkThreadNum());
        try {
            this.serviceRegistry.init(properties.getRegistryConfig());
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossEventLoopGroup, workerEventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    .addLast(new SheaDecoder())
                                    .addLast(new ResponseEncoder())
                                    .addLast(new ProviderHandler());

                        }
                    });
            bootstrap.bind(properties.getPort()).sync();
            registry.allServiceName().stream().map(this::buildMetadata)
                    .forEach(this.serviceRegistry::registerService);
        } catch (InterruptedException e) {
            throw new RuntimeException("服务器启动异常：" + e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ServiceMetadata buildMetadata(String serviceName) {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setServiceName(serviceName);
        serviceMetadata.setPort(properties.getPort());
        serviceMetadata.setHost(properties.getHost());
        return serviceMetadata;
    }

    public class ProviderHandler extends SimpleChannelInboundHandler<Request> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request request) throws Exception {
            ProviderRegistry.Invocation<?> instance = registry.findInstance(request.getServiceName());
            if (instance == null) {
                String format = String.format("%s 没有对应的处理服务", request.getServiceName());
                Response fail = Response.fail(format, request.getRequestId());
                log.error(format);
                ctx.writeAndFlush(fail);
                return;
            }
            try {
                long start = System.currentTimeMillis();
                Object result = instance.invoke(request.getMethodName(), request.getParamsClass(), request.getParams());
                log.info("requestId: {},{},函数调用了{},结果是{},耗时是{}", request.getRequestId(), request.getServiceName(), request.getMethodName(), result, System.currentTimeMillis() - start);
                Response success = Response.success(result, request.getRequestId());
                ctx.writeAndFlush(success);
            } catch (Exception e) {
                Response fail = Response.fail(e.getMessage(), request.getRequestId());
                ctx.writeAndFlush(fail);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} connected", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} disconnected", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("catch exception", cause);
            ctx.channel().close();
        }
    }

    public void stop() {
        if (bossEventLoopGroup != null) {
            bossEventLoopGroup.shutdownGracefully();
        }
        if (workerEventLoopGroup != null) {
            workerEventLoopGroup.shutdownGracefully();
        }
    }
}
