package org.migu.app;

import java.io.IOException;
import org.apache.log4j.Logger;
import org.migu.dao.redis.redisAdaptorControl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class applicationControl {

	public static Logger log = Logger.getLogger(applicationControl.class);
	
	private static applicationControl _Instance;
	
	public static applicationControl Instance() {
	
		if (_Instance == null) {
		
			_Instance = new applicationControl();
		}  
	
		return _Instance;
	}  
	
	public int initialize() {

		
		return errorCode.ERROR_OK;
	}
	
	private applicationControl() {
		
	}

	public int putSession(String key, sessionDef session, int timeout) {

		ObjectMapper mapper = new ObjectMapper();
		
		String value = null;
		
		try 
		{
			value = mapper.writeValueAsString(session);
			
			System.out.println("value: " + value);
			
			redisAdaptorControl.Instance().cluster_set(key, value, timeout);

		} 
		catch (JsonProcessingException e) 
		{
			e.printStackTrace();
		}
		
		
		return errorCode.ERROR_OK;
	}
	
	public int remove_session(String key) {

		redisAdaptorControl.Instance().cluster_delete_key(key);

		return errorCode.ERROR_OK;
	}
	
	public int putSessionIfExist(String key, sessionDef session,int timeout) {

		int ret = 0;
		
		ObjectMapper mapper = new ObjectMapper();
		
		String value = null;
		
		try 
		{
			value = mapper.writeValueAsString(session);
			
			System.out.println("value: " + value);
			
			ret = redisAdaptorControl.Instance().setIfNotExist(key, value, timeout);

		} 
		catch (JsonProcessingException e) 
		{
			e.printStackTrace();
		}
		
		return ret;
	}
	
	public sessionDef get_session(String key) {
		
		String get_value = redisAdaptorControl.Instance().cluster_get(key);

		if (null == get_value) {
			
			return null;
		}
		
		ObjectMapper mapper = new ObjectMapper();
		
		sessionDef reflact = null;
		
		try {
			
			reflact = mapper.readValue(get_value, sessionDef.class);
		} 
		catch (JsonParseException e) {
			e.printStackTrace();
		} 
		catch (JsonMappingException e) {
			e.printStackTrace();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("get value: " + get_value);
		
		return reflact;
	}
}
