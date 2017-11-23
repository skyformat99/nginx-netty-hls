
/*
 * Copyright (C) Igor Sysoev
 * Copyright (C) Nginx, Inc.
 */

#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>


typedef struct {
	ngx_msec_t tstimespan;
} ngx_http_hlsts_loc_conf_t;


static ngx_int_t ngx_http_hlsts_handler(ngx_http_request_t *r);

static char * ngx_http_hlsts(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);
static void * ngx_http_hlsts_create_loc_conf(ngx_conf_t *cf);


static ngx_command_t  ngx_http_hlsts_commands[] = { 
    { ngx_string("hlsts"), 
      NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS, 
      ngx_http_hlsts, 
      0, 
      0,
      NULL },
    { ngx_string("tsspan"), 
      NGX_HTTP_LOC_CONF|NGX_CONF_TAKE1, 
      ngx_conf_set_num_slot, 
      NGX_HTTP_LOC_CONF_OFFSET, 
      offsetof(ngx_http_hlsts_loc_conf_t,tstimespan),
      NULL }, 
      ngx_null_command 
}; 


static 
char *ngx_http_hlsts(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
	ngx_http_core_loc_conf_t  *clcf;

    clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
    clcf->handler = ngx_http_hlsts_handler;

    return NGX_CONF_OK;
}

static ngx_http_module_t  ngx_http_hlsts_module_ctx = {
    NULL,                          /* preconfiguration */
    NULL,                          /* postconfiguration */

    NULL,                          /* create main configuration */
    NULL,                          /* init main configuration */

    NULL,                          /* create server configuration */
    NULL,                          /* merge server configuration */

    ngx_http_hlsts_create_loc_conf,		/*create location configuration */
    NULL        /* merge location configuration */
};

ngx_module_t  ngx_http_hlsts_module = {
    NGX_MODULE_V1,
    &ngx_http_hlsts_module_ctx,      /* module context */
    ngx_http_hlsts_commands,         /* module directives */
    NGX_HTTP_MODULE,               /* module type */
    NULL,                          /* init master */
    NULL,                          /* init module */
    NULL,                          /* init process */
    NULL,                          /* init thread */
    NULL,                          /* exit thread */
    NULL,                          /* exit process */
    NULL,                          /* exit master */
    NGX_MODULE_V1_PADDING
};

static void * 
ngx_http_hlsts_create_loc_conf(ngx_conf_t *cf) 
{ 
	ngx_http_hlsts_loc_conf_t  *conf; 
	conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_hlsts_loc_conf_t)); 
	if (conf == NULL)
		return NGX_CONF_ERROR; 

	
	conf->tstimespan = NGX_CONF_UNSET;
	
    return conf; 
}


static 
ngx_int_t ngx_http_hlsts_handler(ngx_http_request_t *r)
{
	  u_char				    *last;
	  size_t					 root;
	  ngx_int_t 				 rc;
	  ngx_uint_t				 level;
	  ngx_str_t 				 path;
	  ngx_log_t 				*log;
	  ngx_buf_t 				*b;
	  ngx_chain_t				 out;

	  ngx_open_file_info_t		 of;
	  ngx_http_core_loc_conf_t	*clcf;
	

	  if (!(r->method & (NGX_HTTP_GET|NGX_HTTP_HEAD))) {
		  return NGX_HTTP_NOT_ALLOWED;
	  }
	
	  if (r->uri.data[r->uri.len - 1] == '/') {
		  return NGX_DECLINED;
	  }
	
	  rc = ngx_http_discard_request_body(r);
	
	  if (rc != NGX_OK) {
		  return rc;
	  }
	
	  last = ngx_http_map_uri_to_path(r, &path, &root, 0);
	  if (last == NULL) {
		  return NGX_HTTP_INTERNAL_SERVER_ERROR;
	  }
	
	  log = r->connection->log;
	
	  path.len = last - path.data;
	
	  ngx_log_debug1(NGX_LOG_DEBUG_HTTP, log, 0,
					 "http m3u8 filename: \"%V\"", &path);
	  
	  clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);

	  ngx_http_hlsts_loc_conf_t	*lc;

	  lc = ngx_http_get_module_loc_conf(r, ngx_http_hlsts_module);
	printf("%d",(int)lc->tstimespan);
	  ngx_memzero(&of, sizeof(ngx_open_file_info_t));
  
	  of.read_ahead = clcf->read_ahead;
	  of.directio = NGX_MAX_OFF_T_VALUE;
	  of.valid = clcf->open_file_cache_valid;
	  of.min_uses = clcf->open_file_cache_min_uses;
	  of.errors = clcf->open_file_cache_errors;
	  of.events = clcf->open_file_cache_events;
  
	  if (ngx_http_set_disable_symlinks(r, clcf, &path, &of) != NGX_OK) {
		  return NGX_HTTP_INTERNAL_SERVER_ERROR;
	  }

	  if (ngx_open_cached_file(clcf->open_file_cache, &path, &of, r->pool)
        != NGX_OK)
      {
        switch (of.err) {

        case 0:
            return NGX_HTTP_INTERNAL_SERVER_ERROR;

        case NGX_ENOENT:
        case NGX_ENOTDIR:
        case NGX_ENAMETOOLONG:

            level = NGX_LOG_ERR;
            rc = NGX_HTTP_NOT_FOUND;
            break;

        case NGX_EACCES:
#if (NGX_HAVE_OPENAT)
        case NGX_EMLINK:
        case NGX_ELOOP:
#endif

            level = NGX_LOG_ERR;
            rc = NGX_HTTP_FORBIDDEN;
            break;

        default:

            level = NGX_LOG_CRIT;
            rc = NGX_HTTP_INTERNAL_SERVER_ERROR;
            break;
        }

        if (rc != NGX_HTTP_NOT_FOUND || clcf->log_not_found) {
            ngx_log_error(level, log, of.err,
                          "%s \"%s\" failed", of.failed, path.data);
        }

        return rc;
    }

    if (!of.is_file) {

        if (ngx_close_file(of.fd) == NGX_FILE_ERROR) {
            ngx_log_error(NGX_LOG_ALERT, log, ngx_errno,
                          ngx_close_file_n " \"%s\" failed", path.data);
        }

        return NGX_DECLINED;
    }

	
	    r->root_tested = !r->error_page;
    r->allow_ranges = 1;

    r->headers_out.content_length_n = of.size;

	r->headers_out.status = NGX_HTTP_OK;
	r->headers_out.last_modified_time = of.mtime;

	//r->duration_sec = lc->tstimespan;
	//r->mp4_filesize = of.size;
	
	//printf("zz = %d\n",(unsigned int)r->mp4_filesize);
	ngx_str_set(&r->headers_out.content_type, "application/octet-stream");

    b = ngx_pcalloc(r->pool, sizeof(ngx_buf_t));
    if (b == NULL) {
        return NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    b->file = ngx_pcalloc(r->pool, sizeof(ngx_file_t));
    if (b->file == NULL) 
	{
        return NGX_HTTP_INTERNAL_SERVER_ERROR;
    }


    rc = ngx_http_send_header(r);
	
    b->file_pos = 0;
    b->file_last = of.size;

    b->in_file = b->file_last ? 1 : 0;
    b->last_buf = 1;
    b->last_in_chain = 1;

    b->file->fd = of.fd;
    b->file->name = path;
    b->file->log = log;
    b->file->directio = of.is_directio;

    out.buf = b;
    out.next = NULL;

    return ngx_http_output_filter(r, &out);

	return NGX_OK;

}