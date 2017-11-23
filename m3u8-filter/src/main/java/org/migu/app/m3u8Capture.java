package org.migu.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.log4j.Logger;
//import org.junit.Test;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

public class m3u8Capture {

	public static Logger log = Logger.getLogger(m3u8Capture.class); 
	
	private final int _session_timeout = configControl.Instance().getKeyAliveTimeout();
	
	public int get_play_mode(HttpRequest request, Map<String, String> request_params) {
		
		parser(request, request_params);
		
		if (request_params.containsKey("playseek")) {
			
			log.warn("service type: playseek");
			
			return serviceType.PLAY_TYPE_SEEK;
		}
		else if (request_params.containsKey("playbackbegin") && request_params.containsKey("playbackend")) {
			
			log.warn("service type: playback");
			
			return serviceType.PLAY_TYPE_REVIEW;
		}
		
		return serviceType.PLAY_TYPE_NONE;
	}
	
	private boolean parser(HttpRequest request, Map<String, String> params) {
		
		QueryStringDecoder param_decoder = new QueryStringDecoder(request.uri());
		
		Map<String, List<String>> uriAttributes = param_decoder.parameters();
		
		for (Entry<String, List<String>> attr : uriAttributes.entrySet()) {
			
            for (String attrVal : attr.getValue()) {
            	
            	params.put(attr.getKey(), attrVal);
            }
		}
		
		return true;
	}
	
