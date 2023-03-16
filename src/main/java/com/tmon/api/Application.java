package com.tmon.api;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
 
@SpringBootApplication(scanBasePackages = {"com.melchi.common", "com.*.api"})
@MapperScan(basePackages = {"com.melchi.common.config"})        
public class Application extends SpringBootServletInitializer { 
 
	static final Logger logger = LoggerFactory.getLogger(Application.class);

	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {		
		logger.info("Started with Tomcat");  
        return application.sources(Application.class);
    }  

    public static void main(String[] args) { 
    	logger.info("Started with Embeded Tomcat");
        SpringApplication.run(Application.class, args);
    }
}
   