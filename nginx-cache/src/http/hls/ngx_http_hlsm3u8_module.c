/*
* Copyright (C) migu 
*/

#include <assert.h>
#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include <ngx_md5.h>
#include <ngx_time.h>
#include <json-c/json.h>

#define URI_NAME_MAXLEN 2048

typedef struct 
{
	ngx_str_t ts_path;
	ngx_flag_t authentication;
	ngx_str_t check_sum_key;
} ngx_http_hlsm3u8_loc_conf_t;

typedef struct{
	//ngx_uint_t mode;
	//ngx_str_t m3u8_content;
	ngx_buf_t *data;
}ngx_http_hlsm3u8_ctx_t;

static void *ngx_http_hlsm3u8_create_loc_conf(ngx_conf_t *cf);
static char *ngx_http_hlsm3u8_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child);
static ngx_int_t hlsm3u8_seek_subrequest_post_handler(ngx_http_request_t *request,void *data,ngx_int_t rc);
static ngx_int_t ngx_http_hlsm3u8_handler(ngx_http_request_t *r);
static char *ngx_http_hlsm3u8_register(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);
static char *ngx_http_get_tspath(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static ngx_http_module_t  ngx_http_hlsm3u8_module_ctx = 
{
	NULL,                          		/* preconfiguration */
	NULL,    				/* postconfiguration */
	NULL,					/* create main configuration */
	NULL,                          		/* init main configuration */
	NULL,                          		/* create server configuration */
	NULL,                          		/* merge server configuration */
	ngx_http_hlsm3u8_create_loc_conf,	/* create location configration */
	ngx_http_hlsm3u8_merge_loc_conf		/* merge location configuration */
};

static char *ngx_http_get_checksum_key_profile(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_conf_set_str_slot(cf, cmd, conf);

	return NGX_CONF_OK;
}

static ngx_command_t ngx_http_hlsm3u8_commands[] = 
{ 
	{ 
		ngx_string("hlsm3u8"),
		NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS,
		ngx_http_hlsm3u8_register,
		NGX_HTTP_LOC_CONF_OFFSET,
		0,
		NULL
	},

	{
		ngx_string("tspath"),
		NGX_HTTP_LOC_CONF|NGX_CONF_TAKE1,
		ngx_http_get_tspath,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_hlsm3u8_loc_conf_t, ts_path),
		NULL
	},

	{ 
		ngx_string("auth"),
		NGX_HTTP_LOC_CONF|NGX_CONF_FLAG,
		ngx_conf_set_flag_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_hlsm3u8_loc_conf_t, authentication),
		NULL
	},

	{
		ngx_string("encryptkey"),
		NGX_HTTP_LOC_CONF|NGX_CONF_TAKE1,
		ngx_http_get_checksum_key_profile,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_hlsm3u8_loc_conf_t, check_sum_key),
		NULL
	},


	ngx_null_command
}; 

ngx_module_t  ngx_http_hlsm3u8_module = 
{
	NGX_MODULE_V1,
	&ngx_http_hlsm3u8_module_ctx,  /* module context 	*/
	ngx_http_hlsm3u8_commands,     /* module directives */
	NGX_HTTP_MODULE,               /* module type 		*/
	NULL,                          /* init master 		*/
	NULL,                          /* init module 		*/
	NULL,						   /* init process 		*/
	NULL,                          /* init thread 		*/
	NULL,                          /* exit thread 		*/
	NULL,                          /* exit process 		*/
	NULL,                          /* exit master 		*/
	NGX_MODULE_V1_PADDING
};


static char *ngx_http_hlsm3u8_register(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_http_core_loc_conf_t  *clcf;
	//ngx_http_hlsm3u8_loc_conf_t * lc = conf;
	clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	//lc = ngx_http_conf_get_module_loc_conf(cf, ngx_http_hlsm3u8_module);

	clcf->handler = ngx_http_hlsm3u8_handler;

	return NGX_CONF_OK;
}

static char *ngx_http_get_tspath(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_conf_set_str_slot(cf, cmd, conf);

	return NGX_CONF_OK;	
}

static void *ngx_http_hlsm3u8_create_loc_conf(ngx_conf_t *cf) 
{ 
	ngx_http_hlsm3u8_loc_conf_t *conf; 
	conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_hlsm3u8_loc_conf_t)); 
	
	if (conf == NULL)
		return NGX_CONF_ERROR; 

	conf->authentication = NGX_CONF_UNSET;

	conf->ts_path.data = NULL;
	conf->ts_path.len = 0;

	return conf; 
}

