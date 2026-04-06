package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.codec.SheaEncoder;
import com.shea.mini_rpc.rpc.compress.CompressionManager;
import com.shea.mini_rpc.rpc.handler.HeartbeatHandler;
import com.shea.mini_rpc.rpc.handler.TrafficRecordHandler;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.serialize.SerializerManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 连接管理器
 * <p>
 * 负责管理 Consumer 与 Provider 之间的网络连接
 * 维护连接池，支持连接的创建、复用和清理
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/23 20:07
 */
@Slf4j
public class ConnectionManager {

    /**
     * 连接通道表，key 为"host:port"格式
     * 用于缓存已建立的连接，避免重复创建
     */
    private final Map<String,ChannelWrapper> channelTable = new ConcurrentHashMap<>();
    
    /**
     * Netty Bootstrap，用于创建客户端连接
     */
    private final Bootstrap bootstrap;
    
    /**
     * 飞行中请求管理器，管理尚未收到响应的请求
     */
    private final InFlightRequestManager inFlightRequestManager;
    
    /**
     * Consumer 配置信息
     */
    private final ConsumerProperties properties;

    private final SerializerManager serializerManager;

    private final CompressionManager compressionManager;

    /**
     * 构造函数，初始化连接管理器
     * @param inFlightRequestManager 飞行中请求管理器
     * @param properties Consumer 配置信息
     */
    public ConnectionManager(InFlightRequestManager inFlightRequestManager, ConsumerProperties properties) {
        this.inFlightRequestManager = inFlightRequestManager;
        this.bootstrap = createBootstrap(properties);
        this.properties = properties;
        this.serializerManager = new SerializerManager();
        this.compressionManager = new CompressionManager();
    }

    /**
     * 获取与指定服务提供者的连接通道
     * <p>
     * 1. 如果连接已存在且有效，则复用
     * 2. 否则创建新连接并缓存
     * </p>
     * @param metadata 服务元数据信息
     * @return 有效的连接通道，如果连接失败则返回 null
     */
    public Channel getChannel(ServiceMetadata metadata) {
        int port = metadata.getPort();
        String host = metadata.getHost();
        String key = host + ":" + port;
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent(key, (k) -> {
            try {
                ChannelFuture future = bootstrap.connect(host, port).sync();
                Channel channel = future.channel();
                channel.closeFuture().addListener(f -> {
                    channelTable.remove(key);
                    inFlightRequestManager.clearChannel(metadata);
                });
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                log.error("连接超时{},{}",host,port,e);
                return new ChannelWrapper(null);
            }
        });
        Channel channel = channelWrapper.channel;
        if (channel == null || !channel.isActive()) {
            channelTable.remove(key);
            return null;
        }
        return channel;
    }

    /**
     * 创建 Netty Bootstrap 配置
     * <p>
     * 配置事件循环组、连接超时、Channel 处理器等
     * </p>
     * @param properties Consumer 配置信息
     * @return 配置好的 Bootstrap 对象
     */
    private Bootstrap createBootstrap(ConsumerProperties properties) {
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(properties.getWorkThreadNum()))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline()
                                .addLast(new TrafficRecordHandler())
                                .addLast(new SheaDecoder())
                                .addLast(new SheaEncoder())
                                .addLast(new IdleStateHandler(30,5,0, TimeUnit.SECONDS))
                                .addLast(new HeartbeatHandler())
                                .addLast(new ConsumerHandler());
                    }
                });
        return bootstrap;
    }

    private static class ChannelWrapper {
        final Channel channel;

        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }

    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            inFlightRequestManager.completeRequest(response.getRequestId(),response);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} connected", ctx.channel().remoteAddress());
            ctx.channel().attr(SheaEncoder.SERIALIZE_KEY).set(properties.getSerializer());
            ctx.channel().attr(SheaEncoder.SERIALIZER_MANAGER_KEY).set(serializerManager);
            ctx.channel().attr(SheaEncoder.COMPRESS_KEY).set(properties.getCompress());
            ctx.channel().attr(SheaEncoder.COMPRESS_MANAGER_KEY).set(compressionManager);
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} disconnected", ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("catch exception", cause);
            ctx.channel().close();
        }
    }
}
