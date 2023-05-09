package com.tmon.api;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.util.JSONUtil;
import com.melchi.common.util.StringUtil;
import com.melchi.common.vo.RestParameters;

@Component
public class TmonConnector {

	static final Logger logger = LoggerFactory.getLogger(TmonConnector.class);

	@Autowired
	TmonCasheConnector connCache;

	@Autowired
	RestTemplate restTemplate;
	//real
	private final String DOMAIN_URL = "https://interworkapi.tmon.co.kr/api/MELCHI";
	private final String Authorization = "d85005d2-4f42-47dd-b534-59e14db017e7";
	//test
	//private final String DOMAIN_URL = "http://interworkapi-test.tmon.co.kr/api/QAMELCHI";
	//private final String Authorization = "fbf0f280-c23a-4127-ad18-f5be7ac7532f";
	@Bean
	public RestTemplate tmonConnectors() {
		this.restTemplate.setInterceptors(Collections.singletonList(new RequestResponseLoggingInterceptor()));
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(Charset.forName("EUC-KR")));
		return restTemplate;
	}


	class RequestResponseLoggingInterceptor implements ClientHttpRequestInterceptor {

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
				throws IOException {
			// 전
			ClientHttpResponse response = execution.execute(request, body);
			// 후
			return response;
		}
	}



	@SuppressWarnings("unchecked")
	public Map<String, Object> call(HttpMethod method, String path, RestParameters params) throws UserDefinedException{
		//real
		//String Authorization = "";
		//test
		//String Authorization = "fbf0f280-c23a-4127-ad18-f5be7ac7532f";
		//Authorization = "Basic UUFNRUxDSEkxOmNBUmkxclV2NDl4MUFoeUNNVU4w";
		logger.info("CALL " + path);
		if (params == null) {
			params = new RestParameters();
		}

		Map<String, Object> pathVariableParameters = params.getPathVariableParameters();
		String url = DOMAIN_URL + path;
		// logger.info(params.toString());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		Map<String, Object> requestParametersMap = params.getRequestParameters();
		requestParametersMap.forEach((key, value) -> builder.queryParam(key, value));

		UriComponents uri = builder.buildAndExpand(pathVariableParameters);

		//String bodyParameters = params.getBodyParameters().toString().replace(":", "=");
		//bodyParameters.replace("http=", "http:");

		@SuppressWarnings("rawtypes")
		RequestEntity<Map> requestEntity = RequestEntity.method(method, uri.toUri())
				.header("Authorization", "bearer "+ connCache.getToken())
				.header("X-Partner-Id", "richbeam2020")
				.header("X-Partner-Token", Authorization)
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(params.getBodyParameters());

		/*logger.warn("requestEntity : " + requestEntity);
		logger.warn("METHOD : " + method);
		logger.warn("URL : " + uri.toString());
		logger.warn("URI : " + uri.toUri());*/

		ResponseEntity<String> responseEntity = null;
		String result = null;
		String statusCode = null;
		try {
			responseEntity = restTemplate.exchange(requestEntity, String.class);
			logger.warn("responseEntity 값 " + responseEntity);
		} catch (HttpStatusCodeException e) {
			e.printStackTrace();
			// 통신 오류(서버에서 500에러 반환)
			logger.warn("ERROR REQUEST : " + requestEntity.toString());
			logger.warn("ERROR CODE : " + e.getRawStatusCode());
			logger.warn("ERROR BODY : " + e.getResponseBodyAsString());
			if(e.getResponseBodyAsString().contains("invalid_token")){
				logger.error("토큰 만료 재발행 처리~.");
				connCache.refershToken();
				call(method,path,params);
			}else{
				throw new UserDefinedException(String.valueOf(e.getRawStatusCode()), e.getResponseBodyAsString());
			}

		} catch (RestClientException e) {
			if (e.getRootCause() instanceof SocketTimeoutException
					|| e.getRootCause() instanceof ConnectTimeoutException) {
				logger.error("timeout error = {}", e);
				throw new UserDefinedException(UserDefinedException.TIMEOUT_ERROR, "API호출 중에 타임아웃 오류가 발생했습니다.");
			}
		} catch (Exception e) {
			//logger.warn("ERROR 발생....");
			e.printStackTrace();
			logger.warn(e.getMessage());
		}

		//statusCode = String.valueOf(responseEntity.getStatusCode());
		if(null != responseEntity){
			logger.warn("responseEntity=" + responseEntity.toString());
		}

		result = responseEntity.getBody();


		Map<String, Object> sendResult = null;
		//가공이 필요한 경우
        if(result != null){
			logger.warn("result=" + result);
			sendResult=  JSONUtil.json2Map(result);
             if(sendResult != null){
				 try{
					 if(sendResult.containsKey("error")){
						 if(sendResult.get("error").toString().equals("invalid_token")){
							 connCache.refershToken();
							 call(method,path,params);
						 }
					 }
				 }catch (Exception e){
					 e.printStackTrace();
				 }
			}

        }
		//token 이 만료된 경우
		if (sendResult != null) {
			// logger.info(path + " - " + "sendResult\n" + JSONUtil.map2Json(sendResult) );
            logger.info(":::call Result = " + sendResult.toString());
		}
		return sendResult;

	}

	@SuppressWarnings("unchecked")
	public String callString(HttpMethod method, String path, RestParameters params) throws UserDefinedException{

		logger.info("callString " + path);
		if (params == null) {
			params = new RestParameters();
		}

		Map<String, Object> pathVariableParameters = params.getPathVariableParameters();
		String url = DOMAIN_URL + path;
		// logger.info(params.toString());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		Map<String, Object> requestParametersMap = params.getRequestParameters();
		requestParametersMap.forEach((key, value) -> builder.queryParam(key, value));

		UriComponents uri = builder.buildAndExpand(pathVariableParameters);

		//String bodyParameters = params.getBodyParameters().toString().replace(":", "=");
		//bodyParameters.replace("http=", "http:");

		@SuppressWarnings("rawtypes")
		RequestEntity<Map> requestEntity = RequestEntity.method(method, uri.toUri())
				.header("Authorization", "bearer "+ connCache.getToken())
				.header("X-Partner-Id", "richbeam2020")
				.header("X-Partner-Token", Authorization)
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(params.getBodyParameters());

		/*logger.warn("requestEntity : " + requestEntity);
		logger.warn("METHOD : " + method);
		logger.warn("URL : " + uri.toString());
		logger.warn("URI : " + uri.toUri());*/

		ResponseEntity<String> responseEntity = null;
		String result = null;
		String statusCode = null;
		try {
			responseEntity = restTemplate.exchange(requestEntity, String.class);
			//logger.info("responseEntity 값 " + responseEntity);
		} catch (HttpStatusCodeException e) {
			// 통신 오류(서버에서 500에러 반환)
			logger.warn("ERROR REQUEST : " + requestEntity.toString());
			logger.warn("ERROR CODE : " + e.getRawStatusCode());
			logger.warn("ERROR BODY : " + e.getResponseBodyAsString());
			if(e.getResponseBodyAsString().contains("invalid_token")){
				logger.error("토큰 만료 재발행 처리~.");
				connCache.refershToken();
				call(method,path,params);
			}else{
				throw new UserDefinedException(String.valueOf(e.getRawStatusCode()), e.getResponseBodyAsString());
			}

		} catch (RestClientException e) {
			if (e.getRootCause() instanceof SocketTimeoutException
					|| e.getRootCause() instanceof ConnectTimeoutException) {
				logger.error("timeout error = {}", e);
				throw new UserDefinedException(UserDefinedException.TIMEOUT_ERROR, "API호출 중에 타임아웃 오류가 발생했습니다.");
			}
		} catch (Exception e) {
			//logger.warn("ERROR 발생....");
			e.printStackTrace();
			logger.warn(e.getMessage());
		}

		//statusCode = String.valueOf(responseEntity.getStatusCode());
		if(null != responseEntity){
			logger.warn("responseEntity=" + responseEntity.toString());
		}

		result = responseEntity.getBody();


		String sendResult = null;
		//가공이 필요한 경우
		if(result != null){
			logger.warn("result=" + result);
			sendResult = result.toString();
		}
		//token 이 만료된 경우
		if (sendResult != null) {
			// logger.info(path + " - " + "sendResult\n" + JSONUtil.map2Json(sendResult) );
			logger.info(":::call Result = " + sendResult.toString());
		}
		return sendResult;

	}



	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> callList(HttpMethod method, String path, RestParameters params) throws UserDefinedException{

		if (params == null) {
			params = new RestParameters();
		}

		Map<String, Object> pathVariableParameters = params.getPathVariableParameters();
		String url = DOMAIN_URL + path;
		// logger.info(params.toString());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		Map<String, Object> requestParametersMap = params.getRequestParameters();
		requestParametersMap.forEach((key, value) -> builder.queryParam(key, value));

		UriComponents uri = builder.buildAndExpand(pathVariableParameters);

		//String bodyParameters = params.getBodyParameters().toString().replace(":", "=");
		//bodyParameters.replace("http=", "http:");
        logger.warn("callList :" + url);
		@SuppressWarnings("rawtypes")
		RequestEntity<Map> requestEntity = RequestEntity.method(method, uri.toUri())
				.header("Authorization", "bearer "+ connCache.getToken())
				.header("X-Partner-Id", "richbeam2020")
				.header("X-Partner-Token", Authorization)
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(params.getBodyParameters());

		/*logger.warn("requestEntity : " + requestEntity);
		logger.warn("METHOD : " + method);
		logger.warn("URL : " + uri.toString());
		logger.warn("URI : " + uri.toUri());*/

		ResponseEntity<String> responseEntity = null;
		String result = null;
		String statusCode = null;
		try {
			responseEntity = restTemplate.exchange(requestEntity, String.class);
			logger.warn("responseEntity 값 " + responseEntity.getBody());
		} catch (HttpStatusCodeException e) {
			// 통신 오류(서버에서 500에러 반환)
			logger.warn("ERROR REQUEST : " + requestEntity.getBody().toString());
			logger.warn("ERROR CODE : " + e.getRawStatusCode());
			logger.warn("ERROR BODY : " + e.getResponseBodyAsString());
			if(e.getResponseBodyAsString().contains("invalid_token")){
				logger.error("토큰 만료 재발행 처리~");
				connCache.refershToken();
				call(method,path,params);
			}else{
				throw new UserDefinedException(String.valueOf(e.getRawStatusCode()), e.getResponseBodyAsString());
			}

		} catch (RestClientException e) {
			if (e.getRootCause() instanceof SocketTimeoutException
					|| e.getRootCause() instanceof ConnectTimeoutException) {
				logger.error("timeout error = {}", e);
				throw new UserDefinedException(UserDefinedException.TIMEOUT_ERROR, "API호출 중에 타임아웃 오류가 발생했습니다.");
			}
		} catch (Exception e) {
			//logger.warn("ERROR 발생....");
			logger.warn(e.getMessage());
		}

		List<Map<String, Object>> sendResult = null;
		//가공이 필요한 경우
		if(responseEntity != null){
			logger.warn(requestEntity.getBody().toString());
			statusCode = String.valueOf(responseEntity.getStatusCode());
			logger.warn("statusCode = {}",statusCode);
			result = responseEntity.getBody();

			logger.warn("result=" + result);

			sendResult = JSONUtil.jsonStrToListMap(result);
			try{
				if(result.toString().equals("invalid_token")){
					connCache.refershToken();
					call(method,path,params);
				}
			}catch (Exception e){
				e.printStackTrace();
			}
		}

		//token 이 만료된 경우

		if (sendResult != null) {
			// logger.info(path + " - " + "sendResult\n" + JSONUtil.map2Json(sendResult) );
			logger.info(":::call Result = " + sendResult.toString());

		}

		return sendResult;

	}
	

}