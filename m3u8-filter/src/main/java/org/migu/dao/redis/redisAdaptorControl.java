package org.migu.dao.redis;


import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.migu.app.errorCode;
import org.migu.app.redisParam;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;




public class redisAdaptorControl {

	public static Logger log = Logger.getLogger(redisAdaptorControl.class);
	
	private static redisAdaptorControl _Instance;
	
	private JedisPool jedisPool = null;
	
	private JedisCluster redis_cluster = null;
	
	public static redisAdaptorControl Instance() {
		
		if (_Instance == null) {
		
			_Instance = new redisAdaptorControl();
		}
	
		return _Instance;
	}
	
	public int initialize(redisParam param) {

		//JedisPoolConfig config = new JedisPoolConfig();
		
		//config.setMaxTotal(param.max_total);
		//config.setMaxIdle(param.max_idle);
		//config.setMaxWaitMillis(param.max_wait);
		//config.setTestOnBorrow(param.on_borrow);

		//jedisPool = new JedisPool(config, param.host, param.port, param.timeout);  
		
		return errorCode.ERROR_OK;
	}
	
	public int cluster_init(redisParam param) {
		
		JedisPoolConfig poolConfig = new JedisPoolConfig();  

	    poolConfig.setMaxTotal(param.max_total);  

	    poolConfig.setMaxIdle(param.max_idle);  

	    poolConfig.setMaxWaitMillis(param.max_wait);
	    
	    Set<HostAndPort> nodes = new LinkedHashSet<HostAndPort>();
	    
	    int len = param.host.length;
	    
	    for(int i = 0; i < len; i++) {
	    	
	    	String tmp = param.host[i];
	    	String [] host = tmp.split(":");
	    	
	    	log.warn("host: " + host[0] + ", port: " + host[1]);
	    	
	    	nodes.add(new HostAndPort(host[0].trim(), Integer.parseInt(host[1].trim())));
	    }
	    
	    redis_cluster = new JedisCluster(nodes, poolConfig);
		
		return errorCode.ERROR_OK;
	}
	
	public String cluster_set(String key, String value, int time_out){

		log.warn("set key: " + key + ", value: " + value + "time_out: " + time_out);
		
        return redis_cluster.setex(key, time_out, value);
    }
	
	public String cluster_get(String key){
        
		String value = redis_cluster.get(key);
		
		log.warn("get key: " + key + ", value: " + value);
		
        return value;
    }
	
	public int cluster_delete_key(String key){
        
		redis_cluster.del(key);
		
        return errorCode.ERROR_OK;
    }
	
	private redisAdaptorControl() {
		
	}
	
	private synchronized Jedis getJedis() {
		
        return jedisPool.getResource();
    }
	
	public static void release(final Jedis jedis) {
		
		if (jedis != null) {
			
			jedis.close();
		}
	}
	
	public String hmset(String key, Map<String, String> map) {
		
        Jedis jedis = getJedis();
        
        String s = jedis.hmset(key, map);
        
        release(jedis);
        
        return s;
    }
	
	public int setIfNotExist(String key, String value, int timeOut){
		
        Jedis jedis = getJedis();
        
        if(0 != jedis.setnx(key, value)) {
        	
            jedis.expire(key, timeOut);
            
            release(jedis);
            
            return errorCode.ERROR_OK;
        }
        
        release(jedis);
        
        return errorCode.ERROR_SESSION_EXIST;
    }
	
	public int set(String key, String value, int timeOut){
		
        Jedis jedis = getJedis();
        
        jedis.set(key, value);
        
        jedis.expire(key, timeOut);

        release(jedis);
        
        return errorCode.ERROR_OK;
    }
	
	public String get(String key){
		
        Jedis jedis = getJedis();
        
        String tmp = jedis.get(key);

        release(jedis);
        
        return tmp;
    }
	
	public long deleteKey(String key) {
		 
		Jedis jedis = getJedis();
		
		long s = jedis.del(key);
		
		release(jedis);
		
		return s;
	}
	
	public boolean exists(String key) {
		
        Jedis jedis = getJedis();
        
        boolean exists = jedis.exists(key);
        
        release(jedis);
        
        return exists;
    }
	
	public void expire(String key, int seconds) {
		
        Jedis jedis = getJedis();
        
        jedis.expire(key, seconds);
        
        release(jedis);
    }
	
