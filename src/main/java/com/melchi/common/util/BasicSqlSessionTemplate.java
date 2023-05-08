package com.melchi.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
 

@Component
public class BasicSqlSessionTemplate extends SqlSessionTemplate implements EnvironmentAware  {
	static final Logger logger = LoggerFactory.getLogger(BasicSqlSessionTemplate.class);
 
	private String siteName; 
	 
	@Override
	public void setEnvironment(Environment env) {
		this.siteName = env.getProperty("spring.db-input-site-name");	
	}
	  
	public BasicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		super(sqlSessionFactory);		
	}
	
	
	
	@Override 
	public <E> List<E> selectList(String statement) {		
		logger.info(statement);		 
		Map<String, Object> copiedParam = new HashMap<String, Object>();
		copiedParam.put("sitename", this.siteName);
		return super.selectList(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")	
	@Override
	public <E> List<E> selectList(String statement, Object parameter) {
		logger.info(statement);		
		Map<String, Object> copiedParam = new HashMap<String, Object>(((Map<String, Object>) parameter));
		copiedParam.put("sitename", this.siteName);
		return super.selectList(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
		logger.info(statement);		
		Map<String, Object> copiedParam = new HashMap<String, Object>(((Map<String, Object>) parameter));
		copiedParam.put("sitename", this.siteName);
		return super.selectList(statement, copiedParam, rowBounds);
	}

	@Override
	public <T> T selectOne(String statement) {
		logger.info(statement);
		Map<String, Object> copiedParam = new HashMap<String, Object>();
		copiedParam.put("sitename", this.siteName);
		return super.selectOne(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T selectOne(String statement, Object parameter) {
		logger.info(statement);				
		Map<String, Object> copiedParam = new HashMap<String, Object>(((Map<String, Object>) parameter));
		copiedParam.put("sitename", this.siteName);
		return super.selectOne(statement, copiedParam);
		
	}
	
	@Override
	public int insert(String statement) {
		logger.info(statement);
		Map<String, Object> copiedParam = new HashMap<String, Object>();
		copiedParam.put("sitename", this.siteName);		
		return super.insert(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public int insert(String statement, Object parameter) {
		logger.info(statement);		
		((Map<String, Object>) parameter).put("sitename", this.siteName);
		int result = super.insert(statement, parameter);
		((Map<String, Object>) parameter).remove("sitename"); 
		return result; 
	}
	
	@Override
	public int update(String statement) {
		logger.info(statement);
		Map<String, Object> copiedParam = new HashMap<String, Object>();
		copiedParam.put("sitename", this.siteName);
		return super.update(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public int update(String statement, Object parameter) { 		
		logger.info(statement);
		((Map<String, Object>) parameter).put("sitename", this.siteName);
		int result = super.update(statement, parameter);
		((Map<String, Object>) parameter).remove("sitename");   
		return result; 
	}
	
	@Override
	public int delete(String statement) {
		logger.info(statement);		
		Map<String, Object> copiedParam = new HashMap<String, Object>();
		copiedParam.put("sitename", this.siteName); 
		return super.delete(statement, copiedParam);
	}
	
	@SuppressWarnings("unchecked")
	@Override  
	public int delete(String statement, Object parameter) {
		logger.info(statement);
		Map<String, Object> copiedParam = new HashMap<String, Object>(((Map<String, Object>) parameter));
		copiedParam.put("sitename", this.siteName);
		return super.delete(statement, copiedParam); 
	}
}
