package com.shea.mini_rpc.rpc.exception;

public class RpcException extends RuntimeException {

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean retry() {
        return true;
    }
}
