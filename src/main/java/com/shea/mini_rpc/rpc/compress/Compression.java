package com.shea.mini_rpc.rpc.compress;

import lombok.Getter;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:46
 */
public interface Compression {

    byte[] compress(byte[] bytes);

    byte[] decompress(byte[] bytes);

    @Getter
    enum CompressionType {
        NONE(0),GZIP(1);

        private final int type;

        CompressionType(int type) {
            this.type = type;
        }
    }
}
