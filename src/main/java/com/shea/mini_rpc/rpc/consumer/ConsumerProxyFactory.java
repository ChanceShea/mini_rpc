package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.codec.RequestEncoder;
import com.shea.mini_rpc.rpc.codec.SheaDecoder;
import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.loadbalance.LoadBalancer;
import com.shea.mini_rpc.rpc.loadbalance.RandomLoadBalancer;
import com.shea.mini_rpc.rpc.loadbalance.RoundRobinLoadBalancer;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import com.shea.mini_rpc.rpc.retry.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/24 13:59
 */
@Slf4j
public class ConsumerProxyFactory {

    ConnectionManager connectionManager;
    Map<Integer, CompletableFuture<Response>> inflightRequestTable;
    private final ServiceRegistry register;
    private final ConsumerProperties properties;
    private final HashedWheelTimer timer;

    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.properties = properties;
        this.register = new DefaultServiceRegistry();
        this.register.init(properties.getRegistryConfig());
        this.connectionManager = new ConnectionManager(createBootstrap(properties));
        this.inflightRequestTable = new ConcurrentHashMap<>();
        this.timer = new HashedWheelTimer(1, TimeUnit.SECONDS, 64);
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

    @SuppressWarnings("unchecked")
    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass},
                new ConsumerInvocationHandler(interfaceClass, createLoadBalancer(),createRetryPolicy())
        );
    }

    private RetryPolicy createRetryPolicy() {
        return switch (this.properties.getRetryPolicy()){
            case "retrysame" -> new RetrySameRetryPolicy();
            case "failover" -> new FailOverRetryPolicy();
            case "forking" -> new ForkingRetryPolicy();
            default -> throw new IllegalArgumentException("没有这个重试策略 " + properties.getRetryPolicy());
        };
    }

    private LoadBalancer createLoadBalancer() {
        return switch (this.properties.getLoadBalancePolicy()) {
            case "robin" -> new RoundRobinLoadBalancer();
            case "random" -> new RandomLoadBalancer();
            default -> throw new IllegalArgumentException(this.properties.getLoadBalancePolicy() + "负载均衡未实现");
        };
    }

    public class ConsumerInvocationHandler implements InvocationHandler {

        final Class<?> interfaceClass;
        final LoadBalancer loadBalancer;
        final RetryPolicy retryPolicy;

        public ConsumerInvocationHandler(Class<?> interfaceClass, LoadBalancer loadBalancer, RetryPolicy retryPolicy) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
            this.retryPolicy = retryPolicy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            long start = System.currentTimeMillis();
            List<ServiceMetadata> serviceMetadata = register.fetchServiceList(interfaceClass.getName());
            if (serviceMetadata.isEmpty()) {
                throw new RpcException("No service found for " + interfaceClass.getSimpleName());
            }
            ServiceMetadata provider = loadBalancer.select(serviceMetadata);
            Request request = buildRequest(method, args);
            CompletableFuture<Response> requestFuture = callRpcAsync(request, provider);
            Response response;
            try {
                response = requestFuture.get(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                long timeoutMs = properties.getMethodTimeoutMs() - (System.currentTimeMillis() - start);
                if (timeoutMs <= 0 ){
                    throw new TimeoutException();
                }
                RetryContext retryContext = new RetryContext();
                retryContext.setFailService(provider);
                retryContext.setServiceMetadataList(serviceMetadata);
                retryContext.setMethodTimeoutMs(timeoutMs);
                retryContext.setDoRpcFunction(p -> callRpcAsync(buildRequest(method,args),p));
                retryContext.setRequestTimeoutMs(properties.getRequestTimeoutMs());
                retryContext.setLoadBalancer(this.loadBalancer);
                response = this.retryPolicy.retry(retryContext);
            }
            return processResponse(response);
        }

        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMetadata provider) {
            CompletableFuture<Response> responseFuture = new CompletableFuture<>();
            Channel channel = connectionManager.getChannel(provider.getHost(), provider.getPort());
            if (channel == null) {
                responseFuture.completeExceptionally(new RpcException("provider 连接失败"));
                return responseFuture;
            }
            inflightRequestTable.put(request.getRequestId(), responseFuture);
            // 每发送一个请求，就加一个定时任务，超时则进行异常结束
            Timeout timeout = timer.newTimeout(
                    (t) -> responseFuture.completeExceptionally(new TimeoutException()),
                    properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS
            );
            responseFuture.whenComplete((r, e) -> {
                inflightRequestTable.remove(request.getRequestId());
                timeout.cancel();
            });
            channel.writeAndFlush(request).addListener(f -> {
                log.info("发送了request:{}",request.getRequestId());
                if (!f.isSuccess()) {
                    responseFuture.completeExceptionally(f.cause());
                }
            });
            return responseFuture;
        }

        private Object processResponse(Response response) {
            if (response.getCode() == Response.SUCCESS) {
                return response.getResult();
            } else {
                throw new RpcException(response.getErrMsg());
            }
        }

        private Request buildRequest(Method method, Object[] args) {
            Request request = new Request();
            request.setMethodName(method.getName());
            request.setParamsClass(method.getParameterTypes());
            request.setParams(args);
            request.setServiceName(interfaceClass.getName());
            return request;
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("toString")) {
                return "Shea Proxy Consumer " + interfaceClass.getSimpleName();
            }
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            throw new UnsupportedOperationException("Proxy Object is not supported this method " + method.getName());
        }
    }

    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            CompletableFuture<Response> responseFuture = inflightRequestTable.remove(response.getRequestId());
            if (responseFuture == null) {
                log.warn("request id {} is null", response.getRequestId());
                return;
            }
            responseFuture.complete(response);
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

