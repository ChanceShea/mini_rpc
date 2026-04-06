package com.shea.mini_rpc.rpc.serialize;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:20
 */
@Slf4j
public class HessianSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream oos = new ByteArrayOutputStream()) {
            Hessian2Output out = new Hessian2Output(oos);
            out.writeObject(obj);
            out.flush();
            return oos.toByteArray();
        } catch (Exception e) {
            log.error("Hessian 序列化失败 {}",obj.getClass().getName(), e);
            return new byte[0];
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> objectClass) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);) {
            Hessian2Input input = new Hessian2Input(bis);
            return (T) input.readObject();
        } catch (Exception e) {
            log.error("Hessian 反序列化失败 {}",objectClass.getName(), e);
            return null;
        }
    }

    @Override
    public String name() {
        return "hessian";
    }

    @Override
    public int code() {
        return 1;
    }
}
