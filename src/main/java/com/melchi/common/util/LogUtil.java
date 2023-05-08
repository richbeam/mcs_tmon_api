package com.melchi.common.util;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.melchi.common.config.LogMapper;
import com.melchi.common.constant.BaseConst;

@Component
public class LogUtil {
	
	@Autowired  
	LogMapper logMapper; 
	
	static final Logger logger = LoggerFactory.getLogger(LogUtil.class);
	
	private String getMOrderCd(Map<String, Object> paramMap) {		
		logger.info("getMOrderCd - " + paramMap);
		if (paramMap.get("m_ordercd") == null || paramMap.get("m_ordercd").equals("")) {
			List<Map<String, Object>> list = logMapper.selectMOrderCd(paramMap);
				
			if (list.size() > 0) {
				return (String) list.get(0).get("m_ordercd");
			} else {
				return null;
			}   
			 
		} else {  
			return (String) paramMap.get("m_ordercd");
		}  		
		 
	}
	
	private String getProdStatusMessage(Object prodStatus) {
		logger.info("getProdStatusMessage - " + prodStatus);	
		String content = null;
		if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_01)) {
			content = "상품등록";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_02)) {
			content = "상품수정";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_03)) {
			content = "가격수정";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_04)) {
			content = "판매중지";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_05)) {
			content = "판매중지 해제";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_06)) {
			content = "재고수량 수정";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_07)) {
			content = "재고수량 수정 API";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_08)) {
			content = "매핑정보 추가";
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_99)) {
			content = "상품승인조회" ;
		} else if (prodStatus.equals(BaseConst.ProdStauts.STAUTS_98)) {
			content = "상품상세조회" ;
		}
			
		return content;  		
    
	}
	
	private String getOrderStatusMessage(Object orderStatus) {
		logger.info("getOrderStatusMessage - " + orderStatus);  
		String content = null;

		if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_02)) {
			content = "주문확인";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_03)) {
			content = "발주처리";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_04)) {
			content = "배송처리";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_05)) {
			content = "배송완료";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_06)) {
			content = "취소요청";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_07)) {
			content = "취소완료";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_08)) {
			content = "교환요청";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_09)) {
			content = "교환확인중";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_10)) {
			content = "교환회수완료";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_11)) {
			content = "교환배송중";		
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_12)) {
			content = "교환배송완료";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_13)) {
			content = "반품요청";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_14)) {
			content = "반품확인중";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_15)) {
			content = "반품회수완료";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_20)) {
			content = "정산완료"; 
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_21)) {
			content = "취소철회";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_22)) {
			content = "교환철회";
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_23)) {
			content = "반품철회";  
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_24)) {
			content = "취소거부";  
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_25)) {
			content = "교환거부";  
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_26)) {
			content = "반품거부";  
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_27)) {
			content = "판매거부";   
		} else if (orderStatus.equals(BaseConst.OrderStauts.STAUTS_28)) {
			content = "배송번호채번";
		}
		return content;      
	}
	
	/** 
	* <pre>
	*  상품 등록/수정 성공 로그 
		productcd - 멸치상품코드
		productno - 외부쇼핑몰상품번호
		api_url   - API_URL
		content   - 메세지 
		</pre>
	**/
	public int insertProdScheduleSuccessLog(Map<String, Object> paramMap) {
		try {
			String content = getProdStatusMessage(paramMap.get("status"));
			paramMap.put("content", content + " 성공 - " +  paramMap.get("content"));
			paramMap.put("status", 0); 
			int cnt = logMapper.insertScheduleLog(paramMap);		
			return cnt; 
		} catch (Exception e) {
			logger.error(e.getMessage() + " Parameter " + paramMap);
			return 0;
		}

	}	
	
	/** 
	* <pre>
	*  상품 등록/수정 실패 로그 
		productcd - 멸치상품코드
		api_url   - API_URL 
		content   - 메세지 
		</pre>  
	**/
	public int insertProdScheduleFailLog(Map<String, Object> paramMap) { 
		try {
			String content = getProdStatusMessage(paramMap.get("status"));
			paramMap.put("content", content + " 실패 - " + paramMap.get("content")); 
			paramMap.put("status", 1); 
			int cnt = logMapper.insertScheduleLog(paramMap);		
			return cnt; 		
		} catch (Exception e) {
			logger.error(e.getMessage() + " Parameter " + paramMap);
			return 0;
		}

	}
	
	/** 
	 * <pre>
	 *  주문 로그 입력 
		api_url   -  API_URL
		productcd - 멸치상품코드
		productno - 외부쇼핑몰상품번호 
		m_ordercd - 멸치주문번호
		ordercd   - 외부쇼핑몰주문번호
		detail_no - 외부쇼핑몰주문상세번호		
		status    - 주문상태코드 
		</pre>
	 **/
	public int insertOrderScheduleSuccessLog(Map<String, Object> paramMap) {	
		try {
			paramMap.put("m_ordercd", getMOrderCd(paramMap));		
			String content = getOrderStatusMessage(paramMap.get("status"));
			
			if (paramMap.get("content") != null) {
				content = content + " 성공 - " + paramMap.get("content");
			} else {
				content = content + " 성공";
			}
			
			paramMap.put("status", 0); 
			paramMap.put("content", content);  
			int cnt = logMapper.insertScheduleLog(paramMap);		
			return cnt; 		
		} catch (Exception e) {
			logger.error(e.getMessage() + " Parameter " + paramMap);
			return 0;
		}

	}	
	
	/** 
	 * <pre>
	 *  주문 실패 로그 입력 
		api_url   -  API_URL
		productcd - 멸치상품코드 
		productno - 외부쇼핑몰상품번호 
		m_ordercd - 멸치주문번호
		ordercd   - 외부쇼핑몰주문번호
		detail_no - 외부쇼핑몰주문상세번호		
		status    - 주문상태코드
		content   - 실패 메세지 
		</pre>  
	 **/
	public int insertOrderScheduleFailLog(Map<String, Object> paramMap) {	
		try {
			paramMap.put("m_ordercd", getMOrderCd(paramMap)); 		
			String content = getOrderStatusMessage(paramMap.get("status"));
			paramMap.put("content", content + " 실패 - " + paramMap.get("content")); 
			paramMap.put("status", 1); 
			int cnt = logMapper.insertScheduleLog(paramMap);		
			return cnt; 
		
		} catch (Exception e) {
			logger.error(e.getMessage() + " Parameter " + paramMap);
			return 0;
		}

	}						

}
