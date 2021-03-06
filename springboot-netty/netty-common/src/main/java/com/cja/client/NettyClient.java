package com.cja.client;

import com.cja.bean.RequsetBean;
import com.cja.bean.ResponseBean;
import com.cja.bean.ResultSync;
import com.cja.rpc.NettyRPCUtil;
import com.cja.util.MyDecoder;
import com.cja.util.MyEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClient {

    private ChannelFuture channelFuture;

    private SimpleClientHandler simpleClientHandler = new SimpleClientHandler();

    private Integer port;

    private String ip;

    public NettyClient(){}


    public NettyClient(String ip, Integer port){
        this.ip = ip;
        this.port = port;
    }

    public void start() {
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            /**
             *EventLoop的组
             */
            b.group(worker);
            /**
             * 用于构造socketchannel工厂
             */
            b.channel(NioSocketChannel.class);
            /**设置选项
             * 参数：Socket的标准参数（key，value），可自行百度
             保持呼吸，不要断气！
             * */
            b.option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_BACKLOG, 1024)// 配置TCP参数
                    .option(ChannelOption.SO_BACKLOG, 1024) // 设置tcp缓冲区
                    .option(ChannelOption.SO_SNDBUF, 32 * 1024) // 设置发送缓冲大小
                    .option(ChannelOption.SO_RCVBUF, 32 * 1024); // 这是接收缓冲大小

            /**
             * 自定义客户端Handle（客户端在这里搞事情）
             */
            simpleClientHandler = new SimpleClientHandler();
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                            new MyDecoder(ResponseBean.class),
                            new MyEncoder(RequsetBean.class),
                            simpleClientHandler,new ReconnectClientHandler(NettyClient.this));
                }
            });
            /** 开启客户端监听*/

            channelFuture = b.connect(new InetSocketAddress(
                    ip, port));

            ChannelFutureListener channelFutureListener = new ChannelFutureListener() {
                public void operationComplete(ChannelFuture f) throws Exception {
                    if (f.isSuccess()) {
                        log.info("重新连接服务器成功");
                    } else {
                        log.info("重新连接服务器失败----尝试重新连接");
                        start();
                    }
                }
            };
            channelFuture.addListener(channelFutureListener);

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            worker.shutdownGracefully();
        }
    }

    public ResponseBean sendSync(RequsetBean requsetBean) {

        if (!isDone()) {
            throw new RuntimeException("----远程调用失败----");
        }

        String resourceId = UUID.randomUUID().toString();
        try {
            //设置返回值
            CountDownLatch countDownLatch = new CountDownLatch(1);
            ResultSync resultSync = new ResultSync().toBuilder().countDownLatch(countDownLatch).build();
            simpleClientHandler.addResultSync(resourceId, resultSync);

            //发送数据
            requsetBean.setResourceId(resourceId);
            channelFuture.channel().writeAndFlush(requsetBean);
            //超时时间
            countDownLatch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getResult(resourceId);
    }

    private boolean isDone() {
        long beginTime = System.currentTimeMillis();
        long overTime = 10 * 1000;
        long nowTime = System.currentTimeMillis();
        while ((nowTime - beginTime) < overTime) {
            if (channelFuture != null) {
                return true;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            nowTime = System.currentTimeMillis();
        }
        return false;
    }

    private ResponseBean getResult(String resourceId) {
        ResponseBean result = simpleClientHandler.getResponseBean(resourceId);
        if (result == null) {
            return new ResponseBean().toBuilder().msg("系统异常，请重试！").code("500").build();
        }
        return result;
    }

}
