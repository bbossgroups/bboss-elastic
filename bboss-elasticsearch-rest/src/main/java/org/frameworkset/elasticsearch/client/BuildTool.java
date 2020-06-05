package org.frameworkset.elasticsearch.client;/*
 *  Copyright 2008 biaoping.yin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.frameworkset.orm.annotation.BatchContext;
import com.frameworkset.orm.annotation.ESIndexWrapper;
import com.frameworkset.orm.annotation.NameParserException;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.bulk.BulkActionConfig;
import org.frameworkset.elasticsearch.bulk.BulkData;
import org.frameworkset.elasticsearch.entity.ESIndice;
import org.frameworkset.elasticsearch.entity.IndexField;
import org.frameworkset.elasticsearch.entity.IndiceHeader;
import org.frameworkset.elasticsearch.serial.CharEscapeUtil;
import org.frameworkset.elasticsearch.serial.SerialUtil;
import org.frameworkset.soa.BBossStringWriter;
import org.frameworkset.util.ClassUtil;
import org.frameworkset.util.DataFormatUtil;
import org.frameworkset.util.annotations.DateFormateMeta;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.frameworkset.elasticsearch.client.ClientInterfaceNew._doc;

public abstract class BuildTool {
	private static final ThreadLocal<BatchContext> batchContextThreadLocal = new ThreadLocal<BatchContext>();

	public static void buildIndiceName(ESIndexWrapper esIndexWrapper,StringBuilder builder, ESIndexWrapper.GetVariableValue getVariableValue){
		String name = esIndexWrapper.getName();
		if(name != null){
			builder.append(name);
			return;
		}
		boolean useBatchContext = esIndexWrapper.isUseBatchContextIndexName();
		List<ESIndexWrapper.NameGrammarToken> tokens = esIndexWrapper.getNameTokens();
		if(tokens == null || tokens.size() == 0){
			return;
		}
		BatchContext batchContext = getVariableValue.getBatchContext();
		String indexName = null;
		if(batchContext != null && useBatchContext){
			indexName = batchContext.getIndexName(esIndexWrapper.getIndex());
			if(indexName != null){
				builder.append(indexName);
				return;
			}
		}
		boolean onlyCurrentDateTimestamp = esIndexWrapper.isOnlyCurrentDateTimestamp();
		ESIndexWrapper.NameGrammarToken nameGrammarToken = null;
		StringBuilder temp = onlyCurrentDateTimestamp && batchContext != null && indexName == null && useBatchContext?new StringBuilder():null;
		for(int i = 0; i < tokens.size(); i ++){
			nameGrammarToken = tokens.get(i);
			if(!nameGrammarToken.varibletoken()) {
				if(temp != null){
					temp.append(nameGrammarToken.getText());
				}
				builder.append(nameGrammarToken.getText());
			}
			else{
				if(nameGrammarToken.getFieldName() != null) {

//						Object va = classInfo.getPropertyValue(bean, nameGrammarToken.getFieldName());
					Object va = getVariableValue.getValue(nameGrammarToken.getFieldName());
					if (va == null)
						throw new NameParserException(new StringBuilder()
								.append(esIndexWrapper.getNameInfo().toString())
								.append(",property[")
								.append(nameGrammarToken.getFieldName()).append("] is null.").toString());
					if (nameGrammarToken.getDateformat() != null) {
						DateFormat dateFormat = DataFormatUtil.getSimpleDateFormat(nameGrammarToken.getDateformat());
						if (va instanceof Date) {
							builder.append(dateFormat.format((Date) va));
						} else if (va instanceof Long) {
							builder.append(dateFormat.format(new Date((Long) va)));

						} else {
							builder.append(va);
						}
					} else {
						builder.append(va);
					}
				}
				else{ //取当前时间作为索引名称
					DateFormat dateFormat = DataFormatUtil.getSimpleDateFormat(nameGrammarToken.getDateformat());
					Date date = new Date();
					String d = dateFormat.format(date);
					builder.append(d);
					if(temp != null){
						temp.append(d);
					}

				}
			}
		}
		if(temp != null && useBatchContext){
			batchContext.setIndexName(esIndexWrapper.getIndex(),temp.toString());
		}
	}

	public static void buildIndiceName(ESIndexWrapper esIndexWrapper,Writer writer, ESIndexWrapper.GetVariableValue getVariableValue) throws IOException {
		String name = esIndexWrapper.getName();
		if(name != null){
			writer.write(name);
			return;
		}
		List<ESIndexWrapper.NameGrammarToken> tokens = esIndexWrapper.getNameTokens();
		if(tokens == null || tokens.size() == 0){
			return;
		}

		BatchContext batchContext = getVariableValue.getBatchContext();
		boolean useBatchContextIndexName = esIndexWrapper.isUseBatchContextIndexName();
		String indexName = null;
		if(useBatchContextIndexName) {
			if (batchContext != null) {
				indexName = batchContext.getIndexName(esIndexWrapper.getIndex());
				if (indexName != null) {
					writer.write(indexName);
					return;
				}
			}
		}
		ESIndexWrapper.NameGrammarToken nameGrammarToken = null;
		boolean onlyCurrentDateTimestamp = esIndexWrapper.isOnlyCurrentDateTimestamp();
		StringBuilder temp = onlyCurrentDateTimestamp && batchContext != null && indexName == null && useBatchContextIndexName?new StringBuilder():null;
		for(int i = 0; i < tokens.size(); i ++){
			nameGrammarToken = tokens.get(i);
			if(!nameGrammarToken.varibletoken()) {
				if(temp != null){
					temp.append(nameGrammarToken.getText());
				}
				writer.write(nameGrammarToken.getText());
			}
			else{
				if(nameGrammarToken.getFieldName() != null) {

//						Object va = classInfo.getPropertyValue(bean, nameGrammarToken.getFieldName());
					Object va = getVariableValue.getValue(nameGrammarToken.getFieldName());
					if (va == null)
						throw new NameParserException(new StringBuilder()
								.append(esIndexWrapper.getNameInfo().toString())
								.append(",property[")
								.append(nameGrammarToken.getFieldName()).append("] is null.").toString());
					if (nameGrammarToken.getDateformat() != null) {
						DateFormat dateFormat = DataFormatUtil.getSimpleDateFormat(nameGrammarToken.getDateformat());
						if (va instanceof Date) {
							writer.write(dateFormat.format((Date) va));
						} else if (va instanceof Long) {
							writer.write(dateFormat.format(new Date((Long) va)));

						} else {
							writer.write(String.valueOf(va));
						}
					} else {
						writer.write(String.valueOf(va));
					}
				}
				else{ //取当前时间作为索引名称
					DateFormat dateFormat = DataFormatUtil.getSimpleDateFormat(nameGrammarToken.getDateformat());
					Date date = new Date();
					String d = dateFormat.format(date);
					writer.write(d);
					if(temp != null){
						temp.append(d);
					}

				}
			}
		}
		if(useBatchContextIndexName) {
			if (temp != null) {
				batchContext.setIndexName(esIndexWrapper.getIndex(),temp.toString());
			}
		}
	}







	/**
	 * ClassUtil.ClassInfo classInfo, Object bean
	 * @param builder
	 * @param getVariableValue
	 */
	public static void buildIndiceType(ESIndexWrapper esIndexWrapper,StringBuilder builder, ESIndexWrapper.GetVariableValue getVariableValue){
		ESIndexWrapper.TypeInfo typeInfo = esIndexWrapper.getTypeInfo();
		if(typeInfo == null){
			builder.append(_doc);
			return;
		}
		String type = typeInfo.getType();
		if(type != null){
			builder.append(type);
			return;
		}
		List<ESIndexWrapper.NameGrammarToken> tokens = typeInfo.getTokens();
		if(tokens == null || tokens.size() == 0){
			builder.append(_doc);
			return;
		}
		boolean useBatchContext = esIndexWrapper.isUseBatchContextIndexType();
		BatchContext batchContext = getVariableValue.getBatchContext();
		if(batchContext != null && useBatchContext){
			if(batchContext.getIndexType() != null){
				builder.append(batchContext.getIndexType());
				return  ;
			}
		}
		ESIndexWrapper.NameGrammarToken nameGrammarToken = null;
		for(int i = 0; i < tokens.size(); i ++){
			nameGrammarToken = tokens.get(i);
			if(!nameGrammarToken.varibletoken()) {
				builder.append(nameGrammarToken.getText());
			}
			else{
//					Object va = classInfo.getPropertyValue(bean,nameGrammarToken.getFieldName());
				Object va = getVariableValue.getValue(nameGrammarToken.getFieldName());
				if(va == null)
					throw new NameParserException(new StringBuilder()
							.append(typeInfo.toString())
							.append(",property[")
							.append(nameGrammarToken.getFieldName()).append("] is null.").toString());
				builder.append(va);
			}
		}


	}
	/**
	 * ClassUtil.ClassInfo classInfo, Object bean
	 * @param getVariableValue
	 */
	public static String buildIndiceType(ESIndexWrapper esIndexWrapper, ESIndexWrapper.GetVariableValue getVariableValue){
		ESIndexWrapper.TypeInfo typeInfo = esIndexWrapper.getTypeInfo();
		if(typeInfo == null){
			return null;
		}
		String type = typeInfo.getType();
		if(type != null){
			return type;
		}
		List<ESIndexWrapper.NameGrammarToken> tokens = typeInfo.getTokens();
		if(tokens == null || tokens.size() == 0){
			return null;
		}
		boolean useBatchContext = esIndexWrapper.isUseBatchContextIndexType();
		BatchContext batchContext = getVariableValue.getBatchContext();
		if(batchContext != null && useBatchContext){
			if(batchContext.getIndexType() != null){
				return (batchContext.getIndexType());
			}
		}
		ESIndexWrapper.NameGrammarToken nameGrammarToken = null;
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < tokens.size(); i ++){
			nameGrammarToken = tokens.get(i);
			if(!nameGrammarToken.varibletoken()) {
				builder.append(nameGrammarToken.getText());
			}
			else{
//					Object va = classInfo.getPropertyValue(bean,nameGrammarToken.getFieldName());
				Object va = getVariableValue.getValue(nameGrammarToken.getFieldName());
				if(va == null)
					throw new NameParserException(new StringBuilder()
							.append(typeInfo.toString())
							.append(",property[")
							.append(nameGrammarToken.getFieldName()).append("] is null.").toString());
				builder.append(va);
			}
		}
		return builder.toString();


	}


	public static BatchContext initBatchContextThreadLocal(){
		BatchContext batchContext = new BatchContext();
		batchContextThreadLocal.set(batchContext);
		return batchContext;
	}
	public static void cleanBatchContextThreadLocal(){
		batchContextThreadLocal.set(null);
	}
	public static BatchContext getBatchContext(){
		return batchContextThreadLocal.get();
	}
	/**
	 * health status index                         uuid                   pri rep docs.count docs.deleted store.size pri.store.size
	 * @param lineHeader
	 * @return
	 */
	public static Map<Integer,IndiceHeader> buildIndiceHeaders(String lineHeader){
		if(lineHeader == null)
			return null;
		lineHeader = lineHeader.trim();
		Map<Integer,IndiceHeader> indiceHeaders = new HashMap<Integer,IndiceHeader>();
		int k = 0;
		IndiceHeader indiceHeader = null;
		StringBuilder token = new StringBuilder();
		int offset = 0;
		for(int j = 0; j < lineHeader.length(); j ++){
			char c = lineHeader.charAt(j);
			if(c != ' '){
				if(token.length() == 0)
					offset = j;
				token.append(c);
			}
			else {
				if(token.length() == 0)
					continue;
				indiceHeader = new IndiceHeader();
				indiceHeader.setHeaderName(token.toString());
				indiceHeader.setOffset(offset);
				indiceHeader.setPosition(k);
				indiceHeaders.put(k,indiceHeader);
				token.setLength(0);
				k ++;
			}
		}
		if(token.length() > 0){
			indiceHeader = new IndiceHeader();
			indiceHeader.setHeaderName(token.toString());
			indiceHeader.setPosition(k);
			indiceHeaders.put(k,indiceHeader);
			token.setLength(0);
		}
		return indiceHeaders;

	}

	/**
	 * health status index                         uuid                   pri rep docs.count docs.deleted store.size pri.store.size
	 * @param lineHeader
	 * @return
	 */
	public static List<IndiceHeader> buildListIndiceHeaders(String lineHeader){
		if(lineHeader == null)
			return null;
		lineHeader = lineHeader.trim();
		List<IndiceHeader> indiceHeaders = new ArrayList<IndiceHeader>();
		int k = 0;
		IndiceHeader indiceHeader = null;
		StringBuilder token = new StringBuilder();
		int offset = 0;
		for(int j = 0; j < lineHeader.length(); j ++){
			char c = lineHeader.charAt(j);
			if(c != ' '){
				if(token.length() == 0)
					offset = j;
				token.append(c);
			}
			else {
				if(token.length() == 0)
					continue;
				indiceHeader = new IndiceHeader();
				indiceHeader.setHeaderName(token.toString());
				indiceHeader.setOffset(offset);
				indiceHeader.setPosition(k);
				indiceHeaders.add(indiceHeader);
				token.setLength(0);
				k ++;
			}
		}
		if(token.length() > 0){
			indiceHeader = new IndiceHeader();
			indiceHeader.setHeaderName(token.toString());
			indiceHeader.setPosition(k);
			indiceHeader.setOffset(offset);
			indiceHeaders.add(indiceHeader);
			token.setLength(0);
		}
		return indiceHeaders;

	}

	/**
	 * health status index                         uuid                   pri rep docs.count docs.deleted store.size pri.store.size
	 * @param esIndice
	 * @param indiceHeader
	 * @param token
	 * @param format
	 */
	private static void putField(ESIndice esIndice,IndiceHeader indiceHeader,StringBuilder token,SimpleDateFormat format){
//		IndiceHeader indiceHeader = indiceHeaders.get(position);
		if(indiceHeader.getHeaderName().equals("health")) {
			if(token.length() > 0) {
				esIndice.setHealth(token.toString());
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("status")) {
			if(token.length() > 0) {
				esIndice.setStatus(token.toString());
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("index")) {
			if(token.length() > 0) {
				esIndice.setIndex(token.toString());
				putGendate(esIndice, format);
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("uuid")) {
			if(token.length() > 0) {
				esIndice.setUuid(token.toString());
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("pri")) {
			if(token.length() > 0) {
				esIndice.setPri(Integer.parseInt(token.toString()));
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("rep")) {
			if(token.length() > 0) {
				esIndice.setRep(Integer.parseInt(token.toString()));
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("docs.count")) {
			if(token.length() > 0) {
				esIndice.setDocsCcount(Long.parseLong(token.toString()));
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("docs.deleted")) {
			if(token.length() > 0) {
				esIndice.setDocsDeleted(Long.parseLong(token.toString()));
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("store.size")) {
			if(token.length() > 0) {
				esIndice.setStoreSize(token.toString());
				token.setLength(0);
			}
		}
		else if(indiceHeader.getHeaderName().equals("pri.store.size")) {
			if(token.length() > 0) {
				esIndice.setPriStoreSize(token.toString());
				token.setLength(0);
			}
		}
		else{
			esIndice.addOtherData(indiceHeader.getHeaderName(),token.toString());
			token.setLength(0);
		}


	}
	private static IndiceHeader fieldValueStart(int offset,List<IndiceHeader> indiceHeaderList) {
		for(IndiceHeader indiceHeader: indiceHeaderList){
			if(offset == indiceHeader.getOffset()){
				return indiceHeader;
			}
		}
		return null;
	}
	public static String findByFieldValueDsl(String fieldName,Object fieldValue){
		StringBuilder builder = new StringBuilder();
		 builder.append("{ \"size\":").append(1).append(",\"query\": {\"bool\": {\"filter\":[{\"term\":{\"")
				.append(fieldName).append("\":");


		CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));
		if(fieldValue instanceof String) {
			builder.append("\"");
			charEscapeUtil.writeString((String) fieldValue, true);
			builder.append("\"");
		}
		else if(fieldValue instanceof Date){
			DateFormateMeta dateFormateMeta = SerialUtil.getDateFormateMeta();
			DateFormat format = dateFormateMeta.toDateFormat();
			builder.append("\"");
			builder.append(format.format((Date)fieldValue));
			builder.append("\"");
		}
		else{
			builder.append(String.valueOf(fieldValue));
		}
		builder.append("}}]}}}");
		return builder.toString();
	}



	public static String matchByFieldValueDsl(String fieldName,Object fieldValue){
		StringBuilder builder = new StringBuilder();
		builder.append("{ \"size\":").append(1).append(",\"query\": {\"match\": {\"")
				.append(fieldName).append("\":");


		CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));

			builder.append("\"");
			charEscapeUtil.writeString(String.valueOf( fieldValue), true);
			builder.append("\"");
		 builder.append("}}}");

		return builder.toString();
	}

	public static String findByFieldValueDsl(String fieldName,Object fieldValue,int from,int size){
		StringBuilder builder = new StringBuilder();
		builder.append("{ \"from\":").append(from).append(",\"size\":").append(size).append(",\"query\": {\"bool\": {\"filter\":[{\"term\":{\"")
				.append(fieldName).append("\":");


		CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));
		if(fieldValue instanceof String) {
			builder.append("\"");
			charEscapeUtil.writeString((String) fieldValue, true);
			builder.append("\"");
		}
		else if(fieldValue instanceof Date){
			DateFormateMeta dateFormateMeta = SerialUtil.getDateFormateMeta();
			DateFormat format = dateFormateMeta.toDateFormat();
			builder.append("\"");
			builder.append(format.format((Date)fieldValue));
			builder.append("\"");
		}
		else{
			builder.append(String.valueOf(fieldValue));
		}
		builder.append("}}]}}}");
		return builder.toString();
	}



	public static String matchByFieldValueDsl(String fieldName,Object fieldValue,int from,int size){
		StringBuilder builder = new StringBuilder();
		builder.append("{ \"from\":").append(from).append(",\"size\":").append(size).append(",\"query\": {\"match\": {\"")
				.append(fieldName).append("\":");


		CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));

		builder.append("\"");
		charEscapeUtil.writeString(String.valueOf( fieldValue), true);
		builder.append("\"");
		builder.append("}}}");

		return builder.toString();
	}
	public static ESIndice buildESIndice(String line, SimpleDateFormat format,
										 List<IndiceHeader> indiceHeaderList)
	{
		StringBuilder token = new StringBuilder();
		ESIndice esIndice = new ESIndice();

		IndiceHeader indiceHeader = null;
		for(int j = 0; j < line.length(); j ++){
			IndiceHeader _indiceHeader = fieldValueStart(j,indiceHeaderList);
			if(_indiceHeader != null){
				if(indiceHeader == null) {

				}
				else{
					putField(esIndice,indiceHeader,token,format);
				}
				indiceHeader = _indiceHeader;
			}
			char c = line.charAt(j);
			if(c != ' '){
				token.append(c);
			}
			else {
				if(token.length() == 0) {
					continue;
				}
				if(indiceHeader != null) {

					putField(esIndice, indiceHeader, token, format);
					indiceHeader = null;
				}


			}
		}
		if(token.length() > 0){
			putField(esIndice,indiceHeader,token,format);
		}
		return esIndice;
	}
	public static void putGendate(ESIndice esIndice,SimpleDateFormat format){
		int dsplit = esIndice.getIndex().lastIndexOf('-');

		try {
			if(dsplit > 0){
				String date = esIndice.getIndex().substring(dsplit+1);
				esIndice.setGenDate((Date)format.parseObject(date));
			}

		} catch (Exception e) {

		}
	}

	public static String buildGetDocumentRequest(String indexName, String indexType,String documentId,Map<String,Object> options){
		if(documentId == null)
			throw new ElasticSearchException(new StringBuilder().append("GetDocumentRequest failed:indexName[")
											.append(indexName)
											.append("] indexType[").append(indexType).append("] documentId is null.").toString());
		if(documentId.equals(""))
			throw new ElasticSearchException(new StringBuilder().append("GetDocumentRequest failed:indexName[")
					.append(indexName)
					.append("] indexType[").append(indexType).append("] documentId is \"\".").toString());
		StringBuilder builder = new StringBuilder();
//		builder.append("/").append(indexName).append("/").append(indexType).append("/").append(documentId);
		builder.append("/").append(indexName).append("/").append(indexType).append("/");
		CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));
		charEscapeUtil.writeString(documentId, true);
		if(options != null){
			builder.append("?");
			Iterator<Map.Entry<String, Object>> iterable = options.entrySet().iterator();
			boolean first = true;
			while(iterable.hasNext()){
				Map.Entry<String, Object> entry = iterable.next();
				if(first) {
					builder.append(entry.getKey()).append("=").append(entry.getValue());
					first = false;
				}
				else
				{
					builder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
				}
			}
		}
		return builder.toString();
	}

	public static String buildSearchDocumentRequest(String indexName, String indexType,Map<String,Object> options){

		StringBuilder builder = new StringBuilder();
//		builder.append("/").append(indexName).append("/").append(indexType).append("/").append(documentId);
		builder.append("/").append(indexName);
		if(indexType != null)
			builder.append("/").append(indexType);
		builder.append("/_search");
		if(options != null && options.size() > 0){
			builder.append("?");
			Iterator<Map.Entry<String, Object>> iterable = options.entrySet().iterator();
			boolean first = true;
			while(iterable.hasNext()){
				Map.Entry<String, Object> entry = iterable.next();
				if(first) {
					builder.append(entry.getKey()).append("=").append(entry.getValue());
					first = false;
				}
				else
				{
					builder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
				}
			}
		}
		return builder.toString();
	}
	public static void buildId(Object id,StringBuilder builder,boolean escape){
		if (id instanceof String) {
			if(!escape) {
				builder.append("\"")
						.append(id).append("\"");
			}
			else{
				builder.append("\"");
				CharEscapeUtil charEscapeUtil = new CharEscapeUtil(new BBossStringWriter(builder));
				charEscapeUtil.writeString((String) id, true);
				builder.append("\"");
			}

		}
		else{
			builder.append(id);
		}
	}
	public static void buildId(Object id,Writer writer,boolean escape) throws IOException {
		if (id instanceof String) {
			writer.write("\"");
			if(!escape) {
				writer.write((String) id);
			}
			else{
				CharEscapeUtil charEscapeUtil = new CharEscapeUtil(writer);
				charEscapeUtil.writeString((String) id, true);
			}
			writer.write("\"");

		}
		else{
			writer.write(String.valueOf(id));
		}
	}

	/**
	public static void buildMeta(ClassUtil.ClassInfo beanInfo,Writer writer ,String indexType,String indexName, Object params,String action,boolean upper7) throws IOException {
//		ClassUtil.ClassInfo beanInfo = ClassUtil.getClassInfo(params.getClass());
		Object id = getId(params,beanInfo);
		Object parentId = getParentId(params,beanInfo);
		Object routing = getRouting(params,beanInfo);
		Object esRetryOnConflict = getEsRetryOnConflict(params,beanInfo);

		buildMeta( beanInfo, writer ,  indexType,  indexName,   params,  action,  id,  parentId,routing,esRetryOnConflict,  upper7);
	}
*/

	/**
	public static void buildMetaWithDocIdKey(Writer writer ,String indexType,String indexName, Map params,String action,String docIdKey,String parentIdKey,boolean upper7) throws IOException {
//		Object id = docIdKey != null ?params.get(docIdKey):null;
//		Object parentId = parentIdKey != null ?params.get(parentIdKey):null;
//		buildMeta(  writer ,  indexType,  indexName,   params,  action,  id,  parentId,null);
		buildMetaWithDocIdKey(writer ,indexType,indexName, params,action,docIdKey,parentIdKey,null,  upper7);
	}*/

	/**
	public static void buildMetaWithDocIdField(Writer writer ,String indexType,String indexName, Object params,String action,String docIdField,String parentIdField,boolean upper7) throws IOException {
//		Object id = docIdKey != null ?params.get(docIdKey):null;
//		Object parentId = parentIdKey != null ?params.get(parentIdKey):null;
//		buildMeta(  writer ,  indexType,  indexName,   params,  action,  id,  parentId,null);
//		buildMetaWithDocIdKey(writer ,indexType,indexName, params,action,docIdKey,parentIdKey,null);
		ClientOptions clientOption = new ClientOptions();
		clientOption.setIdField(docIdField);
		clientOption.setParentIdField(parentIdField);
		buildMeta(  writer ,  indexType,  indexName,   params,  action,  clientOption,  upper7);
	}
	 */
	/**
	public static void buildMetaWithDocIdKey(Writer writer ,String indexType,String indexName, Map params,String action,String docIdKey,String parentIdKey,String routingKey,boolean upper7) throws IOException {
		Object id = docIdKey != null ?params.get(docIdKey):null;
		Object parentId = parentIdKey != null ?params.get(parentIdKey):null;
		Object routing = routingKey != null ?params.get(routingKey):null;

		buildMeta( null, writer ,  indexType,  indexName,   params,  action,  id,  parentId,routing,  upper7);
	}*/
