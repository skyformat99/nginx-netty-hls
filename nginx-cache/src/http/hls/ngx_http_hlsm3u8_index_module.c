
#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include <ngx_md5.h>
#include <ngx_time.h>
#include <json-c/json.h>


typedef struct 
{
	ngx_flag_t authentification_flag;
	ngx_str_t check_sum_key_profile;
	u_char data[2048];
}ngx_http_hls_index_loc_conf_t;

static char *ngx_http_xxx_module_register(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);
static void *ngx_module_loc_conf_create(ngx_conf_t *cf);
static char *ngx_module_loc_conf_merge(ngx_conf_t *cf, void *parent, void *child);
static char *ngx_http_get_checksum_key_profile(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static ngx_command_t ngx_http_xxx_module_commands[] = 
{ 
	{ 
		ngx_string("hlsm3u8_index"),
		NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS,
		ngx_http_xxx_module_register,
		NGX_HTTP_LOC_CONF_OFFSET,
		0,
		NULL
	},

	{
		ngx_string("profile"),
		NGX_HTTP_LOC_CONF|NGX_CONF_TAKE1,
		ngx_http_get_checksum_key_profile,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_hls_index_loc_conf_t, check_sum_key_profile),
		NULL
	},

	{
		ngx_string("check_auth"),
		NGX_HTTP_LOC_CONF|NGX_CONF_FLAG,
		ngx_conf_set_flag_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_hls_index_loc_conf_t, authentification_flag),
		NULL
	},

	ngx_null_command
};

static ngx_http_module_t ngx_http_xxx_module_ctx = 
{
	NULL,                          	/* preconfiguration */
	NULL,                          	/* postconfiguration */

	NULL,				/* create main configuration */
	NULL,                          	/* init main configuration */

	NULL,                          	/* create server configuration */
	NULL,                          	/* merge server configuration */

	ngx_module_loc_conf_create,	/* create location configration */
	ngx_module_loc_conf_merge	/* merge location configuration */
};

ngx_module_t ngx_http_hlsm3u8_index_module = 
{
	NGX_MODULE_V1,
	&ngx_http_xxx_module_ctx,  			/* module context 	*/
	ngx_http_xxx_module_commands,             	/* module directives */
	NGX_HTTP_MODULE,                	      	/* module type 		*/
	NULL,                          			/* init master 		*/
	NULL,                          			/* init module 		*/
	NULL,			       			/* init process 		*/
	NULL,                          			/* init thread 		*/
	NULL,                          			/* exit thread 		*/
	NULL,                          			/* exit process 		*/
	NULL,                          			/* exit master 	*/
	NGX_MODULE_V1_PADDING
};

static char *ngx_http_get_checksum_key_profile(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_conf_set_str_slot(cf, cmd, conf);

	return NGX_CONF_OK;
}

static ngx_int_t hlsm3u8_index_subrequest_callback(ngx_http_request_t *request, void *data, ngx_int_t rc)
{
	printf("hlsm3u8_index_subrequest_callback\n");
	
	ngx_buf_t *p = &request->upstream->buffer;        	
	
	if(NULL == p)
	{
		printf("p == NULL\n");

		return 404;	
	}

    //printf("buff = %s\n",(char *)p->pos);       
	
	if(NULL == p->pos)
	{
		printf("----------------------------------------");
		printf("buff = %s\n",(char *)p->pos);
		//request->parent->headers_out.status = 404;
		//ngx_http_send_header(request->parent);

		return NGX_ERROR;
	}

	int buf_size = 0;

	request->parent->out = NULL;
	ngx_buf_t *pbuf = NULL;
	ngx_chain_t *cp = NULL;        

	struct json_object *json = json_tokener_parse((char *)p->pos);
	struct json_object *result_object;
	json_object_object_get_ex(json,"First_m3u8_tx", &result_object);
  
	const char *tmp = json_object_get_string(result_object);

	buf_size = strlen(tmp);

	pbuf = ngx_pcalloc(request->parent->pool,sizeof(ngx_buf_t));
	if(pbuf==NULL)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}
	cp = ngx_pcalloc(request->parent->pool,sizeof(ngx_chain_t));
	if(cp==NULL)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	char *p_tmp_out = ngx_pcalloc(request->parent->pool,buf_size);
	if(p_tmp_out==NULL)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	memcpy(p_tmp_out,tmp,buf_size);       

	pbuf->memory = 1;
	pbuf->start = (u_char *)p_tmp_out;
	pbuf->end = ((u_char *)p_tmp_out) + buf_size;
	pbuf->pos = pbuf->start;
	pbuf->last = pbuf->end;       

	cp->buf = pbuf;
	cp->next = NULL;
	cp->buf->last_in_chain = 1;
	cp->buf->flush = 1;

	request->parent->headers_out.status = NGX_HTTP_OK;
	ngx_str_set(&request->parent->headers_out.content_type,"application/octet-stream");
	request->parent->root_tested = !request->parent->error_page;
	request->parent->allow_ranges = 1;
	request->parent->headers_out.content_length_n = buf_size;

	ngx_int_t result;
	result = ngx_http_send_header(request->parent);

	if(result == NGX_ERROR || result > NGX_OK || request->parent->header_only)
	{
		 printf("send header error\n");
		 return result;
	}

	result = ngx_http_output_filter(request->parent,cp);

	return result;
}

