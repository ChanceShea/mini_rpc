package com.shea.mini_rpc.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.message.Request;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/22 21:01
 */
public class RequestEncoder extends MessageToByteEncoder<Request> {
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

    private byte[] serializeRequest(Request request) {
        return JSONObject.toJSONString(request).getBytes(StandardCharsets.UTF_8);
    }
}
