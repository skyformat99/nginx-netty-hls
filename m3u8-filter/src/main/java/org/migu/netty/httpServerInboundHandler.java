package org.migu.netty;


import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.migu.app.commonResp;
import org.migu.app.errorCode;
import org.migu.app.m3u8Capture;
import org.migu.app.m3u8PlayseekInfo;
import org.migu.app.m3u8ReviewInfo;
import org.migu.app.serviceType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.netty.handler.codec.http.HttpHeaderNames.*;


public class httpServerInboundHandler extends ChannelInboundHandlerAdapter {

	public static Logger log = Logger.getLogger(httpServerInboundHandler.class);
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
	    
		if (msg instanceof HttpRequest) {
	        
			HttpRequest request = (HttpRequest) msg;
			
	    	if (request.method() == HttpMethod.GET) {
	    		
	    		int ret = errorCode.ERROR_OK;
	    		
	    		m3u8Capture cp = new m3u8Capture();
	    		
	    		Map<String, String> request_params = new HashMap<String, String>();
	    		
	    		int play_mode = cp.get_play_mode(request, request_params);
	    		
	    		if (serviceType.PLAY_TYPE_SEEK == play_mode) {
	    			
	    			m3u8PlayseekInfo m3u8_info = new m3u8PlayseekInfo();
	    			
	    			ret = cp.playSeekHandler(request, request_params, m3u8_info);
	    			
	    			response_playseek(ctx, request, ret, m3u8_info);
	    		}
	    		else if (serviceType.PLAY_TYPE_REVIEW == play_mode) {
	    			
	    			m3u8ReviewInfo m3u8_info = new m3u8ReviewInfo();
	    			
	    			ret = cp.playBackHandler(request, request_params, m3u8_info);
	    			
	    			response_review(ctx, request, ret, m3u8_info);
	    		}
	    		else {
	    			
	    			commonResp common_resp = new commonResp();
		    		
		    		common_resp.error_code = errorCode.ERROR_POST_NOT_RECOMMENDED;
		    		common_resp.error_text = "http request mode was not expected";
		    		
		    		ObjectMapper mapper = new ObjectMapper();
	    			String resp = mapper.writeValueAsString(common_resp);
	    			
		    		channel_response(FORBIDDEN, ctx, request, resp);
		    		
		    		common_resp = null;
	    		}
	    	}
	    	else {
	    		
	    		commonResp common_resp = new commonResp();
	    		
	    		common_resp.error_code = errorCode.ERROR_POST_NOT_RECOMMENDED;
	    		common_resp.error_text = "http request mode was not expected";
	    		
	    		ObjectMapper mapper = new ObjectMapper();
    			String resp = mapper.writeValueAsString(common_resp);
    			
	    		channel_response(FORBIDDEN, ctx, request, resp);
	    		
	    		common_resp = null;
	    	}
	    }
	}
	
	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
	    
		ctx.flush();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

		String err = cause.getMessage();
		
		log.error(err);
		
		/*
		String resp = null;
		
		commonResp common_resp = new commonResp();
		
		common_resp.error_code = errorCode.ERROR_SERVER_INTERNAL;
		common_resp.error_text = cause.getMessage();
		
		ObjectMapper mapper = new ObjectMapper();

		try {
			resp = mapper.writeValueAsString(common_resp);
		} 
		catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
		
		try {
			channel_response(INTERNAL_SERVER_ERROR, ctx, null, resp);
		} 
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		common_resp = null;
	    
		ctx.close();
		*/
	}
	
	private int channel_response(HttpResponseStatus status, ChannelHandlerContext ctx, HttpRequest req, String resp) throws UnsupportedEncodingException {
		
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.wrappedBuffer(resp.getBytes("UTF-8")));
        
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        
        if (null != req) {
        	
        	 final boolean keepAlive = HttpUtil.isKeepAlive(req);
             
             HttpUtil.setKeepAlive(response, keepAlive);
             
             //log.warn("is keepalive: " + keepAlive);
             
             final ChannelFuture future = ctx.writeAndFlush(response);
             
             if (!keepAlive) {
             
             	future.addListener(ChannelFutureListener.CLOSE);
             }
        }
        else {
        	
        	final ChannelFuture future = ctx.writeAndFlush(response);
        	
        	future.addListener(ChannelFutureListener.CLOSE);
        }
       
		return 0;
	}
	
	private int response_playseek(ChannelHandlerContext ctx, HttpRequest request, int status, m3u8PlayseekInfo m3u8_info) throws JsonProcessingException, UnsupportedEncodingException {
		
		commonResp common_resp = new commonResp();
		
		common_resp.error_code = status;
		
		if (errorCode.ERROR_OK == common_resp.error_code) {
			
			log.warn("m3u8 playseek content: " + m3u8_info.m3u8_content);
			
			common_resp.error_text = "OK";
			common_resp.resp_info = m3u8_info;
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);
			
			channel_response(OK, ctx, request, resp);
			
			m3u8_info = null;
			common_resp = null;
		}
		else if (errorCode.ERROR_PARAM_DISMATCH == common_resp.error_code) {
			
			common_resp.error_text = "paramter dismatch";
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);
			
			channel_response(FORBIDDEN, ctx, request, resp);
			
			common_resp = null;
		}
		else if (errorCode.ERROR_M3U8_FILE_NOT_FOUND == common_resp.error_code) {
			
			common_resp.error_text = "m3u8 not found";
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);

			channel_response(NOT_FOUND, ctx, request, resp);
			
			common_resp = null;
		}
		
		return errorCode.ERROR_OK;
	}
	
	private int response_review(ChannelHandlerContext ctx, HttpRequest request, int status, m3u8ReviewInfo m3u8_info) throws JsonProcessingException, UnsupportedEncodingException {
		
		commonResp common_resp = new commonResp();
		
		common_resp.error_code = status;
		
		if (errorCode.ERROR_OK == common_resp.error_code) {
			
			log.warn("m3u8 review content: " + m3u8_info.play_mode);
			
			common_resp.error_text = "OK";
			common_resp.resp_info = m3u8_info;
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);
			
			channel_response(OK, ctx, request, resp);
			
			m3u8_info = null;
			common_resp = null;
		}
		else if (errorCode.ERROR_PARAM_DISMATCH == common_resp.error_code) {
			
			common_resp.error_text = "paramter dismatch";
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);
			
			channel_response(FORBIDDEN, ctx, request, resp);
			
			common_resp = null;
		}
		else if (errorCode.ERROR_M3U8_FILE_NOT_FOUND == common_resp.error_code) {
			
			common_resp.error_text = "m3u8 not found";
			
			ObjectMapper mapper = new ObjectMapper();
			String resp = mapper.writeValueAsString(common_resp);

			channel_response(NOT_FOUND, ctx, request, resp);
			
			common_resp = null;
		}
		
		return errorCode.ERROR_OK;
	}


}
