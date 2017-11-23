package org.migu.netty;

import org.apache.log4j.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;


public class httpServer {

	public static Logger log = Logger.getLogger(httpServerInboundHandler.class);

	public void listen(int port) throws Exception {
        
		log.warn("httpServer listen: " + port);
		
		EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try 
        {
            ServerBootstrap b = new ServerBootstrap();
            
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                    	
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                        	ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new HttpResponseEncoder());
                            ch.pipeline().addLast(new HttpRequestDecoder());
                            ch.pipeline().addLast(new httpServerInboundHandler());

                        }
                    });
            
            //b.childOption(ChannelOption.SO_KEEPALIVE, true);
            		
            b.option(ChannelOption.SO_BACKLOG, 1024);
            
            ChannelFuture future = b.bind(port).sync();
            
            future.channel().closeFuture().sync();  
        } 
        finally 
        {
        	log.warn("netty finally");
        	
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
