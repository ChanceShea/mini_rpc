package com.shea.mini_rpc.rpc.serialize;

import lombok.Getter;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:16
 */
public interface Serializer {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> objectClass);

    @Getter
    enum SerializerType {
        JSON(0), HESSIAN(1);

        private final int typeCode;

        SerializerType(int typeCode) {
            this.typeCode = typeCode;
        }
    }
}
