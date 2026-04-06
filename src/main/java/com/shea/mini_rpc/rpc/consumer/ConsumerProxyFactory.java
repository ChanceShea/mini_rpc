package com.shea.mini_rpc.rpc.consumer;

import com.shea.mini_rpc.rpc.breaker.CircuitBreaker;
import com.shea.mini_rpc.rpc.breaker.CircuitBreakerManager;
import com.shea.mini_rpc.rpc.exception.RpcException;
import com.shea.mini_rpc.rpc.fallback.CacheFallback;
import com.shea.mini_rpc.rpc.fallback.DefaultFallback;
import com.shea.mini_rpc.rpc.fallback.Fallback;
import com.shea.mini_rpc.rpc.fallback.MockFallback;
import com.shea.mini_rpc.rpc.loadbalance.LoadBalancer;
import com.shea.mini_rpc.rpc.loadbalance.LoadBalancerManager;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import com.shea.mini_rpc.rpc.metrics.RpcCallMetrics;
import com.shea.mini_rpc.rpc.register.DefaultServiceRegistry;
import com.shea.mini_rpc.rpc.register.ServiceMetadata;
import com.shea.mini_rpc.rpc.register.ServiceRegistry;
import com.shea.mini_rpc.rpc.retry.RetryContext;
import com.shea.mini_rpc.rpc.retry.RetryPolicy;
import com.shea.mini_rpc.rpc.retry.RetryPolicyManager;
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
    private final ServiceRegistry registry;
    private final ConsumerProperties properties;
    private final InFlightRequestManager inFlightRequestManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final Fallback fallback;
    private final RetryPolicyManager retryPolicyManager;
    private final LoadBalancerManager loadBalancerManager;

    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.properties = properties;
        this.registry = new DefaultServiceRegistry();
        this.registry.init(properties.getRegistryConfig());
        this.inFlightRequestManager = new InFlightRequestManager(properties);
        this.connectionManager = new ConnectionManager(inFlightRequestManager, properties);
        this.circuitBreakerManager = new CircuitBreakerManager(properties);
        this.fallback = new DefaultFallback(new CacheFallback(), new MockFallback());
        this.retryPolicyManager = new RetryPolicyManager();
        this.loadBalancerManager = new LoadBalancerManager();
    }

    @SuppressWarnings("unchecked")
    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass},
                new ConsumerInvocationHandler(interfaceClass, createLoadBalancer(properties.getLoadBalancer()), createRetryPolicy(properties.getRetryPolicy()))
        );
    }

    private RetryPolicy createRetryPolicy(String name) {
        RetryPolicy retryPolicy = retryPolicyManager.getRetryPolicy(name);
        if (retryPolicy == null) {
            throw new IllegalArgumentException("没有这个重试策略 " + name);
        }
        return retryPolicy;
    }

    private LoadBalancer createLoadBalancer(String name) {
        return loadBalancerManager.getLoadBalancer(name);
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
            boolean genericInvoke = method.getName().equals("$invoke");
            String serviceName = genericInvoke ? args[0].toString() : interfaceClass.getName();
            List<ServiceMetadata> serviceMetadata = new ArrayList<>(registry.fetchServiceList(serviceName));
            ServiceMetadata provider = decideProvider(serviceMetadata);
            RpcCallMetrics metrics = RpcCallMetrics.createRpcCallMetrics(method, provider, args);
            if (provider == null) {
                // 降级策略
                return fallback.fallback(metrics);
            }
            Request request = buildRequest(method, args);
            CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
            try {
                CompletableFuture<Response> requestFuture = callRpcAsync(request, provider);
                Response response = requestFuture.get(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
                Object result = processResponse(response);
                metrics.complete(response);
                breaker.recordRpc(metrics);
                fallback.recordMetrics(metrics);
                return result;
            } catch (Exception e) {
                metrics.errorComplete(e);
                breaker.recordRpc(metrics);
            }
            try {
                return processResponse(doRetry(metrics, serviceMetadata));
            } catch (Exception e) {
                // 降级
                return fallback.fallback(metrics);
            }

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
            return null;
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
                        retryMetrics.complete(r);
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
            boolean genericInvoke = method.getName().equals("$invoke");
            Request request = new Request();
            request.setGenericInvoke(genericInvoke);
            if (genericInvoke) {
                request.setParamsClassStr((String[]) args[2]);
                request.setServiceName(args[0].toString());
                request.setMethodName(args[1].toString());
                request.setParams((Object[]) args[3]);
            } else {
                request.setMethodName(method.getName());
                request.setParamsClass(method.getParameterTypes());
                request.setServiceName(interfaceClass.getName());
                request.setParams(args);
            }
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

