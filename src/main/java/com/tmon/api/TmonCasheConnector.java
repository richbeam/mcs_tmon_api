package com.tmon.api;

import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.util.JSONUtil;
import com.melchi.common.vo.RestParameters;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.*;

@Component
public class TmonCasheConnector {

	static final Logger logger = LoggerFactory.getLogger(TmonCasheConnector.class);

	@Autowired
	RestTemplate restTemplate;
	//real
	private final String DOMAIN_URL = "https://interworkapi.tmon.co.kr/oauth/token";
	//test
	//private final String DOMAIN_URL = "http://interworkapi-test.tmon.co.kr/oauth/token";

	@Bean
	public RestTemplate tmonConnectors__() {
		this.restTemplate.setInterceptors(Collections.singletonList(new RequestResponseLoggingInterceptor()));
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(Charset.forName("EUC-KR")));
		return restTemplate;
	}

	// 캐시 저장
	@Cacheable(value="tokenCacheStore")
	public String getToken() {
        Map<String, Object> result = new HashMap<>();
        String token = "";
		logger.warn("cacheable 실행");

        RestParameters params = new RestParameters();
        Map<String, Object> param = new HashMap<>();
        param.put("grant_type","client_credentials");
        params.setBody(param);

		try {
		    logger.warn("::::::::::::::::::::::::::token call");
            result = tokenCall(HttpMethod.POST,DOMAIN_URL,params);
            logger.warn("token Result :: {}",result.toString());
            token = result.get("access_token").toString();
        }catch (Exception e){
		    e.printStackTrace();
        }

		return token;
	}

	// 캐시 갱신
	@CachePut(value="tokenCacheStore")
	public String refershToken() {

		Map<String, Object> result = new HashMap<>();
		String token = "";
		logger.warn("re cacheable 실행");

		RestParameters params_ = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("grant_type","client_credentials");
		params_.setBody(param);

		try {
			logger.warn("::::::::::::::::::::::::::token call");
			result = tokenCall(HttpMethod.POST,DOMAIN_URL,params_);
			logger.warn("token Result :: {}",result.toString());
			token = result.get("access_token").toString();
		}catch (Exception e){
			e.printStackTrace();
		}

		return token;
	}


	// 티몬 코드 가져오기
	@Cacheable(value="tokenCacheStore")
	public String getTmonCode() {
		Map<String, Object> result = new HashMap<>();
		String token = "";
		logger.warn("------getTmonCode 실행");

		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		params.setBody(param);

		try {
			logger.warn("::::::::::::::::::::::::::getTmonCode call");
			result = codeCall(HttpMethod.GET,DOMAIN_URL,params);
			logger.warn("Code Result :: {}",result.toString());
		}catch (Exception e){
			e.printStackTrace();
		}

		return token;
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
	public Map<String, Object> tokenCall(HttpMethod method, String path, RestParameters params) throws UserDefinedException{

		logger.info("tokenCall " + path);


		//real
		String clientId = "melchi1";
		String clientSecret = "Jd12tYdjhgzqiUXXvM2o";
        //test
		//String clientId = "QAMELCHI1";
        //String clientSecret = "cARi1rUv49x1AhyCMUN0";
        String encodingText = clientId+":"+clientSecret;

        String authorization = Base64.getUrlEncoder().encodeToString(encodingText.getBytes());
        logger.warn("getToken authorization :: {}",authorization);
        authorization = "Basic "+authorization;

		if (params == null) {
			params = new RestParameters();
		}


		Map<String, Object> pathVariableParameters = params.getPathVariableParameters();
		String url = path;
		// logger.info(params.toString());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
		Map<String, Object> requestParametersMap = params.getRequestParameters();
		requestParametersMap.forEach((key, value) -> builder.queryParam(key, value));
		UriComponents uri = builder.buildAndExpand(pathVariableParameters);

		MultiValueMap<String, String> stringPart = new LinkedMultiValueMap<>();
		stringPart.add("grant_type","client_credentials");

		@SuppressWarnings("rawtypes")
		RequestEntity<Map> requestEntity = RequestEntity.method(method, uri.toUri())
				.header("Authorization", authorization)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.accept(MediaType.APPLICATION_JSON)
				.acceptCharset(Charset.forName("UTF-8"))
				.body(stringPart);

		logger.warn("requestEntity 값 " + requestEntity.toString());
		ResponseEntity<String> responseEntity = null;
		String result = null;
		String statusCode = null;
		try {
			//logger.warn("responseEntity 값1 ");
			responseEntity = restTemplate.exchange(requestEntity, String.class);

			logger.warn("responseEntity 값 " + responseEntity.getBody());
		} catch (HttpStatusCodeException e) {
			e.printStackTrace();
			// 통신 오류(서버에서 500에러 반환)
			logger.warn("ERROR REQUEST : " + requestEntity.toString());
			logger.warn("ERROR CODE : " + e.getRawStatusCode());
			logger.warn("ERROR BODY : " + e.getResponseBodyAsString());
			throw new UserDefinedException(String.valueOf(e.getRawStatusCode()), e.getResponseBodyAsString());
		} catch (RestClientException e) {
			e.printStackTrace();
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
		result = responseEntity.getBody();
		logger.warn("result=" + result);

		Map<String, Object> data = JSONUtil.json2Map(result);
		logger.warn("data=" + data.get("access_token"));


		if (data != null) {


		}
		logger.info("token Call Result" + data.toString());
		return data;

	}


	@SuppressWarnings("unchecked")
	public Map<String, Object> codeCall(HttpMethod method, String path, RestParameters params) throws UserDefinedException {

		//real
		String URL = "https://interworkapi.tmon.co.kr/api/QAMELCHI/codes";
		String Authorization = "d85005d2-4f42-47dd-b534-59e14db017e7";
		//String Authorization = "";
		//test
		//String URL = "http://interworkapi-test.tmon.co.kr/api/QAMELCHI/codes";
		//String Authorization = "fbf0f280-c23a-4127-ad18-f5be7ac7532f";
		//Authorization = "Basic UUFNRUxDSEkxOmNBUmkxclV2NDl4MUFoeUNNVU4w";
		//logger.info("CALL " + path);


		//Map<String, Object> pathVariableParameters = params.getPathVariableParameters();
		String url = URL;
		// logger.info(params.toString());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		Map<String, Object> requestParametersMap = params.getRequestParameters();
		requestParametersMap.forEach((key, value) -> builder.queryParam(key, value));

		UriComponents uri = builder.buildAndExpand();

		String bodyParameters = "";
		//bodyParameters.replace("http=", "http:");

		@SuppressWarnings("rawtypes")
		RequestEntity<Map> requestEntity = RequestEntity.method(HttpMethod.GET, uri.toUri())
				.header("Authorization", "bearer "+ getToken())
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
			if (e.getResponseBodyAsString().contains("invalid_token")) {
				logger.error("토큰 만료 재발행 처리~");
			}
			throw new UserDefinedException(String.valueOf(e.getRawStatusCode()), e.getResponseBodyAsString());
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

		statusCode = String.valueOf(responseEntity.getStatusCode());
		result = responseEntity.getBody();
		logger.warn("Code Result = " + result);

		Map<String, Object> sendResult = null;
		//가공이 필요한 경우
		sendResult = JSONUtil.json2Map(result);


		if (sendResult != null) {
			// logger.info(path + " - " + "sendResult\n" + JSONUtil.map2Json(sendResult) );
		}
		logger.warn(":::call Result = " + sendResult);
		return sendResult;

	}
}