	public void persist(String key) {
		
	        Jedis jedis = getJedis();
	        
	        jedis.persist(key);
	        
	        release(jedis);
	}
	

	
}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/*
	 private Jedis jedis;

	    //@Before
	    public void setJedis() {
	    	
	    	Properties pps = new Properties();
  	      try {
				pps.load(new FileInputStream("conf/redis.properties"));
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
  	       Enumeration enum1 = pps.propertyNames();//得到配置文件的名字
  	      while(enum1.hasMoreElements()) {
  	           String strKey = (String) enum1.nextElement();
  	           String strValue = pps.getProperty(strKey);
  	           System.out.println(strKey + "=" + strValue);
  	        }
	        //连接redis服务器(在这里是连接本地的)
	        jedis = new Jedis("192.168.207.130", 6379);
	        //权限认证
	        //jedis.auth("chenhaoxiang");
	        System.out.println("连接服务成功");
	    }


	    //@Test
	    public void testString() {
	        //添加数据
	        jedis.set("name", "chx"); //key为name放入value值为chx
	        System.out.println("拼接前:" + jedis.get("name"));//读取key为name的值

	        //向key为name的值后面加上数据 ---拼接
	        jedis.append("name", " is my name;");
	        System.out.println("拼接后:" + jedis.get("name"));

	        //删除某个键值对
	        jedis.del("name");
	        System.out.println("删除后:" + jedis.get("name"));

	        //s设置多个键值对
	        jedis.mset("name", "chenhaoxiang", "age", "20", "email", "chxpostbox@outlook.com");
	        jedis.incr("age");//用于将键的整数值递增1。如果键不存在，则在执行操作之前将其设置为0。 如果键包含错误类型的值或包含无法表示为整数的字符串，则会返回错误。此操作限于64位有符号整数。
	        System.out.println(jedis.get("name") + " " + jedis.get("age") + " " + jedis.get("email"));
	    }

	    //@Test
	    public void testMap() {
	        //添加数据
	        Map<String, String> map = new HashMap<String, String>();
	        map.put("name", "chx");
	        map.put("age", "100");
	        map.put("email", "***@outlook.com");
	        jedis.hmset("user", map);
	        //取出user中的name，结果是一个泛型的List
	        //第一个参数是存入redis中map对象的key，后面跟的是放入map中的对象的key，后面的key是可变参数
	        List<String> list = jedis.hmget("user", "name", "age", "email");
	        System.out.println(list);

	        //删除map中的某个键值
	        jedis.hdel("user", "age");
	        System.out.println("age:" + jedis.hmget("user", "age")); //因为删除了，所以返回的是null
	        System.out.println("user的键中存放的值的个数:" + jedis.hlen("user")); //返回key为user的键中存放的值的个数2
	        System.out.println("是否存在key为user的记录:" + jedis.exists("user"));//是否存在key为user的记录 返回true
	        System.out.println("user对象中的所有key:" + jedis.hkeys("user"));//返回user对象中的所有key
	        System.out.println("user对象中的所有value:" + jedis.hvals("user"));//返回map对象中的所有value

	        //拿到key，再通过迭代器得到值
	        Iterator<String> iterator = jedis.hkeys("user").iterator();
	        while (iterator.hasNext()) {
	            String key = iterator.next();
	            System.out.println(key + ":" + jedis.hmget("user", key));
	        }
	        jedis.del("user");
	        System.out.println("删除后是否存在key为user的记录:" + jedis.exists("user"));//是否存在key为user的记录

	    }


	    //@Test
	    public void testList(){
	        //移除javaFramwork所所有内容
	        jedis.del("javaFramwork");
	        //存放数据
	        jedis.lpush("javaFramework","spring");
	        jedis.lpush("javaFramework","springMVC");
	        jedis.lpush("javaFramework","mybatis");
	        //取出所有数据,jedis.lrange是按范围取出
	        //第一个是key，第二个是起始位置，第三个是结束位置
	        System.out.println("长度:"+jedis.llen("javaFramework"));
	        //jedis.llen获取长度，-1表示取得所有
	        System.out.println("javaFramework:"+jedis.lrange("javaFramework",0,-1));

	        jedis.del("javaFramework");
	        System.out.println("删除后长度:"+jedis.llen("javaFramework"));
	        System.out.println(jedis.lrange("javaFramework",0,-1));
	    }


	    //@Test
	    public void testSet(){
	        //添加
	        jedis.sadd("user","chenhaoxiang");
	        jedis.sadd("user","hu");
	        jedis.sadd("user","chen");
	        jedis.sadd("user","xiyu");
	        jedis.sadd("user","chx");
	        jedis.sadd("user","are");
	        //移除user集合中的元素are
	        jedis.srem("user","are");
	        System.out.println("user中的value:"+jedis.smembers("user"));//获取所有加入user的value
	        System.out.println("chx是否是user中的元素:"+jedis.sismember("user","chx"));//判断chx是否是user集合中的元素
	        System.out.println("集合中的一个随机元素:"+jedis.srandmember("user"));//返回集合中的一个随机元素
	        System.out.println("user中元素的个数:"+jedis.scard("user"));
	    }


	    //@Test
	    public void test(){
	        jedis.del("number");//先删除数据，再进行测试
	        jedis.rpush("number","4");//将一个或多个值插入到列表的尾部(最右边)
	        jedis.rpush("number","5");
	        jedis.rpush("number","3");

	        jedis.lpush("number","9");//将一个或多个值插入到列表头部
	        jedis.lpush("number","1");
	        jedis.lpush("number","2");
	        System.out.println(jedis.lrange("number",0,jedis.llen("number")));
	        System.out.println("排序:"+jedis.sort("number"));
	        System.out.println(jedis.lrange("number",0,-1));//不改变原来的排序
	        jedis.del("number");//测试完删除数据
	    }
	    */