static char *ngx_http_hlsm3u8_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child) 
{ 
	ngx_http_hlsm3u8_loc_conf_t *prev = parent;
	ngx_http_hlsm3u8_loc_conf_t *conf = child;
	ngx_conf_merge_value(conf->authentication, prev->authentication, 0);
	ngx_conf_merge_str_value(conf->ts_path, prev->ts_path, "");

	if(conf->authentication == 1)
	{
	}

	return NGX_CONF_OK; 
}

#if 0 
static void Traversal(ngx_chain_t *chain) {

	ngx_chain_t *tmp = chain;
        while(tmp != NULL)
        {
              char a[1024] = {0};
              memcpy(a, tmp->buf->start, tmp->buf->end - tmp->buf->start);
              printf("%s", a);
              tmp = tmp->next;
        }
}
#endif

static void hlsm3u8_pr_post_handler(ngx_http_request_t *r)
{
	ngx_int_t result;
	int head_size = 0;

	int content_len = 0;
	int play_mode = 0;
	
	ngx_buf_t *tmp_buf = NULL;
	ngx_chain_t *out_head = NULL;
	ngx_chain_t *out_end = NULL;
	ngx_chain_t *cp = NULL;

	ngx_http_hlsm3u8_ctx_t *ctx = ngx_http_get_module_ctx(r, ngx_http_hlsm3u8_module);

	if(NULL == ctx) {
		
		goto UNEXCEPTED_ERROR; 
	}
	
	ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "hlsm3u8_pr_post_handler - m3u8 filter http status: %d, data: %s", (int)r->headers_out.status, ctx->data->pos);

	r->out = NULL;
	
	if (NGX_HTTP_OK == r->headers_out.status) {

		struct json_object *root = NULL;
		struct json_object *play_mode_object = NULL;
		struct json_object *m3u8_info_object = NULL;	

		root = json_tokener_parse((char *)ctx->data->pos);
	
		json_object_object_get_ex(root, "resp_info", &m3u8_info_object);

		json_object_object_get_ex(m3u8_info_object, "play_mode", &play_mode_object);
		
		play_mode = json_object_get_int(play_mode_object);

		ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "hlsm3u8_pr_post_handler - play mode: %d", play_mode);
		
		switch(play_mode)
		{
		case 1://playseek
		{
        	struct json_object *m3u8_content_object = NULL;
        	const char *m3u8_content = NULL;

			json_object_object_get_ex(m3u8_info_object, "m3u8_content", &m3u8_content_object);

			m3u8_content = json_object_get_string(m3u8_content_object);
			
			content_len = json_object_get_string_len(m3u8_content_object);

			ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "hlsm3u8_pr_post_handler - m3u8_content: %s", m3u8_content);
			
			cp = ngx_pcalloc(r->pool, sizeof(ngx_chain_t));
        
			if(NULL == cp)
        	{            		
				goto UNEXCEPTED_ERROR; 
        	}
        
			tmp_buf = ngx_create_temp_buf(r->pool, content_len);
		
			if (NULL == tmp_buf) {
				
				goto UNEXCEPTED_ERROR; 
			}
	
			ngx_memcpy(tmp_buf->pos, m3u8_content, content_len);
	
			tmp_buf->last = tmp_buf->pos + content_len;
			tmp_buf->last_buf = 1;

			head_size += content_len;

    		cp->buf = tmp_buf;
    		cp->next = NULL;
    		cp->buf->last_in_chain = 1;
    		cp->buf->flush = 1;
		
			out_head = out_end = cp;

			json_object_put(root);

		}
		break;

		case 2://review
		{
			int ii = 0;
			
			struct json_object *m3u8_title_object = NULL;
			struct json_object *resp_info_object = NULL;
			struct json_object *argus_object = NULL;
			struct json_object *ts_list_object = NULL;
			struct json_object *item_object = NULL;

			int title_len = 0;
			//int argus_len = 0;
			int list_size = 0;
			char tspre[32] = {0};
            int offset = 0;

			const char *m3u8_title = NULL;
			const char *argument = NULL;
			const char *ext = NULL;
			const char *ts = NULL;
			struct json_object *ext_object = NULL;
			struct json_object *ts_object = NULL;

			root = json_tokener_parse((char*)ctx->data->pos);

			json_object_object_get_ex(root, "resp_info", &resp_info_object);

			json_object_object_get_ex(resp_info_object, "m3u8_title", &m3u8_title_object);
			
			m3u8_title = json_object_get_string(m3u8_title_object);
			
			title_len = json_object_get_string_len(m3u8_title_object);

			ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "hlsm3u8_pr_post_handler - m3u8 title: %s", m3u8_title);

			json_object_object_get_ex(resp_info_object, "argus", &argus_object);

			argument = json_object_get_string(argus_object);
	
			//argus_len = json_object_get_string_len(argus_object);

			//ngx_log_error(NGX_LOG_INFO, r->pool->log, 0, "hlsm3u8_pr_post_handler - argus: %s", argument);

			json_object_object_get_ex(resp_info_object, "ts_list", &ts_list_object);

			list_size = json_object_array_length(ts_list_object);			

			//ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "hlsm3u8_pr_post_handler - list size = %d", list_size);

			tmp_buf = ngx_create_temp_buf(r->pool, title_len);
		
			if (NULL == tmp_buf) {

				goto UNEXCEPTED_ERROR; 
			}
	
            ngx_memcpy(tmp_buf->pos, m3u8_title, title_len);

			tmp_buf->pos = tmp_buf->start;
			tmp_buf->last = tmp_buf->end;

			head_size += title_len;

			cp = ngx_pcalloc(r->pool, sizeof(ngx_chain_t));

            if(NULL == cp)
            {
				goto UNEXCEPTED_ERROR;
			}

        	cp->buf = tmp_buf;
        	cp->next = NULL;
				
			out_head = out_end = cp;
			
			for(; ii < list_size; ii++) {

				item_object = json_object_array_get_idx(ts_list_object, ii);

				json_object_object_get_ex(item_object, "ext", &ext_object);

				ext = json_object_get_string(ext_object);

				json_object_object_get_ex(item_object, "ts", &ts_object);

                ts = json_object_get_string(ts_object);

				tmp_buf = ngx_create_temp_buf(r->pool, 2048);

                if (NULL == tmp_buf) {

                	goto UNEXCEPTED_ERROR;
                }

				if(0 == ii)
				{
					sprintf(tspre, "%s\r\n", ext);
				}
				else
				{	
					sprintf(tspre, "\r\n%s\r\n", ext);
				}
                                
				offset = snprintf((char*)tmp_buf->pos, 2048, "%s%s?%s", tspre, ts, argument);

				tmp_buf->memory = 1;
                tmp_buf->last = tmp_buf->pos + offset;
				tmp_buf->start = tmp_buf->pos;
				tmp_buf->end = tmp_buf->last;

                head_size += offset;

				cp = ngx_pcalloc(r->pool, sizeof(ngx_chain_t));

                if(NULL == cp)
                {
					goto UNEXCEPTED_ERROR;
				}

				cp->buf = tmp_buf;
				cp->next = NULL;
				
				out_end->next = cp;
				out_end = cp;
			}

			json_object_put(root);

			const char *end_list = "\r\n#EXT-X-ENDLIST";
        	int end_list_len = strlen(end_list);

        	tmp_buf = ngx_create_temp_buf(r->pool, end_list_len);

        	if (NULL == tmp_buf) {

                	goto UNEXCEPTED_ERROR;
        	}

        	ngx_memcpy(tmp_buf->pos, end_list, end_list_len);

        	tmp_buf->last = tmp_buf->pos + end_list_len;
        	tmp_buf->start = tmp_buf->pos;
        	tmp_buf->end = tmp_buf->last;
        	tmp_buf->last_buf = 1;
        	tmp_buf->last_in_chain = 1;
        	tmp_buf->flush = 1;

        	head_size += end_list_len;

        	cp = ngx_pcalloc(r->pool, sizeof(ngx_chain_t));

        	if(NULL == cp)
        	{
                	goto UNEXCEPTED_ERROR;
        	}

        	cp->buf = tmp_buf;
        	cp->next = NULL;

        	out_end->next = cp;
        	out_end = cp;
		}
		break;

		default:
			assert(0);
			break;
		}
		
		//Traversal(out_head);
                
		r->headers_out.status = NGX_HTTP_OK;
        ngx_str_set(&r->headers_out.content_type, "application/x-mpegURL");
    	//r->root_tested = !r->error_page;
    	//r->allow_ranges = 1;
		r->buffered |= NGX_HTTP_WRITE_BUFFERED;
		r->allow_ranges = 0;
		r->keepalive = 0;
    	//r->connection->buffered |= NGX_HTTP_WRITE_BUFFERED;
        r->headers_out.content_length_n = head_size;
        
		result = ngx_http_send_header(r);
        
		if(result == NGX_ERROR || result > NGX_OK || r->header_only)
    	{
    		goto UNEXCEPTED_ERROR;
    	}

        result = ngx_http_output_filter(r, out_head);

		ngx_http_finalize_request(r, result);

		return;
	}

