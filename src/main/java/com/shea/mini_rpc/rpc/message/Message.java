package com.shea.mini_rpc.rpc.message;

import lombok.Data;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC 消息基类
 * <p>
 * 定义了 RPC 通信中的基本消息结构，包含魔数、消息类型和消息体
 * 用于标识和区分不同的消息类型（请求/响应）
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/22 20:13
 */
@Data
public class Message {

    /**
     * 魔数，用于标识协议的合法性
     * 固定值为 "Shea" 的字节数组
     */
    public static final byte[] MAGIC = "Shea".getBytes(StandardCharsets.UTF_8);

    /**
     * 魔数字节数组，用于验证消息是否符合协议格式
     */
    private byte[] magic;

    /**
     * 消息类型标识
     * @see MessageType
     */
    private byte messageType;

    private short version;

    /**
     * 前四位表示序列化算法
     * 后四位表示压缩算法
     * 同时最多可以支持16种序列化算法和压缩算法
     */
    private byte serializeAndCompress;

    /**
     * 消息体内容，承载实际的请求或响应数据
     */
    private byte[] body;

    /**
     * 消息类型枚举
     * <p>
     * 定义了 RPC 通信中的两种基本消息类型：请求和响应
     * </p>
     */
    @Getter
    public enum MessageType {
        /**
         * 请求消息类型，代码值为 1
         */
        REQUEST(1, Request.class),
        /**
         * 响应消息类型，代码值为 2
         */
        RESPONSE(2, Response.class),

        HEARTBEAT_REQUEST(3, HeartbeatRequest.class),
        HEARTBEAT_RESPONSE(4, HeartbeatResponse.class);

        /**
         * -- GETTER --
         *  获取消息类型代码
         *
         * @return 消息类型代码
         */
        private final byte code;
        private final Class<?> messageClass;
        private static final Map<Class<?>,MessageType> CLASS_CACHE = new HashMap<>();
        private static final Map<Byte,MessageType> CODE_CACHE = new HashMap<>();

        static {
            for (MessageType value : values()) {
                if (CLASS_CACHE.put(value.messageClass, value) != null) {
                    throw new IllegalArgumentException(value + "没有唯一对应消息类");
                }
                if (CODE_CACHE.put(value.code,value) != null) {
                    throw new IllegalArgumentException(value + "没有唯一对应消息类型代码");
                }
            }
        }
        
        /**
         * 构造函数
         * @param code 消息类型代码
         */
        MessageType(int code, Class<?> messageClass) {
            this.code = (byte)code;
            this.messageClass = messageClass;
        }

        public static MessageType ofClass(Class<?> messageClass) {
            return CLASS_CACHE.get(messageClass);
        }

        public static MessageType ofCode(byte code) {
            return CODE_CACHE.get(code);
        }
    }
}
