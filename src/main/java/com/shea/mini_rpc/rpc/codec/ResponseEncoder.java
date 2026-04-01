package com.shea.mini_rpc.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.message.Response;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/23 14:26
 */
public class ResponseEncoder extends MessageToByteEncoder<Response> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Response response, ByteBuf byteBuf) throws Exception {
        // length
        // logic
        // type
        // body
        byte[] logic = Message.MAGIC;
        byte messageType = Message.MessageType.RESPONSE.getCode();
        byte[] body = serializeResponse(response);
        int length = logic.length + Byte.BYTES + body.length;
        byteBuf.writeInt(length);
        byteBuf.writeBytes(logic);
        byteBuf.writeByte(messageType);
        byteBuf.writeBytes(body);
    }

    private byte[] serializeResponse(Response response) {
        return JSONObject.toJSONString(response).getBytes(StandardCharsets.UTF_8);
    }
}
