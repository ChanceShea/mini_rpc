package com.shea.mini_rpc.rpc.handler;

import com.shea.mini_rpc.rpc.message.HeartbeatRequest;
import com.shea.mini_rpc.rpc.message.HeartbeatResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * @author : Shea.
 * @since : 2026/4/5 18:44
 */
public class HeartbeatHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HeartbeatRequest request) {
            ctx.writeAndFlush(new HeartbeatResponse(request.getRequestTime()));
            return;
        }
        if (msg instanceof HeartbeatResponse response) {
            long duration = System.currentTimeMillis() - response.getRequestTime();
            System.out.println("接收到了一个心跳响应,延迟：" + duration + "ms");
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            IdleState state = idleStateEvent.state();
            if (state == IdleState.READER_IDLE) {
                ctx.channel().close();
            }else if (state == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(new HeartbeatRequest());
            }
            ctx.fireUserEventTriggered(evt);
        }
    }
}
