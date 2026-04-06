package com.shea.mini_rpc.rpc.serialize;

import com.shea.mini_rpc.rpc.spi.Extension;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:16
 */
public interface Serializer extends Extension {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> objectClass);

}
