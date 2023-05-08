package com.melchi.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;


/**
 * @Configuration, @Component 에서는 사용 불가.
 * @Controller, @Service 등에서 사용 가능
 */

@Component
public class EnviromentUtil implements ApplicationRunner {
		
	@Autowired 
	Environment env;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		
	}
	
	public String getEnvProperty(String propName) {
		try { 
			return env.getProperty(propName);
		} catch (Exception e) { 
			return null; 
		}
	}
	
	
}
