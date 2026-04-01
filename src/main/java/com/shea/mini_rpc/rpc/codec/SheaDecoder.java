package com.shea.mini_rpc.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.message.Request;
import com.shea.mini_rpc.rpc.message.Response;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/22 20:22
 */
public class SheaDecoder extends LengthFieldBasedFrameDecoder {

    public SheaDecoder() {
        /**
         * 第一个参数maxFrameLength表示最大帧长度为1MB
         * 第二个参数lengthFieldOffset表示长度字段在整个数据帧中的偏移量
         * 第三个参数lengthFieldLength表示长度字段占多少个字节
         * 第四个参数lengthAdjustment表示长度修正值，修正长度字段的值，仅包含帧的真实长度
         * 第五个参数initialBytesToStrip表示解码时从哪开始，4字节表示解码后的数据只包含消息体
         */
        super(1024*1024,0,Integer.BYTES,0,Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        /**
         * 因为netty中的ByteBuf不会出现循环引用的问题,所以可以直接使用引用计数法进行jvm的gc
         * 通常情况下,ByteBuf是在pipeline的头节点被创建,经过了一系列的handler,然后到尾节点被回收
         * 但是因为decode方法使用了这个ByteBuf,所以要对其进行一个release方法将其引用计数减一,进行gc回收
         */
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            byte[] logic = new byte[Message.MAGIC.length];
            frame.readBytes(logic);
            if (!Arrays.equals(logic, Message.MAGIC)) {
                throw new IllegalArgumentException("魔数不对，协议有问题");
            }
            byte messageType = frame.readByte();
            byte[] body = new byte[frame.readableBytes()];
            frame.readBytes(body);
            if(Objects.equals(Message.MessageType.REQUEST.getCode(),messageType)){
                return deserializeRequest(body);
            }
            if(Objects.equals(Message.MessageType.RESPONSE.getCode(),messageType)){
                return deserializeResponse(body);
            }
            throw new IllegalArgumentException("不支持的消息类型"+messageType);
        } finally {
            frame.release();
        }
    }

    private Response deserializeResponse(byte[] body) {
        return JSONObject.parseObject(new String(body),Response.class);
    }

    private Request deserializeRequest(byte[] body) {
        return JSONObject.parseObject(new String(body),Request.class, JSONReader.Feature.SupportClassForName);
    }
}
