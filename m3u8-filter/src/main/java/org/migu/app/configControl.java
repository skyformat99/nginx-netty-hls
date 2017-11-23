package org.migu.app;

import java.io.IOException;
import org.apache.log4j.Logger;
import org.migu.utils.PropertyUtil;




public class configControl {

	public static Logger log = Logger.getLogger(configControl.class);
	
	private static configControl _Instance;
	private PropertyUtil _config;
	private redisParam _redis_param = null;
	private String root_path = null;
	private int _port = 0;
	private boolean _debug = false;
	private int debug_time_current = 0;
	
	public static configControl Instance() {
	
		if (_Instance == null) {
		
			_Instance = new configControl();  
		}  
	
		return _Instance;  
	}  
	
	public int initialize() {
		
		_config = new PropertyUtil();
    	
    	try 
    	{
    		_config.configure("properties/config.properties");
		} 
    	catch (IOException e1) {
    		
			e1.printStackTrace();
		}
    	
    	_redis_param = new redisParam();
    	
    	root_path = _config.getProperty("root_path");
    	_port = Integer.parseInt(_config.getProperty("listen"));
    	
    	String cluster = _config.getProperty("redis.host");
    	_redis_param.host = cluster.split(" ");
    	//_redis_param.port = Integer.parseInt(_config.getProperty("redis.port"));
    	_redis_param.timeout = Integer.parseInt(_config.getProperty("redis.timeout"));
    	_redis_param.max_idle = Integer.parseInt(_config.getProperty("redis.maxIdle"));
    	_redis_param.max_total = Integer.parseInt(_config.getProperty("redis.maxTotal"));
    	_redis_param.max_wait = Integer.parseInt(_config.getProperty("redis.maxWaitMillis"));
    	_redis_param.key_alive_timeout = Integer.parseInt(_config.getProperty("redis.keyAliveTimeout"));
    	String tmp = _config.getProperty("debug.time.enable");
    	
    	_debug = tmp.equals("true") ? true : false;
    	
    	if (_debug) {
    		
    		log.warn("debug mode");
    		
    		tmp = _config.getProperty("debug.time.current");
    		
    		debug_time_current = Integer.parseInt(tmp);
    	}
    	else {
    		
    		log.warn("release mode");
    	}
    	
		return errorCode.ERROR_OK;
	}
	
	private configControl() {
	
	}
	
	public redisParam getRedisParam() {
		
		return _redis_param;
	}
	
	public String getRootPath() {
		
		return root_path;
	}
	
	public int getServicePort() {
		
		return _port;
	}
	
	public int getKeyAliveTimeout() {
		
		return _redis_param.key_alive_timeout;
	}
	
	public boolean isDebugMode() {
		
		return _debug;
	}
	
	public int getDebugTimeCurrent() {
		
		return debug_time_current;
	}
}