/**
	public static void buildMapMeta(Writer writer ,String indexType,String indexName, Map params,String action,ClientOptions clientOptions,boolean upper7) throws IOException {
		Object id = null;
		Object parentId = null;
		Object routing = null;
		Object esRetryOnConflict = null;
		if(clientOptions != null) {
			id = clientOptions.getIdField() != null ? params.get(clientOptions.getIdField()) : null;
			parentId = clientOptions.getParentIdField() != null ? params.get(clientOptions.getParentIdField()) : null;
			if(clientOptions.getRouting() == null) {
				routing = clientOptions.getRoutingField() != null ? params.get(clientOptions.getRoutingField()) : null;
			}
			else{
				routing = clientOptions.getRouting();
			}
			if(clientOptions.getEsRetryOnConflict() == null) {
				esRetryOnConflict = clientOptions.getEsRetryOnConflictField() != null ? params.get(clientOptions.getEsRetryOnConflictField()) : null;
			}
			else{
				esRetryOnConflict = clientOptions.getEsRetryOnConflict();
			}
		}
		buildMeta(null,  writer ,  indexType,  indexName,   params,  action,  id,  parentId,routing,esRetryOnConflict,  upper7);
	}
 */
	public static String buildActionUrl(BulkActionConfig bulkConfig){
		if(bulkConfig == null)
			return "_bulk";
		StringBuilder url = new StringBuilder();
		url.append("_bulk");
		String refreshOption = bulkConfig.getRefreshOption();
		if(refreshOption != null)
			url.append("?").append(refreshOption);
		else{
			String refresh  = bulkConfig.getRefresh();
			boolean p = false;
			if(refresh != null) {
				url.append("?refresh=").append(refresh);
				p = true;
			}
			/**
			 Long if_seq_no = clientOptions.getIfSeqNo();
			 if(if_seq_no != null){
			 if(p){
			 url.append("&if_seq_no=").append(if_seq_no);
			 }
			 else{
			 url.append("?if_seq_no=").append(if_seq_no);
			 p = true;
			 }
			 }
			 Long if_primary_term = clientOptions.getIfPrimaryTerm();
			 if(if_primary_term != null){
			 if(p){
			 url.append("&if_primary_term=").append(if_primary_term);
			 }
			 else{
			 url.append("?if_primary_term=").append(if_primary_term);
			 p = true;
			 }
			 }*/

			/**
			 Object retry_on_conflict = clientOptions.getEsRetryOnConflict();
			 if(retry_on_conflict != null){
			 if(p){
			 url.append("&retry_on_conflict=").append(retry_on_conflict);
			 }
			 else{
			 url.append("?retry_on_conflict=").append(retry_on_conflict);
			 p = true;
			 }
			 }*/
			Object routing = bulkConfig.getRouting();
			if(routing != null){
				if(p){
					url.append("&routing=").append(routing);
				}
				else{
					url.append("?routing=").append(routing);
					p = true;
				}
			}
			String timeout = bulkConfig.getTimeout();
			if(timeout != null){
				if(p){
					url.append("&timeout=").append(timeout);
				}
				else{
					url.append("?timeout=").append(timeout);
					p = true;
				}
			}
			/**
			 String master_timeout = clientOptions.getMasterTimeout();
			 if(master_timeout != null){
			 if(p){
			 url.append("&master_timeout=").append(master_timeout);
			 }
			 else{
			 url.append("?master_timeout=").append(master_timeout);
			 p = true;
			 }
			 }*/
			Integer wait_for_active_shards = bulkConfig.getWaitForActiveShards();
			if(wait_for_active_shards != null){
				if(p){
					url.append("&wait_for_active_shards=").append(wait_for_active_shards);
				}
				else{
					url.append("?wait_for_active_shards=").append(wait_for_active_shards);
					p = true;
				}
			}
			/**
			 String op_type = clientOptions.getOpType();
			 if(op_type != null){
			 if(p){
			 url.append("&op_type=").append(op_type);
			 }
			 else{
			 url.append("?op_type=").append(op_type);
			 p = true;
			 }
			 }*/
			String pipeline = bulkConfig.getPipeline();
			if(pipeline != null){
				if(p){
					url.append("&pipeline=").append(pipeline);
				}
				else{
					url.append("?pipeline=").append(pipeline);
					p = true;
				}
			}


		}
		return url.toString();
	}
	/**
	 * 构建请求地址参数
	 * @throws IOException
	 */
	public static String buildAddPathUrlMeta(String indexName ,String indexType,Object params,ClientOptions clientOptions,ClassUtil.ClassInfo beanInfo)  {
		Object docId = null;
		Object parentId = null;
		Object routing = null;
		String refreshOption = null;
		Object esRetryOnConflict = null;
		Object version = null;
		Object versionType = null;
		ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(params.getClass());
		if(clientOptions != null) {
			if (clientOptions.getId() != null) {
				docId = clientOptions.getId();
			} else {
				docId = clientOptions.getIdField() != null ? BuildTool.getId(params, beanClassInfo, clientOptions.getIdField()) : getId(params, beanInfo);
			}
		}
		else{
			docId = getId(params,  beanInfo );
		}
		if(clientOptions != null) {
			if (clientOptions.getParentId() != null) {
				parentId = clientOptions.getParentId();
			} else{
				parentId = clientOptions.getParentIdField() != null ? BuildTool.getParentId(params, beanClassInfo, clientOptions.getParentIdField()) : getParentId(params, beanInfo);
			}
		}
		else{
			parentId = getParentId(params,beanInfo);
		}

		if(clientOptions != null) {
			refreshOption = clientOptions.getRefreshOption();

//			parentId = clientOptions.getParentIdField() != null ? BuildTool.getParentId(params, beanClassInfo, clientOptions.getParentIdField()) : getParentId(params,beanInfo);
//			if(clientOptions.getRouting() == null) {
//				routing = clientOptions.getRoutingField() != null ? BuildTool.getRouting(params, beanClassInfo, clientOptions.getRoutingField()) : null;
//			}
//			else{
//				routing = clientOptions.getRouting();
//			}
		}


		if(clientOptions != null) {

			if(clientOptions.getRouting() == null) {
				routing = clientOptions.getRoutingField() != null ? BuildTool.getRouting(params, beanInfo, clientOptions.getRoutingField()) : getRouting(params,beanInfo);
			}
			else{
				routing = clientOptions.getRouting();
			}


			if(clientOptions.getEsRetryOnConflict() == null) {
				esRetryOnConflict = clientOptions.getEsRetryOnConflictField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo,
						clientOptions.getEsRetryOnConflictField()) : getEsRetryOnConflict(params,beanInfo);
			}
			else{
				esRetryOnConflict = clientOptions.getEsRetryOnConflict();
			}
			if(clientOptions.getVersion() == null) {
				version = clientOptions.getVersionField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOptions.getVersionField()) : getVersion(  beanInfo,   params);
			}
			else{
				version = clientOptions.getVersion();
			}
			if(clientOptions.getVersionType() == null) {
				versionType = clientOptions.getVersionTypeField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOptions.getVersionTypeField()) : getVersionType(  beanInfo,   params);
			}else{
				versionType = clientOptions.getVersionType();
			}
		}
		else{

			routing = getRouting(params,beanInfo);
			esRetryOnConflict = getEsRetryOnConflict(params,beanInfo);
			version = getVersion(  beanInfo,   params);

			versionType = getVersionType(  beanInfo,   params);
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
		boolean p = false;
		if(refreshOption != null ){
			builder.append("?").append(refreshOption);
			if(parentId != null){
				builder.append("&parent=").append(parentId);

			}
			if(routing != null){
				builder.append("&routing=").append(routing);
			}
			p = true;
		}
		else{
			if(parentId != null){
				builder.append("?parent=").append(parentId);
				if(routing != null){
					builder.append("&routing=").append(routing);
				}
				p = true;
			}
			else if(routing != null){
				builder.append("?routing=").append(routing);
				p = true;
			}

		}
		if(esRetryOnConflict != null){
			if(p)
				builder.append("&retry_on_conflict=").append(esRetryOnConflict);
			else {
				builder.append("?retry_on_conflict=").append(esRetryOnConflict);
				p = true;
			}
		}
		if(version != null){
			if(p)
				builder.append("&version=").append(version);
			else {
				builder.append("?version=").append(version);
				p = true;
			}
		}
		if(versionType != null){
			if(p)
				builder.append("&version_type=").append(versionType);
			else {
				builder.append("?version_type=").append(versionType);
				p = true;
			}
		}
		Long if_seq_no = clientOptions!= null?clientOptions.getIfSeqNo():null;
		if(if_seq_no != null){
			if(p)
				builder.append("&if_seq_no=").append(if_seq_no);
			else {
				builder.append("?if_seq_no=").append(if_seq_no);
				p = true;
			}
		}
		Long if_primary_term = clientOptions!= null?clientOptions.getIfPrimaryTerm():null;
		if(if_primary_term != null){
			if(p)
				builder.append("&if_primary_term=").append(if_primary_term);
			else {
				builder.append("?if_primary_term=").append(if_primary_term);
				p = true;
			}
		}
		String pipeline = clientOptions!= null?clientOptions.getPipeline():null;
		if(pipeline != null){
			if(p)
				builder.append("&pipeline=").append(pipeline);
			else {
				builder.append("?pipeline=").append(pipeline);
				p = true;
			}
		}
		String op_type = clientOptions!= null?clientOptions.getOpType():null;

		if(op_type != null){
			if(p)
				builder.append("&op_type=").append(op_type);
			else {
				builder.append("?op_type=").append(op_type);
				p = true;
			}
		}
		String refresh = clientOptions!= null?clientOptions.getRefresh():null;

		if(refresh != null){
			if(p)
				builder.append("&refresh=").append(refresh);
			else {
				builder.append("?refresh=").append(refresh);
				p = true;
			}
		}
		String timeout = clientOptions!= null?clientOptions.getTimeout():null;

		if(timeout != null){
			if(p)
				builder.append("&timeout=").append(timeout);
			else {
				builder.append("?timeout=").append(timeout);
				p = true;
			}
		}

		String master_timeout = clientOptions!= null?clientOptions.getMasterTimeout():null;

		if(master_timeout != null){
			if(p)
				builder.append("&master_timeout=").append(master_timeout);
			else {
				builder.append("?master_timeout=").append(master_timeout);
				p = true;
			}
		}

		Integer wait_for_active_shards = clientOptions!= null?clientOptions.getWaitForActiveShards():null;

		if(wait_for_active_shards != null){
			if(p)
				builder.append("&wait_for_active_shards=").append(wait_for_active_shards);
			else {
				builder.append("?wait_for_active_shards=").append(wait_for_active_shards);
				p = true;
			}
		}
		return builder.toString();






//		if (esRetryOnConflict != null) {
//			if(!upper7) {
//				writer.write(",\"_retry_on_conflict\":");
//			}
//			else{
//				writer.write(",\"retry_on_conflict\":");
//			}
//			writer.write(String.valueOf(esRetryOnConflict));
//		}






	}

	/**
	 * 构建请求地址参数
	 * @throws IOException
	 */
	public static String buildUpdatePathUrlMeta(String index ,String indexType,Object params,ClientOptions clientOptions,ClassUtil.ClassInfo beanInfo,boolean uper7)  {
		Object docId = null;
//		Object parentId = null;
		Object routing = null;
		String refreshOption = null;
		Object esRetryOnConflict = null;
		Object version = null;
		Object versionType = null;
		ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(params.getClass());
		if(clientOptions != null) {
			if (clientOptions.getId() != null) {
				docId = clientOptions.getId();
			} else {
				docId = clientOptions.getIdField() != null ? BuildTool.getId(params, beanClassInfo, clientOptions.getIdField()) : getId(params, beanInfo);
			}
		}
		else{
			docId = getId(params,  beanInfo );
		}

//		if(clientOptions != null) {
//			if (clientOptions.getParentId() != null) {
//				parentId = clientOptions.getParentId();
//			} else{
//				parentId = clientOptions.getParentIdField() != null ? BuildTool.getParentId(params, beanClassInfo, clientOptions.getParentIdField()) : getParentId(params, beanInfo);
//			}
//		}
//		else{
//			parentId = getParentId(params,beanInfo);
//		}

		if(clientOptions != null) {
			refreshOption = clientOptions.getRefreshOption();

//			parentId = clientOptions.getParentIdField() != null ? BuildTool.getParentId(params, beanClassInfo, clientOptions.getParentIdField()) : getParentId(params,beanInfo);
//			if(clientOptions.getRouting() == null) {
//				routing = clientOptions.getRoutingField() != null ? BuildTool.getRouting(params, beanClassInfo, clientOptions.getRoutingField()) : null;
//			}
//			else{
//				routing = clientOptions.getRouting();
//			}
		}


		if(clientOptions != null) {

			if(clientOptions.getRouting() == null) {
				routing = clientOptions.getRoutingField() != null ? BuildTool.getRouting(params, beanInfo, clientOptions.getRoutingField()) : getRouting(params,beanInfo);
			}
			else{
				routing = clientOptions.getRouting();
			}


			 if(clientOptions.getEsRetryOnConflict() == null) {
				 esRetryOnConflict = clientOptions.getEsRetryOnConflictField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo,
				 clientOptions.getEsRetryOnConflictField()) : getEsRetryOnConflict(params,beanInfo);
			 }
			 else{
			 	 esRetryOnConflict = clientOptions.getEsRetryOnConflict();
			 }
			if(clientOptions.getVersion() == null) {
				version = clientOptions.getVersionField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOptions.getVersionField()) : getVersion(  beanInfo,   params);
			}
			else{
				version = clientOptions.getVersion();
			}
			if(clientOptions.getVersionType() == null) {
				versionType = clientOptions.getVersionTypeField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOptions.getVersionTypeField()) : getVersionType(  beanInfo,   params);
			}else{
				versionType = clientOptions.getVersionType();
			}
		}
		else{

			routing = getRouting(params,beanInfo);
			esRetryOnConflict = getEsRetryOnConflict(params,beanInfo);
			version = getVersion(  beanInfo,   params);

			versionType = getVersionType(  beanInfo,   params);
		}

		StringBuilder builder = new StringBuilder();
		Object id = docId;
		if(index == null){
			if(beanClassInfo == null){
				throw   new ElasticSearchException(" _addDocument failed: Class info not setted.");
			}
			ESIndexWrapper esIndexWrapper = beanClassInfo.getEsIndexWrapper();
			if(esIndexWrapper == null){
				throw new ElasticSearchException(builder.append(" ESIndex annotation do not set in class ").append(beanClassInfo.toString()).toString());
			}
			RestGetVariableValue restGetVariableValue = new RestGetVariableValue(beanClassInfo,params);
			BuildTool.buildIndiceName(esIndexWrapper,builder,restGetVariableValue);


			if(!uper7) {
//				if(indexType == null){
//					builder.append("/");
//					BuildTool.buildIndiceType(esIndexWrapper,builder,restGetVariableValue);
//				}
//				else{
//					builder.append("/").append(indexType);
//				}
//				builder.append("/").append(id).append("/_update");
				if (indexType == null || indexType.equals("")) {
					indexType = buildIndiceType(esIndexWrapper, restGetVariableValue);
				}
				if (indexType == null || indexType.equals("")) {

					builder.append("/").append(id).append("/_update");
				}
				else
					builder.append("/").append(indexType).append("/").append(id).append("/_update");
			}
			else{
				builder.append("/_update").append("/").append(id);
			}
		}
		else {
			if(!uper7) {
				if (indexType == null || indexType.equals(""))
					builder.append(index).append("/").append(id).append("/_update");
				else
					builder.append(index).append("/").append(indexType).append("/").append(id).append("/_update");
			}
			else{
				builder.append(index).append("/_update").append("/").append(id);
			}

		}

		boolean p = false;
		if(refreshOption != null ){
			builder.append("?").append(refreshOption);
//			if(parentId != null){
//				builder.append("&parent=").append(parentId);
//
//			}
//			if(routing != null){
//				builder.append("&routing=").append(routing);
//			}
			p = true;
		}


		if(routing != null){
			if(p) {
				builder.append("&routing=").append(routing);
			}
			else {
				builder.append("?routing=").append(routing);
				p = true;
			}

		}

		if(version != null){
			if(p)
				builder.append("&version=").append(version);
			else {
				builder.append("?version=").append(version);
				p = true;
			}
		}
		if(versionType != null){
			if(p)
				builder.append("&version_type=").append(versionType);
			else {
				builder.append("?version_type=").append(versionType);
				p = true;
			}
		}
		Long if_seq_no = clientOptions!= null?clientOptions.getIfSeqNo():null;
		if(if_seq_no != null){
			if(p)
				builder.append("&if_seq_no=").append(if_seq_no);
			else {
				builder.append("?if_seq_no=").append(if_seq_no);
				p = true;
			}
		}
		Long if_primary_term = clientOptions!= null?clientOptions.getIfPrimaryTerm():null;
		if(if_primary_term != null){
			if(p)
				builder.append("&if_primary_term=").append(if_primary_term);
			else {
				builder.append("?if_primary_term=").append(if_primary_term);
				p = true;
			}
		}
		/**
		String pipeline = clientOptions!= null?clientOptions.getPipeline():null;
		if(pipeline != null){
			if(p)
				builder.append("&pipeline=").append(pipeline);
			else {
				builder.append("?pipeline=").append(pipeline);
				p = true;
			}
		}

		String op_type = clientOptions!= null?clientOptions.getOpType():null;

		if(op_type != null){
			if(p)
				builder.append("&op_type=").append(op_type);
			else {
				builder.append("?op_type=").append(op_type);
				p = true;
			}
		}
		 */
		String refresh = clientOptions!= null?clientOptions.getRefresh():null;

		if(refresh != null){
			if(p)
				builder.append("&refresh=").append(refresh);
			else {
				builder.append("?refresh=").append(refresh);
				p = true;
			}
		}
		String timeout = clientOptions!= null?clientOptions.getTimeout():null;

		if(timeout != null){
			if(p)
				builder.append("&timeout=").append(timeout);
			else {
				builder.append("?timeout=").append(timeout);
				p = true;
			}
		}

		String master_timeout = clientOptions!= null?clientOptions.getMasterTimeout():null;

		if(master_timeout != null){
			if(p)
				builder.append("&master_timeout=").append(master_timeout);
			else {
				builder.append("?master_timeout=").append(master_timeout);
				p = true;
			}
		}
		if (esRetryOnConflict != null) {
			if(p)
				builder.append("&retry_on_conflict=").append(esRetryOnConflict);
			else {
				builder.append("?retry_on_conflict=").append(esRetryOnConflict);
				p = true;
			}

		}
		Integer wait_for_active_shards = clientOptions!= null?clientOptions.getWaitForActiveShards():null;

		if(wait_for_active_shards != null){
			if(p)
				builder.append("&wait_for_active_shards=").append(wait_for_active_shards);
			else {
				builder.append("?wait_for_active_shards=").append(wait_for_active_shards);
				p = true;
			}
		}
		return builder.toString();






