package com.shea.mini_rpc.rpc.message;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : Shea.
 * @since : 2026/4/5 18:45
 */
@Data
public class HeartbeatRequest implements Serializable {

    private final long requestTime = System.currentTimeMillis();

}
