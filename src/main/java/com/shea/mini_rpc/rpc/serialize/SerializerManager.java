package com.shea.mini_rpc.rpc.serialize;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:31
 */
public class SerializerManager {

    private final Map<Integer,Serializer> serializerMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    public Serializer getSerializer(int typeCode) {
        return serializerMap.get(typeCode);
    }

    private void init() {
        serializerMap.put(Serializer.SerializerType.JSON.getTypeCode(), new JsonSerializer());
        serializerMap.put(Serializer.SerializerType.HESSIAN.getTypeCode(), new HessianSerializer());
    }
}