//		if (esRetryOnConflict != null) {
//			if(!upper7) {
//				writer.write(",\"_retry_on_conflict\":");
//			}
//			else{
//				writer.write(",\"retry_on_conflict\":");
//			}
//			writer.write(String.valueOf(esRetryOnConflict));
//		}






	}
	/**
	 * bulk
	 * @param writer
	 * @throws IOException
	 */
	public static void buildMeta(Writer writer ,BulkData bulkData,boolean upper7,ClassUtil.ClassInfo beanInfo) throws IOException {
		String indexType= bulkData.getIndexType();
		ClientOptions clientOption = bulkData.getClientOptions();
		String indexName = bulkData.getIndex();
		Object params = bulkData.getData();
		String action = bulkData.getElasticsearchBulkType();

		Object id = null;
		Object parentId = null;
		Object routing = null;
		Object esRetryOnConflict = null;
		Object version = null;
		Object versionType = null;
		if(!bulkData.isDelete()){
			if(clientOption != null && clientOption.getIdField() != null) {
				id = BuildTool.getId(params, beanInfo, clientOption.getIdField());
			}
			else{
				id = getId(params,  beanInfo );
			}

		}
		else{
			id = bulkData.getData();
		}


		if(clientOption != null) {

			parentId = clientOption.getParentIdField() != null ? BuildTool.getParentId(params, beanInfo, clientOption.getParentIdField()) : getParentId(params,  beanInfo );
			if(clientOption.getRouting() == null) {
				routing = clientOption.getRoutingField() != null ? BuildTool.getRouting(params, beanInfo, clientOption.getRoutingField()) : getRouting(params,beanInfo);
			}
			else{
				routing = clientOption.getRouting();
			}

			if(clientOption.getEsRetryOnConflict() == null) {
				esRetryOnConflict = clientOption.getEsRetryOnConflictField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo,
						clientOption.getEsRetryOnConflictField()) : getEsRetryOnConflict(params,beanInfo);
			}
			else{
				esRetryOnConflict = clientOption.getEsRetryOnConflict();
			}
			if(clientOption.getVersion() == null) {
				version = clientOption.getVersionField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOption.getVersionField()) : getVersion(  beanInfo,   params);
			}
			else{
				version = clientOption.getVersion();
			}
			if(clientOption.getVersionType() == null) {
				versionType = clientOption.getVersionTypeField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOption.getVersionTypeField()) : getVersionType(  beanInfo,   params);
			}else{
				versionType = clientOption.getVersionType();
			}
		}
		else{

			parentId = getParentId(params,  beanInfo );
			routing = getRouting(params,beanInfo);
			esRetryOnConflict = getEsRetryOnConflict(params,beanInfo);
			version = getVersion(  beanInfo,   params);

			versionType = getVersionType(  beanInfo,   params);
		}
		writer.write("{ \"");
		writer.write(action);
		writer.write("\" : { \"_index\" : \"");
		ESIndexWrapper esIndexWrapper = null;
		RestGetVariableValue restGetVariableValue = null;
		if(indexName != null) {
			writer.write(indexName);
		}
		else{
			esIndexWrapper = beanInfo != null ?beanInfo.getEsIndexWrapper():null;
			restGetVariableValue = esIndexWrapper != null ?new RestGetVariableValue(beanInfo,params):null;
			if (esIndexWrapper == null ) {
				throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
						.append(beanInfo != null ?beanInfo.toString():"").toString());
			}
			buildIndiceName(esIndexWrapper,writer,restGetVariableValue);
		}
		writer.write("\"");

		if(!upper7) {
			writer.write(", \"_type\" : \"");
			if(indexType != null) {
				writer.write(indexType);
			}
			else{
				if (esIndexWrapper == null ) {
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
							.append(beanInfo != null ?beanInfo.toString():"").append(" which must be set below  elasticsearch 7x  when type or indice name not setted.").toString());
				}
				indexType = buildIndiceType(esIndexWrapper,restGetVariableValue);
				if(indexType != null && !indexType.equals("")) {
					writer.write(indexType);
				}
				else{
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set index type in class ")
							.append(beanInfo != null ?beanInfo.toString():"").append(" which must be set below  elasticsearch 7x when type or indice name not setted.").toString());
				}
