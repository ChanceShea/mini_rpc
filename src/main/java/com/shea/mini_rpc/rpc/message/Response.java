package com.shea.mini_rpc.rpc.message;

import lombok.Data;

/**
 * @author : Shea.
 * @description: TODO
 * @since : 2026/3/22 21:00
 */
@Data
public class Response {


    public static final Integer SUCCESS = 200;
    public static final Integer FAIL = 500;

    private Object result;
    private int code;
    private String errMsg;
    private int requestId;


    public static Response fail(String errMsg,int requestId) {
        Response r = new Response();
        r.setCode(FAIL);
        r.setErrMsg(errMsg);
        r.setRequestId(requestId);
        return r;
    }

    public static Response success(Object result,int requestId) {
        Response r = new Response();
        r.setCode(SUCCESS);
        r.setResult(result);
        r.setRequestId(requestId);
        return r;
    }
}
