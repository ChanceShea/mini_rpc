package com.shea.mini_rpc.rpc.spi;

/**
 * @author : Shea.
 * @since : 2026/4/6 14:22
 */
public interface Extension {

    String name();

    default int code() {
        return -1;
    }
}
