package com.shea.mini_rpc.rpc.codec;

import com.shea.mini_rpc.rpc.compress.Compression;
import com.shea.mini_rpc.rpc.compress.CompressionManager;
import com.shea.mini_rpc.rpc.message.Message;
import com.shea.mini_rpc.rpc.serialize.Serializer;
import com.shea.mini_rpc.rpc.serialize.SerializerManager;
import com.shea.mini_rpc.rpc.version.Version;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:09
 */
@Slf4j
public class SheaEncoder extends MessageToByteEncoder<Object> {

    public static final AttributeKey<String> SERIALIZE_KEY = AttributeKey.valueOf("serialize");
    public static final AttributeKey<SerializerManager> SERIALIZER_MANAGER_KEY = AttributeKey.valueOf("serializerManagerKey");
    public static final AttributeKey<String> COMPRESS_KEY = AttributeKey.valueOf("compressKey");
    public static final AttributeKey<CompressionManager> COMPRESS_MANAGER_KEY = AttributeKey.valueOf("compressManagerKey");

    private volatile Serializer defaultSerializer;
    private volatile Compression defaultCompression;
    private volatile byte defaultSerializerAndCompress;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf byteBuf) throws Exception {
        initIfNecessary(ctx);
        Message.MessageType messageType = Message.MessageType.ofClass(msg.getClass());
        if (messageType == null) {
            log.warn("{} 不支持序列化，无法发送", msg.getClass().getName());
            return;
        }
        byte[] magic = Message.MAGIC;
        byte messageCode = messageType.getCode();
        Version current = Version.V1;
        if (defaultSerializer == null) {
            throw new IllegalArgumentException("不存在默认的序列化器");
        }
        if (defaultCompression == null) {
            throw new IllegalArgumentException("不存在默认的压缩器");
        }
        byte[] body = defaultSerializer.serialize(msg);
        byte finalSac = defaultSerializerAndCompress;
        if (body.length < 256) {
            defaultSerializerAndCompress &= (byte)0b11110000;
        } else {
            body = defaultCompression.compress(body);
        }
        int length = magic.length + Byte.BYTES * 2 + Short.BYTES + body.length;
        byteBuf.writeInt(length);
        byteBuf.writeBytes(magic);
        byteBuf.writeByte(messageCode);
        byteBuf.writeShort(current.getVersionNum());
        // 这里要写出serializeAndCompress
        byteBuf.writeByte(finalSac);
        byteBuf.writeBytes(body);
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if (defaultSerializer != null) {
            return;
        }
        String serializeName = ctx.channel().attr(SERIALIZE_KEY).get();
        SerializerManager serializerManager = ctx.channel().attr(SERIALIZER_MANAGER_KEY).get();
        defaultSerializer = serializerManager.getSerializer(serializeName);

        String compressName = ctx.channel().attr(COMPRESS_KEY).get();
        CompressionManager compressManager = ctx.channel().attr(COMPRESS_MANAGER_KEY).get();
        defaultCompression = compressManager.getCompression(compressName);

        if (defaultSerializer == null) {
            throw new IllegalArgumentException("不存在默认的序列化器");
        }

        if (defaultCompression == null) {
            throw new IllegalArgumentException("不存在默认的压缩器");
        }

        defaultSerializerAndCompress = (byte) ((defaultSerializer.code() << 4) | defaultCompression.code());
    }
}
