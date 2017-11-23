package org.migu.m3u8Filter;



import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.migu.app.applicationControl;
import org.migu.app.configControl;
import org.migu.dao.redis.redisAdaptorControl;
import org.migu.netty.httpServer;
import org.migu.netty.httpServerInboundHandler;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4JLoggerFactory;


public class App 
{
	public static Logger log = Logger.getLogger(httpServerInboundHandler.class);
	
    public static void main( String[] args )
    {
    	PropertyConfigurator.configure("properties/log4j.properties");
    	InternalLoggerFactory.setDefaultFactory(Log4JLoggerFactory.INSTANCE);
    	
    	configControl.Instance().initialize();
    	applicationControl.Instance().initialize();
    	redisAdaptorControl.Instance().cluster_init(configControl.Instance().getRedisParam());
    	
    	//redis_adaptor redis = new redis_adaptor();
    	//redis.setJedis();
    	
    	
    	
    	
        httpServer server = new httpServer();
        
        try
        {
			server.listen(configControl.Instance().getServicePort());
		} 
        catch (Exception e)
        {
			e.printStackTrace();
		}
    }
}
