package org.frameworkset.elasticsearch.client.estodb;

import com.frameworkset.common.poolman.ConfigSQLExecutor;
import com.frameworkset.util.VariableHandler;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.client.*;
import org.frameworkset.elasticsearch.client.context.Context;
import org.frameworkset.elasticsearch.client.context.ContextImpl;
import org.frameworkset.elasticsearch.client.context.ImportContext;
import org.frameworkset.elasticsearch.client.schedule.Status;
import org.frameworkset.elasticsearch.client.tran.BaseDataTran;
import org.frameworkset.elasticsearch.entity.ESDatas;
import org.frameworkset.persitent.util.SQLInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ES2DBDataTran extends BaseDataTran {
	private static Logger logger = LoggerFactory.getLogger(ES2DBDataTran.class);
	private ES2DBContext es2DBContext ;
	private ESTranResultSet esTranResultSet;
	protected void init(){
		es2DBContext = (ES2DBContext)importContext;
		esTranResultSet = (ESTranResultSet)jdbcResultSet;
	}
	public ES2DBDataTran(ESTranResultSet jdbcResultSet, ImportContext importContext) {
		super(jdbcResultSet,importContext);
	}
	public void appendData(ESDatas datas, ESExporterScrollHandler exporterScrollHandler){
		esTranResultSet.appendData(datas,    exporterScrollHandler);
	}

	public void stop(){
		esTranResultSet.stop();
		super.stop();
	}
	public String serialExecute(  ){
		Object lastValue = null;
		Exception exception = null;
		long start = System.currentTimeMillis();
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		long totalCount = 0;
		long ignoreTotalCount = 0;

		try {

			//		GetCUDResult CUDResult = null;
			ConfigSQLExecutor configSQLExecutor = new ConfigSQLExecutor(es2DBContext.getSqlFilepath());
			SQLInfo sqlinfo = configSQLExecutor.getSqlInfo(importContext.getDbConfig().getDbName(), es2DBContext.getSqlName());
			String sql = sqlinfo.getSql();
			VariableHandler.SQLStruction sqlstruction = sqlinfo.getSqlutil().getSQLStruction(sqlinfo,sql);
			List<VariableHandler.Variable> vars = sqlstruction.getVariables();
			Object temp = null;
			Param param = null;
			List<List<ES2DBDataTran.Param>> records = new ArrayList<>();
			while (jdbcResultSet.next()) {
				try {
					if (lastValue == null)
						lastValue = importContext.max(currentValue, getLastValue());
					else {
						lastValue = importContext.max(lastValue, getLastValue());
					}
					Context context = new ContextImpl(importContext, jdbcResultSet, null);
					context.refactorData();
					if (context.isDrop()) {
						ignoreTotalCount ++;
						continue;
					}
					List<ES2DBDataTran.Param> record = buildRecord(  context,  vars );

					records.add(record);
					//						evalBuilk(this.jdbcResultSet, batchContext, writer, context, "index", clientInterface.isVersionUpper7());
					totalCount++;
				} catch (Exception e) {
					throw new ElasticSearchException(e);
				}
			}
			TaskCommand<List<List<Param>>, String> taskCommand = new TaskCommandImpl(sql,importContext,records);
			TaskCall.call(taskCommand);
			importContext.flushLastValue(lastValue);
			if(isPrintTaskLog()) {
				long end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(ignoreTotalCount).append(" records.").toString());

			}
		}
		catch (ElasticSearchException e){
			exception = e;
			throw e;


		}
		catch (Exception e){
			exception = e;
			throw new ElasticSearchException(e);


		} finally {

			if(!TranErrorWrapper.assertCondition(exception ,importContext)){
				stop();
			}
			if(importContext.isCurrentStoped()){
				stop();
			}

		}
		return null;

	}
	private List<ES2DBDataTran.Param> buildRecord(Context context,List<VariableHandler.Variable> vars ){
		Object temp = null;
		Param param = null;
		List<ES2DBDataTran.Param> record = new ArrayList<>();
		Map<String,Object> addedFields = new HashMap<String,Object>();

		List<FieldMeta> fieldValueMetas = context.getFieldValues();//context优先级高于，全局配置，全局配置高于字段值

		appendFieldValues( record, vars,    fieldValueMetas,  addedFields);
		fieldValueMetas = context.getESJDBCFieldValues();
		appendFieldValues(  record, vars,   fieldValueMetas,  addedFields);
		for(int i = 0;i < vars.size(); i ++)
		{
			VariableHandler.Variable var = vars.get(i);
			if(addedFields.get(var.getVariableName()) != null)
				continue;
			temp = jdbcResultSet.getValue(var.getVariableName());
			if(temp == null) {
				logger.warn("未指定绑定变量的值：{}",var.getVariableName());
			}
			param = new Param();
			param.setVariable(var);
			param.setIndex(i +1);
			param.setValue(temp);
			param.setName(var.getVariableName());
			record.add(param);

		}
		return record;
	}
	@Override
	public String parallelBatchExecute() {
		int count = 0;
		ExecutorService service = importContext.buildThreadPool();
		List<Future> tasks = new ArrayList<Future>();
		int taskNo = 0;
		ImportCount totalCount = new ImportCount();
		Exception exception = null;
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		Object lastValue = null;
		TranErrorWrapper tranErrorWrapper = new TranErrorWrapper(importContext);
		int batchsize = importContext.getStoreBatchSize();
		try {
			ConfigSQLExecutor configSQLExecutor = new ConfigSQLExecutor(es2DBContext.getSqlFilepath());
			SQLInfo sqlinfo = configSQLExecutor.getSqlInfo(importContext.getDbConfig().getDbName(), es2DBContext.getSqlName());
			String sql = sqlinfo.getSql();
			VariableHandler.SQLStruction sqlstruction = sqlinfo.getSqlutil().getSQLStruction(sqlinfo,sql);
			List<VariableHandler.Variable> vars = sqlstruction.getVariables();
			Object temp = null;
			Param param = null;
			List<List<ES2DBDataTran.Param>> records = new ArrayList<>();
			while (jdbcResultSet.next()) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}

				Context context = new ContextImpl(importContext, jdbcResultSet, null);
				context.refactorData();
				if (context.isDrop()) {
					totalCount.increamentIgnoreTotalCount();
					continue;
				}
				List<ES2DBDataTran.Param> record = buildRecord(  context,  vars );
				records.add(record);
				//						evalBuilk(this.jdbcResultSet, batchContext, writer, context, "index", clientInterface.isVersionUpper7());
				count++;
				if (count == batchsize) {

					count = 0;
					TaskCommandImpl taskCommand = new TaskCommandImpl(sql,importContext,records);
					records = new ArrayList<>();
					tasks.add(service.submit(new TaskCall(taskCommand,  tranErrorWrapper,taskNo,totalCount,batchsize)));

					taskNo ++;

				}

			}
			if (count > 0) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
