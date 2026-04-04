package com.shea.mini_rpc.rpc.version;

import lombok.Getter;

/**
 * @author : Shea.
 * @since : 2026/4/4 16:33
 */
@Getter
public enum Version {

    V1(0);

    private final int versionNum;

    Version(int code) {
        this.versionNum = code;
    }

}
