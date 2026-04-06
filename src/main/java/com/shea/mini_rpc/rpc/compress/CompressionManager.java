package com.shea.mini_rpc.rpc.compress;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:51
 */
@Slf4j
public class CompressionManager {

    private final Map<Integer, Compression> codeMap = new HashMap<>();
    private final Map<String, Compression> nameMap = new HashMap<>();


    public CompressionManager() {
        init();
    }

    public Compression getCompression(int code) {
        return codeMap.get(code);
    }

    public Compression getCompression(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<Compression> load = ServiceLoader.load(Compression.class);
        for (Compression compression : load) {
            if (compression.code() >= 16) {
                throw new IllegalArgumentException("压缩器的code不能超过15");
            }
            if (codeMap.put(compression.code(), compression) != null) {
                throw new IllegalArgumentException("压缩器的code不能重复");
            }
            if (nameMap.put(compression.name().toUpperCase(Locale.ROOT), compression) != null) {
                throw new IllegalArgumentException("压缩器的name不能重复");
            }
        }
    }
}
