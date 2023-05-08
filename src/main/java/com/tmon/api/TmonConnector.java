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
	//private final String DOMAIN_URL = "https://interworkapi.tmon.co.kr/api/QAMELCHI";
	//test
	private final String DOMAIN_URL = "http://interworkapi-test.tmon.co.kr/api/QAMELCHI";
	private final String Authorization = "fbf0f280-c23a-4127-ad18-f5be7ac7532f";
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
	
	//상품등록
	@SuppressWarnings("unchecked")
	public Map<String, Object> insertItem(Map<String, Object> product) throws UserDefinedException {
		String path = "/item/0.4/insertItem.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("insertItem", product);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultCode") == null) return null;
		if(!"00".equals(result.get("resultCode"))) return  result;
		
		//결과 생성
		Map<String, Object> item = new HashMap<>();
		item.put("resultCode", result.get("resultCode").toString());
		item.put("resultMessage", result.get("resultMessage").toString());
		item.put("resultDesc", result.get("resultDesc").toString());
		item.put("itemId", result.get("itemId").toString());
		
		if(result.get("uitems") == null) return item;
		List<Object> uitems= (List<Object>) result.get("uitems");
		if(uitems.size() == 0) return item;
		if(uitems.get(0) == null) return item;
		if(uitems.get(0) instanceof String) return item;
		
		Map<String, Object> uitem = (Map<String, Object>) uitems.get(0);
		if(uitem.get("uitem") instanceof List) {
			item.put("uitem", (List<Map<String, Object>>) uitem.get("uitem"));
		} else {
			List<Map<String, Object>> uitemList = new ArrayList<>(1);
			uitemList.add((Map<String, Object>) uitem.get("uitem"));
			item.put("uitem", uitemList);
		}
		
		//결과반환
		return item;
	}
	
	//상품수정
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateItem(Map<String, Object> product) throws UserDefinedException {
		String path = "/item/0.3/updateItem.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("updateItem", product);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultCode") == null) return null;
		if(!"00".equals(result.get("resultCode"))) return  result;
		
		//결과 생성
		Map<String, Object> item = new HashMap<>();
		item.put("resultCode", result.get("resultCode").toString());
		item.put("resultMessage", result.get("resultMessage").toString());
		item.put("resultDesc", result.get("resultDesc").toString());
		
		if(result.get("uitems") == null) return item;
		List<Object> uitems= (List<Object>) result.get("uitems");
		if(uitems.size() == 0) return item;
		if(uitems.get(0) == null) return item;
		if(uitems.get(0) instanceof String) return item;
		
		Map<String, Object> uitem = (Map<String, Object>) uitems.get(0);
		if(uitem.get("uitem") instanceof List) {
			item.put("uitem", (List<Map<String, Object>>) uitem.get("uitem"));
		} else {
			List<Map<String, Object>> uitemList = new ArrayList<>(1);
			uitemList.add((Map<String, Object>) uitem.get("uitem"));
			item.put("uitem", uitemList);
		}
		
		//결과반환
		return item;
	}
	
	//상품조회
	@SuppressWarnings("unchecked")
	public Map<String, Object> getItem(String itemId) throws UserDefinedException {
		String path = "/item/0.3/viewItem.ssg?itemId=" + itemId;
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.GET, path, params);
		
		//결과반환
		return result;
	}
	
	//상품 관리 항목 분류 ID로 상품관리 상세항목 조회
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getListItemMngProp(String stdCtgId) throws UserDefinedException {	
		String path = "/common/0.1/listItemMngProp.ssg?stdCtgId=" + stdCtgId;
		RestParameters params = new RestParameters();
		params.setBody(new HashMap<>());  
		Map<String, Object> result = call(HttpMethod.GET, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("itemMngProps") == null) return null;
		List<Object> itemMngProps= (List<Object>) result.get("itemMngProps");
		if(itemMngProps.size() == 0) return null;
		if(itemMngProps.get(0) == null) return null;
		if(itemMngProps.get(0) instanceof String) return null;
		
		Map<String, Object> itemMngProp = (Map<String, Object>) itemMngProps.get(0);
		if(itemMngProp.get("itemMngProp") instanceof List) {
			return (List<Map<String, Object>>) itemMngProp.get("itemMngProp");
		} else {
			List<Map<String, Object>> itemMngPropList = new ArrayList<>(1);
			itemMngPropList.add((Map<String, Object>) itemMngProp.get("itemMngProp"));
			return itemMngPropList;
		}
	}
	
	//업체 배송비 정책 등록
	public String addShppcstPlcy(Map<String, Object> shippingPolicy) throws UserDefinedException {	
		
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("shppcstId", shippingPolicy.get("shppcstId"));
		paramMap.put("shppcstPlcyDivCd", shippingPolicy.get("shppcstPlcyDivCd"));
		paramMap.put("shppcstAplUnitCd", shippingPolicy.get("shppcstAplUnitCd"));
		paramMap.put("prpayCodDivCd", shippingPolicy.get("prpayCodDivCd"));
		paramMap.put("shppcstExmpCritnAmt", shippingPolicy.get("shppcstExmpCritnAmt"));
		paramMap.put("shppcst", shippingPolicy.get("shppcst"));
		paramMap.put("bascPlcyYn", shippingPolicy.get("bascPlcyYn"));
		paramMap.put("shppcstAplYn", shippingPolicy.get("shppcstAplYn"));
		
		//API 호출
		String path = "/venInfo/0.1/insertShppcstPlcy.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestShppcstPlcyInsert", paramMap);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("shppcstId") != null) return result.get("shppcstId").toString();
		
		//기등록건인경우 데이터 조회후 맵핑
		String shppcstId = null;
		List<Map<String, Object>> listShppcstPlcy = listShppcstPlcy(shippingPolicy.get("shppcstPlcyDivCd").toString());
		if(listShppcstPlcy == null) return null;
		for(Map<String, Object> shppcstPlcy: listShppcstPlcy) {
			if(shppcstPlcy == null) continue;
			
			//필수값 체크
			if(!shppcstPlcy.get("shppcstPlcyDivCd").toString().equals(shippingPolicy.get("shppcstPlcyDivCd").toString())) continue;
			if(!shppcstPlcy.get("shppcstAplUnitCd").toString().equals(shippingPolicy.get("shppcstAplUnitCd").toString())) continue;
			if(!shppcstPlcy.get("prpayCodDivCd").toString().equals(shippingPolicy.get("prpayCodDivCd").toString())) continue;
			if(!shppcstPlcy.get("shppcst").toString().equals(shippingPolicy.get("shppcst").toString())) continue;
			
			//옵션값체크
			if(shppcstPlcy.get("shppcstExmpCritnAmt") != null && shippingPolicy.get("shppcstExmpCritnAmt") != null) {
				if(!shppcstPlcy.get("shppcstExmpCritnAmt").toString().equals(shippingPolicy.get("shppcstExmpCritnAmt").toString())) continue;
			}
			
			//모두 값이 같으면 종료
			shppcstId = shppcstPlcy.get("shppcstId").toString();
			break;
		}
			
		return shppcstId;
	}
	
	//업체 배송비 정책 조회
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listShppcstPlcy(String shppcstPlcyDivCd) throws UserDefinedException {	
		//API 호출
		String path = "/venInfo/0.1/listShppcstPlcy.ssg?shppcstPlcyDivCd=" + shppcstPlcyDivCd;
		RestParameters params = new RestParameters();
		params.setBody(new HashMap<>());
		
		Map<String, Object> result = call(HttpMethod.GET, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("shppcstPlcys") == null) return null;
		List<Object> shppcstPlcys= (List<Object>) result.get("shppcstPlcys");
		if(shppcstPlcys.size() == 0) return null;
		if(shppcstPlcys.get(0) == null) return null;
		if(shppcstPlcys.get(0) instanceof String) return null;
		
		Map<String, Object> shppcstPlcy = (Map<String, Object>) shppcstPlcys.get(0);
		if(shppcstPlcy.get("shppcstPlcy") instanceof List) {
			return (List<Map<String, Object>>) shppcstPlcy.get("shppcstPlcy");
		} else {
			List<Map<String, Object>> shppcstPlcyList = new ArrayList<>(1);
			shppcstPlcyList.add((Map<String, Object>) shppcstPlcy.get("shppcstPlcy"));
			return shppcstPlcyList;
		}
	}
	
	//업체 배송지 주소/택배계약정보 등록
	@SuppressWarnings("unchecked")
	public Map<String, Object> insertVenAddr(Map<String, Object> venAddr) throws UserDefinedException {
		//API 호출
		String path = "/venInfo/0.3/insertVenAddr.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestVenAddrInsert", venAddr);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		logger.error("/venInfo/0.3/insertVenAddr.ssg result : {} , Msg : {} ",result.get("resultCode"),result.get("resultMessage"));
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("venAddrDelInfo") == null) return null;
		List<Object> venAddrDelInfo= (List<Object>) result.get("venAddrDelInfo");
		if(venAddrDelInfo.size() == 0) return null;
		if(venAddrDelInfo.get(0) == null) return null;
		if(venAddrDelInfo.get(0) instanceof String) return null;
		
		//결과반환
		return (Map<String, Object>) ((Map<String, Object>) venAddrDelInfo.get(0)).get("venAddrDelInfoDto");
	}
	
	//업체 배송지 주소/택배계약정보 등록
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateVenAddr(Map<String, Object> venAddr) throws UserDefinedException {
		//API 호출
		String path = "/venInfo/0.3/updateVenAddr.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestVenAddrInsert", venAddr);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("venAddrDelInfo") == null) return null;
		List<Object> venAddrDelInfo= (List<Object>) result.get("venAddrDelInfo");
		if(venAddrDelInfo.size() == 0) return null;
		if(venAddrDelInfo.get(0) == null) return null;
		if(venAddrDelInfo.get(0) instanceof String) return null;
		
		//결과반환
		return (Map<String, Object>) ((Map<String, Object>) venAddrDelInfo.get(0)).get("venAddrDelInfoDto");
	}
	
	/**
	 * 접수된 주문을 조회 
	 *   배송진행상태가 배송지시인 주문을 조회 
	 *     - 주문완료 시 배송진행상태 배송지시로 배송데이터가 생성
	 *     - 아직 결제가 완료되지 않은 주문(입금대기)은 대기 상태로 조회되며 결제 후 정상으로 전환
	 *     - 배송지시일, 주문완료일, *출고예정일 기준으로 최대 7일까지 조회 가능
	 *     - 출고예정일: 0시~4시에 들어온 주문은 당일, 4시~0시에 들어온 주문은 익일로 출고예정일이 셋팅
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listShppDirection(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/pd/1/listShppDirection.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestShppDirection", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//logger.warn("shipOrders result listShppDirection ::: {}", result);
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("shppDirections") == null) return null;
		List<Object> shppDirections= (List<Object>) result.get("shppDirections");
		//logger.warn("shppDirections"+ shppDirections);
		
		if(shppDirections.size() == 0) return null;
		if(shppDirections.get(0) == null) return null;
		if(shppDirections.get(0) instanceof String) return null;
		
		Map<String, Object> shppDirection = (Map<String, Object>) shppDirections.get(0);
		if(shppDirection.get("shppDirection") instanceof List) {
			return (List<Map<String, Object>>) shppDirection.get("shppDirection");
		} else {
			List<Map<String, Object>> shppDirectionList = new ArrayList<>(1);
			shppDirectionList.add((Map<String, Object>) shppDirection.get("shppDirection"));
			return shppDirectionList;
		}
	}
	
	/**
	 * 출고처리 목록조회 
	 *   배송진행상태 피킹완료인(주문확인처리된) 주문을 조회
	 *     - 배송지시일, 주문완료일, 출고예정일 기준으로 최대 7일까지 조회 가능
	 *     - 조회된 대상에 운송장을 등록 후 출고처리 가능
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listWarehouseOut(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/pd/1/listWarehouseOut.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestWarehouseOut", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("warehouseOuts") == null) return null;
		List<Object> warehouseOuts= (List<Object>) result.get("warehouseOuts");
		if(warehouseOuts.size() == 0) return null;
		if(warehouseOuts.get(0) == null) return null;
		if(warehouseOuts.get(0) instanceof String) return null;
		
		Map<String, Object> warehouseOut = (Map<String, Object>) warehouseOuts.get(0);
		if(warehouseOut.get("warehouseOut") instanceof List) {
			return (List<Map<String, Object>>) warehouseOut.get("warehouseOut");
		} else {
			List<Map<String, Object>> warehouseOutList = new ArrayList<>(1);
			warehouseOutList.add((Map<String, Object>) warehouseOut.get("warehouseOut"));
			return warehouseOutList;
		}
	}
	
	/**
	 * 미배송완료목록조회
	 *   배송진행상태 출고완료인(출고처리된) 주문을 미배송완료목록에서 조회
	 *     - 출고완료일, 결제완료일 기준으로 최대 7일까지 조회 가능
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listNonDelivery(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/pd/1/listNonDelivery.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestNonDelivery", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("nonDeliverys") == null) return null;
		List<Object> nonDeliverys= (List<Object>) result.get("nonDeliverys");
		if(nonDeliverys.size() == 0) return null;
		if(nonDeliverys.get(0) == null) return null;
		if(nonDeliverys.get(0) instanceof String) return null;
		
		Map<String, Object> nonDelivery = (Map<String, Object>) nonDeliverys.get(0);
		if(nonDelivery.get("nonDelivery") instanceof List) {
			return (List<Map<String, Object>>) nonDelivery.get("nonDelivery");
		} else {
			List<Map<String, Object>> nonDeliveryList = new ArrayList<>(1);
			nonDeliveryList.add((Map<String, Object>) nonDelivery.get("nonDelivery"));
			return nonDeliveryList;
		}  
	}
	
	/**
	 * 배송완료 목록조회
	 *   배송진행상태 배송완료인(배송완료처리된) 대상을 배송완료목록에서 조회 가능합니다.
	 *     - 배송완료일, 출고완료일, 결제완료일, 주문완료일 기준으로 최대 7일까지 조회 가능합니다.
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listDeliveryEnd(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/pd/1/listDeliveryEnd.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestDeliveryEnd", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("deliveryEnds") == null) return null;
		List<Object> deliveryEnds= (List<Object>) result.get("deliveryEnds");
		if(deliveryEnds.size() == 0) return null;
		if(deliveryEnds.get(0) == null) return null;
		if(deliveryEnds.get(0) instanceof String) return null;
		
		Map<String, Object> deliveryEnd = (Map<String, Object>) deliveryEnds.get(0);
		if(deliveryEnd.get("deliveryEnd") instanceof List) {
			return (List<Map<String, Object>>) deliveryEnd.get("deliveryEnd");
		} else {
			List<Map<String, Object>> deliveryEndList = new ArrayList<>(1);
			deliveryEndList.add((Map<String, Object>) deliveryEnd.get("deliveryEnd"));
			return deliveryEndList;
		}  
	}

	/**
	 * 취소신청목록조회
	 *   설정한 기간 내에 취소요청 상태인 주문을 조회합니다. 취소요청은 상품이 출고되기 전까지 접수될 수 있습니다.
	 * @param request
	 * @return
	 * @throws UserDefinedException
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> cancelRequests(Map<String, Object> request) throws UserDefinedException {
		if (request.get("perdStrDts") == null || request.get("perdEndDts") == null) {
			return null;
		}

		//API 호출
		String path = "/api/claim/v2/cancel/requests?perdStrDts=" + request.get("perdStrDts") + "&perdEndDts=" + request.get("perdEndDts");
		RestParameters params = new RestParameters();

		logger.warn("cancelRequests path : " + path);

		Map<String, Object> result = call(HttpMethod.GET, path, params);

		logger.warn("cancelRequests result : " + result);

		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultData") == null) return null;

		//결과반환
		return (List<Map<String, Object>>) ((Map<String, Object>) result).get("resultData");
	}

	/**
	 * 취소주문목록조회
	 *   주문확인 처리 이전에는 고객이 FRONT에서 주문취소 가능
	 *   주문확인 처리 이후에는 고객이 FRONT에서 주문취소 불가하며, 고객센터를 통해서 업체 확인 후 취소 가능
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> inquiry(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/clm/cncl/ord/inquiry.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("request", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("data") == null) return null;
		
		//결과반환
		return (List<Map<String, Object>>) ((Map<String, Object>) result).get("data");  
	}
	
	/**
	 * 반품교환회수대상조회
	 *   배송진행상태가 회수지시인 회수건을 조회 
	 *   반품/교환 접수 시 배송진행상태 회수지시로 회수(배송)데이터가 생성됨
	 *   배송진행상태가 회수확인인 회수건에 한해 회수확인일 +3일(영업일 기준)에 회수완료상태로 자동으로 변경
	 *   
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listExchangeTarget(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/pd/1/listExchangeTarget.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestExchangeTarget", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("exchangeTargets") == null) return null;
		List<Object> exchangeTargets= (List<Object>) result.get("exchangeTargets");
		if(exchangeTargets.size() == 0) return null;
		if(exchangeTargets.get(0) == null) return null;
		if(exchangeTargets.get(0) instanceof String) return null;
		
		Map<String, Object> exchangeTarget = (Map<String, Object>) exchangeTargets.get(0);
		if(exchangeTarget.get("exchangeTarget") instanceof List) {
			return (List<Map<String, Object>>) exchangeTarget.get("exchangeTarget");
		} else {
			List<Map<String, Object>> exchangeTargetList = new ArrayList<>(1);
			exchangeTargetList.add((Map<String, Object>) exchangeTarget.get("exchangeTarget"));
			return exchangeTargetList;
		}
	}
	
	//주문확인
	public Map<String, Object> updateOrderSubjectManage(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/updateOrderSubjectManage.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestOrderSubjectManage", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//배송지연
	public Map<String, Object> listShppingDelay(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/listShppingDelay.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestShppingDelay", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//운송장등록 
	public Map<String, Object> saveWblNo(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveWblNo.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestWhOutCompleteProcess", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}

	//부분출고처리
	public Map<String, Object> savePortionWarehouseOutProcess(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/savePortionWarehouseOutProcess.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestWhOutCompleteProcess", order);
		params.setBody(param);

		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//결과반환
		return result;
	}

	//주문별 상태조회
	public List<Map<String, Object>> order(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/claim/v2/order/" + order.get("ordercd");
		RestParameters params = new RestParameters();
		/*Map<String, Object> param = new HashMap<>();
		param.put("orordNo", order);
		params.setBody(param);*/

		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultData") == null) return null;

		//결과반환
		return (List<Map<String, Object>>) ((Map<String, Object>) result).get("resultData");
	}
	
	//출고처리 
	public Map<String, Object> saveWhOutCompleteProcess(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveWhOutCompleteProcess.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestWhOutCompleteProcess", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//배송완료처리 
	public Map<String, Object> saveDeliveryEnd(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveDeliveryEnd.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestDeliveryEnd", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}

	//취소승인처리
	public Map<String, Object> cancelRequestApprove(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/claim/v2/cancel/requests/approve";
		RestParameters params = new RestParameters();
		params.setBody(order);

		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//결과반환
		return result;
	}

	//판매불가처리 
	public Map<String, Object> saveNoSellRequestRegist(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveNoSellRequestRegist.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestNoSellRequestRegist", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//회수확인
	public Map<String, Object> saveConfirmRcov(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveConfirmRcov.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestConfirmRcov", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//회수완료
	public Map<String, Object> saveCompleteRcov(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveCompleteRcov.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestConfirmRcov", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//반품거부처리
	public Map<String, Object> saveRefusualReturn(Map<String, Object> order) throws UserDefinedException {
		//API 호출
		String path = "/api/pd/1/saveRefusualReturn.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("requestRefusualReturn", order);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	/**
	 * 상품Q&A 리스트를 조회
	 *   
	 * @param request
	 * @return
	 * @throws UserDefinedException 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> qnaList(Map<String, Object> request) throws UserDefinedException {	
		//API 호출
		String path = "/api/postng/qnaList.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("postngReq", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("qnaList") == null) return null;
		List<Object> qnaList= (List<Object>) result.get("qnaList");
		if(qnaList.size() == 0) return null;
		if(qnaList.get(0) == null) return null;
		if(qnaList.get(0) instanceof String) return null;
		
		Map<String, Object> qna = (Map<String, Object>) qnaList.get(0);
		if(qna.get("qna") instanceof List) {
			return (List<Map<String, Object>>) qna.get("qna");
		} else {
			List<Map<String, Object>> newQnaList = new ArrayList<>(1);
			newQnaList.add((Map<String, Object>) qna.get("qna"));
			return newQnaList;
		}
	}
	
	//상품QNA 답변등록
	public Map<String, Object> ansQna(Map<String, Object> request) throws UserDefinedException {
		//API 호출
		String path = "/api/postng/ansQna.ssg";
		RestParameters params = new RestParameters();
		Map<String, Object> param = new HashMap<>();
		param.put("postngReq", request);
		params.setBody(param);
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);
		
		//결과반환
		return result;
	}
	
	//상품 승인 목록 조회
	@SuppressWarnings("unchecked")
	public Map<String ,Object> getApprovalPending(String request) throws UserDefinedException {
		String path = "/item/0.1/getItemChngDemandList.ssg?itemId="+request ;
		RestParameters params = new RestParameters();
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultCode") == null) return null;
		if(!"00".equals(result.get("resultCode"))) return  result;

		//결과 생성
		Map<String, Object> item = new HashMap<>();
		item.put("resultCode", result.get("resultCode").toString());
		item.put("resultMessage", result.get("resultMessage").toString());
		item.put("resultDesc", result.get("resultDesc").toString());
			
		if(result.get("itemChngDemndList") == null) return item;
		List<Object> itemChngDemndList= (List<Object>) result.get("itemChngDemndList");
		if(itemChngDemndList.size() == 0) return item;
		if(itemChngDemndList.get(0) == null) return item;
		if(itemChngDemndList.get(0) instanceof String) return item;
				
		Map<String, Object> itemChngDemnd = (Map<String, Object>) itemChngDemndList.get(0);
		if(itemChngDemnd.get("itemChngDemnd") instanceof List) {
			item.put("itemChngDemnd", (List<Map<String, Object>>) itemChngDemnd.get("itemChngDemnd"));
		} else {
			List<Map<String, Object>> itemChngDemndList1 = new ArrayList<>(1);
			itemChngDemndList1.add((Map<String, Object>) itemChngDemnd.get("itemChngDemnd"));
			item.put("itemChngDemnd", itemChngDemndList1);
		}
				
		//결과반환
		return item;
	}
	
	//판매중지 상품 대상 상태조회
	@SuppressWarnings("unchecked")
	public Map<String ,Object> getItemStatus(String request) throws UserDefinedException {
		String path = "/item/0.3/viewItem.ssg?itemId="+request ;
		RestParameters params = new RestParameters();
		
		Map<String, Object> result = call(HttpMethod.POST, path, params);

		//결과 정합성 체크
		if(result == null) return null;
		if(result.get("resultCode") == null) return null;
		if(!"00".equals(result.get("resultCode"))) return  result;

		//결과 생성
		Map<String, Object> item = new HashMap<>();
		item.put("resultCode", result.get("resultCode").toString());
		item.put("resultMessage", result.get("resultMessage").toString());
		item.put("resultDesc", result.get("resultDesc").toString());
			
		if(result.get("sellStatCd") == null) return item;
		item.put("sellStatCd", result.get("sellStatCd").toString());
				
		//결과반환
		return item;
	}
}