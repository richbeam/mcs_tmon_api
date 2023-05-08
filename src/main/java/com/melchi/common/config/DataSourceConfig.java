package com.melchi.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig { 

	@Autowired
    private Environment env;	
    	
 
	@Primary  
	@Bean(name="basicDataSource")  	
	@ConfigurationProperties("spring.datasource.basic")
	public DataSource basicDataSource() {  	 
		HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class).build();				 		
		dataSource.setMaximumPoolSize(Integer.valueOf(env.getProperty("spring.datasource.basic.hikari.maximum-pool-size")));
		dataSource.setMaxLifetime(Long.valueOf(env.getProperty("spring.datasource.basic.hikari.max-life-time")));
		dataSource.setConnectionTimeout(Long.valueOf(env.getProperty("spring.datasource.basic.hikari.connection-timeout")));
		return dataSource;					     
	}    
	   
	 
	@Bean(name="basicTxManager")   
	public PlatformTransactionManager basicTxManager() { 	 		
		return new DataSourceTransactionManager(basicDataSource());	  	 
	}  
 
}


