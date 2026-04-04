package com.shea.mini_rpc.rpc.compress;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:51
 */
public class CompressionManager {

    private final Map<Integer, Compression> compressionMap = new HashMap<>();
    
    public CompressionManager() {
        init();
    }

    public Compression getCompression(int type) {
        return compressionMap.get(type);
    }

    private void init() {
        compressionMap.put(Compression.CompressionType.NONE.getType(), new NoneCompression());
        compressionMap.put(Compression.CompressionType.GZIP.getType(), new GzipCompression());
    }
}
