package com.shea.mini_rpc.rpc.compress;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:47
 */
public class NoneCompression implements Compression{
    @Override
    public byte[] compress(byte[] bytes) {
        return bytes;
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        return bytes;
    }
}