//				buildIndiceType(esIndexWrapper,writer,restGetVariableValue);
			}
			writer.write("\"");
		}

		if(id != null) {
			writer.write(", \"_id\" : ");
			buildId(id, writer, true);
		}
		if(parentId != null){
			writer.write(", \"parent\" : ");
			buildId(parentId,writer,true);
		}
		if(routing != null){
			if(!upper7) {
				writer.write(", \"_routing\" : ");
			}
			else{
				writer.write(", \"routing\" : ");
			}
			buildId(routing,writer,true);
		}

		if (esRetryOnConflict != null) {
			if(!upper7) {
				writer.write(",\"_retry_on_conflict\":");
			}
			else{
				writer.write(",\"retry_on_conflict\":");
			}
			writer.write(String.valueOf(esRetryOnConflict));
		}

		if(version != null) {

			if(!upper7) {
				writer.write(",\"_version\":");
			}
			else{
				writer.write(",\"version\":");
			}
			writer.write(String.valueOf(version));
		}


		if(versionType != null) {
			if(!upper7) {
				writer.write(",\"_version_type\":\"");
			}
			else{
				writer.write(",\"version_type\":\"");
			}

			writer.write(String.valueOf(versionType));
			writer.write("\"");
		}
//		if(!bulkData.isUpdate()){
			if(upper7) {
				Long if_seq_no = clientOption!= null?clientOption.getIfSeqNo():null;

				if (if_seq_no != null) {

//					if(!upper7) {
//						writer.write(",\"_if_seq_no\":");
//					}
//					else{
//						writer.write(",\"if_seq_no\":");
//					}

					writer.write(",\"if_seq_no\":");

					writer.write(String.valueOf(if_seq_no));
				}

				Long if_primary_term = clientOption != null ? clientOption.getIfPrimaryTerm() : null;

				if (if_primary_term != null) {
//					if (!upper7) {
//						writer.write(",\"_if_primary_term\":");
//					} else {
//						writer.write(",\"if_primary_term\":");
//					}
					writer.write(",\"if_primary_term\":");
					writer.write(String.valueOf(if_primary_term));
				}
			}
			String pipeline = clientOption!= null?clientOption.getPipeline():null;

			if (pipeline != null) {

				writer.write(",\"pipeline\":\"");

				writer.write(pipeline);
				writer.write("\"");
			}
