package com.tmon.api.module.common;

import java.util.HashMap;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.melchi.common.vo.RestParameters;
import com.tmon.api.TmonConnector;

@Service
public class CommonService {
  
	static final Logger logger = LoggerFactory.getLogger(CommonService.class);
		
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;
	

	public void setDetailStatus(String targetStatusCd, String goalStatusCd, String targetApiIndexing) throws Exception {
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("targetStatusCd", targetStatusCd);
		paramMap.put("goalStatusCd", goalStatusCd);
		paramMap.put("targetApiIndexing", targetApiIndexing);
		int cnt = basicSqlSessionTemplate.update("CommonMapper.setDetailStatus", paramMap);	
		logger.info("TARGET : " + targetStatusCd + ", GOAL : " + goalStatusCd + " 로 " + cnt + " 개 변경되었습니다.");
		
	} 
	 

	
   
    @Transactional(rollbackFor = Exception.class)	
	public Map<String, Object> getCategory() throws Exception {  
		String path = "/cateservice/category"; 		  		
		Map<String, Object> result = connector.call(HttpMethod.GET, path, null);  
		return result; 
	} 
	  
    @Transactional(rollbackFor = Exception.class)	
	public Map<String, Object> getProdmarketList(Map<String, Object> paramMap) throws Exception {
		String path = "/prodmarketservice/prodmarket";
		RestParameters params = new RestParameters();
		params.setBody(paramMap);  
		Map<String, Object> result = connector.call(HttpMethod.POST, path, params);   
		return result;  
		
	}
	
	@Transactional(rollbackFor = Exception.class)	
	public Map<String, Object> getProdmarket(Map<String, Object> paramMap) throws Exception {
		String path = "/prodmarketservice/prodmarket/{prdNo}";
		RestParameters params = new RestParameters();      
		params.addPathVariableParameter("prdNo", paramMap.get("prdNo"));   
		Map<String, Object> result = connector.call(HttpMethod.GET, path, params); 
		return result;   
	}

	@Transactional(rollbackFor = Exception.class)	
	public Map<String, Object> getSellerprodcode(Map<String, Object> paramMap) throws Exception {
		String path = "/prodmarketservice/sellerprodcode/{sellerprdcd}";
		RestParameters params = new RestParameters();    
		params.addPathVariableParameter("sellerprdcd", paramMap.get("sellerprdcd"));     
		Map<String, Object> result = connector.call(HttpMethod.GET, path, params);  
		return result;   
	}
	
	@Transactional(rollbackFor = Exception.class)	
	public Map<String, Object> getStackList(Map<String, Object> paramMap) throws Exception {
		String path = "/prodmarketservice/prodmarket/stck/{prdNo}";
		RestParameters params = new RestParameters();     
		params.addPathVariableParameter("prdNo", paramMap.get("prdNo"));     
		Map<String, Object> result = connector.call(HttpMethod.GET, path, params);  
		return result;   
	}	
	