UNEXCEPTED_ERROR:

	ngx_log_error(NGX_LOG_CRIT, r->pool->log, 0, "hlsm3u8_pr_post_handler - internal server error");
	
	ngx_http_finalize_request(r, r->headers_out.status);
	
	return;
}

static ngx_int_t hlsm3u8_seek_subrequest_post_handler(ngx_http_request_t *r, void *data, ngx_int_t rc)
{
	ngx_http_hlsm3u8_ctx_t *ctx = data;

    if(NULL == ctx) {
                
		ngx_log_error(NGX_LOG_ERR, r->pool->log, 0, "hlsm3u8_seek_subrequest_post_handler - m3u8 filter return data null");
                
		return NGX_ERROR;
    }

	r->parent->headers_out.status = r->headers_out.status;

	ctx->data = &r->upstream->buffer;

	r->parent->write_event_handler = hlsm3u8_pr_post_handler;

	return NGX_OK;
}

static ngx_int_t ngx_http_hlsm3u8_handler(ngx_http_request_t *r)
{
	ngx_http_hlsm3u8_loc_conf_t *local_conf = NULL;
	ngx_http_post_subrequest_t *psr = NULL;
	ngx_http_request_t *sr = NULL;
	u_char *p = NULL;
	ngx_str_t review_start;
    ngx_str_t review_end;
    ngx_str_t seek_time;
    ngx_str_t argus;
	ngx_int_t rc = 0;

	ngx_str_t query = ngx_string("/upstream");

	if(!(r->method & (NGX_HTTP_GET | NGX_HTTP_HEAD)))
	{
		return NGX_HTTP_NOT_ALLOWED;
	}

	if(r->uri.data[r->uri.len - 1] == '/')
	{
		return NGX_DECLINED;
	}

	local_conf = ngx_http_get_module_loc_conf(r, ngx_http_hlsm3u8_module);
	
	if(NULL == local_conf)
	{
		ngx_log_error(NGX_LOG_CRIT, r->pool->log, 0, "ngx_http_hlsm3u8_handler - internal server error");
		
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	r->out = NULL;

	psr = ngx_palloc(r->pool, sizeof(ngx_http_post_subrequest_t));
	
	if(NULL == psr)
	{
		ngx_log_error(NGX_LOG_CRIT, r->pool->log, 0, "ngx_http_hlsm3u8_handler - internal server error");
		
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}

	ngx_http_hlsm3u8_ctx_t *ctx = ngx_http_get_module_ctx(r, ngx_http_hlsm3u8_module);

	if(NULL == ctx)
    {
        ctx = ngx_palloc(r->pool, sizeof(ngx_http_hlsm3u8_ctx_t));
        	
		if(NULL == ctx) {

			ngx_log_error(NGX_LOG_CRIT, r->pool->log, 0, "ngx_http_hlsm3u8_handler - internal server error");

			return NGX_ERROR;
		}
        	
		ngx_http_set_ctx(r, ctx, ngx_http_hlsm3u8_module);
    }

	if(((NGX_OK == ngx_http_arg(r, (u_char *) "playbackbegin", 13, &review_start))
		&& (NGX_OK == ngx_http_arg(r, (u_char *) "playbackend", 11, &review_end)))
	|| (NGX_OK == ngx_http_arg(r, (u_char *) "playseek", 8, &seek_time))) {

		psr->handler = hlsm3u8_seek_subrequest_post_handler;
		
		psr->data = ctx;

		argus.data = ngx_palloc(r->pool, URI_NAME_MAXLEN);

		if(NULL == argus.data) {
			
			printf("argus.data == null\n");

			ngx_log_error(NGX_LOG_CRIT, r->pool->log, 0, "ngx_http_hlsm3u8_handler - internal server error");
			
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}

		p = ngx_snprintf(argus.data, URI_NAME_MAXLEN, "%V?%V", &r->uri, &r->args);

		argus.len = p - argus.data;

		ngx_log_error(NGX_LOG_WARN, r->pool->log, 0, "ngx_http_hlsm3u8_handler - argus: %s", argus.data);

		rc = ngx_http_subrequest(r, &query, &argus, &sr, psr, NGX_HTTP_SUBREQUEST_IN_MEMORY);

		if(rc != NGX_OK)
		{
			return NGX_HTTP_SERVICE_UNAVAILABLE;
		}
	}
	else
	{
		ngx_log_error(NGX_LOG_ERR, r->pool->log, 0, "ngx_http_hlsm3u8_handler - http mode unsupport");
		
		return NGX_HTTP_FORBIDDEN;
	}

	return NGX_OK;
}
