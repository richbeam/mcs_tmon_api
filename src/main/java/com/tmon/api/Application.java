package com.tmon.api;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.melchi.common", "com.*.api"})
@MapperScan(basePackages = {"com.melchi.common.config"})
@EnableCaching
public class Application extends SpringBootServletInitializer { 
 
	static final Logger logger = LoggerFactory.getLogger(Application.class);

    // 하나의 저장소를 사용하는 경우
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("tokenCacheStore");
    }

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
   