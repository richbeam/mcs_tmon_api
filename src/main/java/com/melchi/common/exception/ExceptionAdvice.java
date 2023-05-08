package com.melchi.common.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.melchi.common.config.LogMapper;
import com.melchi.common.response.Response;



 

@RestControllerAdvice    
@Order(Ordered.HIGHEST_PRECEDENCE)   
public class ExceptionAdvice  {
 
	static final Logger logger = LoggerFactory.getLogger(ExceptionAdvice.class);
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	LogMapper logMapper;
	//로그 남기기용 데이터
	private String requestUrl;
	private String requestMethod;
	private Map<String, Object> requestParam;
	private Map<String, Object> requestBody;
 
	  
	//예외 발생한 사용자의 Parameter & 요청 URL 정보
    @ModelAttribute   
    public void addAttributes(HttpServletRequest request, HttpServletResponse response, Model model, 
    @RequestParam Map<String, Object> requestParam, @RequestHeader(value = "User-Agent") String userAgent, 
    @RequestBody(required = false) Map<String, Object> requestBody) {
        // do whatever you want to do on the request body and header. 
        // with request object you can get the request method and request path etc.
        this.requestMethod = request.getMethod();             
        this.requestUrl = request.getRequestURI(); 
        this.requestParam = requestParam;
        this.requestBody = requestBody;        
         
    }   
    
    //에러 로그 입력
    public void writeErrorLog(Exception e) { 
    	
    	if (this.logMapper == null) {
    		this.logMapper = basicSqlSessionTemplate.getConfiguration().getMapperRegistry().getMapper(LogMapper.class, basicSqlSessionTemplate);
    	}
    	  
    	logger.error("requestMethod : " + this.requestMethod);
        logger.error("requestUrl : " + this.requestUrl);
        logger.error("requestParam : " + this.requestParam);  
        logger.error("requestBody : " + this.requestBody);      
           
    	String exceptionCause = null;
    	if (e.getCause() != null) {
    		exceptionCause = e.getCause().toString();
    	} 
    	Map<String, Object> paramMap = new HashMap<>();
    	paramMap.put("request_method", this.requestMethod);
    	paramMap.put("request_url", this.requestUrl); 
    	if (this.requestParam != null) {
    		paramMap.put("request_param", this.requestParam.toString());
    	}
    	if (this.requestBody != null) {
    		paramMap.put("request_body", this.requestBody.toString()); 
    	}
    	
    	if (e.getClass() == UserDefinedException.class) {
    		int resultCode = 0;
    		if(((UserDefinedException) e).getCode() != null){
    			resultCode = Integer.valueOf(((UserDefinedException) e).getCode());
    		}
    		String resultMessage = ((UserDefinedException) e).getMessage();  
    		paramMap.put("content", "CODE : " + resultCode + " , MESSAGE : " + resultMessage); 
    	} else {
    		paramMap.put("content", exceptionCause);
    	}
    	
    	    
    	//basicSqlSessionTemplate.insert("CommonMapper.insertExceptionLog", paramMap); 
    	//this.logMapper.insertRestApiExceptionLog(paramMap); 
    }	  
     
  
 
	//키 값으로 입력을 했는데 이미 등록된 키   
    @ExceptionHandler(DuplicateKeyException.class)  
    protected Response handleDuplicateKeyException(HttpServletRequest request, DuplicateKeyException e) {

    	writeErrorLog(e);
    	
    	Response response = new Response();
    	response.setResultCode(500);
    	
    	response.setResultMessage("중복된 항목이 존재합니다."); 
    	   
        return response; 
    } 

    //사용자 정의 Exception
    @ExceptionHandler(UserDefinedException.class)  
    protected Response handleUserDefinedException(HttpServletRequest request, UserDefinedException e) {
    	writeErrorLog(e);
    	Response response = new Response();
    	response.setResultMessage(e.getMessage());
    	if(e.getCode() != null){
    		response.setResultCode(Integer.valueOf(e.getCode()));
		}
        return response;  
    }       

        
    //키 값 미존재
    @ExceptionHandler(SuchNoKeyException.class)      
    protected Response handleSuchNoKeyException(HttpServletRequest request, SuchNoKeyException e) {
    	writeErrorLog(e);
    	Response response = new Response(); 
    	 
    	response.setResultCode(500);
    	if (e.getMessage() != null) {
    		response.setResultMessage(e.getMessage());      
    	} else { 
    		response.setResultMessage("항목이 존재하지 않습니다."); 
    	} 
    	      
        return response; 
    }         
 
 
	//인증 오류
    @ExceptionHandler(AuthenticationException.class)  
    protected Response handleAuthenticationException(HttpServletRequest request, AuthenticationException e) {     
    
    	writeErrorLog(e);
    	Response response = new Response();
    	response.setResultCode(401);
    
    	if (e.getMessage() != null) {
    		response.setResultMessage(e.getMessage());
    	} else {   
    		response.setResultMessage("인증키 번호가 잘못되었습니다.");
    	} 
     
        return response;     
    }  
    
	//Request Parameter 없음
    @ExceptionHandler(MissingServletRequestParameterException.class)  
    protected Response handleMissingServletRequestParameterException(HttpServletRequest request, MissingServletRequestParameterException e) {     
    	
    	//로그는 남기지 않음     
    	Response response = new Response();  
    	response.setResultCode(400);
    
    	if (e.getMessage() != null) {
    		response.setResultMessage(e.getMessage());
    	} else {   
    		response.setResultMessage("필수 파라메터가 누락 되었습니다.");
    	} 
     
        return response;     
    }  
 
 	//필수값이 안들어감
    @ExceptionHandler(DataIntegrityViolationException.class)  
    protected Response HandlePSQLException(HttpServletRequest request, DataIntegrityViolationException e) {
    	
    	writeErrorLog(e);   
    	Response response = new Response();
    	response.setResultCode(500);   
    	
    	/*
	    	"ERROR: null value in column \"seq\" violates not-null constraint\n  
	    	Detail: Failing row contains (null, null, null, 2019-12-30 09:06:59.487738, null, null, null, null, null, null, N)."
    	*/
    	String message = e.getMostSpecificCause().getMessage().replaceAll("\\\\", ""); 
    	Pattern p = Pattern.compile("\"(.*)\"");   
    	Matcher m = p.matcher(message);      
   
    	if (m.find()) {
    		String colName = m.group().replaceAll("\"", "");   
    		message = "'" + colName + "' 항목이 입력되지 않았습니다.";
    	} else { 
    		message = "필수항목이 입력 되지 않았습니다.";  
    	}
    	response.setResultMessage(message);   
        return response;     
    }     
 
 
 
 
	//위에서 처리가 안된 모든 Exception
    @ExceptionHandler(Exception.class)    
    protected Response HandleException(HttpServletRequest request, Exception e) {
    	Response response = new Response(); 
    	try {
	    	e.printStackTrace(); 
	    	writeErrorLog(e);  
	    	
	    	response.setResultCode(500);      
	    	String message = e.getMessage();
	    	response.setResultMessage(message); 
	        return response;      	
    	} catch (Exception ex) {
    		response.setResultCode(999);      
    		response.setResultMessage(ex.getMessage());    
    		logger.error(ex.getMessage());  
    	}
  
    	return response;
 
    }    

}

