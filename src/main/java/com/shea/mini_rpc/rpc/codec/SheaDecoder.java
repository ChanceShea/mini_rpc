package com.shea.mini_rpc.rpc.codec;

import com.shea.mini_rpc.rpc.compress.Compression;
import com.shea.mini_rpc.rpc.compress.CompressionManager;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.serialize.Serializer;
import com.shea.mini_rpc.rpc.serialize.SerializerManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Arrays;

import static com.shea.mini_rpc.rpc.codec.SheaEncoder.COMPRESS_MANAGER_KEY;
import static com.shea.mini_rpc.rpc.codec.SheaEncoder.SERIALIZER_MANAGER_KEY;

/**
 * RPC 协议解码器
 * <p>
 * 基于 LengthFieldBasedFrameDecoder 实现自定义协议解码
 * 支持解析请求消息和响应消息，通过魔数验证协议合法性
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/22 20:22
 */
public class SheaDecoder extends LengthFieldBasedFrameDecoder {

    private volatile SerializerManager serializerManager;
    private volatile CompressionManager compressionManager;

    /**
     * 构造函数，配置 LengthFieldBasedFrameDecoder 参数
     */
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

    /**
     * 解码消息
     * <p>
     * 1. 从 ByteBuf 中读取数据帧
     * 2. 验证魔数确保协议合法性
     * 3. 根据消息类型解析为 Request 或 Response
     * 4. 释放 ByteBuf 引用计数
     * </p>
     * @param ctx Channel 处理上下文
     * @param in Netty 字节缓冲区
     * @return 解码后的对象 (Request 或 Response)
     * @throws Exception 解码异常
     */
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        initIfNecessary(ctx);

        /**
         * 因为 netty 中的 ByteBuf 不会出现循环引用的问题，所以可以直接使用引用计数法进行 jvm 的 gc
         * 通常情况下，ByteBuf 是在 pipeline 的头节点被创建，经过了一系列的 handler，然后到尾节点被回收
         * 但是因为 decode 方法使用了这个 ByteBuf，所以要对其进行一个 release 方法将其引用计数减一，进行 gc 回收
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
            short version = frame.readShort();
            byte serializeAndCompress = frame.readByte();
            Compression compression = this.compressionManager.getCompression(serializeAndCompress & 0b00001111);
            if (compression == null) {
                throw new IllegalArgumentException("没有支持的压缩器");
            }
            Serializer serializer = this.serializerManager.getSerializer((serializeAndCompress & 0b11110000) >>> 4);
            if (serializer == null) {
                throw new IllegalArgumentException("没有支持的反序列化器");
            }
            byte[] body = new byte[frame.readableBytes()];
            frame.readBytes(body);
            body = compression.decompress(body);
            Message.MessageType type = Message.MessageType.ofCode(messageType);
            if (type == null) {
                throw new IllegalArgumentException("不支持的消息类型" + messageType);
            }
            return serializer.deserialize(body, type.getMessageClass());
        } finally {
            frame.release();
        }
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if (serializerManager != null) {
            return;
        }
        serializerManager = ctx.channel().attr(SERIALIZER_MANAGER_KEY).get();
        compressionManager = ctx.channel().attr(COMPRESS_MANAGER_KEY).get();
    }
}
