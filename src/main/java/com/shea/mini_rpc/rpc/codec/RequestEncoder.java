package com.shea.mini_rpc.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.message.Request;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * RPC 请求编码器
 * <p>
 * 将 Request 对象编码为 ByteBuf，用于网络传输
 * 编码格式：长度 (4 字节) + 魔数 + 消息类型 (1 字节) + 消息体
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/22 21:01
 */
public class RequestEncoder extends MessageToByteEncoder<Request> {
    /**
     * 编码 Request 对象为 ByteBuf
     * <p>
     * 编码格式：
     * - 长度 (4 字节): 整个消息的总长度
     * - 魔数：用于验证协议合法性
     * - 消息类型 (1 字节): 标识为请求消息
     * - 消息体：序列化后的请求数据
     * </p>
     * @param ctx Channel 处理上下文
     * @param request 待编码的请求对象
     * @param byteBuf Netty 字节缓冲区
     * @throws Exception 编码异常
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Request request, ByteBuf byteBuf) throws Exception {
        // length
        // logic
        // type
        // body
        byte[] logic = Message.MAGIC;
        byte messageType = Message.MessageType.REQUEST.getCode();
        byte[] body = serializeRequest(request);
        int length = logic.length + Byte.BYTES + body.length;
        byteBuf.writeInt(length);
        byteBuf.writeBytes(logic);
        byteBuf.writeByte(messageType);
        byteBuf.writeBytes(body);
    }

    /**
     * 将 Request 对象序列化为 JSON 字节数组
     * @param request 请求对象
     * @return 序列化后的字节数组
     */
    private byte[] serializeRequest(Request request) {
        return JSONObject.toJSONString(request).getBytes(StandardCharsets.UTF_8);
    }
}