//		}
		if(bulkData.isInsert()){

			String op_type = clientOption!= null?clientOption.getOpType():null;

			if (op_type != null) {

				writer.write(",\"op_type\":\"");

				writer.write(op_type);
				writer.write("\"");
			}
		}

		writer.write(" } }\n");

	}

	/**
	 * String docIdKey,String parentIdKey,String routingKey
	 * @param writer
	 * @param indexType
	 * @param indexName
	 * @param params
	 * @param action
	 * @throws IOException
	 */
	/**
	public static void buildMeta(Writer writer ,String indexType,String indexName, Object params,String action,ClientOptions clientOption,boolean upper7) throws IOException {
		if(params instanceof Map){
			buildMapMeta(  writer ,  indexType,  indexName, (Map) params,  action,  clientOption,  upper7);
			return;
		}
		Object id = null;
		Object parentId = null;
		Object routing = null;
		Object esRetryOnConflict = null;
		Object version = null;
		Object versionType = null;
		ClassUtil.ClassInfo beanInfo = ClassUtil.getClassInfo(params.getClass());
		if(clientOption != null) {

			id = clientOption.getIdField() != null ? BuildTool.getId(params, beanInfo, clientOption.getIdField()) : null;
			parentId = clientOption.getParentIdField() != null ? BuildTool.getParentId(params, beanInfo, clientOption.getParentIdField()) : null;
			if(clientOption.getRouting() == null) {
				routing = clientOption.getRoutingField() != null ? BuildTool.getRouting(params, beanInfo, clientOption.getRoutingField()) : null;
			}
			else{
				routing = clientOption.getRouting();
			}

			if(clientOption.getEsRetryOnConflict() == null) {
				esRetryOnConflict = clientOption.getEsRetryOnConflictField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo,
						clientOption.getEsRetryOnConflictField()) : null;
			}
			else{
				esRetryOnConflict = clientOption.getEsRetryOnConflict();
			}
			if(clientOption.getVersion() == null) {
				version = clientOption.getVersionField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOption.getVersionField()) : null;
			}
			else{
				version = clientOption.getVersion();
			}
			if(clientOption.getVersionType() == null) {
				versionType = clientOption.getVersionTypeField() != null ? BuildTool.getEsRetryOnConflict(params, beanInfo, clientOption.getVersionTypeField()) : null;
			}else{
				versionType = clientOption.getVersionType();
			}
		}
		ESIndexWrapper esIndexWrapper = beanInfo != null ?beanInfo.getEsIndexWrapper():null;
		RestGetVariableValue restGetVariableValue = esIndexWrapper != null ?new RestGetVariableValue(beanInfo,params):null;


		if(id != null) {
			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");
			if(indexName != null) {
				writer.write(indexName);
			}
			else{
				if (esIndexWrapper == null ) {
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
							.append(beanInfo != null ?beanInfo.toString():"").toString());
				}
				buildIndiceName(esIndexWrapper,writer,restGetVariableValue);
			}
			if(!upper7) {
				writer.write("\", \"_type\" : \"");
				if(indexType != null) {
					writer.write(indexType);
				}
				else{
					if (esIndexWrapper == null ) {
						throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
								.append(beanInfo != null ?beanInfo.toString():"").toString());
					}
					buildIndiceType(esIndexWrapper,writer,restGetVariableValue);
				}
			}
			writer.write("\", \"_id\" : ");
			buildId(id,writer,true);
			if(parentId != null){
				writer.write(", \"parent\" : ");
				buildId(parentId,writer,true);
			}
			if(routing != null){
				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				buildId(routing,writer,true);
			}

			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}

			if(version != null) {
				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));
			}


			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}


			writer.write(" } }\n");
		}
		else {

			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");
			if(indexName != null) {
				writer.write(indexName);
			}
			else{
				if (esIndexWrapper == null ) {
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
							.append(beanInfo != null ?beanInfo.toString():"").toString());
				}
				buildIndiceName(esIndexWrapper,writer,restGetVariableValue);
			}
			if(!upper7) {
				writer.write("\", \"_type\" : \"");
				if(indexType != null) {
					writer.write(indexType);
				}
				else{
					if (esIndexWrapper == null ) {
						throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
								.append(beanInfo != null ?beanInfo.toString():"").toString());
					}
					buildIndiceType(esIndexWrapper,writer,restGetVariableValue);
				}

			}
			writer.write("\"");
			if(parentId != null){
				writer.write(", \"parent\" : ");
				buildId(parentId,writer,true);
			}
			if(routing != null){
				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				buildId(routing,writer,true);
			}

			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}

			if(version != null) {
				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));
			}

			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}

			writer.write(" } }\n");
		}
	}*/
	/**
	public static void buildMeta(ClassUtil.ClassInfo classInfo,Writer writer ,String indexType,String indexName, Object params,String action,Object id,Object parentId,Object routing,boolean upper7) throws IOException {
		buildMeta(  classInfo, writer ,  indexType,  indexName,   params,  action,  id,  parentId, routing,null,  upper7);
	}*/
	public static Object getVersion(ClassUtil.ClassInfo classInfo, Object params){
		if(classInfo == null){
			return null;
		}
		ClassUtil.PropertieDescription esVersionProperty = classInfo.getEsVersionProperty();
		Object version = null;
		if (esVersionProperty != null) {
			version = classInfo.getPropertyValue(params, esVersionProperty.getName());
		}
		return version;
	}

	public static Object getVersionType(ClassUtil.ClassInfo classInfo, Object params){
		if(classInfo == null){
			return null;
		}
		ClassUtil.PropertieDescription esVersionTypeProperty = classInfo.getEsVersionTypeProperty();
		Object versionType = null;
		if (esVersionTypeProperty != null)
			versionType = classInfo.getPropertyValue(params,esVersionTypeProperty.getName());
		return versionType;
	}
	/**
	public static void buildMeta(ClassUtil.ClassInfo classInfo,Writer writer ,String indexType,String indexName, Object params,String action,
								 Object id,Object parentId,Object routing,Object esRetryOnConflict,boolean upper7) throws IOException{

		Object version = getVersion(  classInfo,   params);

		Object versionType = getVersionType(  classInfo,   params);

		buildMeta(  classInfo,writer ,  indexType,  indexName,   params,  action,
				  id,  parentId,  routing,  esRetryOnConflict,  version,versionType,  upper7);
	}*/
	/**
	public static void buildMeta(ClassUtil.ClassInfo beanInfo, Writer writer , String indexType, String indexName, Object params, String action,
								 Object id, Object parentId, Object routing, Object esRetryOnConflict, Object version, Object versionType, boolean upper7) throws IOException {

		ESIndexWrapper esIndexWrapper = beanInfo != null ?beanInfo.getEsIndexWrapper():null;
		RestGetVariableValue restGetVariableValue = esIndexWrapper != null ?new RestGetVariableValue(beanInfo,params):null;


		if(id != null) {
			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");
			if(indexName != null) {
				writer.write(indexName);
			}
			else{
				if (esIndexWrapper == null ) {
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
							.append(beanInfo != null ?beanInfo.toString():"").toString());
				}
				buildIndiceName(esIndexWrapper,writer,restGetVariableValue);
			}
			if(!upper7) {
				writer.write("\", \"_type\" : \"");
				if(indexType != null) {
					writer.write(indexType);
				}
				else{
					if (esIndexWrapper == null ) {
						throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
								.append(beanInfo != null ?beanInfo.toString():"").toString());
					}
					buildIndiceType(esIndexWrapper,writer,restGetVariableValue);
				}
			}
			writer.write("\", \"_id\" : ");
			buildId(id,writer,true);
			if(parentId != null){
				writer.write(", \"parent\" : ");
				buildId(parentId,writer,true);
			}
			if(routing != null){
				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				buildId(routing,writer,true);
			}

			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}

			if(version != null) {
				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));
			}


			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}


			writer.write(" } }\n");
		}
		else {

			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");
			if(indexName != null) {
				writer.write(indexName);
			}
			else{
				if (esIndexWrapper == null ) {
					throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
							.append(beanInfo != null ?beanInfo.toString():"").toString());
				}
				buildIndiceName(esIndexWrapper,writer,restGetVariableValue);
			}
			if(!upper7) {
				writer.write("\", \"_type\" : \"");
				if(indexType != null) {
					writer.write(indexType);
				}
				else{
					if (esIndexWrapper == null ) {
						throw new ElasticSearchException(new StringBuilder().append(" ESIndex annotation do not set in class ")
								.append(beanInfo != null ?beanInfo.toString():"").toString());
					}
					buildIndiceType(esIndexWrapper,writer,restGetVariableValue);
				}

			}
			writer.write("\"");
			if(parentId != null){
				writer.write(", \"parent\" : ");
				buildId(parentId,writer,true);
			}
			if(routing != null){
				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				buildId(routing,writer,true);
			}

			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}

			if(version != null) {
				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));
			}

			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}

			writer.write(" } }\n");
		}
	}*/
	/**
	public static void evalBuilk( ClassUtil.ClassInfo classInfo,Writer writer,String indexName, String indexType, Object param, String action,boolean upper7) throws IOException {


		if (param != null) {
			buildMeta( classInfo, writer ,  indexType,  indexName,   param,action,  upper7);
			if(!action.equals("update")) {
				SerialUtil.object2json(param,writer);
				writer.write("\n");
			}
			else
			{
				ClassUtil.PropertieDescription esDocAsUpsertProperty = classInfo != null ?classInfo.getEsDocAsUpsertProperty():null;


				ClassUtil.PropertieDescription esReturnSourceProperty = classInfo != null ?classInfo.getEsReturnSourceProperty():null;

				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				if(esDocAsUpsertProperty != null){
					Object esDocAsUpsert = classInfo.getPropertyValue(param,esDocAsUpsertProperty.getName());
					if(esDocAsUpsert != null){
						writer.write(",\"doc_as_upsert\":");
						writer.write(String.valueOf(esDocAsUpsert));
					}
				}
				if(esReturnSourceProperty != null){
					Object returnSource = classInfo.getPropertyValue(param,esReturnSourceProperty.getName());
					if(returnSource != null){
						writer.write(",\"_source\":");
						writer.write(String.valueOf(returnSource));
					}
				}
				writer.write("}\n");



			}
		}

	}
*/
	/**
	public static void buildMeta(StringBuilder builder ,String indexType,String indexName, Object params,String action){
		ClassUtil.ClassInfo beanInfo = ClassUtil.getClassInfo(params.getClass());
		Object id = getId(params,  beanInfo );
		Object parentId = getParentId(params,  beanInfo );
		if(id != null) {
			builder.append("{ \"").append(action).append("\" : { \"_index\" : \"").append(indexName)
					.append("\", \"_type\" : \"").append(indexType).append("\", \"_id\" : ");
			buildId(id,builder,true);
			if(parentId != null){
				builder.append(",\"parent\":");
				buildId(parentId,builder,true);
			}
			builder.append(" } }\n");
		}
		else {
			if(parentId == null)
				builder.append("{ \"").append(action).append("\" : { \"_index\" : \"").append(indexName).append("\", \"_type\" : \"").append(indexType).append("\" } }\n");
			else{
				builder.append("{ \"").append(action).append("\" : { \"_index\" : \"").append(indexName).append("\", \"_type\" : \"").append(indexType).append("\"");
				builder.append(",\"parent\":");
				buildId(parentId,builder,true);
				builder.append(" } }\n");
			}
		}
	}*/

