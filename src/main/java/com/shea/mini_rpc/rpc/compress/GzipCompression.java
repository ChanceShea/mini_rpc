package com.shea.mini_rpc.rpc.compress;

import com.shea.mini_rpc.rpc.exception.RpcException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:48
 */
public class GzipCompression implements Compression {
    @Override
    public byte[] compress(byte[] bytes) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            GZIPOutputStream gos = new GZIPOutputStream(bos);
            gos.write(bytes);
            gos.close();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RpcException("gzip压缩失败", e);
        }
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RpcException("gzip解压缩失败", e);
        }
    }
}
