package com.melchi.common.config.exceptionlogging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//Request에 대하여 @RequestBody을 여러번 사용할 수 있는 필터 등록
@Configuration
public class LoggingFilterConfig {
    @Bean
    public FilterRegistrationBean<?> getFilterRegistrationBean() {
        FilterRegistrationBean<RequestBodyLoggingFilter> registrationBean = new FilterRegistrationBean<>(new RequestBodyLoggingFilter());        
        return registrationBean;
    }

}  
