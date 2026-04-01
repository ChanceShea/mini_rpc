package com.shea.mini_rpc.rpc.retry;

import com.shea.mini_rpc.rpc.message.Response;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/31 19:41
 */
public interface RetryPolicy {

    Response retry(RetryContext context) throws Exception;
}
