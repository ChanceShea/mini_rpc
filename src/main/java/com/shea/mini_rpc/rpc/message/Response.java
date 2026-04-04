package com.shea.mini_rpc.rpc.message;

import lombok.Data;

import java.io.Serializable;

/**
 * RPC 响应类
 * <p>
 * 封装了 RPC 调用的响应信息，包括执行结果、状态码、错误信息等
 * 用于 Provider 向 Consumer 返回调用结果
 * </p>
 * @author Shea.
 * @version 1.0
 * @since 2026/3/22 21:00
 */
@Data
public class Response implements Serializable {


    /**
     * 成功状态码，值为 200
     */
    public static final Integer SUCCESS = 200;
    
    /**
     * 失败状态码，值为 500
     */
    public static final Integer FAIL = 500;

    /**
     * 方法执行结果
     * 当调用成功时，包含远程方法的返回值
     */
    private Object result;
    
    /**
     * 响应状态码
     * @see #SUCCESS
     * @see #FAIL
     */
    private int code;
    
    /**
     * 错误信息
     * 当调用失败时，包含详细的错误描述
     */
    private String errMsg;
    
    /**
     * 关联的请求 ID
     * 用于匹配对应的请求，确保响应能正确返回
     */
    private int requestId;


    /**
     * 创建失败响应
     * @param errMsg 错误信息
     * @param requestId 请求 ID
     * @return 失败响应对象
     */
    public static Response fail(String errMsg,int requestId) {
        Response r = new Response();
        r.setCode(FAIL);
        r.setErrMsg(errMsg);
        r.setRequestId(requestId);
        return r;
    }

    /**
     * 创建成功响应
     * @param result 方法执行结果
     * @param requestId 请求 ID
     * @return 成功响应对象
     */
    public static Response success(Object result,int requestId) {
        Response r = new Response();
        r.setCode(SUCCESS);
        r.setResult(result);
        r.setRequestId(requestId);
        return r;
    }
}
