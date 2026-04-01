package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.api.Add;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 15:37
 */
public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
//        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4));
        return a + b;
    }

    @Override
    public int minus(int a, int b) {
        return a - b;
    }
}
