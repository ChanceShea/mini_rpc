package com.shea.mini_rpc.rpc.serialize;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;

import java.nio.charset.StandardCharsets;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:17
 */
public class JsonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) {
        return JSONObject.toJSONString(obj).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> objectClass) {
        String jsonString = new String(bytes, StandardCharsets.UTF_8);
        return JSONObject.parseObject(jsonString, objectClass, JSONReader.Feature.SupportClassForName);
    }
}
