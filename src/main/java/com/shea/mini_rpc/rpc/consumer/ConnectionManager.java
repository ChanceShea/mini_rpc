package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.codec.RequestEncoder;
import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 20:07
 */
@Slf4j
public class ConnectionManager {

    private final Map<String,ChannelWrapper> channelTable = new ConcurrentHashMap<>();
    private final Bootstrap bootstrap;
    private final InFlightRequestManager inFlightRequestManager;
    private final ConsumerProperties properties;

    public ConnectionManager(InFlightRequestManager inFlightRequestManager,ConsumerProperties properties) {
        this.inFlightRequestManager = inFlightRequestManager;
        this.bootstrap = createBootstrap(properties);
        this.properties = properties;
    }

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

    private Bootstrap createBootstrap(ConsumerProperties properties) {
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(properties.getWorkThreadNum()))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline()
                                .addLast(new SheaDecoder())
                                .addLast(new RequestEncoder())
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
}
