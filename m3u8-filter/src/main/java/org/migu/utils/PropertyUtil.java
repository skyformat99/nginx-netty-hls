package org.migu.utils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

//import org.junit.Test;


public class PropertyUtil {
	
	Properties _pps;
	
	public int configure(String file_path) throws IOException {
		
		_pps = new Properties();
		
		InputStream in = new BufferedInputStream(new FileInputStream(file_path));
		
		_pps.load(in);
		
		return 0;
	}

	public String getProperty(String key) {
      
		String value = _pps.getProperty(key);
		
		System.out.println(key + " = " + value);
		
		return value;
	}
  
	public void getAllProperties() {

        Enumeration<?> en = _pps.propertyNames();
      
		while(en.hasMoreElements()) {
			  
		    String strKey = (String) en.nextElement();
		    String strValue = _pps.getProperty(strKey);
		    System.out.println(strKey + "=" + strValue);
		}
	}
}