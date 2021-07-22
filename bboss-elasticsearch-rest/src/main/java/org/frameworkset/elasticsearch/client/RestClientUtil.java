package org.frameworkset.elasticsearch.client;

import com.frameworkset.util.SimpleStringUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.frameworkset.elasticsearch.ElasticSearch;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.IndexNameBuilder;
import org.frameworkset.elasticsearch.SliceRunTask;
import org.frameworkset.elasticsearch.bulk.BulkCommand;
import org.frameworkset.elasticsearch.bulk.BulkConfig;
import org.frameworkset.elasticsearch.bulk.BulkData;
import org.frameworkset.elasticsearch.entity.*;
import org.frameworkset.elasticsearch.entity.sql.ColumnMeta;
import org.frameworkset.elasticsearch.entity.sql.SQLRestResponse;
import org.frameworkset.elasticsearch.entity.sql.SQLRestResponseHandler;
import org.frameworkset.elasticsearch.entity.sql.SQLResult;
import org.frameworkset.elasticsearch.entity.suggest.*;
import org.frameworkset.elasticsearch.handler.*;
import org.frameworkset.elasticsearch.scroll.*;
import org.frameworkset.elasticsearch.scroll.thread.ScrollTask;
import org.frameworkset.elasticsearch.serial.ESInnerHitSerialThreadLocal;
import org.frameworkset.elasticsearch.serial.ESTypeReferences;
import org.frameworkset.elasticsearch.serial.SerialContext;
import org.frameworkset.elasticsearch.serial.SerialUtil;
import org.frameworkset.elasticsearch.template.ESInfo;
import org.frameworkset.json.JsonTypeReference;
import org.frameworkset.soa.BBossStringWriter;
import org.frameworkset.util.ClassUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.frameworkset.spi.remote.http.HttpRequestProxy.entityEmpty;

/**
 * @see <p>https://esdoc.bbossgroups.com/#/development</p>
 */
public class RestClientUtil extends ClientUtil{
	private static Logger logger = LoggerFactory.getLogger(RestClientUtil.class);
	protected ElasticSearchRestClient client;
	protected StringBuilder bulkBuilder;
	protected IndexNameBuilder indexNameBuilder;
	/**
	 * ".security",".watches",
	 * 清除监控表
	 */
	protected String[] monitorIndices = new String[]{
			".monitoring*",".triggered_watches",
			".watcher-history*",".ml*",".kibana_*"
	};
	protected String monitorIndicesString = ".monitoring*,.triggered_watches,.watcher-history*,.ml*,.kibana_*";

	public RestClientUtil(ElasticSearchClient client,IndexNameBuilder indexNameBuilder) {
		this.client = (ElasticSearchRestClient)client;
		this.indexNameBuilder = indexNameBuilder;
	}

	public String getElasticsearchName(){
		return client.getElasticsearchName();
	}
	public ElasticSearch getElasticSearch() {
		return client.getElasticSearch();
	}

	public String getCurrentDateString(){
		return this.indexNameBuilder.getCurrentDateString();
	}

	public String getDateString(Date date){
		return this.indexNameBuilder.getDateString(date);
	}

	/**
	 * 获取索引表字段信息
	 * @param index
	 * @return
	 * @throws ElasticSearchException
	 */
	public List<IndexField> getIndexMappingFields(String index,final String indexType) throws ElasticSearchException{
		try{
			final List<IndexField> fields = new ArrayList<IndexField>();
			getIndexMapping(index,false,new ResponseHandler<Void>(){

				@Override
				public Void handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
					int status = response.getStatusLine().getStatusCode();

					if (status >= 200 && status < 300) {
						HttpEntity entity = response.getEntity();
						InputStream inputStream = null;
						try{
							inputStream = entity.getContent();
							if(entityEmpty(entity,inputStream)){
								return null;
							}
							/**
							 * Map<indexName,Mapping<Type,Properties<fieldName<"fileds",Map<subFieldName,IndexField>>,Type>>>
							 */
							if(!client.isUpper7()) {
								Map<String, Object> map = SimpleStringUtil.json2ObjectWithType(inputStream, new JsonTypeReference<Map<String, Object>>() {
								});
								Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
								while (entries.hasNext()) {
									Map.Entry<String, Object> entry = entries.next();//去最新的映射版本，区别于每天的索引表版本
									Map<String, Map<String, Object>> mapping = (Map<String, Map<String, Object>>) entry.getValue();
									Map<String, Map<String, Object>> typeProperties = (Map<String, Map<String, Object>>) mapping.get("mappings").get(indexType);
									Map<String, Object> properties = (Map<String, Object>) typeProperties.get("properties");
									Iterator<Map.Entry<String, Object>> fileds = properties.entrySet().iterator();
									while (fileds.hasNext()) {
										Map.Entry<String, Object> field = fileds.next();
										IndexField indexField = BuildTool.buildIndexField(field, fields, null);
									}
									break;

								}
							}
							else{
								Map<String, Object> map = SimpleStringUtil.json2ObjectWithType(inputStream, new JsonTypeReference<Map<String, Object>>() {
								});
								Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
								while (entries.hasNext()) {
									Map.Entry<String, Object> entry = entries.next();//去最新的映射版本，区别于每天的索引表版本
									Map<String, Map<String, Object>> index = (Map<String, Map<String, Object>>) entry.getValue();
									Map<String,Object> mapping = index.get("mappings");
									Map<String, Object> properties = (Map<String, Object>) mapping.get("properties");
									Iterator<Map.Entry<String, Object>> fileds = properties.entrySet().iterator();
									while (fileds.hasNext()) {
										Map.Entry<String, Object> field = fileds.next();
										IndexField indexField = BuildTool.buildIndexField(field, fields, null);
									}
									break;

								}
							}
						}
						finally {
							if(inputStream != null)
								inputStream.close();
						}
						/**
						 * Map<indexName,Properties<fieldName<"fileds",Map<subFieldName,IndexField>>,Type>>
						 */
						return null;

					} else {
						HttpEntity entity = response.getEntity();
						if (entity != null )
							throw new ElasticSearchException(new StringBuilder().append("Unexpected response : " ).append( EntityUtils.toString(entity)).toString(),status);
						else
							throw new ElasticSearchException("Unexpected response status: " + status,status);
					}
				}
			});
			return fields;
		}
		catch(ElasticSearchException e){
			return (List<IndexField>)ResultUtil.hand404HttpRuntimeException(e,Object.class,ResultUtil.OPERTYPE_getIndice);
		}

	}

	
	 
	public String updateDocuments(String indexName, String indexType,String updateTemplate, List<?> beans) throws ElasticSearchException{
		return null;
	}
	public   String updateDocuments(String indexName, String indexType,String updateTemplate, List<?> beans,String refreshOption) throws ElasticSearchException{
		return null;
	}
	@Override
	public String executeRequest(String path, String templateName,Map params) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	public String getDynamicIndexName(String indexName){
		return this.indexNameBuilder.getIndexName(indexName);
	}

	/**************************************创建或者修改文档开始**************************************************************/
	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocument(String indexName, String indexType, Object bean) throws ElasticSearchException{
		return addDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean);
	}

	public String addDateDocument(String indexName, String indexType, Object bean,String refreshOption) throws ElasticSearchException{
		return addDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,refreshOption);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentWithId(String indexName, String indexType, Object bean,Object docId) throws ElasticSearchException{
		return addDocumentWithId(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,docId);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentWithId(String indexName, String indexType, Object bean,Object docId,Object parentId) throws ElasticSearchException{
		return addDocumentWithId(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,docId,parentId);
	}
	public String addDateDocumentWithParentId(String indexName, String indexType, Object bean,Object parentId) throws ElasticSearchException{
		return addDocumentWithId(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,(Object)null,parentId);
	}


	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocument(String indexName, String indexType, Object bean,Object docId,String refreshOption) throws ElasticSearchException{
		return addDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,docId,refreshOption);
	}

	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocument(String indexName, String indexType, Object bean,Object docId,Object parentId,String refreshOption) throws ElasticSearchException{
		return addDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,docId,  parentId,refreshOption);
	}

	public String addDateDocumentWithParentId(String indexName, String indexType, Object bean,Object parentId,String refreshOption) throws ElasticSearchException{
		return addDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,     bean,(Object)null,  parentId,refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocuments(String indexName, String indexType, List<?> beans) throws ElasticSearchException{
		return addDocuments(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans);
	}



	public String addDateDocuments(String indexName, String indexType, List<?> beans,String refreshOption) throws ElasticSearchException{
		return addDocuments(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,refreshOption);
	}

	/**************************************创建或者修改文档结束**************************************************************/

	/***************************添加或者修改文档开始************************************/
	/**
	 * 批量创建索引
	 * @param indexName
	 * @param indexType
	 * @param beans
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocuments(String indexName, String indexType, List<?> beans) throws ElasticSearchException{
		return addDocuments(indexName, indexType,  beans,(String) null);
	}
	public String addDocuments(String indexName, String indexType,  List<?> beans,String refreshOption) throws ElasticSearchException{
		if(beans == null || beans.size() == 0)
			return null;
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
		ClientOptions clientOptions = null;
		if(refreshOption != null){
			clientOptions = new ClientOptions();
			clientOptions.setRefreshOption(refreshOption);
		}
		return addDocuments(indexName, indexType, beans,clientOptions);
//		for(Object bean:beans) {
//			try {
////				if(beanInfo == null) {
////					beanInfo = ClassUtil.getClassInfo(bean.getClass());
////				}
//				BulkData bulkData = new BulkData(BulkData.INSERT,bean);
//				bulkData.setIndex(indexName);
//				bulkData.setIndexType(indexType);
//				bulkData.setClientOptions(clientOptions);
//				BuildTool.evalBuilk(writer,bulkData,client.isUpper7());
////				BuildTool.evalBuilk(beanInfo,writer,indexName,indexType,bean,"index",this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
////		if(refreshOption == null)
//			return this.client.executeHttp(BuildTool.buildActionUrl(clientOptions),builder.toString(),ClientUtil.HTTP_POST);
////		else
////			return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdKey(String indexName, String indexType, List<Map> beans,String docIdKey) throws ElasticSearchException{
		return addDocumentsWithIdKey(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdKey);
	}

	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String refreshOption) throws ElasticSearchException{
		return addDocuments(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdKey,refreshOption);
	}
	public String addDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String refreshOption) throws ElasticSearchException{
//		if(beans == null || beans.size() == 0)
//			return null;
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
//		for(Map bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",docIdKey,(String)null);
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
//		if(refreshOption == null)
//			return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
//		else
//			return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
		return addDocuments(  indexName,   indexType,  beans,  docIdKey,(String)null,  refreshOption);
	}
	public String addDocumentsWithIdKey(String indexName, String indexType,  List<Map> beans,String docIdKey) throws ElasticSearchException{
		return addDocuments(indexName, indexType, beans,docIdKey,(String )null);
	}

/******/
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdKey(String indexName, String indexType, List<Map> beans,String docIdKey,String parentIdKey) throws ElasticSearchException{
		return addDocumentsWithIdKey(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdKey,parentIdKey);
	}

	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String parentIdKey,String refreshOption) throws ElasticSearchException{
		return addDocuments(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdKey,parentIdKey,refreshOption);
	}
	public String addDateMapDocuments(String indexName, String indexType, List<Map> beans,ClientOptions clientOptions) throws ElasticSearchException{
		return addMapDocuments(this.indexNameBuilder.getIndexName(indexName),   indexType,  beans,  clientOptions);
	}
	public String addMapDocuments(String indexName, String indexType, List<Map> beans,ClientOptions clientOptions) throws ElasticSearchException{
		if(beans == null || beans.size() == 0)
			return null;

		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		for(Map bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",clientOptions,this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
			BulkData bulkData = new BulkData(BulkData.INSERT,bean);
			bulkData.setIndexType(indexType);
			bulkData.setIndex(indexName);
			bulkData.setClientOptions(clientOptions);
			try {
				BuildTool.evalBuilk(writer,bulkData,client.isUpper7());
			} catch (IOException e) {
				throw new ElasticSearchException("evalBuilk addMapDocuments failed",e);
			}
		}
		writer.flush();
		return this.client.executeHttp(BuildTool.buildActionUrl(clientOptions, BulkConfig.ERROR_FILTER_PATH),builder.toString(),ClientUtil.HTTP_POST);
	}
	public String addDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String parentIdKey,String refreshOption) throws ElasticSearchException{
		if(beans == null || beans.size() == 0)
			return null;
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setIdField(docIdKey);
		clientOptions.setParentIdField(parentIdKey);
		clientOptions.setRefreshOption(refreshOption);
		return addDocuments(  indexName,   indexType, beans,clientOptions);
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
//		for(Map bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",docIdKey,parentIdKey,this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
//		if(refreshOption == null)
//			return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
//		else
//			return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
	}
	public String addDocumentsWithIdKey(String indexName, String indexType,  List<Map> beans,String docIdKey,String parentIdKey) throws ElasticSearchException{
		return addDocuments(indexName, indexType, beans,docIdKey, parentIdKey,(String)null);
	}




	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType, Object bean) throws ElasticSearchException{
		return this.addDocument(indexName,indexType,bean,(String)null);
	}


	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType, Object bean,String refreshOption) throws ElasticSearchException{
//		ClassUtil.ClassInfo beanInfo = ClassUtil.getClassInfo(bean.getClass());
//
//		Object id = BuildTool.getId(bean,beanInfo);
//		Object parentId = BuildTool.getParentId(bean,beanInfo);
//		Object routing = BuildTool.getRouting(bean,beanInfo);
		ClientOptions clientOptions = null;
		if(refreshOption != null) {
			clientOptions = new ClientOptions();
			clientOptions.setRefreshOption(refreshOption);
		}
		return addDocument(indexName, indexType, bean,clientOptions);

	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentWithId(String indexName, String indexType, Object bean,Object docId) throws ElasticSearchException{
		return addDocument(indexName, indexType, bean,docId,null);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentWithId(String indexName, String indexType, Object bean,Object docId,Object parentId) throws ElasticSearchException{
		return addDocument(indexName, indexType, bean,docId,  parentId,(String)null);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentWithParentId(String indexName, String indexType, Object bean,Object parentId) throws ElasticSearchException{
		return addDocument(indexName, indexType, bean,(Object)null,  parentId,(String)null);
	}

	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType, Object bean,Object docId,String refreshOption) throws ElasticSearchException{
		return addDocument(  indexName,   indexType,   bean,docId,(Object)null,refreshOption);
	}
	public String addDocument(String indexName, String indexType, Object bean,Object docId,Object parentId,String refreshOption) throws ElasticSearchException{
//		ClassUtil.ClassInfo beanInfo = ClassUtil.getClassInfo(bean.getClass());
//		Object routing = BuildTool.getRouting(bean,beanInfo);
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setId(docId);
		clientOptions.setParentId(parentId);
		clientOptions.setRefreshOption(refreshOption);
		return addDocument( indexName,  indexType,  bean, clientOptions);
	}
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType, Object params,ClientOptions clientOptions) throws ElasticSearchException{
		/**if(params instanceof Map){
			return addMapDocument(indexName,indexType,(Map)params,clientOptions);
		}
		Object docId = null;
		Object parentId = null;
		Object routing = null;
		String refreshOption = null;
		ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(params.getClass());
		if(clientOptions != null) {
			refreshOption = clientOptions.getRefreshOption();

			docId = clientOptions.getIdField() != null ? BuildTool.getId(params, beanClassInfo, clientOptions.getIdField()) : null;
			parentId = clientOptions.getParentIdField() != null ? BuildTool.getParentId(params, beanClassInfo, clientOptions.getParentIdField()) : null;
			if(clientOptions.getRouting() == null) {
				routing = clientOptions.getRoutingField() != null ? BuildTool.getRouting(params, beanClassInfo, clientOptions.getRoutingField()) : null;
			}
			else{
				routing = clientOptions.getRouting();
			}
		}
		StringBuilder builder = new StringBuilder();
		Object id = docId;
		if(indexName == null){
			if(beanClassInfo == null){
				throw   new ElasticSearchException(" _addDocument failed: Class info not setted.");
			}
			ESIndexWrapper esIndexWrapper = beanClassInfo.getEsIndexWrapper();
			if(esIndexWrapper == null){
				throw new ElasticSearchException(builder.append(" ESIndex annotation do not set in class ").append(beanClassInfo.toString()).toString());
			}
			RestGetVariableValue restGetVariableValue = new RestGetVariableValue(beanClassInfo,params);
			BuildTool.buildIndiceName(esIndexWrapper,builder,restGetVariableValue);
			builder.append("/");
			if(indexType == null){
				BuildTool.buildIndiceType(esIndexWrapper,builder,restGetVariableValue);
			}
			else{
				builder.append("/").append(indexType);
			}

		}
		else {
			builder.append(indexName);
			if(indexType == null || indexType.equals("")) {
				builder.append("/").append(_doc);
			}
			else{
				builder.append("/").append(indexType);
			}

		}


		if(id != null){
			builder.append("/").append(id);
		}
		if(refreshOption != null ){
			builder.append("?").append(refreshOption);
			if(parentId != null){
				builder.append("&parent=").append(parentId);
			}
			if(routing != null){
				builder.append("&routing=").append(routing);
			}
		}
		else{
			if(parentId != null){
				builder.append("?parent=").append(parentId);
				if(routing != null){
					builder.append("&routing=").append(routing);
				}
			}
			else if(routing != null){
				builder.append("?routing=").append(routing);
			}

		}
		String path = builder.toString();
		builder = null;
		 */
		String path = BuildTool.buildAddPathUrlMeta( indexName , indexType, params, clientOptions,ClassUtil.getClassInfo(params.getClass()));
		path = this.client.executeHttp(path, SerialUtil.object2json(params),ClientUtil.HTTP_POST);
		return path;
//		return _addDocument( beanClassInfo, indexName,   indexType,   params,  docId,  parentId,  routing,  refreshOption);
	}
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocument(String indexName, String indexType, Object bean,ClientOptions clientOptions) throws ElasticSearchException{
		return addDocument(  this.indexNameBuilder.getIndexName(indexName),   indexType,   bean,  clientOptions);
	}
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addMapDocument(String indexName, String indexType, Map params,ClientOptions clientOptions) throws ElasticSearchException{
		Object docId = null;
		Object parentId = null;
		Object routing = null;
		String refreshOption = null;
		if(clientOptions != null) {
			refreshOption = clientOptions.getRefreshOption();
			docId = clientOptions.getIdField() != null ? params.get(clientOptions.getIdField()) : null;
			parentId = clientOptions.getParentIdField() != null ? params.get( clientOptions.getParentIdField()) : null;
			if(clientOptions.getRouting() == null) {
				routing = clientOptions.getRoutingField() != null ? params.get(clientOptions.getRoutingField()) : null;
			}
			else{
				routing = clientOptions.getRouting();
			}
		}
		return addDocument(  indexName,   indexType,   params,  docId,  parentId,  routing,  refreshOption);
	}

	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addMapDocument(String indexName, String indexType, Map bean) throws ElasticSearchException{
		return addDocument(  indexName,   indexType,   bean,null,null,null,(String )null);
	}

	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateMapDocument(String indexName, String indexType, Map bean) throws ElasticSearchException{
		return addDocument(  this.indexNameBuilder.getIndexName(indexName),   indexType,   bean,null,null,null,(String )null);
	}
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String addDateMapDocument(String indexName, String indexType, Map params,ClientOptions clientOptions) throws ElasticSearchException{
		return addMapDocument(this.indexNameBuilder.getIndexName(indexName),   indexType,   params,clientOptions);
	}
	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType, Object bean,Object docId,Object parentId,Object routing,String refreshOption) throws ElasticSearchException{
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setRefreshOption(refreshOption);
		clientOptions.setId(docId);
		clientOptions.setParentId(parentId);
		clientOptions.setRouting(routing);

		return addDocument( indexName,   indexType,   bean,  clientOptions);
	}

	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */

//	private String _addDocument(ClassUtil.ClassInfo beanInfo,String indexName, String indexType, Object bean, Object docId, Object parentId, Object routing, String refreshOption) throws ElasticSearchException{
//		StringBuilder builder = new StringBuilder();
//		Object id = docId;
//		if(indexName == null){
//			if(beanInfo == null){
//				throw   new ElasticSearchException(" _addDocument failed: Class info not setted.");
//			}
//			ESIndexWrapper esIndexWrapper = beanInfo.getEsIndexWrapper();
//			if(esIndexWrapper == null){
//				throw new ElasticSearchException(builder.append(" ESIndex annotation do not set in class ").append(beanInfo.toString()).toString());
//			}
//			RestGetVariableValue restGetVariableValue = new RestGetVariableValue(beanInfo,bean);
//			BuildTool.buildIndiceName(esIndexWrapper,builder,restGetVariableValue);
//			builder.append("/");
//			if(indexType == null){
//				BuildTool.buildIndiceType(esIndexWrapper,builder,restGetVariableValue);
//			}
//			else{
//				builder.append("/").append(indexType);
//			}
//
//		}
//		else {
//			builder.append(indexName);
//			if(indexType == null || indexType.equals("")) {
//				builder.append("/").append(_doc);
//			}
//			else{
//				builder.append("/").append(indexType);
//			}
//
//		}
//
//
//		if(id != null){
//			builder.append("/").append(id);
//		}
//		if(refreshOption != null ){
//			builder.append("?").append(refreshOption);
//			if(parentId != null){
//				builder.append("&parent=").append(parentId);
//			}
//			if(routing != null){
//				builder.append("&routing=").append(routing);
//			}
//		}
//		else{
//			if(parentId != null){
//				builder.append("?parent=").append(parentId);
//				if(routing != null){
//					builder.append("&routing=").append(routing);
//				}
//			}
//			else if(routing != null){
//				builder.append("?routing=").append(routing);
//			}
//
//		}
//		String path = builder.toString();
//		builder = null;
//		path = this.client.executeHttp(path, SerialUtil.object2json(bean),ClientUtil.HTTP_POST);
//		return path;
//	}



	/**
	 *
	 * @param indexName
	 * @param indexType
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentWithParentId(String indexName, String indexType, Object bean,Object parentId,String refreshOption) throws ElasticSearchException{
		return addDocument(  indexName,   indexType,   bean,(Object )null,  parentId,  refreshOption);
	}




	public String updateDocuments(String indexName, String indexType, List<?> beans) throws ElasticSearchException{
		return updateDocuments(indexName, indexType, beans,(String)null);
	}
	public String updateDocuments(String indexName, String indexType, List<?> beans,String refreshOption) throws ElasticSearchException{
		ClientOptions clientOptions = null;
		if(refreshOption != null) {
			clientOptions = new ClientOptions();
			clientOptions.setRefreshOption(refreshOption);
		}
		return updateDocuments(  indexName,   indexType,beans,clientOptions);
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
//		ClassUtil.ClassInfo beanInfo = null;
//		for(Object bean:beans) {
//			try {
//				if(beanInfo == null){
//					beanInfo = ClassUtil.getClassInfo(bean.getClass());
//				}
//				BuildTool.evalBuilk(beanInfo,writer,indexName,indexType,bean,"update",this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
//		if(refreshOption != null) {
//			return this.client.executeHttp("_bulk?" + refreshOption, builder.toString(), ClientUtil.HTTP_POST);
//		}
//		else {
//			return this.client.executeHttp("_bulk", builder.toString(), ClientUtil.HTTP_POST);
//		}
	}
	public  String updateDocumentsWithIdKey(String indexName, String indexType, List<Map> beans,String docIdKey) throws ElasticSearchException
	{
		return updateDocuments( indexName,  indexType, beans, docIdKey,(String)null);
	}
	public String updateDocumentsWithIdKey(String indexName, String indexType, List<Map> beans,String docIdKey,String parentIdKey) throws ElasticSearchException{
		return updateDocuments( indexName,  indexType, beans, docIdKey,parentIdKey);
	}
	public  String updateDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String refreshOption) throws ElasticSearchException
	{
		return updateDocuments(  indexName,   indexType,   beans,  docIdKey,(String )null,  refreshOption);
	}
	public   String addDocuments(String indexName, String indexType, List<?> beans,ClientOptions clientOptions) throws ElasticSearchException{
		if(beans == null || beans.size() == 0)
			return null;
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		for(Object bean:beans) {
			try {
				BulkData bulkData = new BulkData(BulkData.INSERT,bean);
				bulkData.setIndex(indexName);
				bulkData.setIndexType(indexType);
				bulkData.setClientOptions(clientOptions);
				BuildTool.evalBuilk(writer,bulkData,this.client.isUpper7());
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",clientOptions,this.client.isUpper7());
			} catch (IOException e) {
				throw new ElasticSearchException(e);
			}
		}
		writer.flush();
//		if(clientOptions == null)
//			return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
//		else
			return this.client.executeHttp(BuildTool.buildActionUrl(clientOptions, BulkConfig.ERROR_FILTER_PATH),builder.toString(),ClientUtil.HTTP_POST);
	}

	public String deleteDocumentsWithrefreshOption(String indexName, String indexType, String refreshOption,String[] ids) throws ElasticSearchException{
		StringBuilder builder = new StringBuilder();
		if(!this.client.isUpper7() ) {
			for (String id : ids) {

				builder.append("{ \"delete\" : { \"_index\" : \"").append(indexName).append("\", \"_type\" : \"").append(indexType).append("\", \"_id\" : \"").append(id).append("\" } }\n");
			}
		}
		else{
			for (String id : ids) {

				builder.append("{ \"delete\" : { \"_index\" : \"").append(indexName).append("\", \"_id\" : \"").append(id).append("\" } }\n");
			}
		}
		return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
	}

	public String updateDocuments(String indexName, String indexType, List<?> beans,ClientOptions clientOptions) throws ElasticSearchException{
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		for(Object bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"update",clientOptions,this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
			try {
				BulkData bulkData = new BulkData(BulkData.UPDATE,bean);
				bulkData.setIndex(indexName);
				bulkData.setIndexType(indexType);
				bulkData.setClientOptions(clientOptions);
				BuildTool.evalBuilk(writer,bulkData,this.client.isUpper7());
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",clientOptions,this.client.isUpper7());
			} catch (IOException e) {
				throw new ElasticSearchException(e);
			}
		}
		writer.flush();
//		if(clientOptions != null) {
			return this.client.executeHttp(BuildTool.buildActionUrl(clientOptions, BulkConfig.ERROR_FILTER_PATH), builder.toString(), ClientUtil.HTTP_POST);
//		}
//		else {
//			return this.client.executeHttp("_bulk", builder.toString(), ClientUtil.HTTP_POST);
//		}
	}

	public String updateDocument(String index,String indexType,Object params,ClientOptions updateOptions) throws ElasticSearchException{
//		Object id = null;
//		String refreshOption = null;
		Object detect_noop = null;
		Object doc_as_upsert = null;
		ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(params.getClass());
		Boolean returnSource = null;
		List<String> sourceUpdateExcludes = null;

		List<String> sourceUpdateIncludes = null;

		if(updateOptions != null) {
//			refreshOption = updateOptions.getRefreshOption();

			if(updateOptions.getDetectNoop() != null){
				detect_noop = updateOptions.getDetectNoop();
			}
			else {
				detect_noop = updateOptions.getDetectNoopField() != null ? BuildTool.getFieldValue(params, beanClassInfo, updateOptions.getDetectNoopField()) : null;
			}
			if(updateOptions.getDocasupsert() != null) {
				doc_as_upsert =updateOptions.getDocasupsert();
			}
			else {
				doc_as_upsert = updateOptions.getDocasupsertField() != null ? BuildTool.getRouting(params, beanClassInfo, updateOptions.getDocasupsertField()) : BuildTool.getEsDocAsUpsert(params,beanClassInfo);
			}
			returnSource = updateOptions.getReturnSource();

			sourceUpdateExcludes  = updateOptions.getSourceUpdateExcludes();

			sourceUpdateIncludes  = updateOptions.getSourceUpdateIncludes();


		}
		else{
			doc_as_upsert = BuildTool.getEsDocAsUpsert(params,beanClassInfo);
		}


		/**
		StringBuilder path = new StringBuilder();

		if(!this.client.isUpper7()) {
			if (indexType == null || indexType.equals(""))
				path.append(index).append("/").append(id).append("/_update");
			else
				path.append(index).append("/").append(indexType).append("/").append(id).append("/_update");
		}
		else{
			path.append(index).append("/_update").append("/").append(id);
		}
		if(refreshOption != null){
			path.append("?").append(refreshOption);
		}
		 */
		String path = BuildTool.buildUpdatePathUrlMeta(index,indexType,params,updateOptions,beanClassInfo,client.isUpper7());
		StringBuilder builder = new StringBuilder();
		builder.append(" {\"doc\":");
		Writer writer = new BBossStringWriter(builder);
		SerialUtil.object2json(params,writer);
		if(detect_noop != null){
			builder.append(",\"detect_noop\":").append(detect_noop);
		}
		if(doc_as_upsert != null){
			builder.append(",\"doc_as_upsert\":").append(doc_as_upsert);
		}
		if(returnSource != null){
			builder.append(",\"_source\":").append(String.valueOf(returnSource));

		}
		if (sourceUpdateExcludes != null) {
			/**
			 if(!upper7) {
			 writer.write(",\"_source_excludes\":");
			 }
			 else{
			 writer.write(",\"source_excludes\":");
			 }
			 */
			if(!this.client.isUpper7()) {
				builder.append(",\"_source_excludes\":");
				SerialUtil.object2json(sourceUpdateExcludes,writer);
			}

		}
		if (sourceUpdateIncludes != null) {
			/**
			 if(!upper7) {
			 writer.write(",\"_source_includes\":");
			 }
			 else{
			 writer.write(",\"source_includes\":");
			 }
			 */
			if(!this.client.isUpper7()) {
				builder.append(",\"_source_includes\":");
				SerialUtil.object2json(sourceUpdateIncludes,writer);

			}


		}
		builder.append("}");
		try {
			String searchResult = this.client.executeHttp(path, builder.toString(), ClientUtil.HTTP_POST);

			return searchResult;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_updateDocument);
		}
