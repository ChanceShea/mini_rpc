package com.shea.mini_rpc.rpc.limit;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : Shea.
 * @since : 2026/4/1 16:32
 * 通过令牌桶算法，实现速率限流
 */
@Deprecated
public class BucketLimiter implements Limiter {

    private final AtomicInteger tokens;
    private final ScheduledFuture<?> refillSchedule;
    private static final EventLoop REFILL_EVENT_LOOP = new DefaultEventLoop(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "refill_event_loop");
            thread.setDaemon(true);
            return thread;
        }
    });


    public BucketLimiter(int permitsPerSecond) {
        this.tokens = new AtomicInteger(permitsPerSecond);
        this.refillSchedule = REFILL_EVENT_LOOP.scheduleAtFixedRate(
                () -> tokens.set(permitsPerSecond),
                1,1, TimeUnit.SECONDS);
    }

    public void destroy() {
        refillSchedule.cancel(false);
    }

    @Override
    public boolean tryAcquire() {
        while(true) {
            int currentTokens = tokens.get();
            if(currentTokens < 0) {
                return false;
            }
            if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return true;
            }
        }
    }

    @Override
    public void release() {

    }
}
