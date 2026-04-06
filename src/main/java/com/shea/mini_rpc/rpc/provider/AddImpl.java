package com.shea.mini_rpc.rpc.provider;

import com.shea.mini_rpc.rpc.api.Add;
import com.shea.mini_rpc.rpc.api.User;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 15:37
 */
public class AddImpl implements Add {
    @Override
    public Integer add(int a, int b) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1500));
        return a + b;
    }

    @Override
    public Integer minus(int a, int b) {
        return a - b;
    }

    @Override
    public User merge(User user1, User user2) {
        int sumAge = user1.getAge() + user2.getAge();
        String  sumName = user1.getName() + user2.getName();
        User user = new User();
        user.setAge(sumAge);
        user.setName(sumName);
        return user;
    }
}