//				if(this.error != null && !importContext.isContinueOnError()) {
//					throw error;
//				}
				TaskCommandImpl taskCommand = new TaskCommandImpl(sql,importContext,records);
				tasks.add(service.submit(new TaskCall(taskCommand,tranErrorWrapper,taskNo,totalCount,count)));
				taskNo ++;
				if(isPrintTaskLog())
					logger.info(new StringBuilder().append("submit tasks:").append(taskNo).toString());
			}
			else{
				if(isPrintTaskLog())
					logger.info(new StringBuilder().append("submit tasks:").append(taskNo).toString());
			}

		} catch (SQLException e) {
			exception = e;
			throw new ElasticSearchException(e);

		} catch (ElasticSearchException e) {
			exception = e;
			throw e;
		} catch (Exception e) {
			exception = e;
			throw new ElasticSearchException(e);
		}
		finally {
			waitTasksComplete(   tasks,  service,exception,  lastValue,totalCount ,tranErrorWrapper);

		}

		return null;
	}

	@Override
	public String batchExecute() {
		int count = 0;
		String ret = null;
		int taskNo = 0;
		Exception exception = null;
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		Object lastValue = null;
		TranErrorWrapper tranErrorWrapper = new TranErrorWrapper(importContext);
		long start = System.currentTimeMillis();
		long istart = 0;
		long end = 0;
		long totalCount = 0;
		long ignoreTotalCount = 0;
		int batchsize = importContext.getStoreBatchSize();
		String refreshOption = importContext.getRefreshOption();
		try {
			istart = start;
			ConfigSQLExecutor configSQLExecutor = new ConfigSQLExecutor(es2DBContext.getSqlFilepath());
			SQLInfo sqlinfo = configSQLExecutor.getSqlInfo(importContext.getDbConfig().getDbName(), es2DBContext.getSqlName());
			String sql = sqlinfo.getSql();
			VariableHandler.SQLStruction sqlstruction = sqlinfo.getSqlutil().getSQLStruction(sqlinfo,sql);
			List<VariableHandler.Variable> vars = sqlstruction.getVariables();
			List<List<ES2DBDataTran.Param>> records = new ArrayList<>();
			while (jdbcResultSet.next()) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}
				Context context = new ContextImpl(importContext, jdbcResultSet, null);
				context.refactorData();
				if (context.isDrop()) {
					ignoreTotalCount ++;
					continue;
				}
				List<ES2DBDataTran.Param> record = buildRecord(  context,  vars );
				records.add(record);
				count++;
				if (count == batchsize) {
					count = 0;
					taskNo ++;
					TaskCommandImpl taskCommand = new TaskCommandImpl(sql,importContext,records);
					records = new ArrayList<>();
					ret = TaskCall.call(taskCommand);
					importContext.flushLastValue(lastValue);

					if(isPrintTaskLog())  {
						end = System.currentTimeMillis();
						logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
								.append(",import ").append(batchsize).append(" records.").toString());
						istart = end;
					}
					totalCount += batchsize;


				}

			}
			if (count > 0) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				taskNo ++;
				TaskCommandImpl taskCommand = new TaskCommandImpl(sql,importContext,records);
				ret = TaskCall.call(taskCommand);
				importContext.flushLastValue(lastValue);
				if(isPrintTaskLog())  {
					end = System.currentTimeMillis();
					logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
							.append(",import ").append(count).append(" records.").toString());

				}
				totalCount += count;
			}
			if(isPrintTaskLog()) {
				end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("Execute Tasks:").append(taskNo).append(",All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(ignoreTotalCount).append(" records.").toString());

			}
		}  catch (ElasticSearchException e) {
			exception = e;
			throw e;
		} catch (Exception e) {
			exception = e;
			throw new ElasticSearchException(e);
		}
		finally {
			if(!tranErrorWrapper.assertCondition(exception)){
				stop();
			}

		}

		return ret;
	}


	private void appendFieldValues(List<ES2DBDataTran.Param> record,
			List<VariableHandler.Variable> vars,
			List<FieldMeta> fieldValueMetas,
			Map<String, Object> addedFields) {
		if(fieldValueMetas ==  null || fieldValueMetas.size() == 0){
			return;
		}
		int i = 0;
		Param param = null;
		for(VariableHandler.Variable variable:vars){
			if(addedFields.containsKey(variable.getVariableName()))
				continue;
			for(FieldMeta fieldMeta:fieldValueMetas){
				if(variable.getVariableName().equals(fieldMeta.getEsFieldName())){
					param = new Param();
					param.setVariable(variable);
					param.setIndex(i +1);
					param.setValue(fieldMeta.getValue());
					param.setName(variable.getVariableName());
					record.add(param);
//					statement.setObject(i +1,fieldMeta.getValue());
					addedFields.put(variable.getVariableName(),dummy);
					break;
				}
			}
		}
	}




	public static class Param{
		private int index;
		private Object value;
		private String name;
		private VariableHandler.Variable variable;
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public Object getValue() {
			return value;
		}

		public void setValue(Object value) {
			this.value = value;
		}

		public VariableHandler.Variable getVariable() {
			return variable;
		}

		public void setVariable(VariableHandler.Variable variable) {
			this.variable = variable;
		}
	}
