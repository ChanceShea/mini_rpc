package com.shea.mini_rpc.rpc.compress;

import com.shea.mini_rpc.rpc.spi.Extension;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:46
 */
public interface Compression extends Extension {

    byte[] compress(byte[] bytes);

    byte[] decompress(byte[] bytes);

}
