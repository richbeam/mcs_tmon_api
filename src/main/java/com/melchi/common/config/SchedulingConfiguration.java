package com.melchi.common.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.ErrorHandler;

import com.melchi.common.exception.AuthenticationException;
import com.melchi.common.exception.SuchNoKeyException;
import com.melchi.common.exception.UserDefinedException;

import lombok.Data;



@EnableScheduling
@Configuration
public class SchedulingConfiguration implements SchedulingConfigurer {
    private final Logger logger = LoggerFactory.getLogger(SchedulingConfiguration.class);
    private final ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    SqlSessionTemplate basicSqlSessionTemplate;

	@Autowired
	private Environment env;    
		
    CustomSchedulingErrorhandler customSchedulingErrorhandler;

    SchedulingConfiguration() {     
 
        taskScheduler = new ThreadPoolTaskScheduler();
        customSchedulingErrorhandler = new CustomSchedulingErrorhandler();       
        taskScheduler.setErrorHandler(customSchedulingErrorhandler);
        taskScheduler.setThreadNamePrefix("@Scheduler-");                
        taskScheduler.initialize(); 
    }
     
      
     
    
    @Override 
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        int poolSize = Integer.valueOf(env.getProperty("spring.task.scheduling.pool.size"));

        taskScheduler.setPoolSize(poolSize); 

        //LogMapper logMapper = basicSqlSessionTemplate.getConfiguration().getMapperRegistry().getMapper(LogMapper.class, basicSqlSessionTemplate);
       // customSchedulingErrorhandler.setLogMapper(logMapper);  
        //String packageName = env.getProperty("spring.package-name");
      //  customSchedulingErrorhandler.setPackageName(packageName);
        taskRegistrar.setScheduler(taskScheduler);   
              
    }
    
    @Data
    class CustomSchedulingErrorhandler implements ErrorHandler {
    

    	@Autowired
    	SqlSessionTemplate basicSqlSessionTemplate;
    	
    	//로그 남기기용 데이터 
		private LogMapper logMapper;
		
		private String packageName; 
		
		public CustomSchedulingErrorhandler() {   

			
		}
		 
		@Override 
		public void handleError(Throwable t) {
			logger.info("Exception in @Scheduled task.");   					
			try {
				if (t.getClass() == SuchNoKeyException.class) {
					handleSuchNoKeyException((SuchNoKeyException) t);  
				} else if (t.getClass() == DuplicateKeyException.class) {
					handleDuplicateKeyException((DuplicateKeyException) t);
				} else if (t.getClass() == UserDefinedException.class) {
					handleUserDefinedException((UserDefinedException) t);
				} else if (t.getClass() == AuthenticationException.class) {
					handleAuthenticationException((AuthenticationException) t);
				} else if (t.getClass() == DataIntegrityViolationException.class) {
					handleDataIntegrityViolationException((DataIntegrityViolationException) t);
				} else {
					handleException((Exception) t);
				}
			} catch (Exception e) {
				logger.error("스케줄러 사망함");
				e.printStackTrace(); 
			}

			  
		}
		

		
		public void handleDuplicateKeyException(DuplicateKeyException e) {
			String message = "중복된 항목이 존재합니다.";
			writeErrorLog(e, message);
		}
		
		public void handleUserDefinedException(UserDefinedException e) {
			String message = null;
			if (e != null) {
				message = e.getMessage();
			} else {
				message = "알 수 없는 오류가 발생하였습니다.";
			}
			   
			writeErrorLog(e, message);
		}		
		
		public void handleSuchNoKeyException(SuchNoKeyException e) {
			String message = "항목이 존재하지 않습니다.";
			writeErrorLog(e, message);
		}		
		 
		public void handleAuthenticationException(AuthenticationException e) {
			String message = "인증 오류가 발생했습니다.";
			writeErrorLog(e, message);
		}	
 		
		public void handleDataIntegrityViolationException(DataIntegrityViolationException e) {
			
	    	String message = e.getMostSpecificCause().getMessage().replaceAll("\\\\", ""); 
	    	Pattern p = Pattern.compile("\"(.*)\"");   
	    	Matcher m = p.matcher(message);      
	   
	    	if (m.find()) {
	    		String colName = m.group().replaceAll("\"", "");   
	    		message = "'" + colName + "' 항목이 입력되지 않았습니다.";
	    	} else { 
	    		message = "필수항목이 입력 되지 않았습니다.";  
	    	}		
	    	writeErrorLog(e, message); 	
		}							
    	

		public void handleException(Exception e) {
			writeErrorLog(e, e.getMessage()); 	
		}
	 
		
    	private void writeErrorLog(Exception e, String message) {
    		try {
    			e.printStackTrace();  
	    		String content = "";    		
	    		StackTraceElement[] elements = e.getStackTrace();
	    		String fullPackageName = "com." + packageName + ".api";
	    		for (int i=0; i<elements.length; i++) {
	    			System.out.println(i + " : " + elements[i].toString()); 
	    			if (elements[i].toString().indexOf(fullPackageName) > -1) {
	    				content += elements[i].toString() + "\n"; 
	    			}
	    		} 
		  
				Map<String, Object> paramMap = new HashMap<>();	    	
				paramMap.put("content", content); 	    	 
				paramMap.put("message", message);		
				//this.logMapper.insertScheduleExceptionLog(paramMap);     		
    		} catch (Exception ex) {
    			ex.printStackTrace();
    			logger.info("Error 로그 쓰기 실패"); 
    		}
    		

		}
		
		
    }
}