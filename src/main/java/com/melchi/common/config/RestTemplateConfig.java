package com.melchi.common.config;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration  
public class RestTemplateConfig { 	
		 
	@Autowired
    private Environment env; 		 
		 
	@Bean
	public RestTemplate restTemplate() {

		int maxConnTotal = 50;
		int maxConnPerRoute = 20;
		int connectTimeout = 2000;
		int readTimeout = 5000; 

		if (env.getProperty("spring.rest-template.max-conn-total") != null) {
			maxConnTotal = Integer.valueOf(env.getProperty("spring.rest-template.max-conn-total"));
			maxConnPerRoute = Integer.valueOf(env.getProperty("spring.rest-template.max-per-route"));
			connectTimeout = Integer.valueOf(env.getProperty("spring.rest-template.connect-timeout"));
			readTimeout = Integer.valueOf(env.getProperty("spring.rest-template.read-timeout"));
		}
        
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		HttpClient client = HttpClientBuilder.create().setMaxConnTotal(maxConnTotal).setMaxConnPerRoute(maxConnPerRoute).build();
		factory.setHttpClient(client);
		factory.setConnectTimeout(connectTimeout);
		factory.setReadTimeout(readTimeout);	
		RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));		
		return restTemplate; 
	}		
 
}