	/* Mh Code Mapping */
	public String getMhCodeMapping(String code, String codeGrp) {
    	String result = "";
    	
    	switch (codeGrp) {
			/* 과세여부 mapping
			 * MH	- 0:비과세,1:과세
			 * TMON  - 10 : 과세, 20 : 면세, 30 : 영세
			 * */    	
			case "istaxed":
				switch(code) {
					case "0":
						result = "20"; 
					break;
					case "1":
						result = "10";
					break;
				}
			break;
			/* 성인인증 mapping
			 * MH	- 0:일반,1:성인
			 * TMON -  10 : 성인 상품, 20 : 주류 상품, 90 : 일반 상품
			 * */    	
			case "isadultauth":
				switch(code) {
					case "0":
						result = "90"; 
					break;
					case "1":
						result = "10";
					break;
				}
			break;
			/* 배송방법 mapping
			 * MH	- 01:택배, 02:직배송
			 * 11st	- 01:택배, 02:우편(소포/등기), 03:직접전달(화물배달), 04:퀵서비스, 05:배송필요없음
			 */
    		case "shippingmethod":
				switch (code) {
					case "01":
						result = "01"; 
					break;
					case "02":
						result = "03";
					break;
				}
			break;
			/* 배송비 종류 mapping
			 * MH	- 01:무료 배송, 02:유료 배송, 03:유료배송(예외), 04:유료배송(조건부)
			 * 11st	- 01:무료, 02:고정비 배송, 03:상품 조건부 무료, 04:수량별 차등, 05:1개당 배송비, 07:판매자 조건부 배송비, 08:출고지 조건부 배송비, 09:11번가 통합 출고지 배송비, 10:11번가해외배송조건부배송비
			 * 03:유료배송(예외) 코드는 소스에서 예외 처리
			 */
    		case "shippingfeetype":
				switch (code) {
					case "01":
						result = "01"; 
					break;
					case "02":
						result = "02";
					break;
					case "03":
						result = "02";
					break;		
					case "04":
						result = "03";
					break;
				}
			break;
			/* 배송비 결제방식 mapping
			 * MH	- 01:무료, 02:착불/선결제, 03:착불만 가능, 04:선결제만 가능
			 * 11st	- 01:선결제가능, 02:선결제불가, 03:선결제필수
			 */
    		case "shippingfeepaytype":
				switch (code) {
					case "02":
						result = "01"; 
					break;
					case "03":
						result = "02";
					break;		
					case "04":
						result = "03";
					break;
				}
			break;
			/* 택배사코드 mapping */
    		case "shippingcompanycd":
				switch (code) {
					case "1":
						result = "00034";	//CJ대한통운
					break;
					case "2":
						result = "00099";	//동부택배
					break;
					case "3":
						result = "00002";	//로젠택배 
					break;
					case "4":
						result = "00007";	//우체국 택배
					break;
					case "6":
						result = "00011";	//한진택배
					break;
					case "8":
						result = "00001";	//KGB 택배
					break;
					case "10":
						result = "00027";	//천일택배
					break;
					case "11":
						result = "00021";	//대신택배
					break;
					case "12":
						result = "00099";	//GTX로지스
					break;
					case "13":
						result = "00022";	//일양로지스
					break;
					case "14":
						result = "00064";	//한의사랑택배
					break;
					case "15":
						result = "00060";	//CVSnet편의점택배
					break;
					case "16":
						result = "00037";	//건영택배
					break;
					case "17":
						result = "00026";	//경동택배
					break;
					case "18":
						result = "00099";	//OCS택배(위메프연동불가)
					break;
					case "19":
						result = "00099";	//TNT Express
					break;
					case "20":
						result = "00099";	//UPS택배
					break;
					case "21":
						result = "00099";	//드림택배
					break;
					case "22":
						result = "00035";	//합동택배
					break;
					case "23":
						result = "00099";	//EMS국제배송
					break;
					case "24":
						result = "00012";	//롯데택배
					break;
					case "25":
						result = "00039";	//DHL국제배송
					break;
					case "26":
						result = "00099";	//1~2주소요예상(위메프연동불가)
					break;
					case "27":
						result = "00099";	//범한판토스
					break;
					case "28":
						result = "00099";	//FedEx(페덱스)
					break;
					case "29":
						result = "00062";	//호남택배
					break;
					case "30":
						result = "00099";	//sf-express(위메프연동불가)
					break;
					case "31":
						result = "00063";	//SLX택배
					break;
					case "32":
						result = "00099";	//홈픽택배
					break;
				}
			break;
    	}

		return result;
    } 
	
	/* Qna Code Mapping */
	public String getQnaCodeMapping(String code, String codeGrp) {
    	String result = "";
    	
    	switch (codeGrp) {
			/* 처리상태
			 * 11st	- Y:답변완료, N:미답변
			 * */    	
			case "answerYn":
				switch(code) {
					case "Y":
						result = "답변완료"; 
					break;
					case "N":
						result = "미답변";
					break;
				}
			break;
			/* 구매여부
			 * 11st	- Y:구매, N:미구매
			 * */    	
			case "buyYn":
				switch(code) {
					case "Y":
						result = "구매"; 
					break;
					case "N":
						result = "미구매";
					break;
				}
			break;
			/* 전시상태
			 * 11st	- Y:전시, N:전시안함
			 * */    	
			case "dispYn":
				switch(code) {
					case "Y":
						result = "전시"; 
					break;
					case "N":
						result = "전시안함";
					break;
				}
			break;
    	}

		return result;
    } 


  
	
}
 