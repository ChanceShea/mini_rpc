package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.breaker.CircuitBreaker;
import com.shea.mini_rpc.rpc.breaker.CircuitBreakerManager;
import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.loadbalance.LoadBalancer;
import com.shea.mini_rpc.rpc.loadbalance.RandomLoadBalancer;
import com.shea.mini_rpc.rpc.loadbalance.RoundRobinLoadBalancer;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import com.shea.mini_rpc.rpc.retry.*;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    private final ServiceRegistry register;
    private final ConsumerProperties properties;
    private final InFlightRequestManager inFlightRequestManager;
    private final CircuitBreakerManager circuitBreakerManager;

    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.properties = properties;
        this.register = new DefaultServiceRegistry();
        this.register.init(properties.getRegistryConfig());
        this.inFlightRequestManager = new InFlightRequestManager(properties);
        this.connectionManager = new ConnectionManager(inFlightRequestManager, properties);
        this.circuitBreakerManager = new CircuitBreakerManager(properties);
    }

    @SuppressWarnings("unchecked")
    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass},
                new ConsumerInvocationHandler(interfaceClass, createLoadBalancer(), createRetryPolicy())
        );
    }

    private RetryPolicy createRetryPolicy() {
        return switch (this.properties.getRetryPolicy()) {
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
            List<ServiceMetadata> serviceMetadata = new ArrayList<>(register.fetchServiceList(interfaceClass.getName()));
            ServiceMetadata provider = decideProvider(serviceMetadata);
            Request request = buildRequest(method, args);
            Response response;
            RpcCallMetrics metrics = RpcCallMetrics.createRpcCallMetrics(method, provider, args);
            CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
            try {
                CompletableFuture<Response> requestFuture = callRpcAsync(request, provider);
                response = requestFuture.get(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
                metrics.complete();
                breaker.recordRpc(metrics);
            } catch (Exception e) {
                metrics.errorComplete(e);
                breaker.recordRpc(metrics);
                response = doRetry(metrics, serviceMetadata);
            }
            return processResponse(response);
        }

        private ServiceMetadata decideProvider(List<ServiceMetadata> candidate) {
            while (!candidate.isEmpty()) {
                ServiceMetadata select = this.loadBalancer.select(candidate);
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(select);
                if (breaker.allowRequest()) {
                    return select;
                }
                candidate.remove(select);
            }
            throw new RpcException("当前没有可以提供服务的provider");
        }

        private Response doRetry(RpcCallMetrics metrics, List<ServiceMetadata> serviceMetadata) throws Exception {
            Throwable e = metrics.getThrowable();
            if (e instanceof ExecutionException ee && ee.getCause() instanceof RpcException rpcException) {
                throw rpcException;
            }
            Response response;
            long timeoutMs = properties.getMethodTimeoutMs() - (System.currentTimeMillis() - metrics.getStartTime());
            if (timeoutMs <= 0) {
                throw new TimeoutException();
            }
            log.error("rpc出现了异常，进行重试", e);
            RetryContext retryContext = createRetryContextFromFailMetrics(metrics, serviceMetadata, timeoutMs);
            response = this.retryPolicy.retry(retryContext);
            return response;
        }

        private RetryContext createRetryContextFromFailMetrics(RpcCallMetrics metrics, List<ServiceMetadata> serviceMetadata, long timeoutMs) {
            RetryContext retryContext = new RetryContext();
            retryContext.setFailService(metrics.getProvider());
            retryContext.setServiceMetadataList(serviceMetadata);
            retryContext.setMethodTimeoutMs(timeoutMs);
            retryContext.setDoRpcFunction(p -> {
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(p);
                if (!breaker.allowRequest()) {
                    CompletableFuture<Response> breakFuture = new CompletableFuture<>();
                    breakFuture.completeExceptionally(new RpcException("provider 被熔断了"));
                    return breakFuture;
                }
                RpcCallMetrics retryMetrics = RpcCallMetrics.createRpcCallMetrics(metrics.getMethod(), p, metrics.getParams());
                CompletableFuture<Response> requestFuture = callRpcAsync(buildRequest(metrics.getMethod(), metrics.getParams()), p);
                requestFuture.whenComplete((r, retryE) -> {
                    if (retryE == null) {
                        retryMetrics.complete();
                    } else {
                        retryMetrics.errorComplete(retryE);
                    }
                    breaker.recordRpc(retryMetrics);
                });
                return requestFuture;
            });
            retryContext.setRequestTimeoutMs(properties.getRequestTimeoutMs());
            retryContext.setLoadBalancer(this.loadBalancer);
            return retryContext;
        }

        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMetadata provider) {
            CompletableFuture<Response> responseFuture = inFlightRequestManager.inFlightRequest(request, properties.getRequestTimeoutMs(), provider);
            Channel channel = connectionManager.getChannel(provider);
            if (channel == null) {
                responseFuture.completeExceptionally(new RpcException("provider 连接失败"));
                return responseFuture;
            }
            channel.writeAndFlush(request).addListener(f -> {
                log.info("发送了request:{}", request.getRequestId());
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
}

