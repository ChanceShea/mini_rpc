package com.shea.mini_rpc.rpc.serialize;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:31
 */
public class SerializerManager {

    private final Map<Integer,Serializer> codeMap = new HashMap<>();
    private final Map<String,Serializer> nameMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    public Serializer getSerializer(int typeCode) {
        return codeMap.get(typeCode);
    }

    public Serializer getSerializer(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<Serializer> load = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : load) {
            if (serializer.code() >= 16) {
                throw new IllegalArgumentException("序列化器code不能超过16");
            }
            if (codeMap.put(serializer.code(), serializer) != null) {
                throw new IllegalArgumentException("序列化器code不能重复");
            }
            if (nameMap.put(serializer.name().toUpperCase(Locale.ROOT), serializer) != null) {
                throw new IllegalArgumentException("序列化器name不能重复");
            }
        }
    }
}
