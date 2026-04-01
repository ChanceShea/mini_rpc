package com.shea.mini_rpc.rpc.limit;

import java.util.concurrent.Semaphore;

/**
 * @author : Shea.
 * @since : 2026/4/1 16:30
 */
public class ConcurrencyLimiter implements Limiter {

    private final Semaphore semaphore;

    public ConcurrencyLimiter(int limitNum) {
        this.semaphore = new Semaphore(limitNum);
    }

    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    @Override
    public void release() {
        semaphore.release();
    }
}
