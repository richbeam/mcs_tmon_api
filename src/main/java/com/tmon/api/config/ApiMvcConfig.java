package com.tmon.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tmon.api.interceptor.SessionInterceptor;



@Configuration 
public class ApiMvcConfig implements WebMvcConfigurer {

	@Bean
	public SessionInterceptor sessionInterceptor() {
		return new SessionInterceptor();
	}
	
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionInterceptor()) 
                .addPathPatterns("/v1/**");  
                                 
    } 
     
}  
  