	public int playSeekHandler(HttpRequest request, Map<String, String> request_params, m3u8PlayseekInfo m3u8_info) throws URISyntaxException {
		
		int ret = errorCode.ERROR_OK;
		String timestamp = null;
		String mtv_session = null;
		String path = null;
		String query = null;
		String seek_time = null;
		
		seek_time = request_params.get("playseek");

		if (seek_time == null) {
			
			log.warn("seek time null");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("seek time: " + seek_time);
		
		mtv_session = request_params.get("mtv_session");
		
		if (mtv_session == null) {
			
			log.warn("mtv session null");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("mtv session: " + mtv_session);
		
		timestamp = request_params.get("timestamp");
		
		if (timestamp == null) {
			
			log.warn("timestamp null");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("timestamp: " + timestamp);
		
		URI uri = new URI(request.uri());
		
		path = uri.getPath();
		query = uri.getQuery();

		log.warn("query = " + query);
		log.warn("path: " + path);
		
		String file_m3u8 = configControl.Instance().getRootPath() + path;
		String file_m3u8_index = null;
		String file_path = null;
		
		file_m3u8_index = file_m3u8 + ".index";
		
		File fd_index = new File(file_m3u8_index);
		File fd = new File(file_m3u8);
		
		if (fd_index.exists()) {
		
			log.warn("fatching ts from .m3u8.index");
			
			file_path = file_m3u8_index;
		}
		else if (fd.exists()){
			
			log.warn("fatching ts from .m3u8");
			
			file_path = file_m3u8;
		}
		else {
			
			log.error(".m3u8 or .m3u8.index not found");
			
			return errorCode.ERROR_M3U8_FILE_NOT_FOUND;
		}

		m3u8_info.play_mode = serviceType.PLAY_TYPE_SEEK;
		
		try {
			
			String key = "mgcloud_streaming:playseek_session:" + mtv_session;
			
			sessionDef session = get_session(key);

			log.warn("mtv session key: " + key);
			
			if (session == null) {
				
				ret = m3u8_playseek_filter(file_path, seek_time, key, timestamp, query, m3u8_info);
			}
			else {
				
				if (check_timestamp_change(timestamp, session)) {
					
					remove_session(key);
					
					session = null;
					
					ret = m3u8_playseek_filter(file_path, seek_time, key, timestamp, query, m3u8_info);
				}
				else {
					
					ret = m3u8_playseek_filter_session(file_path, seek_time, key, session, query, m3u8_info);
				}
			}
		} 
		catch (IOException e) {

			e.printStackTrace();
		}

		return ret;
	}
	
	public int playBackHandler(HttpRequest request, Map<String, String> request_params, m3u8ReviewInfo m3u8_info) throws URISyntaxException, IOException {
		
		int ret = errorCode.ERROR_OK;
		
		String timestamp = null;
		String path = null;
		String query = null;
		String playbackbegin = null;
		String playbackend = null;
		
		playbackbegin = request_params.get("playbackbegin");

		if (playbackbegin == null) {
			
			log.warn("playbackbegin not exist");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("playbackbegin: " + playbackbegin);
		
		playbackend = request_params.get("playbackend");

		if (playbackend == null) {
			
			log.warn("playbackend not exist");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("playbackend: " + playbackend);
		
		timestamp = request_params.get("timestamp");
		
		if (timestamp == null) {
			
			log.warn("timestamp not exist");
			
			return errorCode.ERROR_PARAM_DISMATCH;
		}
		
		log.warn("timestamp: " + timestamp);
		
		URI uri = new URI(request.uri());
		
		path = uri.getPath();
		query = uri.getQuery();

		log.warn("query = " + query);
		log.warn("path: " + path);
		
		String file_m3u8 = configControl.Instance().getRootPath() + path;
		String file_m3u8_index = null;
		String file_path = null;
		
		file_m3u8_index = file_m3u8 + ".index";
		
		File fd_index = new File(file_m3u8_index);
		File fd = new File(file_m3u8);
		
		if (fd_index.exists()) {
		
			log.warn("fatching ts from .m3u8.index");
			
			file_path = file_m3u8_index;
		}
		else if (fd.exists()){
			
			log.warn("fatching ts from .m3u8");
			
			file_path = file_m3u8;
		}
		else {
			
			log.error(".m3u8 or .m3u8.index not found");
			
			return errorCode.ERROR_M3U8_FILE_NOT_FOUND;
		}

		m3u8_info.play_mode = serviceType.PLAY_TYPE_REVIEW;

		ret = m3u8_playback_filter(file_path, playbackbegin, playbackend, timestamp, query, m3u8_info);

		return ret;
	}
	
	private int get_ts_time(String ts_str) {
		
		String a[] = ts_str.split("-");
		String b[] = a[1].split("\\.");
		
		return Integer.parseInt(b[0]);
	}
	
	private int transform_absolute_time(String relative_time) {

		long currentTime = 0;
		
		if (configControl.Instance().isDebugMode()) {
			
			currentTime = configControl.Instance().getDebugTimeCurrent();
		}
		else {
			currentTime = System.currentTimeMillis()/1000;
		}
		//long currentTime = System.currentTimeMillis()/1000;
		//long currentTime = 1508153152;
		
		log.warn("current time: " + currentTime);
		
		long absolute_time = currentTime - transform2TimeOffset(relative_time);

		log.warn("absolute time: " + absolute_time);
    	
		return (int)absolute_time;
	}
	

	public long transform2UnixTime(String dateStr, String format) {

		long unix_time = 0;
		
		SimpleDateFormat sdf = new SimpleDateFormat(format);

		try {
			
			unix_time = sdf.parse(dateStr).getTime() / 1000;
		} 
		catch (ParseException e) {

			e.printStackTrace();
		}
 
		return unix_time;
    }
	
	//@Test
	private int transform2TimeOffset(String date) {
		
		int sum = 0;
		
		char a[] = date.toCharArray();

		if (a.length == 6) {
			
			sum += ((a[0] - 48) * 10 + a[1] - 48) * 3600;
			sum += ((a[2] - 48) *10 + a[3] - 48) * 60;
			sum += (a[4] - 48) *10 + a[5] - 48;
		
			log.warn("offset time :" + sum);
		}
		else {
			
			throw new IllegalArgumentException("seek time buffer len must 6");
		}
		
		return sum;
	}

	private int m3u8_playseek_filter_session(String file_path, String seek_time, String key, sessionDef session, String query, m3u8PlayseekInfo m3u8_info) throws IOException {

		log.warn("has session");
		
		int ts_time = 0;
		String s = null;
		String ext_info = null;

		StringBuffer sb = new StringBuffer();
		
		int index = -1;
		
		BufferedReader br = new BufferedReader(new FileReader(file_path));
		
		while((s = br.readLine()) != null) {
			
			if(s.contains("#EXTINF:")) {
				
				ext_info = s;
			}
			else if (s.contains(".ts")) {
				
				ts_time = get_ts_time(s);
				
				if (ts_time == session.last_time) {
					
					index++;
					
					continue;
				}
				
				if (ts_time - session.last_time > 0) {
					
					if(index == 0) {
						
						sb.append(++session.sequence);
						
						session.last_time = ts_time;
						
						update_session(key, session, _session_timeout);
					}
					
					if (index < 3) {
						
						sb.append("\r\n");
						sb.append(ext_info);
						sb.append("\r\n");
						sb.append(s);
						if (query != null) {
							sb.append("?" + query);
						}
					}
					
					index++;
				}
			}
			else if (s.contains("#EXTM3U")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-VERSION")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-TARGETDURATION")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-MEDIA-SEQUENCE")) {
				
				sb.append("#EXT-X-MEDIA-SEQUENCE:");
			}
		}
		
		if (index == 0) {
			
			sb.append(session.sequence);
		}
		
		br.close();
		
		m3u8_info.m3u8_content = sb.toString();
		
		sb = null;

		return errorCode.ERROR_OK;
	}
	
	private int m3u8_playback_filter(String file_path, String start, String end, String timestamp, String query, m3u8ReviewInfo m3u8_info) throws IOException {

		String s = null;
		String ext_info = null;
		int ts_time = 0;
		
		StringBuffer m3u8_title = new StringBuffer();
		
		long start_time = transform2UnixTime(start, "yyyyMMddHHmmss");
		long end_time = transform2UnixTime(end, "yyyyMMddHHmmss");
		
		log.warn("playback start time: " + start_time);
		log.warn("playback end time: " + end_time);

		List<tsInfo> ts_list = new ArrayList<tsInfo>();

		BufferedReader br = new BufferedReader(new FileReader(file_path));
		
		while((s = br.readLine()) != null) {
			
			if(s.contains("#EXTINF:")) {
				
				ext_info = s;
			}
			else if (s.contains(".ts")) {
				
				ts_time = get_ts_time(s);
				
				if ((start_time < ts_time) && (ts_time < end_time)) {
					
					tsInfo ts_info = new tsInfo();
					
					ts_info.ext = ext_info;
					ts_info.ts = s;
					
					ts_list.add(ts_info);
				}
			}
			else if (s.contains("#EXTM3U")) {
				
				m3u8_title.append(s);
				
				m3u8_title.append("\r\n");
			}
			else if (s.contains("#EXT-X-VERSION")) {
				
				m3u8_title.append(s);
				
				m3u8_title.append("\r\n");
			}
			else if (s.contains("#EXT-X-TARGETDURATION")) {
				
				m3u8_title.append(s);
				
				m3u8_title.append("\r\n");
			}
			else if (s.contains("#EXT-X-MEDIA-SEQUENCE")) {
				
				m3u8_title.append(s);
				
				m3u8_title.append("\r\n");
			}
		}
		
		br.close();

		m3u8_info.m3u8_title = m3u8_title.toString();
		log.warn("m3u8 title = " + m3u8_info.m3u8_title);
		m3u8_info.ts_list = ts_list;
		m3u8_info.argus = query;
		log.warn("argus = " + m3u8_info.argus);
		
		m3u8_title = null;
		
		return errorCode.ERROR_OK;
	}
	
	private int m3u8_playseek_filter(String file_path, String relative_time, String key, String timestamp, String query, m3u8PlayseekInfo m3u8_info) throws IOException {

		log.warn("no session");
		
		String s = null;
		String ext_info = null;
		int ts_time = 0;
		
		StringBuffer sb = new StringBuffer();
		
		int absolute_time = transform_absolute_time(relative_time);
		
		log.warn("----------------" + absolute_time);	
		
		sessionDef session = new sessionDef();
		
		int index = 0;

		BufferedReader br = new BufferedReader(new FileReader(file_path));
		
		while((s = br.readLine()) != null) {
			
			if(s.contains("#EXTINF:")) {
				
				ext_info = s;
			}
			else if (s.contains(".ts")) {
				
				ts_time = get_ts_time(s);
				
				if (ts_time - absolute_time > 0) {
					
					if(index == 0) {
						
						session.timestamp = timestamp;
						session.last_time = ts_time;
						session.sequence = 0;
						
						set_session(key, session, _session_timeout);
					}
					
					if (index < 3) {
						
						sb.append("\r\n");
						sb.append(ext_info);
						sb.append("\r\n");
						sb.append(s);
						if (query != null) {
							sb.append("?" + query);
						}
						
					}
					
					index++;
				}
			}
			else if (s.contains("#EXTM3U")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-VERSION")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-TARGETDURATION")) {
				
				sb.append(s);
				
				sb.append("\r\n");
			}
			else if (s.contains("#EXT-X-MEDIA-SEQUENCE")) {
				
				sb.append("#EXT-X-MEDIA-SEQUENCE:0");
			}
		}
		
		br.close();
		
		m3u8_info.m3u8_content = sb.toString();
		
		sb = null;
		
		return errorCode.ERROR_OK;
	}
	
	private boolean check_timestamp_change(String timestamp, sessionDef session) {
		
		if (timestamp.equals(session.timestamp)) {
			
			return false;
		}
		
		log.warn("timestamp change, time seek calculate again");
		
		return true;
	}
	
	private int update_session(String key, sessionDef session, int timeout) {
		
		return applicationControl.Instance().putSession(key, session, timeout);
	}

	private sessionDef get_session(String key) {
		
		return applicationControl.Instance().get_session(key);
	}
	
	private int set_session(String key, sessionDef session, int timeout) {
		
		return applicationControl.Instance().putSession(key, session, timeout);
	}
	
	private int remove_session(String key) {
		
		return applicationControl.Instance().remove_session(key);
	}

}