/**

	public static void evalBuilk( Writer writer,String indexName, String indexType, Map param, String action,String docIdKey,String parentIdKey,boolean upper7) throws IOException {

		if (param != null) {
			buildMetaWithDocIdKey(  writer ,  indexType,  indexName,   param,action,docIdKey,parentIdKey,  upper7);
			if(!action.equals("update")) {
				SerialUtil.object2json(param,writer);
				writer.write("\n");
			}
			else
			{
				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				writer.write("}\n");
			}
		}

	}
 */
/**
	public static void evalBuilk( Writer writer,String indexName, String indexType, Map param, String action,ClientOptions ClientOptions,boolean upper7) throws IOException {

		if (param != null) {
			buildMeta(  writer ,  indexType,  indexName,   param,action,ClientOptions,  upper7);
			if(!action.equals("update")) {
				SerialUtil.object2json(param,writer);
				writer.write("\n");
			}
			else
			{
				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				writer.write("}\n");
			}
		}

	}*/
	/**
	public static void evalDeleteBuilk(BBossStringWriter writer, boolean isUpper7, BulkData bulkData){

		try {
			BuildTool.evalBuilk(writer,bulkData,isUpper7);
//			if(!isUpper7 ) {
//
//				writer.write("{ \"delete\" : { \"_index\" : \"");
//				writer.write(bulkData.getIndex());
//				writer.write("\", \"_type\" : \"");
//				writer.write(bulkData.getIndexType());
//				writer.write("\", \"_id\" : \"");
//				writer.write(bulkData.getData().toString());
//				writer.write("\" } }\n");
//			}
//			else{
//
//				writer.write("{ \"delete\" : { \"_index\" : \"");
//				writer.write(bulkData.getIndex());
//				writer.write("\", \"_id\" : \"");
//				writer.write(bulkData.getData().toString());
//				writer.write("\" } }\n");
//			}

		} catch (Exception e) {
			throw new ElasticSearchException(e);
		}

	}
	 */
	/**
	public static void evalBuilk( Writer writer,String indexName, String indexType, Object param, String action,ClientOptions clientOptions,boolean upper7) throws IOException {

		if (param != null) {
			buildMeta(  writer ,  indexType,  indexName,   param,action,clientOptions,  upper7);
			if(!action.equals("update")) {
				SerialUtil.object2json(param,writer);
				writer.write("\n");
			}
			else
			{
				Object detect_noop = null;
				Object doc_as_upsert = null;

				if(!(param instanceof Map)) {
					ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(param.getClass());

					if(clientOptions.getDetectNoop() != null){
						detect_noop = clientOptions.getDetectNoop();
					}
					else {
						detect_noop = clientOptions.getDetectNoopField() != null ? BuildTool.getFieldValue(param, beanClassInfo, clientOptions.getDetectNoopField()) : null;
					}
					if(clientOptions.getDocasupsert() != null) {
						doc_as_upsert =clientOptions.getDocasupsert();
					}
					else {
						doc_as_upsert = clientOptions.getDocasupsertField() != null ? BuildTool.getFieldValue(param, beanClassInfo, clientOptions.getDocasupsertField()) : null;
					}
				}
				else{
					Map _params = (Map)param;

					if(clientOptions.getDetectNoop() != null){
						detect_noop = clientOptions.getDetectNoop();
					}
					else {
						detect_noop = clientOptions.getDetectNoopField() != null ? _params.get( clientOptions.getDetectNoopField()) : null;
					}
					if(clientOptions.getDocasupsert() != null) {
						doc_as_upsert = clientOptions.getDocasupsert();
					}
					else {
						doc_as_upsert = clientOptions.getDocasupsertField() != null ? _params.get(clientOptions.getDocasupsertField()) : null;
					}

				}
				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				if(detect_noop != null){
					writer.write(",\"detect_noop\":");
					writer.write(detect_noop.toString());
				}
				if(doc_as_upsert != null){
//					builder.append(",\"doc_as_upsert\":").append(doc_as_upsert);
					writer.write(",\"doc_as_upsert\":");
					writer.write(doc_as_upsert.toString());
				}
				Boolean returnSource = clientOptions.getReturnSource();
				if(returnSource != null){
					writer.write(",\"_source\":");
					writer.write(String.valueOf(returnSource));
				}
				writer.write("}\n");
			}
		}

	}
	 */

	public static void evalBuilk( Writer writer,BulkData bulkData,boolean upper7) throws IOException {
			Object param = bulkData.getData();
			ClassUtil.ClassInfo beanClassInfo = ClassUtil.getClassInfo(param.getClass());
			buildMeta(  writer ,  bulkData,  upper7,beanClassInfo);
			if(bulkData.isInsert()) {
				SerialUtil.object2json(bulkData.getData(),writer);
				writer.write("\n");
			}
			else if(bulkData.isUpdate())
			{

				Object detect_noop = null;
				Object doc_as_upsert = null;
				ClientOptions clientOptions = bulkData.getClientOptions();

				if(clientOptions != null) {
					if (clientOptions.getDetectNoop() != null) {
						detect_noop = clientOptions.getDetectNoop();
					} else {
						detect_noop = clientOptions.getDetectNoopField() != null ? BuildTool.getFieldValue(param, beanClassInfo, clientOptions.getDetectNoopField()) : null;
					}
					if (clientOptions.getDocasupsert() != null) {
						doc_as_upsert = clientOptions.getDocasupsert();
					} else {
						doc_as_upsert = clientOptions.getDocasupsertField() != null ? BuildTool.getFieldValue(param, beanClassInfo, clientOptions.getDocasupsertField()) : getEsDocAsUpsert(param, beanClassInfo );
					}
				}
				else{
					doc_as_upsert = getEsDocAsUpsert(param, beanClassInfo );
				}


				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				if(detect_noop != null){
					writer.write(",\"detect_noop\":");
					writer.write(detect_noop.toString());
				}
				if(doc_as_upsert != null){
//					builder.append(",\"doc_as_upsert\":").append(doc_as_upsert);
					writer.write(",\"doc_as_upsert\":");
					writer.write(doc_as_upsert.toString());
				}
				Boolean returnSource = clientOptions != null?clientOptions.getReturnSource():null;
				if(returnSource != null){
					writer.write(",\"_source\":");
					writer.write(String.valueOf(returnSource));
				}
				List<String> sourceUpdateExcludes  = clientOptions!= null?clientOptions.getSourceUpdateExcludes():null;

				if (sourceUpdateExcludes != null) {
					/**
					 if(!upper7) {
					 writer.write(",\"_source_excludes\":");
					 }
					 else{
					 writer.write(",\"source_excludes\":");
					 }
					 */
					if(!upper7) {
						writer.write(",\"_source_excludes\":");
						SerialUtil.object2json(sourceUpdateExcludes,writer);
					}

				}
				List<String> sourceUpdateIncludes  = clientOptions!= null?clientOptions.getSourceUpdateIncludes():null;

				if (sourceUpdateIncludes != null) {
					/**
					 if(!upper7) {
					 writer.write(",\"_source_includes\":");
					 }
					 else{
					 writer.write(",\"source_includes\":");
					 }
					 */
					if(!upper7) {
						writer.write(",\"_source_includes\":");
						SerialUtil.object2json(sourceUpdateIncludes,writer);
					}


				}
				writer.write("}\n");
			}


	}
	/**
	public static void evalBuilk( Writer writer,String indexName, String indexType, Object param, String action,String docIdField,String parentIdField,boolean upper7) throws IOException {

		if (param != null) {
			buildMetaWithDocIdField(  writer ,  indexType,  indexName,   param,action,docIdField,parentIdField,  upper7);
			if(!action.equals("update")) {
				SerialUtil.object2json(param,writer);
				writer.write("\n");
			}
			else
			{
				writer.write("{\"doc\":");
				SerialUtil.object2json(param,writer);
				writer.write("}\n");
			}
		}

	}

*/

	public static void handleFields(Map<String,Object> subFileds,String fieldName,List<IndexField> fields){
		if(subFileds == null || subFileds.size() == 0)
			return ;
		Iterator<Map.Entry<String,Object>> iterator = subFileds.entrySet().iterator();
		while(iterator.hasNext()){
			Map.Entry<String,Object> entry = iterator.next();
			IndexField indexField = buildIndexField(entry, fields,fieldName);
		}

	}

	public static Boolean parseBoolean(Object norms){
		if(norms == null){
			return null;
		}
		if(norms instanceof Boolean){
			return (Boolean)norms;
		}
		else if(norms instanceof Map){
			return (Boolean) ((Map) norms).get("enabled");
		}
		return null;
	}
	public static IndexField buildIndexField(Map.Entry<String,Object> field,List<IndexField> fields,String parentFieldName){
//		Map.Entry<String,Object> field = fileds.next();
		IndexField indexField = new IndexField();
		String fieldName = null;
		if(parentFieldName != null){
			fieldName = parentFieldName + "."+field.getKey();
		}
		else {
			fieldName = field.getKey();
		}
		indexField.setFieldName(fieldName);
		Map<String,Object> fieldInfo = (Map<String,Object>)field.getValue();
		indexField.setType((String)fieldInfo.get("type"));
		indexField.setIgnoreAbove(ResultUtil.intValue(fieldInfo.get("ignore_above"),null));
		indexField.setAnalyzer((String)fieldInfo.get("analyzer"));
		indexField.setNormalizer((String)fieldInfo.get("normalizer"));
		indexField.setBoost(fieldInfo.get("boost"));
		indexField.setCoerce(parseBoolean( fieldInfo.get("coerce")));
		indexField.setCopyTo((String)fieldInfo.get("copy_to"));
		indexField.setDocValues(parseBoolean(fieldInfo.get("doc_values")));//setCoerce();
		indexField.setDynamic(parseBoolean(fieldInfo.get("doc_values")));	//dynamic
		indexField.setEnabled(parseBoolean(fieldInfo.get("enabled")));			//enabled
		indexField.setFielddata(parseBoolean(fieldInfo.get("fielddata")));	//fielddata
		indexField.setFormat((String)fieldInfo.get("format"));		//	format
		indexField.setIgnoreMalformed(parseBoolean(fieldInfo.get("ignore_malformed")));//Coerce();	//		ignore_malformed
		indexField.setIncludeInAll(parseBoolean(fieldInfo.get("include_in_all")));	//include_in_all
		indexField.setIndexOptions((String)fieldInfo.get("index_options"));
		indexField.setIndex(parseBoolean(fieldInfo.get("index")));	//
		indexField.setFields((Map<String,Object>)fieldInfo.get("fields"));	//

		indexField.setNorms(parseBoolean(fieldInfo.get("norms")));//	norms
		indexField.setNullValue(fieldInfo.get("null_value"));	//
		indexField.setPositionIncrementGap((Integer)fieldInfo.get("position_increment_gap"));
		indexField.setProperties((Map<String,Object>)fieldInfo.get("properties"));	//
		indexField.setSearchAnalyzer((String)fieldInfo.get("search_analyzer"));	//search_analyzer
		indexField.setSimilarity((String)fieldInfo.get("similarity"));	//
		indexField.setStore(parseBoolean(fieldInfo.get("store")));	//store
		indexField.setTermVector((String)fieldInfo.get("term_vector"));	//
		fields.add(indexField);
		handleFields(indexField.getFields(), fieldName,fields);
		return indexField;
	}

	public static  Object getId(Object bean,ClassUtil.ClassInfo beanInfo ){
		if(beanInfo == null)
			return null;
		ClassUtil.PropertieDescription pkProperty = beanInfo.getEsIdProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(pkProperty == null)
			return null;
		return getFieldValue(  bean,  beanInfo ,pkProperty.getName());
	}

	public static  Object getId(Object bean,ClassUtil.ClassInfo beanInfo,String docIdField ){

//		ClassUtil.PropertieDescription pkProperty = beanInfo.getEsIdProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		return getFieldValue(  bean, beanInfo ,docIdField);
	}

	public static  Object getEsRetryOnConflict(Object bean,ClassUtil.ClassInfo beanInfo ){
		if(beanInfo == null)
			return null;
		ClassUtil.PropertieDescription esRetryOnConflictProperty = beanInfo.getEsRetryOnConflictProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(esRetryOnConflictProperty == null)
			return null;
		return getFieldValue(  bean,  beanInfo ,esRetryOnConflictProperty.getName());
	}

	public static  Object getEsRetryOnConflict(Object bean,ClassUtil.ClassInfo beanInfo ,String esRetryOnConflictField){
		return getFieldValue(  bean, beanInfo ,esRetryOnConflictField);
	}
	public static  Object getRouting(Object bean,ClassUtil.ClassInfo beanInfo ){
		if(beanInfo == null){
			return null;
		}
		ClassUtil.PropertieDescription routingProperty = beanInfo.getEsRoutingProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(routingProperty == null)
			return null;
		return getFieldValue(  bean,  beanInfo ,routingProperty.getName());
	}

	public static  Object getRouting(Object bean,ClassUtil.ClassInfo beanInfo,String routingField ){
		return getFieldValue(  bean, beanInfo ,routingField);
	}

	public static  Object getEsDocAsUpsert(Object bean,ClassUtil.ClassInfo beanClassInfo ){
		if(beanClassInfo == null){
			return null;
		}
		ClassUtil.PropertieDescription propertieDescription = beanClassInfo.getEsDocAsUpsertProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(propertieDescription == null)
			return null;
		return getFieldValue(  bean,  beanClassInfo ,propertieDescription.getName());
	}
	public static  Object getParentId(Object bean,ClassUtil.ClassInfo beanInfo ){
		if(beanInfo == null){
			return null;
		}
		ClassUtil.PropertieDescription pkProperty = beanInfo.getEsParentProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(pkProperty == null)
			return null;
		return getFieldValue(  bean,  beanInfo ,pkProperty.getName());
	}

	public static  Object getParentId(Object bean,ClassUtil.ClassInfo beanInfo ,String parentIdField){
		return getFieldValue(  bean, beanInfo ,parentIdField);
	}

	public static  Object getFieldValue(Object bean,ClassUtil.ClassInfo beanInfo ,String field){
		if(beanInfo == null){
			return null;
		}
//		ClassUtil.PropertieDescription pkProperty = beanInfo.getEsParentProperty();
//		if(pkProperty == null)
//			pkProperty = beanInfo.getPkProperty();
		if(field == null)
			return null;
		if(!beanInfo.isMap())
			return beanInfo.getPropertyValue(bean,field);
		else{
			return ((Map)bean).get(field);
		}
	}
}
