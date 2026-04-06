package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.codec.SheaEncoder;
import com.shea.mini_rpc.rpc.compress.Compression;
import com.shea.mini_rpc.rpc.compress.CompressionManager;
import com.shea.mini_rpc.rpc.handler.HeartbeatHandler;
import com.shea.mini_rpc.rpc.handler.TrafficRecordHandler;
import com.shea.mini_rpc.rpc.limit.ConcurrencyLimiter;
import com.shea.mini_rpc.rpc.limit.Limiter;
import com.shea.mini_rpc.rpc.limit.RateLimiter;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import com.shea.mini_rpc.rpc.serialize.Serializer;
import com.shea.mini_rpc.rpc.serialize.SerializerManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 服务提供者服务器
 * <p>
 * 负责启动 Netty 服务器，注册服务实例，处理 Consumer 的请求
 * 支持限流、服务注册与发现等功能
 * </p>
 *
 * @author Shea.
 * @version 1.0
 * @since 2026/3/21 14:40
 */
@Slf4j
public class ProviderServer {

    /**
     * Netty Boss 事件循环组，负责接受客户端连接
     */
    private EventLoopGroup bossEventLoopGroup;

    /**
     * Netty Worker 事件循环组，负责处理 I/O 操作
     */
    private EventLoopGroup workerEventLoopGroup;

    /**
     * 服务注册表，维护服务接口与实现实例的映射关系
     */
    private final ProviderRegistry registry;

    /**
     * 外部服务注册中心（如 ZooKeeper、Redis 等）
     */
    private final ServiceRegistry serviceRegistry;

    /**
     * Provider 配置信息
     */
    private final ProviderProperties properties;

    /**
     * 全局限流器，控制整个服务器的请求处理能力
     */
    private final Limiter globalLimiter;

    private final SerializerManager serializerManager;

    private final CompressionManager compressionManager;

    private ThreadPoolExecutor invokeExecutor;

