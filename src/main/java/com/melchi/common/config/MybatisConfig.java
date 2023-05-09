package com.melchi.common.config;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.melchi.common.util.BasicSqlSessionTemplate;


@Configuration
public class MybatisConfig {
	
	@Autowired
    private Environment env; 
    
	@Bean
	public SqlSessionFactory basicSqlSessionFactory(DataSource dataSource) throws Exception {  
		String packageName = env.getProperty("spring.package-name"); 
		SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();		
		sessionFactory.setDataSource(dataSource);  			
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();					
		String strResourcesPath = "classpath*:com/tmon/api/module/**/**-sqlMap.xml";
		sessionFactory.setMapperLocations(resolver.getResources(strResourcesPath));				
		return sessionFactory.getObject();     
	}
	   
	/*@Bean
	SqlSessionTemplate basicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) throws Exception {		
		final SqlSessionTemplate sqlSessionTemplate = new BasicSqlSessionTemplate(sqlSessionFactory); 		
		return sqlSessionTemplate;   
	}   */
}  













