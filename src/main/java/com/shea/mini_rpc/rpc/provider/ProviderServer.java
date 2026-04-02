package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.codec.ResponseEncoder;
import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.limit.ConcurrencyLimiter;
import com.shea.mini_rpc.rpc.limit.Limiter;
import com.shea.mini_rpc.rpc.limit.RateLimiter;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

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
    private final Limiter globalLimiter;

    public ProviderServer(ProviderProperties properties) {
        this.properties = properties;
        this.serviceRegistry = new DefaultServiceRegistry();
        this.registry = new ProviderRegistry();
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalMaxRequest());
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
                                    .addLast(new LimitHandler())
                                    .addLast(new ProviderHandler());
                    // header --> SheaDecoder(Inbound) --> ResponseEncoder(Outbound)
                            // --> LimiterHandler(Inbound/Outbound) --> ProviderHandler(Inbound) --> tail
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

    /**
     * 双向的处理器（入栈和出战都会经过）
     * LimitHandler不光要限流Consumer传过来的请求，所以需要对其进行acquire获得许可
     * 因为Limit需要把acquire获得的许可，通过release释放掉
     */
    public class LimitHandler extends ChannelDuplexHandler {

        private static final AttributeKey<Limiter> CHANNEL_LIMIT_KEY = AttributeKey.valueOf("channel_limit_key");
        private static final AttributeKey<AtomicInteger> GLOBAL_PERMITS = AttributeKey.valueOf("global_permits");
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            if(!globalLimiter.tryAcquire()) {
                ctx.writeAndFlush(Response.fail("provider 限流",request.getRequestId()));
                return;
            }
            Limiter channelLimiter = ctx.channel().attr(CHANNEL_LIMIT_KEY).get();
            if(!channelLimiter.tryAcquire()) {
                globalLimiter.release();
                ctx.writeAndFlush(Response.fail("provider 限流",request.getRequestId()));
                return;
            }
            ctx.channel().attr(GLOBAL_PERMITS).get().incrementAndGet();
            // 获得到许可，将消息继续向下传递
            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Limiter limiter = null;
            // promise是一个future，对其进行监听，返回结果时会触发release来释放许可
            promise.addListener(f -> {
                int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndDecrement();
                if(remain > 0) {
                    ctx.channel().attr(CHANNEL_LIMIT_KEY).get().release();
                    globalLimiter.release();
                }
            });
            // ctx.write继续将response向前写回
            ctx.write(msg, promise);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Limiter channelLimiter = new RateLimiter(properties.getPreConsumerMaxRequest());
            ctx.channel().attr(CHANNEL_LIMIT_KEY).set(channelLimiter);
            ctx.channel().attr(GLOBAL_PERMITS).set(new AtomicInteger(0));
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndSet(0);
            globalLimiter.release(remain);
            ctx.fireChannelInactive();
        }
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