//	private void buildParamsByVariableParser(SQLInfo sqlinfo,Map<String,Object> sqlparams,String dbname) throws SetSQLParamException
//	{
//		String sql = sqlinfo.getSql();
//		VariableHandler.SQLStruction sqlstruction =  null;
//		if(sqlinfo.getSqlutil() == null)
//		{
//			sqlstruction =  SQLUtil.getGlobalSQLUtil().getSQLStruction(sqlinfo,sql);
//		}
//		else
//		{
//			sqlstruction = sqlinfo.getSqlutil().getSQLStruction(sqlinfo,sql);
//		}
//
//		Object temp = null;
//		List<Param> realParams = new ArrayList<>();
//		List<VariableHandler.Variable> vars = sqlstruction.getVariables();
//		for(int i = 0;i < vars.size(); i ++)
//		{
//			VariableHandler.Variable var = vars.get(i);
//			temp = sqlparams.get(var.getVariableName());
//			if(temp == null) {
//				throw new SetSQLParamException("未指定绑定变量的值："
//						+ var.getVariableName()
//						+ "\r\n"
//						+ this.toString());
//			}
//			Param newparam = new Param();
//			//绑定变量索引从1开始
//			newparam.setIndex( i + 1);
//			newparam.setValue(temp);
//			newparam.setName(var.getVariableName());
//			newparam.setVariable(var);
//			realParams.add(newparam);
//		}
//
//
//
//		if(sqlstruction.hasVars() )
//		{
//			if(logger.isDebugEnabled())
//				logger.debug("SQL INFO:" + this.toString() );
//
//		}
//
//	}


}