    /**
     * 构造函数，初始化 Provider 服务器
     *
     * @param properties Provider 配置信息
     */
    public ProviderServer(ProviderProperties properties) {
        this.properties = properties;
        this.serviceRegistry = new DefaultServiceRegistry();
        this.registry = new ProviderRegistry();
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalMaxRequest());
        this.serializerManager = new SerializerManager();
        this.compressionManager = new CompressionManager();
        this.invokeExecutor = new ThreadPoolExecutor(
                4,
                4,
                10,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new FastFailResponseHandler());
    }

    /**
     * 注册服务实例
     * <p>
     * 将服务接口与实现实例绑定到本地注册表
     * </p>
     *
     * @param interfaceClass  服务接口类
     * @param serviceInstance 服务实现实例
     * @param <I>             服务接口类型
     */
    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    /**
     * 启动服务器
     * <p>
     * 1. 初始化事件循环组
     * 2. 初始化服务注册中心
     * 3. 配置 Netty ServerBootstrap
     * 4. 绑定端口并监听
     * 5. 将所有注册的服务发布到注册中心
     * </p>
     */
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
                                    .addLast(new TrafficRecordHandler())
                                    .addLast(new SheaDecoder())
                                    .addLast(new SheaEncoder())
                                    .addLast(new IdleStateHandler(30, 5, 0, TimeUnit.SECONDS))
                                    .addLast(new HeartbeatHandler())
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

    /**
     * 构建服务元数据
     * <p>
     * 将服务名称、主机地址、端口等信息封装为 ServiceMetadata
     * </p>
     *
     * @param serviceName 服务名称
     * @return 服务元数据对象
     */
    private ServiceMetadata buildMetadata(String serviceName) {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setServiceName(serviceName);
        serviceMetadata.setPort(properties.getPort());
        serviceMetadata.setHost(properties.getHost());
        return serviceMetadata;
    }

    /**
     * 限流处理器 - 双向处理器（入站和出站）
     * <p>
     * 功能：
     * 1. 对 Consumer 传入的请求进行限流控制
     * 2. 管理全局和通道级别的许可
     * 3. 在响应返回后释放占用的许可
     * </p>
     */
    public class LimitHandler extends ChannelDuplexHandler {

        /**
         * 通道限流器属性键，用于存储每个连接的独立限流器
         */
        private static final AttributeKey<Limiter> CHANNEL_LIMIT_KEY = AttributeKey.valueOf("channel_limit_key");

        /**
         * 全局许可计数器属性键，跟踪当前通道已使用的许可数
         */
        private static final AttributeKey<AtomicInteger> GLOBAL_PERMITS = AttributeKey.valueOf("global_permits");

        /**
         * 处理入站请求（Consumer 发起的 RPC 调用）
         * <p>
         * 限流逻辑：
         * 1. 先尝试获取全局许可
         * 2. 再尝试获取通道级别的许可
         * 3. 都成功后才将请求向下传递
         * </p>
         *
         * @param ctx Channel 处理上下文
         * @param msg 消息对象
         * @throws Exception 处理异常
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            if (!globalLimiter.tryAcquire()) {
                ctx.writeAndFlush(Response.fail("provider 限流", request.getRequestId()));
                return;
            }
            Limiter channelLimiter = ctx.channel().attr(CHANNEL_LIMIT_KEY).get();
            if (!channelLimiter.tryAcquire()) {
                globalLimiter.release();
                ctx.writeAndFlush(Response.fail("provider 限流", request.getRequestId()));
                return;
            }
            ctx.channel().attr(GLOBAL_PERMITS).get().incrementAndGet();
            // 获得到许可，将消息继续向下传递
            ctx.fireChannelRead(msg);
        }

        /**
         * 处理出站响应（写回给 Consumer）
         * <p>
         * 在响应成功返回后，释放之前占用的限流许可
         * </p>
         *
         * @param ctx     Channel 处理上下文
         * @param msg     响应消息
         * @param promise Netty 异步操作结果回调
         * @throws Exception 处理异常
         */
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Limiter limiter = null;
            // promise 是一个 future，对其进行监听，返回结果时会触发 release 来释放许可
            promise.addListener(f -> {
                int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndDecrement();
                if (remain > 0) {
                    ctx.channel().attr(CHANNEL_LIMIT_KEY).get().release();
                    globalLimiter.release();
                }
            });
            // ctx.write 继续将 response 向前写回
            ctx.write(msg, promise);
        }

        /**
         * 通道激活时的初始化
         * <p>
         * 为每个新连接创建独立的限流器和许可计数器
         * </p>
         *
         * @param ctx Channel 处理上下文
         * @throws Exception 初始化异常
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Limiter channelLimiter = new RateLimiter(properties.getPreConsumerMaxRequest());
            ctx.channel().attr(CHANNEL_LIMIT_KEY).set(channelLimiter);
            ctx.channel().attr(GLOBAL_PERMITS).set(new AtomicInteger(0));
            Compression.CompressionType compressionType = Compression.CompressionType.valueOf(properties.getCompress().toUpperCase(Locale.ROOT));
            ctx.channel().attr(SheaEncoder.COMPRESS_KEY).set(compressionType.getType());
            ctx.channel().attr(SheaEncoder.COMPRESS_MANAGER_KEY).set(compressionManager);
            ctx.fireChannelActive();
        }

        /**
         * 通道断开时的清理
         * <p>
         * 释放该通道占用的所有全局许可
         * </p>
         *
         * @param ctx Channel 处理上下文
         * @throws Exception 清理异常
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndSet(0);
            globalLimiter.release(remain);
            ctx.fireChannelInactive();
        }
    }

    /**
     * Provider 业务处理器
     * <p>
     * 负责实际执行服务方法，处理 RPC 请求并返回结果
     * </p>
     */
    public class ProviderHandler extends SimpleChannelInboundHandler<Request> {
        /**
         * 处理 RPC 请求
         * <p>
         * 1. 查找对应的服务实例
         * 2. 反射调用指定方法
         * 3. 返回执行结果或错误信息
         * </p>
         *
         * @param ctx     Channel 处理上下文
         * @param request RPC 请求对象
         * @throws Exception 处理异常
         */
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
            invokeExecutor.execute(new InvokeTask(request,ctx,instance));
        }

        /**
         * 通道连接成功回调
         *
         * @param ctx Channel 处理上下文
         * @throws Exception 回调异常
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} connected", ctx.channel().remoteAddress());
            Serializer.SerializerType serializerType = Serializer.SerializerType.valueOf(properties.getSerializer().toUpperCase(Locale.ROOT));
            ctx.channel().attr(SheaEncoder.SERIALIZE_KEY).set(serializerType.getTypeCode());
            ctx.channel().attr(SheaEncoder.SERIALIZER_MANAGER_KEY).set(serializerManager);
            ctx.fireChannelActive();
        }

        /**
         * 通道断开连接回调
         *
         * @param ctx Channel 处理上下文
         * @throws Exception 回调异常
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("address:{} disconnected", ctx.channel().remoteAddress());
        }

        /**
         * 捕获通道异常
         * <p>
         * 记录异常日志并关闭连接
         * </p>
         *
         * @param ctx   Channel 处理上下文
         * @param cause 异常原因
         * @throws Exception 处理异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("catch exception", cause);
            ctx.channel().close();
        }
    }

    private static class FastFailResponseHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (r instanceof InvokeTask invokeTask) {
                Response fastFail = Response.fail("服务提供者忙", invokeTask.request.getRequestId());
                invokeTask.ctx.writeAndFlush(fastFail);
                return;
            }
            throw new RuntimeException("task有问题");
        }
    }

    private class InvokeTask implements Runnable {

        private final Request request;
        private final ChannelHandlerContext ctx;
        private final ProviderRegistry.Invocation<?> instance;

        public InvokeTask(Request request,ChannelHandlerContext ctx,ProviderRegistry.Invocation<?> invocation) {
            this.request = request;
            this.ctx = ctx;
            this.instance = invocation;
        }
        @Override
        public void run() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            try {
                long start = System.currentTimeMillis();
                Object result = instance.invoke(request.getMethodName(), request.getParamsClass(), request.getParams());
                log.info("requestId: {},{},函数调用了{},结果是{},耗时是{}",
                        request.getRequestId(),
                        request.getServiceName(),
                        request.getMethodName(),
                        result,
                        System.currentTimeMillis() - start);
                eventLoop.execute(() -> ctx.writeAndFlush(Response.success(result, request.getRequestId())));
            } catch (Exception e) {
                eventLoop.execute(() -> ctx.writeAndFlush(Response.fail(e.getMessage(), request.getRequestId())));
            }
        }
    }

    /**
     * 停止服务器
     * <p>
     * 优雅关闭事件循环组，释放资源
     * </p>
     */
    public void stop() {
        if (bossEventLoopGroup != null) {
            bossEventLoopGroup.shutdownGracefully();
        }
        if (workerEventLoopGroup != null) {
            workerEventLoopGroup.shutdownGracefully();
        }
    }
}