//		return this._update(  index,  indexType,
//				id,  params,  refreshOption,  detect_noop,  doc_as_upsert);
	}
	/**
	 * @see "https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html"
	 * @param bulkCommand
	 * @return
	 */
	public String executeBulk(BulkCommand bulkCommand){
		List<BulkData> bulkDatas = bulkCommand.getBatchBulkDatas();
		if(bulkDatas == null || bulkDatas.size() == 0){
			return null;
		}
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		try {
			for (BulkData bulkData : bulkDatas) {
//			if(bulkData.isInsert() || bulkData.isUpdate()){
//				if(!bulkData.isCollection()){
				try {
					BuildTool.evalBuilk(writer, bulkData, this.client.isUpper7());
				} catch (IOException e) {
					throw new ElasticSearchException(e);
				}
//				}
//				else{
//					List<Object> innerDatas = bulkData.getDatas();
//					if(innerDatas != null && innerDatas.size() > 0) {
//						for(Object idata:innerDatas) {
//							try {
//								BuildTool.evalBuilk(writer,idata,this.client.isUpper7());
//							} catch (IOException e) {
//								throw new ElasticSearchException(e);
//							}
//						}
//					}
//
//				}
//			}
//
//			else if(bulkData.isDelete()){
//				if(!bulkData.isCollection()){
//					BuildTool.evalDeleteBuilk(  writer,this.client.isUpper7(),  bulkData);
//				}
//				else{
//					List<Object> innerDatas = bulkData.getDatas();
//					if(innerDatas != null && innerDatas.size() > 0) {
//						for(Object idata:innerDatas) {
//							BuildTool.evalDeleteBuilk(  writer,this.client.isUpper7(),  bulkData);
//						}
//					}
//
//				}
//			}
//			else{
//				throw new BulkProcessorException(new StringBuilder().append("unknown bulk operation type:")
//						.append(bulkData.getType()).append(", bulk operation type support: 0(insert),1(update),2(delete)").toString());
//			}
			}
			writer.flush();

			return this.client.executeHttp(BuildTool.buildActionUrl(bulkCommand.getBulkProcessor().getBulkConfig(), BulkConfig.ERROR_FILTER_PATH), builder.toString(), ClientUtil.HTTP_POST);


		}
		finally {
			builder.setLength(0);
			builder = null;
			writer = null;
		}
//		if(bulkCommand.getRefreshOption() != null) {
//			return this.client.executeHttp(new StringBuilder().append("_bulk?")
//							                                  .append( bulkCommand.getRefreshOption()).toString(),
//					                                           builder.toString(), ClientUtil.HTTP_POST);
//		}
//		else {
//			return this.client.executeHttp("_bulk", builder.toString(), ClientUtil.HTTP_POST);
//		}

	}
	public String updateDocuments(String indexName, String indexType, List<Map> beans,String docIdKey,String parentIdKey,String refreshOption) throws ElasticSearchException{
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setIdField(docIdKey);
		clientOptions.setParentIdField(parentIdKey);
		clientOptions.setRefreshOption(refreshOption);
		return updateDocuments(  indexName,   indexType,   beans,clientOptions);
/**
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		for(Map bean:beans) {
			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"update");
				BuildTool.evalBuilk(writer,indexName,indexType,bean,"update",docIdKey,parentIdKey,this.client.isUpper7());
			} catch (IOException e) {
				throw new ElasticSearchException(e);
			}
		}
		writer.flush();
		if(refreshOption != null) {
			return this.client.executeHttp("_bulk?" + refreshOption, builder.toString(), ClientUtil.HTTP_POST);
		}
		else {
			return this.client.executeHttp("_bulk", builder.toString(), ClientUtil.HTTP_POST);
		}
 */
	}


	/***************************添加或者修改文档结束************************************/
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param addTemplate
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, String indexType,String addTemplate, Object bean) throws ElasticSearchException{
		return null;
	}
	/**
	 * 创建或者更新索引文档
	 * @param indexName
	 * @param indexType
	 * @param addTemplate
	 * @param bean
	 * @param refreshOption
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocument(String indexName, String indexType,String addTemplate, Object bean,String refreshOption) throws ElasticSearchException{
		return null;
	}

	public String addDateDocument(String indexName, String indexType,String addTemplate, Object bean,String refreshOption) throws ElasticSearchException{
		return null;
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param indexType
	 * @param addTemplate
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocument(String indexName, String indexType,String addTemplate, Object bean) throws ElasticSearchException{
		return null;
	}
	
	@Override 
	public String executeRequest(String path, String templateName,Object params) throws ElasticSearchException {
		// TODO Auto-generated method stub
				return null;
	}
	 /**
	  * 
	  * @param path
	  * @return
	  */
	 public Object executeRequest(String path) throws ElasticSearchException 
	 {
		 return executeRequest( path,null);
	 }
 

	@Override
	public String deleteDocuments(String indexName, String indexType, String[] ids) throws ElasticSearchException {
		return deleteDocuments(indexName,  indexType, ids,(ClientOptions)null);
		
	}
	public Map getClusterInfo(){
	 	return this.client.getClusterInfo();
	}

	public String getElasticsearchVersion(){
		return this.client.getEsVersion();
	}
	@Override
	public String deleteDocuments(String indexName, String indexType, String[] ids,ClientOptions clientOptions) throws ElasticSearchException {
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		for (String id : ids) {
			BulkData bulkData = new BulkData(BulkData.DELETE,id);
			bulkData.setIndexType(indexType);
			bulkData.setIndex(indexName);
			bulkData.setClientOptions(clientOptions);
			try {
				BuildTool.evalBuilk(writer,bulkData,client.isUpper7());
			} catch (IOException e) {
				throw new ElasticSearchException("evalBuilk deleteDocuments failed:docid["+id+"]",e);
			}
//			builder.append("{ \"delete\" : { \"_index\" : \"").append(indexName).append("\", \"_type\" : \"").append(indexType).append("\", \"_id\" : \"").append(id).append("\" } }\n");
		}
		writer.flush();
//		if(!this.client.isUpper7() ) {
//
//		}
//		else{
//			for (String id : ids) {
//				builder.append("{ \"delete\" : { \"_index\" : \"").append(indexName).append("\", \"_id\" : \"").append(id).append("\" } }\n");
//			}
//		}

		return this.client.executeHttp(BuildTool.buildActionUrl(clientOptions, BulkConfig.ERROR_FILTER_PATH), builder.toString(), ClientUtil.HTTP_POST);

	}



	/**
	 *
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html
	 * @param path twitter/_delete_by_query?routing=1
	 *             twitter/_doc/_delete_by_query?conflicts=proceed
	 *             twitter/_delete_by_query
	 *             twitter/_delete_by_query?scroll_size=5000
	 *
	 * @param entity
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteByQuery(String path,String entity) throws ElasticSearchException{
		return this.client.executeHttp(path,entity,ClientUtil.HTTP_POST);
	}
	/**
	 *
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html
	 * @param path twitter/_delete_by_query?routing=1
	 *             twitter/_doc/_delete_by_query?conflicts=proceed
	 *             twitter/_delete_by_query
	 *             twitter/_delete_by_query?scroll_size=5000
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteByQuery(String path,String templateName,Map params) throws ElasticSearchException{
		return null;
	}
	/**
	 *
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html
	 * @param path twitter/_delete_by_query?routing=1
	 *             twitter/_doc/_delete_by_query?conflicts=proceed
	 *             twitter/_delete_by_query
	 *             twitter/_delete_by_query?scroll_size=5000
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteByQuery(String path,String templateName,Object params) throws ElasticSearchException{
		return null;
	}
	
	
	/**
	 * 删除模板
	 * @param template
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteTempate(String template) throws ElasticSearchException {
		try{
			return client.executeHttp("/_template/"+template,ClientUtil.HTTP_DELETE);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_deleteTempate);
		}
	}
	
	/**
	 * 查询模板
	 * @param template
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String getTemplate(String template) throws ElasticSearchException {
		try {
			return client.executeHttp("/_template/" + template, ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getTemplate);
		}
	}
	
	/**
	 * 查询所有模板
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String getTemplate() throws ElasticSearchException {
		return client.executeHttp("/_template",ClientUtil.HTTP_GET);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param addTemplate
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocuments(String indexName, String indexType,String addTemplate, List<?> beans) throws ElasticSearchException
	{
		return null;
	}
	public String addDateDocuments(String indexName, String indexType,String addTemplate, List<?> beans,String refreshOption) throws ElasticSearchException{
		return null;
	}

	@Override
	/**
	 * only use by config rest clientutil
	 */
	public String addDocuments(String indexName, String indexType,String addTemplate, List<?> beans) throws ElasticSearchException {
//		StringBuilder builder = new StringBuilder();
//		for(Object id:beans) {
//			builder.append("{ \"index\" : { \"_index\" : \"").append(indexName).append("\", \"_type\" : \"").append(indexType).append("\", \"_id\" : \"").append(id).append("\" } }\n");
//		}
//		return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
		return null;

	}
	/**
	 * 批量创建索引
	 * @param indexName
	 * @param indexType
	 * @param addTemplate
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocuments(String indexName, String indexType,String addTemplate, List<?> beans,String refreshOption) throws ElasticSearchException
	{
		return null;
	}

	/**
	 * 获取文档，通过options设置获取文档的参数
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public String getDocument(String indexName, String indexType,String documentId,Map<String,Object> options) throws ElasticSearchException{
		try {
			return this.client.executeHttp(BuildTool.buildGetDocumentRequest(indexName, indexType, documentId, options), ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getDocument);
		}
	}
	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentByFieldLike(String indexName, String fieldName,Object fieldValue,Class<T> type) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,fieldValue);
		return searchObject(actionUrl,dsl,  type);
	}

	/**
	 * 根据属性获取文档json报文
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String getDocumentByFieldLike(String indexName, String fieldName,Object fieldValue) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,fieldValue);
		return client.executeRequest(actionUrl,dsl);

	}

	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentByFieldLike(String indexName, String fieldName,Object fieldValue,Class<T> type,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,fieldValue);
		return searchObject(actionUrl,dsl,  type);
	}

	/**
	 * 根据属性获取文档json报文
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String getDocumentByFieldLike(String indexName, String fieldName,Object fieldValue,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,fieldValue);
		return client.executeRequest(actionUrl,dsl);
	}

	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentByField(String indexName, String fieldName,Object fieldValue,Class<T> type) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,fieldValue);
		return searchObject(actionUrl,dsl,  type);
	}



	public <T> ESDatas<T> searchListByField(String indexName, String fieldName,Object fieldValue,Class<T> type,int from,int size) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,fieldValue,from, size);
		return searchList(actionUrl,dsl,type);
	}
	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param value
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchListByField(String indexName, String fieldName,Object value,Class<T> type,int from,int size,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,value,from, size);
		return searchList(actionUrl,dsl,type);
	}

	public <T> ESDatas<T> searchListByFieldLike(String indexName, String fieldName,Object value,Class<T> type,int from,int size) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,value,from, size);
		return searchList(actionUrl,dsl,type);
	}
	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param value
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchListByFieldLike(String indexName, String fieldName,Object value,Class<T> type,int from,int size,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.matchByFieldValueDsl(fieldName,value,from, size);
		return searchList(actionUrl,dsl,type);
	}


	/**
	 * 根据属性获取文档json报文
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String getDocumentByField(String indexName, String fieldName,Object fieldValue) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,null);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,fieldValue);
		return client.executeRequest(actionUrl,dsl);

	}

	/**
	 * 根据属性获取type类型文档对象
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentByField(String indexName, String fieldName,Object fieldValue,Class<T> type,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,fieldValue);
		return searchObject(actionUrl,dsl,  type);
	}

	/**
	 * 根据属性获取文档json报文
	 * @param indexName
	 * @param fieldName
	 * @param fieldValue
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String getDocumentByField(String indexName, String fieldName,Object fieldValue,Map<String,Object> options) throws ElasticSearchException{
		String actionUrl = BuildTool.buildSearchDocumentRequest(indexName,null,options);
		String dsl = BuildTool.findByFieldValueDsl(fieldName,fieldValue);
		return client.executeRequest(actionUrl,dsl);
	}

	/**
	 * 获取json格式文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html
	 * @param path twitter/_doc/0
	 *             twitter/_doc/0?_source=false
	 *             twitter/_doc/0?_source_include=*.id&_source_exclude=entities
	 *             twitter/_doc/0?_source=*.id,retweeted
	 *             twitter/_doc/1?stored_fields=tags,counter
	 *             twitter/_doc/2?routing=user1&stored_fields=tags,counter
	 *             twitter/_doc/1/_source
	 *             GET twitter/_doc/2?routing=user1
	 *
	 * @param path
	 * @return
	 * @throws ElasticSearchException
	 */
	public String getDocumentByPath(String path) throws ElasticSearchException{
		try {
			return this.client.executeHttp(path,ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getDocument);
		}
	}


	/**
	 * 获取json格式文档Source,不带索引元数据
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html
	 * @param path twitter/_doc/1/_source 其中1为文档id
	 * @return
	 * @throws ElasticSearchException
	 */
	public String getDocumentSource(String path) throws ElasticSearchException{
		try {
			return this.client.executeHttp(path,ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getDocument);
		}
	}

	/**
	 * 获取json格式文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html
	 * @param path twitter/_doc/0
	 *             twitter/_doc/0?_source=false
	 *             twitter/_doc/0?_source_include=*.id&_source_exclude=entities
	 *             twitter/_doc/0?_source=*.id,retweeted
	 *             twitter/_doc/1?stored_fields=tags,counter
	 *             twitter/_doc/2?routing=user1&stored_fields=tags,counter
	 *             twitter/_doc/1/_source
	 *             GET twitter/_doc/2?routing=user1
	 *
	 * @param path
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentByPath(String path,Class<T> beanType) throws ElasticSearchException{
		try {
			SearchHit searchResult = this.client.executeRequest(path,null,   new GetDocumentResponseHandler( beanType),ClientUtil.HTTP_GET);
			return ResultUtil.buildObject(searchResult, beanType);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,beanType,ResultUtil.OPERTYPE_getDocument);
		}
	}



	/**
	 * 获取文档Source对象，不带索引元数据
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html
	 * @param path twitter/_doc/1/_source 其中1为文档id
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocumentSource(String path,Class<T> beanType) throws ElasticSearchException{
		try {
			T searchResult = (T) this.client.executeRequest(path,null,   new GetDocumentSourceResponseHandler( beanType),ClientUtil.HTTP_GET);
			return searchResult;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,beanType,ResultUtil.OPERTYPE_getDocument);
		}
	}



	/**
	 * 获取json格式的文档
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public String getDocument(String indexName, String indexType,String documentId) throws ElasticSearchException{
		return getDocument(indexName, indexType,documentId,(Map<String,Object>)null);
	}

	/**
	 * 获取文档对象
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocument(String indexName, String indexType,String documentId,Class<T> beanType) throws ElasticSearchException{
		return getDocument(  indexName,   indexType,  documentId,(Map<String,Object>)null,beanType);
	}

	/**
	 * 获取文档，通过options设置获取文档的参数
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocument(String indexName, String indexType,String documentId,Map<String,Object> options,Class<T> beanType) throws ElasticSearchException{
		try{
			SearchHit searchResult = this.client.executeRequest(BuildTool.buildGetDocumentRequest(  indexName,   indexType,  documentId,  options),null,   new GetDocumentResponseHandler( beanType),ClientUtil.HTTP_GET);

			return ResultUtil.buildObject(searchResult, beanType);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,beanType,ResultUtil.OPERTYPE_getDocument);
		}

	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param path _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param entity
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T> mgetDocuments(String path,String entity,Class<T> type)  throws ElasticSearchException{
		MGetDocs searchResult = (MGetDocs) this.client.executeRequest(path,entity,   new MGetDocumentsSourceResponseHandler( type),ClientUtil.HTTP_POST);

		return ResultUtil.buildObjects(searchResult, type);
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param path _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param templateName
	 * @param params
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T> mgetDocuments(String path,String templateName,Object params,Class<T> type)  throws ElasticSearchException{
		return null;
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param path _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param templateName
	 * @param params
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T> mgetDocuments(String path,String templateName,Map params,Class<T> type)  throws ElasticSearchException{
		return null;
	}

	/**
	 * 获取文档MapSearchHit对象，封装了索引文档的所有属性数据
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public   MapSearchHit getDocumentHit(String indexName, String indexType,String documentId,Map<String,Object> options) throws ElasticSearchException{
		try {
			MapSearchHit searchResult = this.client.executeRequest(BuildTool.buildGetDocumentRequest(indexName, indexType, documentId, options), null, new GetDocumentHitResponseHandler(), ClientUtil.HTTP_GET);
			return searchResult;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,MapSearchHit.class,ResultUtil.OPERTYPE_getDocument);
		}
	}

	/**
	 * 获取文档MapSearchHit对象，封装了索引文档的所有属性数据
	 * @param indexName
	 * @param indexType
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public   MapSearchHit getDocumentHit(String indexName, String indexType,String documentId) throws ElasticSearchException{
		return getDocumentHit(indexName, indexType,documentId,(Map<String,Object> )null);
	}




	@Override
	public String deleteDocument(String indexName, String indexType, String id) throws ElasticSearchException {

		try {
			return this.client.executeHttp(new StringBuilder().append(indexName).append("/").append(indexType).append("/").append(id).toString(), ClientUtil.HTTP_DELETE);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_deleteDocument);
		}
	}
	public   String deleteDocument(String indexName, String indexType, String id,String refreshOption) throws ElasticSearchException{
		try {
			if(refreshOption == null || refreshOption.equals("")){
				return deleteDocument(indexName, indexType, id);
			}
			return this.client.executeHttp(new StringBuilder().append(indexName).append("/").append(indexType).append("/")
					.append(id).append("?").append(refreshOption).toString(),ClientUtil.HTTP_DELETE);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_deleteDocument);
		}
	}
	@Override
	public String executeRequest(String path, String entity) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeRequest(path,entity);
	}
	
	@Override
	public <T> T executeRequest(String path, String entity,ResponseHandler<T> responseHandler) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeRequest(path,entity,  responseHandler);
	}

	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T>  sql(Class<T> beanType , String entity ,Map params) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public  <T> List<T>  sql(Class<T> beanType , String entity ,Object bean) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity

	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T>  sql(Class<T> beanType,  String entity) throws ElasticSearchException {
		SQLRestResponse result = this.client.executeRequest("/_xpack/sql",entity,   new SQLRestResponseHandler());
		return ResultUtil.buildSQLResult(result,beanType);
	}

	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> SQLResult<T>  fetchQuery(Class<T> beanType , String entity ,Map params) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public  <T> SQLResult<T> fetchQuery(Class<T> beanType , String entity , Object bean) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity

	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> SQLResult<T>  fetchQuery(Class<T> beanType,  String entity) throws ElasticSearchException {
		SQLRestResponse result = this.client.executeRequest("/_xpack/sql",entity,   new SQLRestResponseHandler());
		SQLResult<T> datas = ResultUtil.buildFetchSQLResult(  result,  beanType,  (SQLResult<T> )null);
		datas.setClientInterface(this);
		return datas;
	}



	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public   <T> T  sqlObject(Class<T> beanType , String entity ,Map params) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public   <T> T  sqlObject(Class<T> beanType , String entity ,Object bean) throws ElasticSearchException {
		return null;
	}
	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param entity

	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T  sqlObject(Class<T> beanType,  String entity ) throws ElasticSearchException {
		SQLRestResponse result = this.client.executeRequest("/_xpack/sql",entity,   new SQLRestResponseHandler());
		return ResultUtil.buildSQLObject(result,beanType);
	}
	public String closeSQLCursor(String cursor) throws ElasticSearchException {
		return this.client.executeRequest("/_xpack/sql/close",
				new StringBuilder().append("{\"cursor\": \"").append(cursor).append("\"}").toString(),
				new ESStringResponseHandler(),HTTP_POST);
	}

	/**
	 * 发送es restful sql请求/_xpack/sql，获取返回值，返回值类型由beanType决定
	 * @param beanType
	 * @param oldPage
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> SQLResult<T> fetchQueryByCursor(Class<T> beanType, SQLResult<T> oldPage ) throws ElasticSearchException {
		if(oldPage.getCursor() == null ){
			return null;
		}
		SQLRestResponse result = this.client.executeRequest("/_xpack/sql",
														new StringBuilder().append("{\"cursor\": \"").append(oldPage.getCursor()).append("\"}").toString(),
														new SQLRestResponseHandler());

		SQLResult<T> datas = ResultUtil.buildFetchSQLResult(  result,  beanType,  oldPage);
		datas.setClientInterface(this);
		return datas;
	}

	/**
	 * 发送es restful sql请求下一页数据，获取返回值，返回值类型由beanType决定
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/sql-rest.html
	 * @param beanType
	 * @param cursor
	 * @param metas
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> SQLResult<T> fetchQueryByCursor(Class<T> beanType, String cursor, ColumnMeta[] metas) throws ElasticSearchException{
		if(cursor == null ){
			return null;
		}
		SQLRestResponse result = this.client.executeRequest("/_xpack/sql",
				new StringBuilder().append("{\"cursor\": \"").append(cursor).append("\"}").toString(),
				new SQLRestResponseHandler());

		SQLResult<T> datas = ResultUtil.buildFetchSQLResult(  result,  beanType,  metas);
		datas.setClientInterface(this);
		return datas;
	}

	/**
	 * 没有报文的请求处理api
	 * @param path 请求url相对路径，可以带参数
	 * @param action
	 * @return
	 * @throws ElasticSearchException
	 */
	@Override
	public String executeHttp(String path, String action) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeHttp(path,action);
	}
	@Override
	public String executeHttp(String path, String entity,String action) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeHttp(path,entity,action);
	}

	@Override
	public <T> T executeHttp(String path, String entity, String action, Map params, ResponseHandler<T> responseHandler) throws ElasticSearchException {
		return null;
	}

	@Override
	public String executeHttp(String path, String entity, Map params, String action) throws ElasticSearchException {
		return null;
	}

	@Override
	public <T> T executeHttp(String path, String entity, String action, Object bean, ResponseHandler<T> responseHandler) throws ElasticSearchException {
		return null;
	}

	@Override
	public String executeHttp(String path, String entity, Object bean, String action) throws ElasticSearchException {
		return null;
	}

	@Override
	public <T> T executeHttp(String path, String action,ResponseHandler<T> responseHandler) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeHttp(path,action,responseHandler);
	}
	public <T> T discover(String path, String action,ResponseHandler<T> responseHandler) throws ElasticSearchException {
		return this.client.discover(path,action,responseHandler);
	}
	@Override
	public <T> T  executeHttp(String path, String entity,String action,ResponseHandler<T> responseHandler) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return this.client.executeHttp(path,entity,action,responseHandler);
	}
	
	public String getIndexMapping(String index) throws ElasticSearchException{
		return this.getIndexMapping(index,true);

	}
	public <T> T getIndexMapping(String index,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		return this.getIndexMapping(index,true,responseHandler);

	}

	public String getIndexMapping(String index,boolean pretty) throws ElasticSearchException{
		try {
			if (pretty)
				return this.client.executeHttp(index + "/_mapping?pretty", ClientUtil.HTTP_GET);
			else
				return this.client.executeHttp(index + "/_mapping", ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getIndice);
		}
	}

	public <T> T getIndexMapping(String index,boolean pretty,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		try {
			if (pretty)
				return this.client.executeRequest(index + "/_mapping?pretty", null, responseHandler, ClientUtil.HTTP_GET);
			else
				return this.client.executeRequest(index + "/_mapping", null, responseHandler, ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return (T)ResultUtil.hand404HttpRuntimeException(e,Object.class,ResultUtil.OPERTYPE_getIndice);
		}
	}
	/**
	 * 删除索引文档
	 * @param path /twitter/_doc/1
	 *             /twitter/_doc/1?routing=kimchy
	 *             /twitter/_doc/1?timeout=5m
	 * @return
	 */
	public String deleteByPath(String path) throws ElasticSearchException{
		try{
			return this.client.executeHttp(path,ClientUtil.HTTP_DELETE);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_deleteDocument);
		}
	}
	/**
	 * 判断索引是否存在
	 * @param indiceName
	 * @return
	 * @throws ElasticSearchException
	 */
	public boolean existIndice(String indiceName) throws ElasticSearchException{
		try {
			executeHttp(indiceName, ClientInterface.HTTP_HEAD);
			return true;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,boolean.class,ResultUtil.OPERTYPE_existIndice);
		}

	}

	/**
	 * 判断所引类型是否存在
	 * @param indiceName
	 * @param type
	 * @return
	 * @throws ElasticSearchException
	 */
	public boolean existIndiceType(String indiceName,String type) throws ElasticSearchException{
		try {
			executeHttp(new StringBuilder(indiceName).append("/_mapping/").append(type).toString(), ClientInterface.HTTP_HEAD);
			return true;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,boolean.class,ResultUtil.OPERTYPE_existIndiceType);
		}
	}
	public <T> T executeRequest(String path, String templateName,Map params,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		return null;
	}
	
	 
	public <T> T  executeRequest(String path, String templateName,Object params,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		return null;
	}
	@Override
	public MapRestResponse search(String path, String templateName, Map params) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public MapRestResponse search(String path, String templateName, Object params) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public MapRestResponse search(String path, String entity) throws ElasticSearchException {
		MapRestResponse searchResult = this.client.executeRequest(path,entity,   new ElasticSearchMapResponseHandler(  ));
//		if(searchResult instanceof ErrorResponse){
//			throw new ElasticSearchException(SimpleStringUtil.object2json(searchResult));
//		}
		return searchResult;
	}

	public long count(String index,String entity)  throws ElasticSearchException{
		MapRestResponse searchResult = this.client.executeRequest(new StringBuilder().append(index).append("/_count").toString(),
				entity,   new ElasticSearchMapResponseHandler(  ));
//		if(searchResult instanceof ErrorResponse){
//			throw new ElasticSearchException(SimpleStringUtil.object2json(searchResult));
//		}
		return searchResult.getCount();
	}
	public long count(String index,String template,Map params)  throws ElasticSearchException{
		throw new ElasticSearchException("Un implements method");
	}
	public long count(String index,String template,Object params)  throws ElasticSearchException{
		throw new ElasticSearchException("Un implements method");
	}

	public long countAll(String index)  throws ElasticSearchException{
		String queryAll = "{\"query\": {\"match_all\": {}}}";
		MapRestResponse searchResult = this.client.executeRequest(new StringBuilder().append(index).append("/_count").toString(),
				queryAll,   new ElasticSearchMapResponseHandler(  ));
//		if(searchResult instanceof ErrorResponse){
//			throw new ElasticSearchException(SimpleStringUtil.object2json(searchResult));
//		}
		return searchResult.getCount();
	}

	/**
	 * 检索索引所有数据
	 * @param index
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAll(String index,  Class<T> type) throws ElasticSearchException{

		return searchAll(  index,    DEFAULT_FETCHSIZE , type) ;

	}

	/**
	 * 检索索引所有数据
	 * @param index
	 * @param fetchSize
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public  <T> ESDatas<T> searchAll(String index,  int fetchSize ,Class<T> type) throws ElasticSearchException{
		return searchAll(index,  fetchSize ,(ScrollHandler<T>) null,type);
	}

	/**
	 * 检索索引所有数据
	 * @param index
	 * @param fetchSize 指定每批次返回的数据，不指定默认为5000
	 * @param scrollHandler 每批数据处理方法
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAll(String index,  int fetchSize ,ScrollHandler<T> scrollHandler,Class<T> type) throws ElasticSearchException{
		StringBuilder builder = new StringBuilder();
		String queryAll = builder.append("{ \"size\":").append(fetchSize).append(",\"query\": {\"match_all\": {}},\"sort\": [\"_doc\"]}").toString();
		builder.setLength(0);
		return this.scroll(builder.append(index).append("/_search").toString(),queryAll,"10m",type,scrollHandler);
	}



	/**
	 * 检索索引所有数据,每批次返回默认为5000条数据，
	 * @param index
	 * @param scrollHandler 每批数据处理方法
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAll(String index,ScrollHandler<T> scrollHandler , Class<T> type) throws ElasticSearchException{
		return searchAll(  index,  DEFAULT_FETCHSIZE ,  scrollHandler,type);
	}

	/***************************slice searchAll start****************************/

	/**
	 * 并行检索索引所有数据
	 * @param index
	 * @param fetchSize 指定每批次返回的数据，不指定默认为5000
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAllParallel(String index,  int fetchSize ,Class<T> type,int thread) throws ElasticSearchException{
		return searchAllParallel(index,  fetchSize,(ScrollHandler<T>)null,type,thread);
	}

	/**
	 * 并行检索索引所有数据,每批次返回默认为5000条数据，
	 * @param index
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAllParallel(String index,  Class<T> type,int thread) throws ElasticSearchException{
			return searchAllParallel(index,  ClientInterface.DEFAULT_FETCHSIZE,type,thread);
	}

	/**
	 * 并行检索索引所有数据
	 * @param index
	 * @param fetchSize 指定每批次返回的数据，不指定默认为5000
	 * @param scrollHandler 每批数据处理方法
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAllParallel(String index,  final int fetchSize ,ScrollHandler<T> scrollHandler,final Class<T> type,int max) throws ElasticSearchException{
		if(!this.client.isLower5()) {
			SliceScroll sliceScroll = new SliceScroll() {
				@Override
				public String buildSliceDsl(int sliceId, int max) {
					StringBuilder builder = new StringBuilder();
					String sliceDsl = builder.append("{\"slice\": {\"id\": ").append(sliceId).append(",\"max\": ")
							.append(max).append("},\"size\":").append(fetchSize).append(",\"query\": {\"match_all\": {}},\"sort\": [\"_doc\"]}").toString();
					return sliceDsl;
//				return buildSliceDsl(i,max, params, dslTemplate);
				}
			};

			return _slice(index + "/_search", scrollHandler, type, max, "10m", sliceScroll);
		}
		else{
			StringBuilder builder = new StringBuilder();
			String queryAll = builder.append("{ \"size\":").append(fetchSize).append(",\"query\": {\"match_all\": {}},\"sort\": [\"_doc\"]}").toString();
			builder.setLength(0);
			return this.scrollParallel(builder.append(index).append("/_search").toString(),queryAll,"10m",type,scrollHandler);
		}

	}
	/**
	 * 并行检索索引所有数据
	 * @param path
	 * @param scrollHandler 每批数据处理方法
	 * @param type
	 * @param max 并行度，线程数
	 * @param sliceScroll
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	protected <T> ESDatas<T> _slice(String path,  ScrollHandler<T> scrollHandler,final Class<T> type,int max,
								  final String scroll,SliceScroll sliceScroll) throws ElasticSearchException{



		long starttime = System.currentTimeMillis();
		//scroll slice分页检索

//		final CountDownLatch countDownLatch = new CountDownLatch(max);//线程任务完成计数器，每个线程对应一个sclice,每运行完一个slice任务,countDownLatch计数减去1

		final String _path = path.indexOf('?') < 0 ? new StringBuilder().append(path).append("?scroll=").append(scroll).toString() :
				new StringBuilder().append(path).append("&scroll=").append(scroll).toString();

		ExecutorService executorService = this.client.getSliceScrollQueryExecutorService();
		List<Future> tasks = new ArrayList<Future>();
		//辅助方法，用来累计每次scroll获取到的记录数
		final ParallelSliceScrollResult sliceScrollResult = new ParallelSliceScrollResult();
		if(scrollHandler != null)
			sliceScrollResult.setScrollHandler(scrollHandler);

		try {
//			SliceRunTask<T> sliceRunTask = null;
			SerialContext serialContext = ESInnerHitSerialThreadLocal.buildSerialContext();
			for (int j = 0; j < max; j++) {//启动max个线程，并行处理每个slice任务
				if(sliceScrollResult.isBreaked())
					break;
				String sliceDsl = sliceScroll.buildSliceDsl(j,max);
//				final String sliceDsl = builder.append("{\"slice\": {\"id\": ").append(i).append(",\"max\": ")
//									.append(max).append("},\"size\":").append(fetchSize).append(",\"query\": {\"match_all\": {}}}").toString();

				runSliceTask(j,_path,sliceDsl,scroll,type,sliceScrollResult,
						executorService,tasks,serialContext );
			}
		}
		finally {
			waitTasksComplete(tasks);
		}

		//打印处理耗时和实际检索到的数据
		if(logger.isDebugEnabled()) {
			long endtime = System.currentTimeMillis();
			logger.debug("Slice scroll query耗时：" + (endtime - starttime) + ",realTotalSize：" + sliceScrollResult.getRealTotalSize());
		}


		sliceScrollResult.complete();
		return sliceScrollResult.getSliceResponse();
	}
	public <T> void runSliceTask(int sliceId,String path,String sliceDsl,  String scroll,  Class<T> type,  ParallelSliceScrollResult sliceScrollResult,ExecutorService executorService,List<Future> tasks,SerialContext serialContext ){
		SliceRunTask<T> sliceRunTask = new SliceRunTask<T>(this,sliceId,path,sliceDsl,scroll,type,sliceScrollResult,   serialContext);
		tasks.add(executorService.submit(sliceRunTask));
	}
	protected void waitTasksComplete(final List<Future> tasks){

		for (Future future : tasks) {
			try {
				future.get();
			} catch (ExecutionException e) {
				logger.error("",e);
			}catch (Exception e) {
				logger.error("",e);
			}
		}
		tasks.clear();

	}

	/**
	 * 并行检索索引所有数据,每批次返回默认为5000条数据，
	 * @param index
	 * @param scrollHandler 每批数据处理方法
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchAllParallel(String index,ScrollHandler<T> scrollHandler,  Class<T> type,int thread) throws ElasticSearchException{
		return searchAllParallel(index,  DEFAULT_FETCHSIZE,scrollHandler,type,thread);
	}

	public  <T> void _doSliceScroll(int i,String path,
									String entity,
									String scroll,Class<T> type,

									SliceScrollResultInf<T> sliceScrollResult,boolean parallel) throws Exception {
		List<Future> tasks = null;
		try{
			if(sliceScrollResult.isBreaked()){ //other slice query has breaked continue to slice scroll query.
				return;
			}
			RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
			ESDatas<T> sliceResponse = ResultUtil.buildESDatas(result,type);
			int taskId = 0;
			List<T> sliceDatas = sliceResponse.getDatas();
			String scrollId = sliceResponse.getScrollId();
			ExecutorService executorService = parallel ?client.getScrollQueryExecutorService():null;
//			System.out.println("sliceDatas:"+i+":" + sliceDatas);
//			System.out.println("scrollId:"+i+":" + scrollId);
			Set<String> scrollIds = null;
			if (scrollId != null) {
				scrollIds = new TreeSet<String>();
				scrollIds.add(scrollId);
			}
			boolean useDefaultHandler = false;
			if (sliceDatas != null && sliceDatas.size() > 0) {//每页100条记录，迭代scrollid，遍历scroll分页结果
				tasks = new ArrayList<Future>();
				ScrollHandler<T> _scrollHandler = sliceScrollResult.getScrollHandler();
				HandlerInfo handlerInfo = new HandlerInfo();
				handlerInfo.setTaskId(taskId);
//				handlerInfo.setScrollId(scrollId);
				handlerInfo.setSliceId(i);
				ScrollTask<T> scrollTask = null;
				taskId ++;
				if (_scrollHandler == null) {
					useDefaultHandler = true;
					_scrollHandler = sliceScrollResult.setScrollHandler(sliceResponse,handlerInfo);
					sliceScrollResult.incrementSize(sliceDatas.size());//统计实际处理的文档数量
				}
				else {
					if(parallel) {
//						scrollTask = new  ScrollTask<T>(_scrollHandler, sliceResponse, handlerInfo,sliceScrollResult);
//						tasks.add(executorService.submit(scrollTask));
						runSliceScrollTask( tasks,  _scrollHandler,
								sliceResponse,   handlerInfo,
								sliceScrollResult,
								executorService);
					}
					else {
						_scrollHandler.handle(sliceResponse, handlerInfo);
						sliceScrollResult.incrementSize(sliceDatas.size());//统计实际处理的文档数量
					}
					sliceScrollResult.setSliceResponse(sliceResponse);
				}

				ESDatas<T> _sliceResponse = null;
				List<T> _sliceDatas = null;
				do {
					if(sliceScrollResult.isBreaked()){
						break;
					}
					_sliceResponse = searchScroll(scroll, scrollId, type);
					String sliceScrollId = _sliceResponse.getScrollId();
					if (sliceScrollId != null)
						scrollIds.add(sliceScrollId);
					//处理完毕后清除scroll上下文信息

					_sliceDatas = _sliceResponse.getDatas();
					if (_sliceDatas == null || _sliceDatas.size() == 0) {
						break;
					}
					handlerInfo = new HandlerInfo();
					handlerInfo.setTaskId(taskId);
					handlerInfo.setSliceId(i);
					handlerInfo.setScrollId(scrollId);
					taskId ++;
					scrollId = sliceScrollId;
					if(!useDefaultHandler ) {
						if(parallel) {
//							scrollTask = new ScrollTask<T>(_scrollHandler, sliceResponse, handlerInfo,sliceScrollResult);
//							tasks.add(executorService.submit(scrollTask));
							runSliceScrollTask( tasks,  _scrollHandler,
									_sliceResponse,   handlerInfo,
									  sliceScrollResult,
									  executorService);
						}
						else{
							_scrollHandler.handle(_sliceResponse, handlerInfo);
							sliceScrollResult.incrementSize(_sliceDatas.size());//统计实际处理的文档数量
						}
					}
					else {
						_scrollHandler.handle(_sliceResponse, handlerInfo);
						sliceScrollResult.incrementSize(_sliceDatas.size());//统计实际处理的文档数量
					}

				} while (true);
			}
			if(tasks != null && tasks.size() > 0)
				this.waitTasksComplete(tasks);
			//处理完毕后清除scroll上下文信息
			if(scrollIds != null && scrollIds.size() > 0) {
				try {
					deleteScrolls(scrollIds);
				}
				catch (Exception e){

				}
//			System.out.println(scrolls);
			}

		} catch (ElasticSearchException e) {
			throw e;
		} catch (Exception e) {
			throw new ElasticSearchException("slice query task["+i+"] failed:",e);
		}
		finally {
			if(tasks != null && tasks.size() > 0)
				this.waitTasksComplete(tasks);
		}
	}

	public <T> void runSliceScrollTask(List<Future> tasks,ScrollHandler<T> _scrollHandler,
								  ESDatas<T> sliceResponse, HandlerInfo handlerInfo,
								  SliceScrollResultInf<T> sliceScrollResult,
								  ExecutorService executorService){
		ScrollTask<T> scrollTask = new ScrollTask<T>(_scrollHandler, sliceResponse, handlerInfo,sliceScrollResult);
		tasks.add(executorService.submit(scrollTask));
	}

	/************************************slice searchAll end*****************************/


	@Override
	public TermRestResponse termSuggest(String path, String entity) throws ElasticSearchException {
		TermRestResponse searchResult = this.client.executeRequest(path,entity,   new ElasticSearchTermResponseHandler( ));
		return searchResult;
	}

	@Override
	public PhraseRestResponse phraseSuggest(String path, String entity) throws ElasticSearchException {
		PhraseRestResponse searchResult = this.client.executeRequest(path,entity,   new ElasticSearchPhraseResponseHandler( ));
		return searchResult;
	}

	@Override
	public CompleteRestResponse complateSuggest(String path, String entity, Class<?> type) throws ElasticSearchException {
		RestResponse searchResult = this.client.executeRequest(path,entity,   new CompleteElasticSearchResponseHandler( type));
		return (CompleteRestResponse)searchResult;
	}

	@Override
	public CompleteRestResponse complateSuggest(String path, String entity) throws ElasticSearchException {
		RestResponse searchResult = this.client.executeRequest(path,entity,   new CompleteElasticSearchResponseHandler( Map.class));
		return (CompleteRestResponse)searchResult;
	}

	public CompleteRestResponse complateSuggest(String path, String templateName,Map params) throws ElasticSearchException{
		return null;
	}

	public CompleteRestResponse complateSuggest(String path, String templateName,Object params) throws ElasticSearchException{
		return null;
	}
	@Override
	public TermRestResponse termSuggest(String path, String templateName, Object params) throws ElasticSearchException {
		return null;
	}

	@Override
	public PhraseRestResponse phraseSuggest(String path, String templateName, Object params) throws ElasticSearchException {
		return null;
	}

	@Override
	public TermRestResponse termSuggest(String path, String templateName, Map params) throws ElasticSearchException {
		return null;
	}

	@Override
	public PhraseRestResponse phraseSuggest(String path, String templateName, Map params) throws ElasticSearchException {
		return null;
	}



	@Override
	public CompleteRestResponse complateSuggest(String path, String templateName, Object params, Class<?> type) throws ElasticSearchException {
		return null;
	}

	@Override
	public CompleteRestResponse complateSuggest(String path, String templateName, Map params, Class<?> type) throws ElasticSearchException {
		return null;
	}

	@Override
	public RestResponse search(String path, String templateName, Map params,Class<?> type) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RestResponse search(String path, String templateName, Object params,Class<?> type) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RestResponse search(String path, String entity,Class<?> type) throws ElasticSearchException {
		RestResponse searchResult = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
//		if(searchResult instanceof ErrorResponse){
//			throw new ElasticSearchException(SimpleStringUtil.object2json(searchResult));
//		}
		return searchResult;
	}

	/**
	 * scroll search
	 * https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-scroll.html
	 * @param scroll
	 * @param scrollId
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> searchScroll(String scroll,String scrollId ,Class<T> type) throws ElasticSearchException{

		if(!this.client.isV1()) {
			StringBuilder entity = new StringBuilder();
			entity.append("{\"scroll\" : \"").append(scroll).append("\",\"scroll_id\" : \"").append(scrollId).append("\"}");
			RestResponse result = this.client.executeRequest("_search/scroll", entity.toString(), new ElasticSearchResponseHandler(type));
			return ResultUtil.buildESDatas(result,type);
		}
		else {
			StringBuilder path = new StringBuilder();
			path.append("_search/scroll?scroll=").append( scroll ).append( "&scroll_id=" ).append( scrollId);
			RestResponse result = this.client.executeHttp(path.toString(), ClientUtil.HTTP_GET, new ElasticSearchResponseHandler(type));
			return ResultUtil.buildESDatas(result, type);
		}
	}

	/**
	 * scroll search
	 * https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-scroll.html
	 * @param scroll
	 * @param scrollId

	 * @return
	 * @throws ElasticSearchException
	 */
	public String searchScroll(String scroll,String scrollId ) throws ElasticSearchException{
		if(!this.client.isV1()) {
			StringBuilder entity = new StringBuilder();
			entity.append("{\"scroll\" : \"").append(scroll).append("\",\"scroll_id\" : \"").append(scrollId).append("\"}");
			String result = this.client.executeHttp("_search/scroll",entity.toString(),ClientUtil.HTTP_GET);
			return result;
		}
		else {
			StringBuilder path = new StringBuilder();
			path.append("_search/scroll?scroll=").append( scroll ).append( "&scroll_id=" ).append( scrollId);
			String result = this.client.executeHttp(path.toString(), ClientUtil.HTTP_GET);
			return result;
		}
//		StringBuilder entity = new StringBuilder();
//		entity.append("{\"scroll\" : \"").append(scroll).append("\",\"scroll_id\" : \"").append(scrollId).append("\"}");
//		String result = this.client.executeHttp("_search/scroll",entity.toString(),ClientUtil.HTTP_GET);
//		return result;
	}


	/**
	 * 清理scrollId
	 * @param scrollIds
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteScrolls(String [] scrollIds) throws ElasticSearchException{
		if(scrollIds == null || scrollIds.length == 0)
			return null;
		if(!client.isV1()) {
			StringBuilder entity = new StringBuilder();
			entity.append("{\"scroll_id\" : [");
			for (int i = 0; i < scrollIds.length; i++) {
				String scrollId = scrollIds[i];
				if (i > 0)
					entity.append(",");
				entity.append("\"").append(scrollId).append("\"");
			}
			entity.append("]}");
			String result = this.client.executeHttp("_search/scroll", entity.toString(), ClientUtil.HTTP_DELETE);
			return result;
		}
		else{
//			for (int i = 0; i < scrollIds.length; i++) {
//				String scrollId = scrollIds[i];
//				this.client.executeHttp("_search/scroll?scroll_id="+scrollId, ClientUtil.HTTP_DELETE);
//			}

			if(logger.isTraceEnabled()){
				logger.trace(new StringBuilder().append("Elasticsearch ").append(client.getEsVersion() )
						.append( " do not support delete scrollId.").toString());
			}
			return "";
		}
	}
	public String deleteScrolls(List<String> scrollIds) throws ElasticSearchException{
		if(scrollIds == null || scrollIds.size() == 0 )
			return null;
		return deleteScrolls(scrollIds.iterator());
	}
	private String deleteScrolls(Iterator<String> scrollIds) throws ElasticSearchException{

		if(!client.isV1()) {
			StringBuilder entity = new StringBuilder();
			entity.append("{\"scroll_id\" : [");

			int i = 0;
			for (; scrollIds.hasNext(); ) {

				String scrollId = scrollIds.next();
				if (i > 0)
					entity.append(",");
				i ++;
				entity.append("\"").append(scrollId).append("\"");
			}
			entity.append("]}");
			String result = this.client.executeHttp("_search/scroll", entity.toString(), ClientUtil.HTTP_DELETE);
			return result;
		}
		else{
//			String result = null;
//////			for (int i = 0; i < scrollIds.size(); i++) {
//////				String scrollId = scrollIds.get(i);
//////				result = this.client.executeHttp("_search/scroll?scroll_id="+scrollId, ClientUtil.HTTP_DELETE);
//////			}
//////			return result;
			if(logger.isTraceEnabled()){
				logger.trace(new StringBuilder().append("Elasticsearch ").append(client.getEsVersion() )
						.append( " do not support delete scrollId.").toString());
			}
			return "";

		}
	}
	/**
	 * 清理scrollId
	 * @param scrollIds
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteScrolls(Set<String> scrollIds) throws ElasticSearchException{
		if(scrollIds == null || scrollIds.size() == 0 )
			return null;
		return deleteScrolls(scrollIds.iterator());
	}

	/**
	 * 清理all scrollId
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteAllScrolls() throws ElasticSearchException{
		String result = this.client.executeHttp("_search/scroll/_all",ClientUtil.HTTP_DELETE);
		return result;
	}


	public <T> ESDatas<T> searchList(String path, String templateName, Map params, Class<T> type) throws ElasticSearchException {
	 	return null;
	}


	public <T> ESDatas<T> searchList(String path, String templateName, Object params,Class<T> type) throws ElasticSearchException {
	 	return null;
	}
	public <T> ESDatas<T> searchList(String path, String entity, Class<T> type) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
		return ResultUtil.buildESDatas(result,type);
	}
	@Override
	public <T> ESDatas<T> searchList(String path, Class<T> type) throws ElasticSearchException {
		RestResponse result = this.client.executeRequest(path, (String)null, new ElasticSearchResponseHandler( type),ClientUtil.HTTP_GET);
		return ResultUtil.buildESDatas(result,type);
	}


	/**
	 * 一次性返回scroll检索结果
	 * @param path
	 * @param dslTemplate
	 * @param scroll
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public  <T> ESDatas<T> scroll(String path,String dslTemplate,String scroll,Map params,Class<T> type) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}


	/**
	 * scroll检索,每次检索结果列表交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate
	 * @param scroll
	 * @param type
	 * @param scrollHandler
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scroll(String path,String dslTemplate,String scroll,Map params,Class<T> type,ScrollHandler<T> scrollHandler)
			throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}


	public <T> ESDatas<T> scrollParallel(String path,String dslTemplate,String scroll,Map params,Class<T> type,ScrollHandler<T> scrollHandler)
			throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}



	/**
	 * 一次性返回scroll检索结果
	 * @param path like agentstat/_search
	 * @param entity
	 * @param scroll
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scroll(String path,String entity,String scroll ,Class<T> type) throws ElasticSearchException{
		return scroll(path,entity,  scroll , type,(ScrollHandler<T>)null);

	}


	public <T> ESDatas<T> scrollParallel(String path,String entity,String scroll ,Class<T> type,ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		List<Future> tasks = null;
		try {
			path = path.indexOf('?') < 0 ? new StringBuilder().append(path).append("?scroll=").append(scroll).toString() :
					new StringBuilder().append(path).append("&scroll=").append(scroll).toString();

			RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
			ESDatas<T> response =  ResultUtil.buildESDatas(result,type);
			int taskId = 0;
			boolean useDefaultScrollHandler = false;
			ExecutorService executorService = this.client.getScrollQueryExecutorService();
			List<T> datas = response.getDatas();//第一页数据
			ScrollTask<T> scrollTask = null;
			BreakableScrollHandler breakedScrollHandler = null;
			if(scrollHandler == null){
				scrollHandler = new DefualtScrollHandler<T>(response);
				useDefaultScrollHandler = true;
			}
			else{
				if(scrollHandler instanceof BreakableScrollHandler){
					breakedScrollHandler = (BreakableScrollHandler)scrollHandler;
				}
				if (datas != null && datas.size() > 0) {
					tasks = new ArrayList<Future>();
					HandlerInfo handlerInfo = new HandlerInfo();
					handlerInfo.setTaskId(taskId);
					taskId++;
//					scrollTask = new ScrollTask<T>(scrollHandler, response, handlerInfo);
//
//					tasks.add(executorService.submit(scrollTask));
					runScrollTask( tasks, scrollHandler,
							response,   handlerInfo,
							  executorService);
//				scrollHandler.handle(response);
				}
			}


			Set<String> scrollIds = null;//用于记录每次scroll的scrollid，便于检索完毕后清除
//			long totalSize = response.getTotalSize();//总记录数
			String scrollId = response.getScrollId();//第一次的scrollid
			if (scrollId != null) {
				scrollIds = new TreeSet<String>();
				scrollIds.add(scrollId);
			}
//		System.out.println("totalSize:"+totalSize);
//		System.out.println("scrollId:"+scrollId);
			if (datas != null && datas.size() > 0) {//每页1000条记录，通过迭代scrollid，遍历scroll分页结果
				ESDatas<T> _response = null;
				List<T> _datas = null;
				do {
					 if(breakedScrollHandler != null && breakedScrollHandler.isBreaked()){
					 	break;
					 }
					_response = searchScroll(scroll, scrollId, type);

					String _scrollId = _response.getScrollId();//每页的scrollid
					if (scrollId != null)
						scrollIds.add(_scrollId);
					_datas = _response.getDatas();//每页的纪录数
					if (_datas == null || _datas.size() == 0) {
						break;
					} else {
						HandlerInfo handlerInfo = new HandlerInfo();
						handlerInfo.setTaskId(taskId);
						handlerInfo.setScrollId(scrollId);
						taskId ++;
						if(!useDefaultScrollHandler) {
							taskId++;
//							scrollTask = new ScrollTask<T>(scrollHandler, response, handlerInfo);
//							tasks.add(executorService.submit(scrollTask));
							runScrollTask( tasks, scrollHandler,
									_response,   handlerInfo,
									executorService);
						}
						else {
							scrollHandler.handle(_response,handlerInfo);
						}
					}
					scrollId = _scrollId;

				} while (true);
			}
			//等待所有异步任务执行完毕
			if(tasks != null && tasks.size() > 0)
				this.waitTasksComplete(tasks);

			//清除scroll上下文信息,虽然说超过1分钟后，scrollid会自动失效，
			//但是手动删除不用的scrollid，是一个好习惯
			if (scrollIds != null && scrollIds.size() > 0) {
				try {
					deleteScrolls(scrollIds);
				}
				catch (Exception e){

				}
			}
			if(!useDefaultScrollHandler)//结果自行处理，所以清空默认结果
				response.setDatas(null);
			return response;
		}
		catch (ElasticSearchException e){
			throw e;
		}
		catch (Exception e){
			throw new ElasticSearchException(e);
		}
		finally {
			if(tasks != null && tasks.size() > 0)
				this.waitTasksComplete(tasks);
		}
	}

	@Override
	public String forcemerge(String indices) {
		String result = this.client.executeHttp(new StringBuilder().append(indices)
				.append("/_forcemerge").toString(),ClientUtil.HTTP_POST);
		return result;
	}

	@Override
	public String forcemerge(String indices, MergeOption mergeOption) {
		StringBuilder action = new StringBuilder().append(indices)
				.append("/_forcemerge");
		if(mergeOption != null) {
			boolean seted = false;
			if(mergeOption.getMaxnumSegments() != null) {
				seted = true;
				action.append("?max_num_segments=").append(mergeOption.getMaxnumSegments());
			}
			if(mergeOption.getFlush() != null){
				if(seted)
					action.append("&");
				else {
					seted = true;
					action.append("?");
				}
				action.append("flush=").append(mergeOption.getFlush());
			}

			if(mergeOption.getOnlyExpungeDeletes() != null){
				if(seted)
					action.append("&");
				else {
					action.append("?");
				}
				action.append("only_expunge_deletes=").append(mergeOption.getOnlyExpungeDeletes());
			}

		}
		String result = this.client.executeHttp(action.toString(),ClientUtil.HTTP_POST);
		return result;
	}

	@Override
	public String forcemerge() {
		String result = this.client.executeHttp("/_forcemerge",ClientUtil.HTTP_POST);
		return result;
	}

	@Override
	public String forcemerge(MergeOption mergeOption) {
		StringBuilder action = new StringBuilder()
				.append("/_forcemerge");
		if(mergeOption != null) {
			boolean seted = false;
			if(mergeOption.getMaxnumSegments() != null) {
				seted = true;
				action.append("?max_num_segments=").append(mergeOption.getMaxnumSegments());
			}
			if(mergeOption.getFlush() != null){
				if(seted)
					action.append("&");
				else {
					seted = true;
					action.append("?");
				}
				action.append("flush=").append(mergeOption.getFlush());
			}

			if(mergeOption.getOnlyExpungeDeletes() != null){
				if(seted)
					action.append("&");
				else {
					action.append("?");
				}
				action.append("only_expunge_deletes=").append(mergeOption.getOnlyExpungeDeletes());
			}

		}
		String result = this.client.executeHttp(action.toString(),ClientUtil.HTTP_POST);
		return result;
	}

	public <T> void runScrollTask(List<Future> tasks,ScrollHandler<T> _scrollHandler,
									   ESDatas<T> sliceResponse, HandlerInfo handlerInfo,
									   ExecutorService executorService){
		ScrollTask<T> scrollTask = new ScrollTask<T>(_scrollHandler, sliceResponse, handlerInfo);
		tasks.add(executorService.submit(scrollTask));
	}
	/**
	 * scroll检索,每次检索结果交给scrollHandler回调函数处理
	 * @param path
	 * @param entity
	 * @param scroll
	 * @param type
	 * @param scrollHandler
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scroll(String path,String entity,String scroll ,Class<T> type,ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		try {
			path = path.indexOf('?') < 0 ? new StringBuilder().append(path).append("?scroll=").append(scroll).toString() :
					new StringBuilder().append(path).append("&scroll=").append(scroll).toString();

			RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
			ESDatas<T> response =  ResultUtil.buildESDatas(result,type);
			int taskId = 0;
			boolean useDefaultScrollHandler = false;
			BreakableScrollHandler breakedScrollHandler = null;
			if(scrollHandler == null){
				scrollHandler = new DefualtScrollHandler<T>(response);
				useDefaultScrollHandler = true;
			}
			else{
				if(scrollHandler instanceof BreakableScrollHandler){
					breakedScrollHandler = (BreakableScrollHandler)scrollHandler;
				}
				HandlerInfo handlerInfo = new HandlerInfo();
				handlerInfo.setTaskId(taskId);
				taskId ++;
				scrollHandler.handle(response,handlerInfo);
			}
			List<T> datas = response.getDatas();//第一页数据

			Set<String> scrollIds = null;//用于记录每次scroll的scrollid，便于检索完毕后清除
//			long totalSize = response.getTotalSize();//总记录数
			String scrollId = response.getScrollId();//第一次的scrollid
			if (scrollId != null) {
				scrollIds = new TreeSet<String>();
				scrollIds.add(scrollId);
			}
//		System.out.println("totalSize:"+totalSize);
//		System.out.println("scrollId:"+scrollId);
			if (datas != null && datas.size() > 0) {//每页1000条记录，通过迭代scrollid，遍历scroll分页结果
				ESDatas<T> _response = null;
				List<T> _datas = null;
				do {
					if(breakedScrollHandler != null && breakedScrollHandler.isBreaked()){
						break;
					}
					_response = searchScroll(scroll, scrollId, type);

					String _scrollId = _response.getScrollId();//每页的scrollid
					if (scrollId != null)
						scrollIds.add(_scrollId);

					_datas = _response.getDatas();//每页的纪录数
					if (_datas == null || _datas.size() == 0) {
						break;
					} else {
						HandlerInfo handlerInfo = new HandlerInfo();
						handlerInfo.setTaskId(taskId);
						handlerInfo.setScrollId(scrollId);
						taskId ++;
						scrollHandler.handle(_response,handlerInfo);
					}
					scrollId = _scrollId;

				} while (true);
			}

			//清除scroll上下文信息,虽然说超过1分钟后，scrollid会自动失效，
			//但是手动删除不用的scrollid，是一个好习惯
			if (scrollIds != null && scrollIds.size() > 0) {
				try {
					deleteScrolls(scrollIds);
				}
				catch (Exception e){

				}
			}
			if(!useDefaultScrollHandler)//结果自行处理，所以清空默认结果
				response.setDatas(null);
			return response;
		}
		catch (ElasticSearchException e){
			throw e;
		}
		catch (Exception e){
			throw new ElasticSearchException(e);
		}
	}


	/**
	 * slice scroll并行检索，每次检索结果列表交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate here is a example dsltemplate: must contain id,max varialbe
	 *    <property name="scrollSliceQuery">
	 *         <![CDATA[
	 *          {
	 *            "slice": {
	 *                 "id": #[id],
	 *                 "max": #[max]
	 *             },
	 *             "size":#[size],
	 *             "query": {
	 *                 "term" : {
	 *                     "gc.jvmGcOldCount" : 3
	 *                 }
	 *             }
	 *         }
	 *         ]]>
	 *     </property>
	 * @param scroll
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scrollSlice(String path,final String dslTemplate,final Map params ,
									  final String scroll  ,final Class<T> type) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}
	public <T> ESDatas<T> scrollSliceParallel(String path,final String dslTemplate,final Map params ,
									  final String scroll  ,final Class<T> type) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}

	/**
	 * slice scroll并行检索，每次检索结果列表交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate here is a example dsltemplate: must contain id,max varialbe
	 *    <property name="scrollSliceQuery">
	 *         <![CDATA[
	 *          {
	 *            "slice": {
	 *                 "id": #[id],
	 *                 "max": #[max]
	 *             },
	 *             "size":#[size],
	 *             "query": {
	 *                 "term" : {
	 *                     "gc.jvmGcOldCount" : 3
	 *                 }
	 *             }
	 *         }
	 *         ]]>
	 *     </property>
	 * @param scroll
	 * @param type
	 * @param scrollHandler 每次检索结果会被异步交给handle来处理
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scrollSlice(String path,final String dslTemplate,final Map params ,
									  final String scroll  ,final Class<T> type,
									  ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}

	public <T> ESDatas<T> scrollSliceParallel(String path,final String dslTemplate,final Map params ,
									  final String scroll  ,final Class<T> type,
									  ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}

	/**
	 * scroll检索,每次检索结果交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate
	 * @param scroll
	 * @param type
	 * @param scrollHandler
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public   <T> ESDatas<T> scroll(String path,String dslTemplate,String scroll,Object params,Class<T> type,ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}
	public   <T> ESDatas<T> scrollParallel(String path,String dslTemplate,String scroll,Object params,Class<T> type,ScrollHandler<T> scrollHandler) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}

	/**
	 * 一次性返回scroll检索结果
	 * @param path
	 * @param dslTemplate
	 * @param scroll
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public   <T> ESDatas<T> scroll(String path,String dslTemplate,String scroll,Object params,Class<T> type) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}
	/**
	 * slice scroll并行检索，每次检索结果列表交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate here is a example dsltemplate: must contain id,max varialbe
	 *    <property name="scrollSliceQuery">
	 *         <![CDATA[
	 *          {
	 *            "slice": {
	 *                 "id": #[id],
	 *                 "max": #[max]
	 *             },
	 *             "size":#[size],
	 *             "query": {
	 *                 "term" : {
	 *                     "gc.jvmGcOldCount" : 3
	 *                 }
	 *             }
	 *         }
	 *         ]]>
	 *     </property>
	 * @param scroll
	 * @param type
	 * @param scrollHandler 每次检索结果会被异步交给handle来处理
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scrollSlice(String path,final String dslTemplate,final Object params ,int max,
									  final String scroll  ,final Class<T> type,
									  ScrollHandler<T> scrollHandler,boolean parallel) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}
	/**
	 * slice scroll并行检索，每次检索结果列表交给scrollHandler回调函数处理
	 * @param path
	 * @param dslTemplate here is a example dsltemplate: must contain id,max varialbe
	 *    <property name="scrollSliceQuery">
	 *         <![CDATA[
	 *          {
	 *            "slice": {
	 *                 "id": #[id],
	 *                 "max": #[max]
	 *             },
	 *             "size":#[size],
	 *             "query": {
	 *                 "term" : {
	 *                     "gc.jvmGcOldCount" : 3
	 *                 }
	 *             }
	 *         }
	 *         ]]>
	 *     </property>
	 * @param scroll
	 * @param type
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> ESDatas<T> scrollSlice(String path,final String dslTemplate,final Object params ,int max,
									  final String scroll  ,final Class<T> type,boolean parallel) throws ElasticSearchException{
		throw new java.lang.UnsupportedOperationException();
	}

	public <T> T searchObject(String path, String templateName, Map params, Class<T> type) throws ElasticSearchException {
	 	return null;
	}

	public <T> T searchObject(String path, String templateName, Object params,Class<T> type) throws ElasticSearchException {
	 	return null;
	}

	public <T> T searchObject(String path, String entity, Class<T> type) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
		return ResultUtil.buildObject(result, type);
	}
	@Override
	public <T> T searchObject(String path, Class<T> type) throws ElasticSearchException {
		RestResponse result = this.client.executeRequest(path, (String )null,new ElasticSearchResponseHandler( type),ClientUtil.HTTP_GET);
		return ResultUtil.buildObject(result, type);
	}



	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Map params,Class<T> type,String aggs,String stats) throws ElasticSearchException{

		return null;
	}

	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Object params,Class<T> type,String aggs,String stats) throws ElasticSearchException{
		return null;
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Map params,Class<T> type,String aggs,String stats,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{

		return null;
	}

	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Object params,Class<T> type,String aggs,String stats,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{
		return null;
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Class<T> type,String aggs,String stats) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( ));
		return ResultUtil.buildESAggDatas(result,type,aggs,stats,(ESAggBucketHandle<T>)null);
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Class<T> type,String aggs,String stats,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( ));
		return ResultUtil.buildESAggDatas(result,type,aggs,stats,aggBucketHandle);
	}

	/****************/

	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Map params,Class<T> type,String aggs) throws ElasticSearchException{

		return null;
	}

	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Object params,Class<T> type,String aggs) throws ElasticSearchException{
		return null;
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Map params,Class<T> type,String aggs,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{

		return null;
	}

	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Object params,Class<T> type,String aggs,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{
		return null;
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Class<T> type,String aggs) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( ));
		return ResultUtil.buildESAggDatas(result,type,aggs,null,(ESAggBucketHandle<T>)null);
	}
	public <T extends AggHit> ESAggDatas<T> searchAgg(String path,String entity,Class<T> type,String aggs,ESAggBucketHandle<T> aggBucketHandle) throws ElasticSearchException{
		RestResponse result = this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( ));
		return ResultUtil.buildESAggDatas(result,type,aggs,null,aggBucketHandle);
	}

	@Override
	public String createTempate(String template, String entity) throws ElasticSearchException {
		return this.client.executeHttp("_template/"+this.handleIndexName(template),entity,ClientUtil.HTTP_PUT);
	}

	@Override
	public String createTempate(String template, String templateName,Object params) throws ElasticSearchException {
		return null;
	}

	@Override
	public String createTempate(String template, String templateName,Map params) throws ElasticSearchException {
		return null;
	}


	@Override
	public RestResponse search(String path, String templateName, Map params,ESTypeReferences type) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RestResponse search(String path, String templateName, Object params, ESTypeReferences type) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RestResponse search(String path, String entity,ESTypeReferences type) throws ElasticSearchException {
		return this.client.executeRequest(path,entity,   new ElasticSearchResponseHandler( type));
	}
	
	
	 public Map<String,Object>  searchMap(String path, String templateName,Map params) throws ElasticSearchException{
		 return null;
	 }
		
	 
		public Map<String,Object> searchMap(String path, String templateName,Object params) throws ElasticSearchException{
			return null;
		}
		/**
		  * 
		  * @param path
		  * @param entity
		  * @return
		  */
		 @SuppressWarnings("unchecked")
		public Map<String,Object> searchMap(String path, String entity) throws ElasticSearchException {
			 // TODO Auto-generated method stub
			 return this.client.executeRequest(path,entity,  new ESMapResponseHandler());
		 }
	
	 public String dropIndice(String index)  throws ElasticSearchException {
		 	try {
				return this.client.executeHttp(index + "?pretty", ClientUtil.HTTP_DELETE);
			}
			catch(ElasticSearchException e){
				return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_dropIndice);
			}
		 
	 }
	 
	 
	 /**
	  * 更新索引定义：my_index/_mapping
	  * https://www.elastic.co/guide/en/elasticsearch/reference/7.0/indices-put-mapping.html
	  * @param indexMapping
	  * @return
	  * @throws ElasticSearchException
	  */
	 public String updateIndiceMapping(String action,String indexMapping)  throws ElasticSearchException {
	 	try {
			return this.client.executeHttp(action, indexMapping, ClientUtil.HTTP_PUT);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_updateIndiceMapping);
		}
	 }
	 
	 /**
	  * 创建索引定义
	  * curl -XPUT 'localhost:9200/test?pretty' -H 'Content-Type: application/json' -d'
		{
		    "settings" : {
		        "number_of_shards" : 1
		    },
		    "mappings" : {
		        "type1" : {
		            "properties" : {
		                "field1" : { "type" : "text" }
		            }
		        }
		    }
		}
	  * @param indexMapping
	  * @return
	  * @throws ElasticSearchException
	  */
	 public String createIndiceMapping(String indexName,String indexMapping)  throws ElasticSearchException {
		 return this.client.executeHttp(handleIndexName(indexName),indexMapping,ClientUtil.HTTP_PUT);
	 }

	 private String handleIndexName(String indexName){
	 	if(this.client.isUpper7() && this.client.getElasticSearch().isIncludeTypeName()){
	 		if(indexName.indexOf("include_type_name=") > 0){
	 			return indexName;
			}
	 		else{
	 			StringBuilder ret = new StringBuilder();
	 			if(indexName.indexOf("?") > 0){
					ret.append(indexName).append("&include_type_name=true");
				}
	 			else{
					ret.append(indexName).append("?include_type_name=true");
				}
	 			return ret.toString();
			}
		}
	 	else{
	 		return indexName;
		}
	 }

	@Override
	public String updateIndiceMapping(String action, String templateName, Object parameter)
			throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String createIndiceMapping(String indexName, String templateName, Object parameter)
			throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String updateIndiceMapping(String action, String templateName, Map parameter) throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String createIndiceMapping(String indexName, String templateName, Map parameter)
			throws ElasticSearchException {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	  * 获取索引定义
	  * @param index
	  * @return
	  * @throws ElasticSearchException
	  */
	 public String getIndice(String index)  throws ElasticSearchException {
//		 String response = (String)client.executeHttp(index+"/_mapping?pretty",ClientUtil.HTTP_GET);
//		 return response;
		 return this.getIndexMapping(index,true);
	 }

	/**
	 * health status index                         uuid                   pri rep docs.count docs.deleted store.size pri.store.size
	 * 获取elasticsearch索引监控数据
	 * @return
	 * @throws ElasticSearchException
	 */
	 public List<ESIndice> getIndexes() throws ElasticSearchException{
		 String data = this.client.executeHttp("_cat/indices?v",HTTP_GET);
		 if(logger.isDebugEnabled()) {
			 logger.debug(data);
		 }

         if(SimpleStringUtil.isNotEmpty(data)){
			try {
				List<ESIndice> indices = extractIndice(data);
				 return indices;
			} catch (IOException e) {
				throw new ElasticSearchException(e);
			}
        	
         }
         return null;
	 }

	/**
	 * health status index                         uuid                   pri rep docs.count docs.deleted store.size pri.store.size
	 * 获取elasticsearch索引监控数据
	 * @return
	 * @throws ElasticSearchException
	 */
	public List<ESIndice> getIndexes(String indicePattern) throws ElasticSearchException{
		String data = this.client.executeHttp(new StringBuilder().append("_cat/indices/").append(indicePattern).append("?v").toString(),HTTP_GET);
		if(logger.isDebugEnabled()) {
			logger.debug(data);
		}

		if(SimpleStringUtil.isNotEmpty(data)){
			try {
				List<ESIndice> indices = extractIndice(data);
				return indices;
			} catch (IOException e) {
				throw new ElasticSearchException(e);
			}

		}
		return null;
	}
	 
	 public List<ESIndice> extractIndice(String data) throws IOException {
	        Reader reader = null;
	        BufferedReader br = null;
	        try{
	        	reader = new StringReader(data);
	            br = new BufferedReader(reader);
		        List<ESIndice> indices = new ArrayList<ESIndice>();
		        int i = 0;
		        SimpleDateFormat format = new SimpleDateFormat(client.getDateFormat());
				List<IndiceHeader> indiceHeaderList = null;
		        while(true){
		            String line = br.readLine();
		            if(line == null)
		                break;
		            if(i == 0){
						indiceHeaderList = BuildTool.buildListIndiceHeaders(line);
		                i ++;
		                continue;
		            }
		            
		            ESIndice esIndice = BuildTool.buildESIndice(  line,  format,indiceHeaderList);
		            indices.add(esIndice);

		        }
		        return indices;
	        }
	        finally
	        {
	        	 if(reader != null)
					try {
						reader.close();
					} catch (IOException e) {
						 
					}
	        	 if(br != null)
	        		 try {
	 					br.close();
	 				} catch (IOException e) {
	 					 
	 				}
	        }


	    }
	    

	    public String refreshIndexInterval(String indexName,int interval) throws ElasticSearchException{
	    	return this.client.executeHttp(new StringBuilder().append(indexName).append("/_settings").toString(), new StringBuilder().append("{  \"index\" : {  \"refresh_interval\" : \"").append(interval).append("s\"   } }").toString(), HTTP_PUT);
	    }
	    public String refreshIndexInterval(String indexName,String indexType,int interval) throws ElasticSearchException{
	    	return this.client.executeHttp(new StringBuilder().append(indexName).append("/").append(indexType).append("/_settings").toString(), new StringBuilder().append("{  \"index\" : {  \"refresh_interval\" : \"").append(interval).append("s\"    } }").toString(), HTTP_PUT);
	    }
	    
	    public String refreshIndexInterval(int interval,boolean preserveExisting) throws ElasticSearchException{
	    	if(preserveExisting)
	    		return this.client.executeHttp("_all/_settings?preserve_existing=true", new StringBuilder().append("{  \"index\" : {  \"refresh_interval\" : \"").append(interval).append("s\"    } }").toString(), HTTP_PUT);
	    	else
	    		return this.client.executeHttp("_all/_settings?preserve_existing=false", new StringBuilder().append("{  \"index\" : {  \"refresh_interval\" : \"").append(interval).append("s\"    } }").toString(), HTTP_PUT);
	    }
	    
	    public String refreshIndexInterval(int interval) throws ElasticSearchException{
	    	return refreshIndexInterval(interval,false);
	    }

	/**
	 * 删除所有监控索引
	 * .security,.monitoring*,.watches,.triggered_watches,.watcher-history*,.ml*
	 * @return
	 */
	    public String cleanAllXPackIndices() throws ElasticSearchException{

			StringBuilder ret = new StringBuilder();
			for(String monitor:monitorIndices) {
				try {
					ret.append(this.client.executeHttp(java.net.URLEncoder.encode(monitor, "UTF-8") + "?pretty", HTTP_DELETE)).append("\n");
				}
				catch (Exception e){
					ret.append(e.getMessage()).append("\n");
				}
			}
			return ret.toString();

		}


	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param path test/_doc/1
	 *             test/_doc/1/_update
	 *
	 *
	 * @param entity
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByPath(String path,String entity) throws ElasticSearchException{
		try {
			return this.client.executeHttp(path, entity, ClientUtil.HTTP_POST);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_updateDocument);
		}
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param path test/_doc/1
	 *             test/_doc/1/_update
	 *
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByPath(String path,String templateName,Map params) throws ElasticSearchException{
		return null;
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param path test/_doc/1
	 *             test/_doc/1/_update
	 *
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByPath(String path,String templateName,Object params) throws ElasticSearchException{
		return null;
	}

	/**
	 * The simplest usage of _update_by_query just performs an update on every document in the index without changing the source. This is useful to pick up a new property or some other online mapping change. Here is the API:

	 * POST twitter/_update_by_query?conflicts=proceed
	 * @param path twitter/_update_by_query?conflicts=proceed
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByQuery(String path) throws ElasticSearchException{
		return this.client.executeHttp(path,ClientUtil.HTTP_POST);
	}
	/**
	 * The simplest usage of _update_by_query just performs an update on every document in the index without changing the source. This is useful to pick up a new property or some other online mapping change. Here is the API:

	 * POST twitter/_update_by_query?conflicts=proceed
	 * @param path twitter/_update_by_query?conflicts=proceed
	 *             twitter/_doc/_update_by_query?conflicts=proceed
	 *             twitter/_update_by_query
	 *             twitter,blog/_doc,post/_update_by_query
	 *             twitter/_update_by_query?routing=1
	 *             twitter/_update_by_query?scroll_size=100
	 *             twitter/_update_by_query?pipeline=set-foo
	 *
	 *
	 * @param entity
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByQuery(String path,String entity) throws ElasticSearchException{
		return this.client.executeHttp(path,entity,ClientUtil.HTTP_POST);
	}

	/**
	 * The simplest usage of _update_by_query just performs an update on every document in the index without changing the source. This is useful to pick up a new property or some other online mapping change. Here is the API:

	 * POST twitter/_update_by_query?conflicts=proceed
	 * @param path twitter/_update_by_query?conflicts=proceed
	 *             twitter/_doc/_update_by_query?conflicts=proceed
	 *             twitter/_update_by_query
	 *             twitter,blog/_doc,post/_update_by_query
	 *             twitter/_update_by_query?routing=1
	 *             twitter/_update_by_query?scroll_size=100
	 *             twitter/_update_by_query?pipeline=set-foo
	 *
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByQuery(String path,String templateName,Map params) throws ElasticSearchException{
		return null;
	}

	/**
	 * The simplest usage of _update_by_query just performs an update on every document in the index without changing the source. This is useful to pick up a new property or some other online mapping change. Here is the API:

	 * POST twitter/_update_by_query?conflicts=proceed
	 * @param path twitter/_update_by_query?conflicts=proceed
	 *             twitter/_doc/_update_by_query?conflicts=proceed
	 *             twitter/_update_by_query
	 *             twitter,blog/_doc,post/_update_by_query
	 *             twitter/_update_by_query?routing=1
	 *             twitter/_update_by_query?scroll_size=100
	 *             twitter/_update_by_query?pipeline=set-foo
	 *
	 *
	 * @param templateName
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateByQuery(String path,String templateName,Object params) throws ElasticSearchException{
		return null;
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param index _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param indexType
	 * @param type
	 * @param ids
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T> mgetDocuments(String index,String indexType,Class<T> type,Object ... ids)  throws ElasticSearchException{
		if(ids == null || ids.length ==0)
			return null;
		StringBuilder path = new StringBuilder();
		if(indexType == null || indexType.equals(""))
			path.append(index).append("/_mget");
		else
			path.append(index).append("/").append(indexType).append("/_mget");
		StringBuilder builder = new StringBuilder();
		builder.append(" {\"ids\":");
		Writer writer = new BBossStringWriter(builder);
		SerialUtil.object2json(ids,writer);
		builder.append("}");
		MGetDocs searchResult = (MGetDocs) this.client.executeRequest(path.toString(),builder.toString(),   new MGetDocumentsSourceResponseHandler( type),ClientUtil.HTTP_POST);

		return ResultUtil.buildObjects(searchResult, type);

	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param index _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param indexType
	 * @param ids
	 * @return
	 * @throws ElasticSearchException
	 */
	public String mgetDocuments(String index,String indexType,Object ... ids)  throws ElasticSearchException{
		if(ids == null || ids.length ==0)
			return null;
		StringBuilder path = new StringBuilder();
		if(indexType == null || indexType.equals(""))
			path.append(index).append("/_mget");
		else
			path.append(index).append("/").append(indexType).append("/_mget");
		StringBuilder builder = new StringBuilder();
		builder.append(" {\"ids\":");
		Writer writer = new BBossStringWriter(builder);
		SerialUtil.object2json(ids,writer);
		builder.append("}");
		String searchResult = this.client.executeHttp(path.toString(),builder.toString(),   ClientUtil.HTTP_POST);

		return searchResult;

	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Object params) throws ElasticSearchException{
		return updateDocument(index,indexType,id,params,(String)null);
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Map params) throws ElasticSearchException{
		return updateDocument(index,indexType,id,params,(String)null);
	}


	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Map params,String refreshOption) throws ElasticSearchException{

		return updateDocument(index,indexType,id,params,refreshOption,(Boolean)null,(Boolean)null);
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Object params,String refreshOption) throws ElasticSearchException{

		return updateDocument(index,indexType,id,params,refreshOption,(Boolean)null,(Boolean)null);
	}


	/*********************************************************/

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Object params,Boolean detect_noop,Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument(index,indexType,id,params,(String)null,  detect_noop,  doc_as_upsert);
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Map params,Boolean detect_noop,Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument(index,indexType,id,params,(String)null,  detect_noop,  doc_as_upsert);
	}


	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,Object id,Map params,String refreshOption,
								 Boolean detect_noop,Boolean doc_as_upsert) throws ElasticSearchException{
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setId(id);
		clientOptions.setRefreshOption(refreshOption);
		clientOptions.setDetectNoop(detect_noop);
		clientOptions.setDocasupsert(doc_as_upsert);
		return updateDocument(index,indexType,params,clientOptions);
//		StringBuilder path = new StringBuilder();
//		if(indexType == null || indexType.equals(""))
//			path.append(index).append("/").append(id).append("/_update");
//		else
//			path.append(index).append("/").append(indexType).append("/").append(id).append("/_update");
//		if(refreshOption != null){
//			path.append("?").append(refreshOption);
//		}
//		StringBuilder builder = new StringBuilder();
//		builder.append(" {\"doc\":");
//		Writer writer = new BBossStringWriter(builder);
//		SerialUtil.object2json(params,writer);
//		if(detect_noop != null){
//			builder.append(",\"detect_noop\":").append(detect_noop);
//		}
//		if(doc_as_upsert != null){
//			builder.append(",\"doc_as_upsert\":").append(doc_as_upsert);
//		}
//		builder.append("}");
//		try {
//			String searchResult = this.client.executeHttp(path.toString(), builder.toString(), ClientUtil.HTTP_POST);
//
//			return searchResult;
//		}
//		catch(ElasticSearchException e){
//			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_updateDocument);
//		}
//		return _update(index,indexType,
//				id, params, refreshOption, detect_noop, doc_as_upsert);
	}

	/**
	 * 根据路径更新文档
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param indexType
	 * @param id
	 * @param params
	 * @param refreshOption
	 * @param detect_noop default null
	 * @param doc_as_upsert default null
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,String indexType,
								 Object id,Object params,String refreshOption,Boolean detect_noop,Boolean doc_as_upsert) throws ElasticSearchException{
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setId(id);
		clientOptions.setRefreshOption(refreshOption);
		clientOptions.setDetectNoop(detect_noop);
		clientOptions.setDocasupsert(doc_as_upsert);
		return updateDocument(index,indexType,params,clientOptions);
//		return _update(index,indexType,
//				 id, params, refreshOption, detect_noop, doc_as_upsert);
	}
/**
	private String _update(String index,String indexType,
						   Object id,Object params,String refreshOption,Object detect_noop,Object doc_as_upsert){
		StringBuilder path = new StringBuilder();
		if(!this.client.isUpper7()) {
			if (indexType == null || indexType.equals(""))
				path.append(index).append("/").append(id).append("/_update");
			else
				path.append(index).append("/").append(indexType).append("/").append(id).append("/_update");
		}
		else{
			path.append(index).append("/_update").append("/").append(id);
		}
		if(refreshOption != null){
			path.append("?").append(refreshOption);
		}
		StringBuilder builder = new StringBuilder();
		builder.append(" {\"doc\":");
		Writer writer = new BBossStringWriter(builder);
		SerialUtil.object2json(params,writer);
		if(detect_noop != null){
			builder.append(",\"detect_noop\":").append(detect_noop);
		}
		if(doc_as_upsert != null){
			builder.append(",\"doc_as_upsert\":").append(doc_as_upsert);
		}
		builder.append("}");
		try {
			String searchResult = this.client.executeHttp(path.toString(), builder.toString(), ClientUtil.HTTP_POST);

			return searchResult;
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_updateDocument);
		}
	}

*/



	/**
	 *
	 * Reindex does not attempt to set up the destination index.
	 * It does not copy the settings of the source index. You should set up the destination index prior to running a _reindex action, including setting up mappings, shard counts, replicas, etc.
	 * more detail see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html
	 *
	 * @param sourceIndice
	 * @param destIndice
	 * @return
	 */
	public String reindex(String sourceIndice,String destIndice){
		String reindex = new StringBuilder().append("{\"source\": {\"index\": \"").append(sourceIndice).append("\"},\"dest\": {\"index\": \"").append(destIndice).append("\"}}").toString();
		return this.client.executeHttp("_reindex",reindex,ClientUtil.HTTP_POST);

	}

	/**
	 *
	 * Reindex does not attempt to set up the destination index.
	 * It does not copy the settings of the source index. You should set up the destination index prior to running a _reindex action, including setting up mappings, shard counts, replicas, etc.
	 * more detail see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html
	 *
	 * @param actionUrl reindex请求相对url，例如：
	 *                  _reindex
	 *                  _reindex?slices=5&refresh
	 * @param dslName  xml配置文件中dsl对应的name
	 * @param params dslName 中对应的变量参数信息
	 * @return
	 */
	public String reindexByDsl(String actionUrl,String dslName,Object params){
		throw new UnsupportedOperationException("reindexByDsl(String actionUrl,String dslName,Object params)");
	}
	/**
	 *
	 * Reindex does not attempt to set up the destination index.
	 * It does not copy the settings of the source index. You should set up the destination index prior to running a _reindex action, including setting up mappings, shard counts, replicas, etc.
	 * more detail see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html
	 *
	 * @param actionUrl reindex请求相对url，例如：
	 *                  _reindex
	 *                  _reindex?slices=5&refresh
	 * @param dsl reindex对应的dsl脚本
	 * @return
	 */
	public String reindexByDsl(String actionUrl,String dsl){
		return this.client.executeHttp(actionUrl,dsl,ClientUtil.HTTP_POST);
	}

	/**
	 *
	 * Reindex does not attempt to set up the destination index.
	 * It does not copy the settings of the source index. You should set up the destination index prior to running a _reindex action, including setting up mappings, shard counts, replicas, etc.
	 * more detail see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html
	 *
	 * @param sourceIndice
	 * @param destIndice
	 * @return
	 */
	public String reindex(String sourceIndice,String destIndice,String versionType){
		String reindex = new StringBuilder().append("{\"source\": {\"index\": \"").append(sourceIndice).append("\"},\"dest\": {\"index\": \"").append(destIndice).append("\",\"version_type\": \"").append(versionType).append("\"}}").toString();
		return this.client.executeHttp("_reindex",reindex,ClientUtil.HTTP_POST);
	}

	/**
	 *
	 * Reindex does not attempt to set up the destination index.
	 * It does not copy the settings of the source index. You should set up the destination index prior to running a _reindex action, including setting up mappings, shard counts, replicas, etc.
	 * more detail see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html
	 *
	 * @param sourceIndice
	 * @param destIndice
	 * @return
	 */
	public String reindex(String sourceIndice,String destIndice,String opType,String conflicts){
		if(conflicts == null || conflicts.equals("")) {
			String reindex = new StringBuilder().append("{\"source\": {\"index\": \"").append(sourceIndice).append("\"},\"dest\": {\"index\": \"").append(destIndice).append("\",\"op_type\": \"").append(opType).append("\"}}").toString();
			return this.client.executeHttp("_reindex", reindex, ClientUtil.HTTP_POST);
		}
		else
		{
			String reindex = new StringBuilder().append("{\"conflicts\": \"").append(conflicts).append("\",\"source\": {\"index\": \"").append(sourceIndice).append("\"},\"dest\": {\"index\": \"").append(destIndice).append("\",\"op_type\": \"").append(opType).append("\"}}").toString();
			return this.client.executeHttp("_reindex", reindex, ClientUtil.HTTP_POST);
		}
	}

	/**
	 * Associating the alias alias with index indice
	 * more detail see :
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-aliases.html
	 * @param indice
	 * @param alias
	 * @return
	 */
	public String addAlias(String indice,String alias){
		String aliasJson = new StringBuilder().append("{\"actions\": [{\"add\": {\"index\":\"").append(indice).append("\",\"alias\": \"").append(alias).append("\"}}]}").toString();
		return this.client.executeHttp("_aliases",aliasJson,ClientUtil.HTTP_POST);
	}

	/**
	 * removing that same alias [alias] of [indice]
	 * more detail see :
	 * 	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-aliases.html
	 * @param indice
	 * @param alias
	 * @return
	 */
	public String removeAlias(String indice,String alias){
		String removeAlias = new StringBuilder().append("{\"actions\": [{\"remove\": {\"index\":\"").append(indice).append("\",\"alias\": \"").append(alias).append("\"}}]}").toString();
		return this.client.executeHttp("_aliases",removeAlias,ClientUtil.HTTP_POST);
	}

	public ESInfo getESInfo(String templateName){
		return  null;
	}

	@Override
	public String closeIndex(String index) {
		String closeIndex = new StringBuilder().append("/").append(index).append("/_close").toString();
		return this.client.executeHttp(closeIndex,ClientUtil.HTTP_POST);
	}

	@Override
	public String openIndex(String index) {
		String closeIndex = new StringBuilder().append("/").append(index).append("/_open").toString();
		return this.client.executeHttp(closeIndex,ClientUtil.HTTP_POST);
	}
	/**
	 * GET /_cluster/settings
	 * @return
	 */
	public String getClusterSettings(){
		return getClusterSettings(true);
	}
	public String getClusterSettings(boolean includeDefault){
		if(includeDefault) {
			return this.client.executeHttp("/_cluster/settings?include_defaults=true", ClientInterface.HTTP_GET);
		}
		else{
			return this.client.executeHttp("/_cluster/settings",ClientInterface.HTTP_GET);
		}
	}

	/**
	 * GET indice/_settings
	 * PUT _all/_settings
	 *     {
	 *       "settings": {
	 *         "index.unassigned.node_left.delayed_timeout": "5m"
	 *       }
	 *     }
	 * @param indice
	 * @return
	 */
	public String getIndiceSetting(String indice){

		return getIndiceSetting(indice,(String )null);

	}

	public String getIndiceSetting(String indice,String params){

		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		if(params != null && params.length() > 0){
			builder.append("?").append(params);
		}
		return this.client.executeHttp(builder.toString(),ClientInterface.HTTP_GET);

	}
	public String getIndiceSettingByName(String indice,String settingName){
		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		if(settingName != null && settingName.length() > 0){
			builder.append("/").append(settingName);
		}
		return this.client.executeHttp(builder.toString(),ClientInterface.HTTP_GET);
	}

	/**
	 * {
	 *             "settings":{
	 *                 "index.unassigned.node_left.delayed_timeout":"1d"
	 *             }
	 *         }
	 * @param delayedTimeout
	 * @return
	 */
	public String unassignedNodeLeftDelayedTimeout(String indice,String delayedTimeout){
		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{")
				.append("\"index.unassigned.node_left.delayed_timeout\":\"").append(delayedTimeout)
				.append("\"}}");
		return this.client.executeHttp(builder.toString(),updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	@Override
	public String updateNumberOfReplicas(String indice, int numberOfReplicas) {
		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{")
				.append("\"index.number_of_replicas\":").append(numberOfReplicas)
				.append("}}");
		return this.client.executeHttp(builder.toString(),updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	@Override
	public String updateNumberOfReplicas(int numberOfReplicas) {
		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{")
				.append("\"index.number_of_replicas\":").append(numberOfReplicas)
				.append("}}");
		return this.client.executeHttp("_all/_settings",updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	@Override
	public String updateAllIndicesSettings(Map<String,Object> settings) {
		if(settings == null || settings.size() ==0)
			return "";
		return _updateIndiceSettings(  "_all/_settings",settings);

	}
	public String updateIndiceSettings(String indice,Map<String,Object> settings) {
		if(settings == null || settings.size() ==0)
			return "";
		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		return _updateIndiceSettings(  builder.toString(),settings);
	}


	public String _updateIndiceSettings(String path,Map<String,Object> settings){
		if(settings == null || settings.size() ==0)
			return "";

		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{");
		Iterator<Map.Entry<String,Object>> iterator = settings.entrySet().iterator();
		boolean seted = false;
		while (iterator.hasNext()){
			Map.Entry<String,Object> entry = iterator.next();
			if(seted)
				updateDsl.append(",");
			else
				seted = true;
			Object value = entry.getValue();
			updateDsl.append("\"").append(entry.getKey()).append("\":");
			if(value == null){
				updateDsl.append("null");
			}
			else if(value instanceof String) {
				updateDsl.append("\"").append(value).append("\"");
			}
			else
				updateDsl.append(value);
		}

		updateDsl.append("}}");
		return this.client.executeHttp(path,updateDsl.toString(),ClientInterface.HTTP_PUT);
	}
	public String _updateIndiceSetting(String path,String key,Object value){


		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{");

		updateDsl.append("\"").append(key).append("\":");
		if(value == null){
			updateDsl.append("null");
		}
		else if(value instanceof String) {
			updateDsl.append("\"").append(value).append("\"");
		}
		else
			updateDsl.append(value);


		updateDsl.append("}}");
		return this.client.executeHttp(path,updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	public String updateAllIndicesSetting(String key,Object value) {

		return _updateIndiceSetting(  "_all/_settings",  key,  value);
	}
	public String updateIndiceSetting(String indice,String key,Object value) {
		StringBuilder builder = new StringBuilder().append(indice).append("/_settings");
		return _updateIndiceSetting(  builder.toString(),  key,  value);
	}

	public String unassignedNodeLeftDelayedTimeout(String delayedTimeout){

		StringBuilder updateDsl = new StringBuilder();
		updateDsl.append("{").append("\"settings\":{")
				.append("\"index.unassigned.node_left.delayed_timeout\":\"").append(delayedTimeout)
				.append("\"}}");
		return this.client.executeHttp("_all/_settings",updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	/**
	 * 指定对象集合的文档id字段
	 */
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdField 对象中作为文档id的Field
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdOptions(String indexName, String indexType, List<Object> beans,String docIdField,String refreshOption) throws ElasticSearchException{
		return addDocumentsWithIdField(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdField,refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdField(String indexName, String indexType, List<Object> beans,String docIdField) throws ElasticSearchException{
		return addDocumentsWithIdField(  this.indexNameBuilder.getIndexName(indexName),   indexType,    beans,  docIdField);
	}
	public String addDocumentsWithIdField(String indexName, String indexType, List<Object> beans,String docIdField,String refreshOption) throws ElasticSearchException{
//		if(beans == null || beans.size() == 0)
//			return null;
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
//		for(Object bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",docIdField,(String)null);
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
//		if(refreshOption == null)
//			return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
//		else
//			return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
		return addDocumentsWithIdField(  indexName,   indexType,  beans,  docIdField,(String )null,  refreshOption);
	}
	public String addDocumentsWithIdField(String indexName, String indexType,  List<Object> beans,String docIdField) throws ElasticSearchException{
		return addDocumentsWithIdField(  indexName,   indexType,   beans, docIdField,(String )null,(String )null);
	}


	/**********************/
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdField(String indexName, String indexType, List<Object> beans,String docIdField,String parentIdField,String refreshOption) throws ElasticSearchException{
		return addDocumentsWithIdField(this.indexNameBuilder.getIndexName(indexName),   indexType,   beans,  docIdField,  parentIdField,  refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param indexType
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdField(String indexName, String indexType, List<Object> beans,String docIdField,String parentIdField) throws ElasticSearchException
	{
		return addDocumentsWithIdParentField(this.indexNameBuilder.getIndexName(indexName),   indexType,     beans,docIdField,parentIdField);
	}

	public String addDateDocuments(String indexName, String indexType, List<?> beans,ClientOptions clientOptions) throws ElasticSearchException{
		return addDocuments(  this.indexNameBuilder.getIndexName(indexName),   indexType,  beans,  clientOptions);
	}

	public  String addDocumentsWithIdField(String indexName, String indexType, List<Object> beans,String docIdField,String parentIdField,String refreshOption) throws ElasticSearchException{
		if(beans == null || beans.size() == 0)
			return null;
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setIdField(docIdField);
		clientOptions.setParentIdField(parentIdField);
		clientOptions.setRefreshOption(refreshOption);
		return addDocuments(  indexName,   indexType,   beans,clientOptions);
//		StringBuilder builder = new StringBuilder();
//		BBossStringWriter writer = new BBossStringWriter(builder);
//		for(Object bean:beans) {
//			try {
//				BuildTool.evalBuilk(writer,indexName,indexType,bean,"index",docIdField,parentIdField,this.client.isUpper7());
//			} catch (IOException e) {
//				throw new ElasticSearchException(e);
//			}
//		}
//		writer.flush();
//		if(refreshOption == null)
//			return this.client.executeHttp("_bulk",builder.toString(),ClientUtil.HTTP_POST);
//		else
//			return this.client.executeHttp("_bulk?"+refreshOption,builder.toString(),ClientUtil.HTTP_POST);
	}
	public   String addDocumentsWithIdParentField(String indexName, String indexType,  List<Object> beans,String docIdField,String parentIdField) throws ElasticSearchException{
		return addDocumentsWithIdField(indexName, indexType, beans,docIdField, parentIdField,(String)null);
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-update-settings.html
	 *
	 Cluster Update Settingsedit
	 Use this API to review and change cluster-wide settings.

	 To review cluster settings:

	 GET /_cluster/settings
	 COPY AS CURLVIEW IN CONSOLE
	 By default, this API call only returns settings that have been explicitly defined, but can also include the default settings.

	 Updates to settings can be persistent, meaning they apply across restarts, or transient, where they don’t survive a full cluster restart. Here is an example of a persistent update:

	 PUT /_cluster/settings
	 {
	 "persistent" : {
	 "indices.recovery.max_bytes_per_sec" : "50mb"
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 This update is transient:

	 PUT /_cluster/settings?flat_settings=true
	 {
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : "20mb"
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 The response to an update returns the changed setting, as in this response to the transient example:

	 {
	 ...
	 "persistent" : { },
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : "20mb"
	 }
	 }
	 You can reset persistent or transient settings by assigning a null value. If a transient setting is reset, the first one of these values that is defined is applied:

	 the persistent setting
	 the setting in the configuration file
	 the default value.
	 This example resets a setting:

	 PUT /_cluster/settings
	 {
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : null
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 The response does not include settings that have been reset:

	 {
	 ...
	 "persistent" : {},
	 "transient" : {}
	 }
	 You can also reset settings using wildcards. For example, to reset all dynamic indices.recovery settings:

	 PUT /_cluster/settings
	 {
	 "transient" : {
	 "indices.recovery.*" : null
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 Order of Precedenceedit
	 The order of precedence for cluster settings is:

	 transient cluster settings
	 persistent cluster settings
	 settings in the elasticsearch.yml configuration file.
	 It’s best to set all cluster-wide settings with the settings API and use the elasticsearch.yml file only for local configurations. This way you can be sure that the setting is the same on all nodes. If, on the other hand, you define different settings on different nodes by accident using the configuration file, it is very difficult to notice these discrepancies.

	 You can find the list of settings that you can dynamically update in Modules.
	 * @param clusterSetting
	 * @return
	 */
	public String updateClusterSetting(ClusterSetting clusterSetting){
		StringBuilder updateDsl = new StringBuilder();
		if(clusterSetting.isPersistent()) {
			updateDsl.append("{").append("\"persistent\":{");
		}
		else {
			updateDsl.append("{").append("\"transient\":{");
		}

		updateDsl.append("\"").append(clusterSetting.getKey()).append("\":");
		if(clusterSetting.getValue() == null){
			updateDsl.append("null");
		}
		else if(clusterSetting.getValue() instanceof String) {
			updateDsl.append("\"").append(clusterSetting.getValue()).append("\"");
		}
		else{
			updateDsl.append(clusterSetting.getValue());
		}
		updateDsl.append("}}");
		return this.client.executeHttp("_cluster/settings",updateDsl.toString(),ClientInterface.HTTP_PUT);
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-update-settings.html
	 *
	 Cluster Update Settingsedit
	 Use this API to review and change cluster-wide settings.

	 To review cluster settings:

	 GET /_cluster/settings
	 COPY AS CURLVIEW IN CONSOLE
	 By default, this API call only returns settings that have been explicitly defined, but can also include the default settings.

	 Updates to settings can be persistent, meaning they apply across restarts, or transient, where they don’t survive a full cluster restart. Here is an example of a persistent update:

	 PUT /_cluster/settings
	 {
	 "persistent" : {
	 "indices.recovery.max_bytes_per_sec" : "50mb"
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 This update is transient:

	 PUT /_cluster/settings?flat_settings=true
	 {
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : "20mb"
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 The response to an update returns the changed setting, as in this response to the transient example:

	 {
	 ...
	 "persistent" : { },
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : "20mb"
	 }
	 }
	 You can reset persistent or transient settings by assigning a null value. If a transient setting is reset, the first one of these values that is defined is applied:

	 the persistent setting
	 the setting in the configuration file
	 the default value.
	 This example resets a setting:

	 PUT /_cluster/settings
	 {
	 "transient" : {
	 "indices.recovery.max_bytes_per_sec" : null
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 The response does not include settings that have been reset:

	 {
	 ...
	 "persistent" : {},
	 "transient" : {}
	 }
	 You can also reset settings using wildcards. For example, to reset all dynamic indices.recovery settings:

	 PUT /_cluster/settings
	 {
	 "transient" : {
	 "indices.recovery.*" : null
	 }
	 }
	 COPY AS CURLVIEW IN CONSOLE
	 Order of Precedenceedit
	 The order of precedence for cluster settings is:

	 transient cluster settings
	 persistent cluster settings
	 settings in the elasticsearch.yml configuration file.
	 It’s best to set all cluster-wide settings with the settings API and use the elasticsearch.yml file only for local configurations. This way you can be sure that the setting is the same on all nodes. If, on the other hand, you define different settings on different nodes by accident using the configuration file, it is very difficult to notice these discrepancies.

	 You can find the list of settings that you can dynamically update in Modules.
	 * @param clusterSettings
	 * @return
	 */
	public String updateClusterSettings(List<ClusterSetting> clusterSettings){
		if(clusterSettings == null || clusterSettings.size() ==0)
			return "";
		StringBuilder updateDsl = new StringBuilder();
		StringBuilder persistentDsl = new StringBuilder();
		StringBuilder transientDsl = new StringBuilder();
		boolean persistentSet = false;
		boolean transientSet = false;

		updateDsl.append("{");
		for(int i =0; i < clusterSettings.size() ; i ++) {
			ClusterSetting clusterSetting = clusterSettings.get(i);
			if (clusterSetting.isPersistent()) {
				if(!persistentSet) {
					persistentSet = true;
					persistentDsl.append("\"persistent\":{");
				}
				else{
					persistentDsl.append(",");
				}

				persistentDsl.append("\"").append(clusterSetting.getKey()).append("\":");
				if(clusterSetting.getValue() == null){
					persistentDsl.append("null");
				}
				else if (clusterSetting.getValue() instanceof String) {
					persistentDsl.append("\"").append(clusterSetting.getValue()).append("\"");
				} else {
					persistentDsl.append(clusterSetting.getValue());
				}

			} else {
				if(!transientSet) {
					transientSet = true;
					transientDsl.append("\"transient\":{");
				}
				else{
					transientDsl.append(",");
				}
				transientDsl.append("\"").append(clusterSetting.getKey()).append("\":");
				if(clusterSetting.getValue() == null){
					transientDsl.append("null");
				}
				else if (clusterSetting.getValue() instanceof String) {
					transientDsl.append("\"").append(clusterSetting.getValue()).append("\"");
				} else {
					transientDsl.append(clusterSetting.getValue());
				}
			}
		}
		if(persistentDsl.length() > 0){
			persistentDsl.append("}");
			updateDsl.append(persistentDsl.toString());
		}
		if(transientDsl.length() > 0 && persistentDsl.length() > 0)
		{
			updateDsl.append(",");
		}
		if(transientDsl.length() > 0)
		{
			transientDsl.append("}");
			updateDsl.append(transientDsl.toString());
		}
		updateDsl.append("}");
		return this.client.executeHttp("_cluster/settings",updateDsl.toString(),ClientInterface.HTTP_PUT);

	}

	public String disableClusterRoutingAllocation(){
		ClusterSetting clusterSetting = new ClusterSetting();
		clusterSetting.setPersistent(true);
		clusterSetting.setKey("cluster.routing.allocation.enable");
		clusterSetting.setValue("none");
		return this.updateClusterSetting(clusterSetting);
	}
	public String enableClusterRoutingAllocation(){
		ClusterSetting clusterSetting = new ClusterSetting();
		clusterSetting.setPersistent(true);
		clusterSetting.setKey("cluster.routing.allocation.enable");
		clusterSetting.setValue(null);
		return this.updateClusterSetting(clusterSetting);
	}
	public String flushSynced(){
		return this.client.executeHttp("_flush/synced",ClientInterface.HTTP_POST);
	}
	/**
	 *
	 * https://www.elastic.co/guide/en/elasticsearch/reference/6.3/indices-synced-flush.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/6.3/rolling-upgrades.html
	 * @return
	 */
	public String flushSynced(String indice){
		return this.client.executeHttp(new StringBuilder().append(indice).append("/_flush/synced").toString(),ClientInterface.HTTP_POST);
	}


	/**
	 * ES 7+ API
	 */

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentWithParentId(String indexName,   Object bean, Object parentId) throws ElasticSearchException{
		return addDocumentWithParentId( indexName,    _doc,bean,  parentId);
	}

	public String addDocumentWithParentId(String indexName,   Object bean, Object parentId, String refreshOption) throws ElasticSearchException{
		return addDocumentWithParentId(  indexName,    _doc, bean,   parentId,   refreshOption);
	}

	public String addDateDocumentWithParentId(String indexName, Object bean, Object parentId) throws ElasticSearchException{
		return addDateDocumentWithParentId(  indexName,    _doc, bean,   parentId);
	}

	public String addDateDocumentWithParentId(String indexName,   Object bean, Object parentId, String refreshOption) throws ElasticSearchException{
		return addDateDocumentWithParentId(  indexName,    _doc,   bean,   parentId,   refreshOption);
	}


	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_update/1
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object id, Map params) throws ElasticSearchException{
		return updateDocument(  index,  (String)null,   id,   params);
	}

	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *            test/_update/1
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index , Object id, Object params) throws ElasticSearchException{
		return updateDocument( index ,  (String)null,id,  params);
	}


	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_update/1
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object id, Map params, String refreshOption) throws ElasticSearchException{
		return updateDocument(  index,   (String)null, id,   params,   refreshOption);
	}

	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object id, Object params, String refreshOption) throws ElasticSearchException{
		return updateDocument(  index,  (String)null,  id,   params,   refreshOption);
	}

	/**
	 *
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param ids
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteDocuments(String indexName,  String[] ids) throws ElasticSearchException{
		return deleteDocuments(  indexName, (String)null,    ids);
	}
	/**
	 *
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.

	 * @param ids
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteDocumentsWithrefreshOption(String indexName,   String refreshOption, String[] ids) throws ElasticSearchException{
		return deleteDocumentsWithrefreshOption(  indexName,   (String)null,  refreshOption,   ids);
	}


	/**
	 * 获取索引表
	 * For Elasticsearch 7 and 7+
	 * @param index
	 * @return
	 * @throws ElasticSearchException
	 */
	public List<IndexField> getIndexMappingFields(String index ) throws ElasticSearchException{
		return getIndexMappingFields(  index ,null);
	}

	/***************************读取模板文件添加或者修改文档开始************************************/
	/**
	 * 批量创建索引
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param addTemplate
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocumentsNew(String indexName,   String addTemplate, List<?> beans, String refreshOption) throws ElasticSearchException{
		return addDocuments( indexName,   _doc,addTemplate,  beans,refreshOption);
	}
	public   String addDocumentsNew(String indexName,  String addTemplate, List<?> beans) throws ElasticSearchException{
		return addDocuments( indexName,   _doc,addTemplate,  beans);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param addTemplate
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentNew(String indexName,  String addTemplate, Object bean) throws ElasticSearchException{
		return addDocument( indexName,    _doc,addTemplate,  bean);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param addTemplate
	 * @param bean
	 * @param refreshOption
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocumentNew(String indexName,   String addTemplate, Object bean, String refreshOption) throws ElasticSearchException{
		return addDocument( indexName,    _doc,addTemplate,  bean,  refreshOption);

	}

	public  String updateDocumentsNew(String indexName,   String updateTemplate, List<?> beans) throws ElasticSearchException{
		return updateDocuments(  indexName,    _doc,   updateTemplate,   beans);
	}

	/**
	 *
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param updateTemplate
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocumentsNew(String indexName , String updateTemplate, List<?> beans, String refreshOption) throws ElasticSearchException{
		return updateDocuments(  indexName ,  _doc, updateTemplate,   beans,   refreshOption);
	}

	/***************************读取模板文件添加或者修改文档结束************************************/

	/***************************添加或者修改文档开始************************************/
	/**
	 * 批量创建索引
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocuments(String indexName,  List<?> beans, String refreshOption) throws ElasticSearchException{
		return addDocuments(  indexName,  _doc,   beans,refreshOption);
	}
	public String addDocuments(String indexName,   List<?> beans) throws ElasticSearchException{
		return addDocuments(  indexName,  _doc,   beans);
	}


	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocument(String indexName,  Object bean) throws ElasticSearchException{
		return addDocument(  indexName,  _doc,   bean);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName,  Object bean, ClientOptions clientOptions) throws ElasticSearchException{
		return addDocument(  indexName,  _doc,   bean,clientOptions);
	}
	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocument(String indexName,  Object bean, ClientOptions clientOptions) throws ElasticSearchException{
		return addDateDocument(  indexName,  _doc,   bean,clientOptions);
	}
	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addMapDocument(String indexName,  Map bean, ClientOptions clientOptions) throws ElasticSearchException{
		return addDateMapDocument(  indexName,  _doc,   bean,clientOptions);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String addDateMapDocument(String indexName,  Map bean) throws ElasticSearchException{
		return addDateMapDocument(  indexName,  _doc,   bean);
	}
	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addMapDocument(String indexName , Map bean) throws ElasticSearchException{
		return addMapDocument(  indexName,  _doc,   bean);
	}
	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateMapDocument(String indexName,   Map bean, ClientOptions clientOptions) throws ElasticSearchException{
		return addDateMapDocument(  indexName,  _doc,   bean,   clientOptions);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocument (String indexName, Object bean, String refreshOption) throws ElasticSearchException{
		return addDocument (  indexName, _doc,  bean,   refreshOption);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @param docId
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocumentWithId(String indexName, Object bean, Object docId) throws ElasticSearchException{
		return addDocumentWithId(  indexName, _doc,  bean,   docId);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @param docId
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocumentWithId(String indexName,  Object bean, Object docId, Object parentId) throws ElasticSearchException{
		return addDocumentWithId(  indexName,  _doc,  bean,   docId,   parentId);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @param docId
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName, Object bean, Object docId, Object parentId, String refreshOption) throws ElasticSearchException{
		return addDocument(  indexName, _doc,  bean,   docId,   parentId,   refreshOption);
	}

	/**
	 * 创建或者更新索引文档
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param bean
	 * @param docId
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(String indexName,  Object bean, Object docId, String refreshOption) throws ElasticSearchException{
		return addDocument(  indexName,    _doc,bean,   docId,   refreshOption);
	}

	/**
	 *
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param beans
	 * @param clientOptions 传递es操作的相关控制参数，采用ClientOptions后，定义在对象中的相关注解字段将不会起作用（失效）
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String updateDocuments (String indexName,   List<?> beans, ClientOptions clientOptions) throws ElasticSearchException{
		return updateDocuments (  indexName, (String)null,   beans,  clientOptions);
	}
	public  String updateDocuments (String indexName, List<?> beans) throws ElasticSearchException{
		return updateDocuments (  indexName, (String)null,   beans);
	}
	public  String updateDocumentsWithIdKey (String indexName,  List<Map> beans, String docIdKey) throws ElasticSearchException{
		return updateDocumentsWithIdKey (  indexName, (String)null,    beans,   docIdKey);
	}
	public  String updateDocumentsWithIdKey (String indexName,   List<Map> beans, String docIdKey, String parentIdKey) throws ElasticSearchException{
		return updateDocumentsWithIdKey (  indexName, (String)null,   beans,   docIdKey,   parentIdKey);
	}
	/**
	 *
	 * @param indexName
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String updateDocuments(String indexName, List<?> beans, String refreshOption) throws ElasticSearchException{
		return updateDocuments(  indexName, (String)null, beans,   refreshOption);
	}
	public   String updateDocuments(String indexName,   List<Map> beans, String docIdKey, String refreshOption) throws ElasticSearchException{
		return updateDocuments(  indexName,   (String)null,  beans,   docIdKey,   refreshOption);
	}
	public String updateDocuments(String indexName,  List<Map> beans, String docIdKey, String parentIdKey, String refreshOption) throws ElasticSearchException{
		return updateDocuments(  indexName, (String)null, beans,   docIdKey,   parentIdKey,   refreshOption);
	}

	/***************************添加或者修改文档结束************************************/
	/**
	 * 获取json格式文档
	 * @param indexName
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String getDocument(String indexName,  String documentId) throws ElasticSearchException{
		return getDocument(  indexName,  _doc,  documentId);
	}
	/**
	 * 获取json格式文档，通过options设置获取文档的参数
	 * @param indexName
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String getDocument(String indexName,   String documentId, Map<String, Object> options) throws ElasticSearchException{
		return getDocument(  indexName,  _doc,     documentId,   options);
	}






	/**
	 * 获取文档,返回类型可以继承ESBaseData(这样方法自动将索引的元数据信息设置到T对象中)和ESId（方法自动将索引文档id设置到对象中）
	 * @param indexName
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T getDocument(String indexName, String documentId, Class<T> beanType) throws ElasticSearchException{
		return getDocument(  indexName,   _doc,  documentId,  beanType);
	}

	/**
	 * 获取文档，通过options设置获取文档的参数，返回类型可以继承ESBaseData(这样方法自动将索引的元数据信息设置到T对象中)和ESId（方法自动将索引文档id设置到对象中）
	 * @param indexName
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public   <T> T getDocument(String indexName,  String documentId, Map<String, Object> options, Class<T> beanType) throws ElasticSearchException{
		return getDocument(  indexName,   _doc,  documentId,   options,   beanType);
	}



	/**
	 * 获取文档MapSearchHit对象，封装了索引文档的所有属性数据
	 * @param indexName
	 * @param documentId
	 * @param options
	 * @return
	 * @throws ElasticSearchException
	 */
	public MapSearchHit getDocumentHit(String indexName,   String documentId, Map<String, Object> options) throws ElasticSearchException{
		return getDocumentHit(  indexName,  _doc,   documentId,  options);
	}

	/**
	 * 获取文档MapSearchHit对象，封装了索引文档的所有属性数据
	 * @param indexName
	 * @param documentId
	 * @return
	 * @throws ElasticSearchException
	 */
	public MapSearchHit getDocumentHit(String indexName,  String documentId) throws ElasticSearchException{
		return getDocumentHit(indexName,  _doc,documentId);
	}

	/**************************************创建或者修改文档开始**************************************************************/
	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocument(String indexName,   Object bean) throws ElasticSearchException{
		return addDateDocument(  indexName, _doc,    bean);
	}

	/**
	 *
	 * @param indexName
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocument(String indexName,  Object bean, String refreshOption) throws ElasticSearchException{
		return addDateDocument(  indexName, _doc,    bean,   refreshOption);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentWithId(String indexName,  Object bean, Object docId) throws ElasticSearchException{
		return addDateDocumentWithId(  indexName, _doc,    bean,   docId);
	}

	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentWithId(String indexName,   Object bean, Object docId, Object parentId) throws ElasticSearchException{
		return addDateDocumentWithId(  indexName, _doc,    bean,   docId,   parentId);
	}

	/**
	 *
	 * @param indexName
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocument(String indexName,  Object bean, Object docId, String refreshOption) throws ElasticSearchException{
		return addDateDocument(indexName,  _doc,  bean,   docId,   refreshOption);
	}

	/**
	 *
	 * @param indexName
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocument(String indexName,  Object bean, Object docId, Object parentId, String refreshOption) throws ElasticSearchException{
		return addDateDocument( indexName,  _doc,  bean,  docId,   parentId,   refreshOption);
	}

	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocuments (String indexName,   List<?> beans) throws ElasticSearchException{
		return addDateDocuments (  indexName,  (String)null,      beans);
	}



	/**
	 *
	 * @param indexName
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocuments(String indexName,  List<?> beans, String refreshOption) throws ElasticSearchException{
		return addDateDocuments( indexName,  (String)null,   beans,   refreshOption);
	}

	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocuments (String indexName,   List<Map> beans, String docIdKey, String refreshOption) throws ElasticSearchException{
		return addDateDocuments (  indexName,   (String)null,  beans,   docIdKey,   refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocumentsWithIdKey(String indexName,  List<Map> beans, String docIdKey) throws ElasticSearchException{
		return addDateDocumentsWithIdKey(  indexName,  (String)null,  beans,   docIdKey);
	}
	public   String addDocuments(String indexName,  List<Map> beans, String docIdKey, String refreshOption) throws ElasticSearchException{
		return addDocuments(  indexName,  (String)null,   beans,   docIdKey,   refreshOption);
	}
	public  String addDocumentsWithIdKey(String indexName,  List<Map> beans, String docIdKey) throws ElasticSearchException{
		return addDocumentsWithIdKey(  indexName,  (String)null,    beans,   docIdKey);
	}


	/**********************/
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocuments(String indexName,   List<Map> beans, String docIdKey, String parentIdKey, String refreshOption) throws ElasticSearchException{
		return addDateDocuments(  indexName, (String)null,   beans,   docIdKey,   parentIdKey,   refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdKey map中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocumentsWithIdKey(String indexName,   List<Map> beans, String docIdKey, String parentIdKey) throws ElasticSearchException{
		return addDateDocumentsWithIdKey(  indexName,  (String)null,   beans,   docIdKey,   parentIdKey);
	}
	public  String addDocuments(String indexName,  List<Map> beans, String docIdKey, String parentIdKey, String refreshOption) throws ElasticSearchException{
		return addDocuments(  indexName,   (String)null,beans,   docIdKey,   parentIdKey,   refreshOption);
	}
	public   String addDocumentsWithIdKey(String indexName,  List<Map> beans, String docIdKey, String parentIdKey) throws ElasticSearchException{
		return addDocumentsWithIdKey(  indexName, (String)null,   beans,   docIdKey,   parentIdKey);
	}

	/**
	 * 指定对象集合的文档id字段
	 */
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdField 对象中作为文档id的Field
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdOptions(String indexName,   List<Object> beans, String docIdField, String refreshOption) throws ElasticSearchException{
		return addDateDocumentsWithIdOptions( indexName,   (String)null, beans,   docIdField,   refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocumentsWithIdField(String indexName, List<Object> beans, String docIdField) throws ElasticSearchException{
		return addDateDocumentsWithIdField( indexName, (String)null,  beans,   docIdField);
	}
	public   String addDocumentsWithIdField(String indexName,  List<Object> beans, String docIdField, String refreshOption) throws ElasticSearchException{
		return addDocumentsWithIdField(  indexName, (String)null,   beans,   docIdField,   refreshOption);
	}
	public   String addDocumentsWithIdField(String indexName,   List<Object> beans, String docIdField) throws ElasticSearchException{
		return addDocumentsWithIdField( indexName,  (String)null, beans,   docIdField);
	}


	/**********************/
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocumentsWithIdField(String indexName,   List<Object> beans, String docIdField, String parentIdField, String refreshOption) throws ElasticSearchException{
		return addDateDocumentsWithIdField(  indexName,   (String)null,  beans,   docIdField,   parentIdField,   refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * @param indexName
	 * @param beans
	 * @param docIdField 对象中作为文档id的Key
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsWithIdField(String indexName , List<Object> beans, String docIdField, String parentIdField) throws ElasticSearchException{
		return addDateDocumentsWithIdField( indexName ,(String)null, beans,  docIdField,   parentIdField);
	}
	public String addDocumentsWithIdField(String indexName , List<Object> beans, String docIdField, String parentIdField, String refreshOption) throws ElasticSearchException{
		return addDocumentsWithIdField(  indexName ,(String)null, beans,   docIdField,   parentIdField,   refreshOption);
	}
	public   String addDocumentsWithIdParentField(String indexName , List<Object> beans, String docIdField, String parentIdField) throws ElasticSearchException{
		return addDocumentsWithIdParentField( indexName ,(String) null, beans,   docIdField,   parentIdField);
	}

	/**
	 *
	 * @param indexName
	 * @param beans
	 * @param ClientOptions 传递es操作的相关控制参数，采用ClientOptions后，定义在对象中的相关注解字段将不会起作用（失效）
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocuments(String indexName,   List<?> beans, ClientOptions ClientOptions) throws ElasticSearchException{
		return addDateDocuments(  indexName,  (String) null,  beans,   ClientOptions);
	}

	/**
	 *
	 * @param indexName
	 * @param beans
	 * @param ClientOptions 传递es操作的相关控制参数，采用ClientOptions后，定义在对象中的相关注解字段将不会起作用（失效）
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String addDocuments(String indexName, List<?> beans, ClientOptions ClientOptions) throws ElasticSearchException{
		return addDocuments(indexName, _doc,beans, ClientOptions);
	}
	/**************************************创建或者修改文档结束**************************************************************/


	/**************************************基于query dsl配置文件脚本创建或者修改文档开始**************************************************************/
	/**
	 * 创建索引文档，根据elasticsearch.xml中指定的日期时间格式，生成对应时间段的索引表名称
	 * @param indexName
	 * @param addTemplate
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDateDocumentNew(String indexName,   String addTemplate, Object bean) throws ElasticSearchException{
		return addDateDocument(  indexName,  _doc,   addTemplate,   bean);
	}

	/**
	 *
	 * @param indexName
	 * @param addTemplate
	 * @param bean
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentNew(String indexName, String addTemplate, Object bean, String refreshOption) throws ElasticSearchException{
		return addDateDocument(  indexName, _doc,    addTemplate,   bean,   refreshOption);
	}
	/**
	 * 批量创建索引,根据时间格式建立新的索引表
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param addTemplate
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDateDocumentsNew(String indexName,   String addTemplate, List<?> beans) throws ElasticSearchException{
		return addDateDocuments( indexName, _doc,   addTemplate, beans);
	}


	/**
	 * For Elasticsearch 7 and 7+
	 *
	 * @param indexName
	 * @param addTemplate
	 * @param beans
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public  String addDateDocumentsNew(String indexName,   String addTemplate, List<?> beans, String refreshOption) throws ElasticSearchException{
		return addDateDocuments( indexName,    _doc, addTemplate,  beans,   refreshOption);
	}

	/**************************************基于query dsl配置文件脚本创建或者修改文档结束**************************************************************/
	/**
	 *
	 * For Elasticsearch 7 and 7+
	 * @param indexName
	 * @param id
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String deleteDocumentNew(String indexName,  String id) throws ElasticSearchException{
		return deleteDocument( indexName,_doc  ,id);
	}

	/**
	 * For Elasticsearch 7 and 7+
	 *
	 * @param indexName
	 * @param id
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 * @return
	 * @throws ElasticSearchException
	 */
	public String deleteDocumentNew(String indexName,   String id, String refreshOption) throws ElasticSearchException{
		return deleteDocument(indexName,   _doc, id,  refreshOption);
	}




	/**
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param index _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param type
	 * @param ids
	 * @param <T>
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> List<T> mgetDocuments(String index, Class<T> type, Object... ids)  throws ElasticSearchException{
		return mgetDocuments(  index, _doc,type, ids);
	}
	/**
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html
	 * @param index _mget
	 *             test/_mget
	 *             test/type/_mget
	 *             test/type/_mget?stored_fields=field1,field2
	 *             _mget?routing=key1
	 * @param ids
	 * @return
	 * @throws ElasticSearchException
	 */
	public String mgetDocumentsNew(String index,  Object... ids)  throws ElasticSearchException{
		return mgetDocuments(index, _doc,ids);
	}



	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object id, Object params, Boolean detect_noop, Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument( index, (String)null,  id,   params,   detect_noop,   doc_as_upsert);
	}

	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param id
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object id, Map params, Boolean detect_noop, Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument(  index, (String)null,  id,  params,  detect_noop,  doc_as_upsert);
	}


	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param id
	 * @param params
	 * @param refreshOption
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,   Object id, Map params, String refreshOption, Boolean detect_noop, Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument(  index,  (String)null,   id,   params,  refreshOption,   detect_noop,   doc_as_upsert);
	}

	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param id
	 * @param params
	 * @param refreshOption
	 * @param detect_noop default null
	 * @param doc_as_upsert default null
	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,   Object id, Object params, String refreshOption, Boolean detect_noop, Boolean doc_as_upsert) throws ElasticSearchException{
		return updateDocument(index,   (String )null, id,  params,  refreshOption,  detect_noop,  doc_as_upsert);
	}


	/**
	 * 根据路径更新文档
	 * For Elasticsearch 7 and 7+
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html
	 * @param index test/_doc/1
	 *             test/_doc/1/_update
	 * @param params
	 * @param updateOptions 指定更新的相关参数

	 *    refresh=wait_for
	 *    refresh=false
	 *    refresh=true
	 *    refresh
	 *    Empty string or true
	Refresh the relevant primary and replica shards (not the whole index) immediately after the operation occurs, so that the updated document appears in search results immediately. This should ONLY be done after careful thought and verification that it does not lead to poor performance, both from an indexing and a search standpoint.
	wait_for
	Wait for the changes made by the request to be made visible by a refresh before replying. This doesn’t force an immediate refresh, rather, it waits for a refresh to happen. Elasticsearch automatically refreshes shards that have changed every index.refresh_interval which defaults to one second. That setting is dynamic. Calling the Refresh API or setting refresh to true on any of the APIs that support it will also cause a refresh, in turn causing already running requests with refresh=wait_for to return.
	false (the default)
	Take no refresh related actions. The changes made by this request will be made visible at some point after the request returns.
	 *
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(String index,  Object params, ClientOptions updateOptions) throws ElasticSearchException{
		 return updateDocument( index, (String) null,params, updateOptions);

	}
	public boolean isVersionUpper7(){
		return this.client.isUpper7();
	}

	/**
	 * 创建或者更新索引文档
	 *  indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param bean
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String addDocument(Object bean) throws ElasticSearchException{
		return this.addDocument((String)null,(String)null,bean);
	}
	/**
	 * 创建或者更新索引文档
	 *  indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param bean
	 * @param clientOptions
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocument(Object bean,ClientOptions clientOptions) throws ElasticSearchException{
		return this.addDocument((String)null,(String)null,bean,clientOptions);
	}

	/**
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param params
	 * @param updateOptions
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(Object params,ClientOptions updateOptions) throws ElasticSearchException{
		return updateDocument((String)null,(String)null,params,  updateOptions);
	}

	/**
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param documentId
	 * @param params
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocument(Object documentId,Object params) throws ElasticSearchException{
		return updateDocument((String)null,documentId,params);

	}

	/**
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocuments(List<?> beans) throws ElasticSearchException{
		try {
			BuildTool.initBatchContextThreadLocal();
			return this.addDocuments((String)null,(String)null,beans);
		}
		finally {
			BuildTool.cleanBatchContextThreadLocal();
		}
	}

	/**
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param beans
	 * @param clientOptions
	 * @return
	 * @throws ElasticSearchException
	 */
	public String addDocuments(List<?> beans,ClientOptions clientOptions) throws ElasticSearchException{
		try {
			BuildTool.initBatchContextThreadLocal();
			return this.addDocuments((String)null,(String)null,beans,clientOptions);
		}
		finally {
			BuildTool.cleanBatchContextThreadLocal();
		}
	}
	/**
	 *
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param beans
	 * @param clientOptions 传递es操作的相关控制参数，采用ClientOptions后，定义在对象中的相关注解字段将不会起作用（失效）
	 * @return
	 * @throws ElasticSearchException
	 */
	public String updateDocuments( List<?> beans,ClientOptions clientOptions) throws ElasticSearchException{
		try {
			BuildTool.initBatchContextThreadLocal();
			return this.updateDocuments((String) null, (String) null, beans, clientOptions);
		}
		finally {
			BuildTool.cleanBatchContextThreadLocal();
		}
	}

	/**
	 * indexName，	 indexType索引类型和type必须通过bean对象的ESIndex来指定，否则抛出异常
	 * @param beans
	 * @return
	 * @throws ElasticSearchException
	 */
	public   String updateDocuments( List<?> beans) throws ElasticSearchException{
		try {
			BuildTool.initBatchContextThreadLocal();
			return this.updateDocuments((String) null, (String) null, beans);
		}
		finally {
			BuildTool.cleanBatchContextThreadLocal();
		}
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
	 *
	 * @param scriptName
	 * @param scriptDsl
	 * @return
	 */
	public String createScript(String scriptName,String scriptDsl){
		return this.client.executeHttp(new StringBuilder().append("_scripts/").append(scriptName).toString(), scriptDsl,ClientUtil.HTTP_POST);
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
	 *
	 * @param scriptName
	 * @param scriptDslTemplate
	 * @return
	 */
	public String createScript(String scriptName,String scriptDslTemplate,Map params){
		return null;
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
	 *
	 * @param scriptName
	 * @param scriptDslTemplate
	 * @return
	 */
	public String createScript(String scriptName,String scriptDslTemplate,Object params){
		return null;
	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
	 * @param scriptName
	 * @return
	 */
	public String deleteScript(String scriptName){
		 return this.client.executeHttp(new StringBuilder().append("_scripts/").append(scriptName).toString(), ClientUtil.HTTP_DELETE);

	}

	/**
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
	 * @param scriptName
	 * @return
	 */
	public String getScript(String scriptName){
		try {
			return this.client.executeHttp(new StringBuilder().append("_scripts/").append(scriptName).toString(), ClientUtil.HTTP_GET);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_getScript);
		}
	}
	public PitId requestPitId(String index, String keepaliveTime){
		if(!keepaliveTime.startsWith("keep_alive=")) {
			return this.client.executeHttp(new StringBuilder().append("/").append(index).append("/_pit?keep_alive=").append(keepaliveTime).toString(),
					ClientUtil.HTTP_POST,new ESPitIdResponseHandler());
		}
		else{
			return this.client.executeHttp(new StringBuilder().append("/").append(index).append("/_pit?").append(keepaliveTime).toString(),
					ClientUtil.HTTP_POST,new ESPitIdResponseHandler());
		}
	}
	public String deletePitId(String pitId){
		try {
			return this.client.executeHttp("/_pit",
					new StringBuilder().append("{\"id\" : \"").append(pitId).append("\"}").toString(),
					ClientUtil.HTTP_DELETE);
		}
		catch(ElasticSearchException e){
			return ResultUtil.hand404HttpRuntimeException(e,String.class,ResultUtil.OPERTYPE_deletePitId);
		}
	}

}