static ngx_int_t ngx_http_hlsm3u8_index_module_handler(ngx_http_request_t *request)
{
	printf("-----------------------  ngx_http_hlsm3u8_index_module_handler\n");
	
	if (!(request->method & (NGX_HTTP_GET|NGX_HTTP_HEAD)))
	{
		return NGX_HTTP_NOT_ALLOWED;
	}

	if (request->uri.data[request->uri.len - 1] == '/') 
	{
		return NGX_DECLINED;
	}

	ngx_http_hls_index_loc_conf_t *local_conf = ngx_http_get_module_loc_conf(request, ngx_http_hlsm3u8_index_module);
	
	if(NULL == local_conf)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	request->out = NULL;

	ngx_http_post_subrequest_t *psr = ngx_palloc(request->pool, sizeof(ngx_http_post_subrequest_t));
	
	if(NULL == psr)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	u_char *data = ngx_palloc(request->pool, 2048);
	
	if(NULL == data)
	{
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	psr->handler = hlsm3u8_index_subrequest_callback;
	psr->data = data;
	
	ngx_str_t query = ngx_string("/query");
	
	printf("query = %s\n", query.data);

	ngx_str_t argus;
	argus.data = ngx_palloc(request->pool, 1024);
	u_char *p = ngx_snprintf(argus.data, 1024, "%V?%V", &request->uri, &request->args);
	argus.len = p - argus.data;

	printf("argus = %s\n", argus.data);

	ngx_http_request_t *sr;
	ngx_int_t rc = ngx_http_subrequest(request, &query, &argus, &sr, psr, NGX_HTTP_SUBREQUEST_IN_MEMORY);

	if(rc != NGX_OK) 
		return NGX_ERROR;

	return NGX_OK;
}

static char *ngx_http_xxx_module_register(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_http_core_loc_conf_t *clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	
	//ngx_http_hls_index_loc_conf_t *lc = conf;

	clcf->handler = ngx_http_hlsm3u8_index_module_handler;
	
	//lc = ngx_http_conf_get_module_loc_conf(cf, ngx_http_secondary_rank_request_module);

	return NGX_CONF_OK;
}

static void *ngx_module_loc_conf_create(ngx_conf_t *cf) 
{
	ngx_http_hls_index_loc_conf_t *conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_hls_index_loc_conf_t)); 
	
	if (NULL == conf)
	{
		return NGX_CONF_ERROR; 
	}

	conf->check_sum_key_profile.data = NULL;
	conf->check_sum_key_profile.len = 0;

	conf->authentification_flag = 0;

	return conf;
}

static char *ngx_module_loc_conf_merge(ngx_conf_t *cf, void *parent, void *child) 
{
	ngx_http_hls_index_loc_conf_t *prev = parent;
	ngx_http_hls_index_loc_conf_t *conf = child;

	ngx_conf_merge_str_value(conf->check_sum_key_profile, prev->check_sum_key_profile, "");
	//ngx_conf_merge_value(conf->authentification_flag, prev->authentification_flag, 0);

	return NGX_CONF_OK; 
}