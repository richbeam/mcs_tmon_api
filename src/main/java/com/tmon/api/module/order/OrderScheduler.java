package com.tmon.api.module.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.melchi.common.constant.BaseConst;
import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.vo.RestParameters;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.melchi.common.constant.BaseConst.OrderStauts;
import com.melchi.common.util.StringUtil;
import com.tmon.api.TmonConnector;

import io.swagger.annotations.Api;

@Api(tags = { "3. Order Scheduler" })
@RestController
@EnableScheduling
@RequestMapping(value = "/order/schedule")
public class OrderScheduler {

	static final Logger logger = LoggerFactory.getLogger(OrderScheduler.class);
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;


	//결제완료 주문건 신규 생성처리 [배송 대상 수집]
	//@Scheduled(cron = "0 0/10 * * * *")
	@Scheduled(initialDelay = 2000, fixedDelay = 160000)
	public void getOrderList() throws Exception {
		logger.warn(">>>>>>>>>>> getOrderList 결제완료 시작");
		//대상조회 - 접수된 주문 조회
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		List<Map<String, Object>> orders = null;
		//판매 날짜 설정
		String path = "/orders";

		String today = StringUtil.getTodayString("yyyy-MM-dd HH:mm");

		String startDate = StringUtil.orderTimeAdd(today, -1530);
		String endDate = StringUtil.orderTimeAdd(today, -40);
		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);


        params.setPathVariableParameters(paramMap);
        params.setRequestParameters(paramMap);
		paramMap.put("startDate", startDate);  // endDate 기준 7일 이내
		paramMap.put("endDate", endDate);	  //현재시간 - 30 분보다 과거
		paramMap.put("deliveryStatus", "D1");   //D1, D2 만 가능

		params.setBody(paramMap);

		orders = connector.callList(HttpMethod.GET, path, params);

		//주문등록
		if(orders != null) {
			//logger.warn(">>> requestOrders 있음: today {} / orders {}", today, orders);
			for(Map<String, Object> order : orders) {
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
					//logger.warn(">>> requestOrders error {}", e.getMessage());
				}
			}
		} else {
			logger.warn(">>> getOrderList 없음: {}", orders);
		}
		logger.warn(">>>>>>>>>>> getOrderList 결제완료 종료");
	}

	//주문확인 연동 처리 [ 배송 대상 확인 ] 
	//@Scheduled(cron = "0 0/13 * * * *")
	@Scheduled(initialDelay = 2500, fixedDelay = 123000)
	public void confirmOrders() throws Exception {
		logger.warn(">>>>>>>>>>> 주문확인 처리 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");                //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_03); //주문상태 주문확인
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);

		//주문 확인처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				String deliveryStatus = orderService.getOrderStatus(order.get("ordercd").toString(),order.get("detail_no").toString());
				try {
					//주문 상태값 체크
					if(deliveryStatus.equals("D1")){
						orderService.confirmOrders(order);
					}else{
						//주문확인 처리를 할수 없을 경우 해당 상태값으로 동기화
						Map<String, Object> tranDetail = new HashMap<String, Object>();
						tranDetail.put("ordercd"    , order.get("ordercd").toString());
						tranDetail.put("productcd"  , order.get("productcd"));

						if(deliveryStatus.equals("D2")){
							//주문확인
							tranDetail.put("status", OrderStauts.STAUTS_03);
							tranDetail.put("apiindexing", "Y");
						}else if(deliveryStatus.equals("D3")){
							//배송중
							tranDetail.put("status", OrderStauts.STAUTS_04);
							tranDetail.put("apiindexing", "N");
						}else if(deliveryStatus.equals("D4")){
							//배송중
							tranDetail.put("status", OrderStauts.STAUTS_04);
							tranDetail.put("apiindexing", "N");
						}else if(deliveryStatus.equals("D5")){
							//배송완료
							tranDetail.put("status", OrderStauts.STAUTS_05);
							tranDetail.put("apiindexing", "N");
						}
						basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
						basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.info(">>>>>>>>>>> 주문조회 주문확인 종료");
	}


	//배송중 - 송장등록 처리 [ 송장 등록/수정 ] 
	//@Scheduled(cron = "0 0/6 * * * ?")
	@Scheduled(initialDelay = 3500, fixedDelay = 125000)
	public void shipOrders() throws Exception {
		logger.warn(">>>>>>>>>>> 송장등록 처리 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_04); //주문상태 배송중
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetailShip", paramMap);

		//발송처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				String deliveryStatus = orderService.getOrderStatus(order.get("ordercd").toString(),order.get("detail_no").toString());
				try {
					//주문 상태값 체크
					if(deliveryStatus.equals("D2")){
						orderService.shipOrders(order);
					}else{
						//배송중 처리를 할수 없을 경우 해당 상태값으로 동기화
						Map<String, Object> tranDetail = new HashMap<String, Object>();
						tranDetail.put("ordercd"    , order.get("ordercd").toString());
						tranDetail.put("productcd"  , order.get("productcd"));

						if(deliveryStatus.equals("D1")){
							//결제완료
							tranDetail.put("status", OrderStauts.STAUTS_02);
							tranDetail.put("apiindexing", "N");
						}else if(deliveryStatus.equals("D3")){
							//배송중
							tranDetail.put("status", OrderStauts.STAUTS_04);
							tranDetail.put("apiindexing", "Y");
						}else if(deliveryStatus.equals("D4")){
							//배송중
							tranDetail.put("status", OrderStauts.STAUTS_04);
							tranDetail.put("apiindexing", "Y");
						}else if(deliveryStatus.equals("D5")){
							//배송완료
							tranDetail.put("status", OrderStauts.STAUTS_05);
							tranDetail.put("apiindexing", "N");
						}
						basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
						basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트
					}


				} catch (Exception e) {
					logger.warn("shipOrders ::: {}", e.getMessage());
					e.printStackTrace();
				}
			}
		} else {
			logger.warn(">>> 주문확인 내역없음: {}", orders);
		}
		logger.warn(">>>>>>>>>>> 송장등록 처리 종료");
	}




	//주문 상태값 동기화 처리
	@Scheduled(initialDelay = 1000, fixedDelay = 600000)
	public void setOrderStatusSync() throws Exception {
		logger.warn(">>>>>>>>>>> setOrderStatusSync 주문건 상태 동기화 시작");
		//대상조회 - 접수된 주문 조회
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		Map<String, Object> result = null;
		//판매 일지 정지
		String path = "/orders/search";

		String today = StringUtil.getTodayString("yyyy-MM-dd HH:mm");

		String startDate = StringUtil.orderTimeAdd(today, -2600);
		String endDate = StringUtil.orderTimeAdd(today, -40);
		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);

		params.setRequestParameters(paramMap);
		params.setPathVariableParameters(paramMap);
		paramMap.put("startDate", startDate);  // endDate 기준 7일 이내
		paramMap.put("endDate", endDate);	  //현재시간 - 30 분보다 과거
		String[] statusL ={"D2","D3","D4"};
		for(String deliveryStatus : statusL){
			int page = 1;
			String hasNext = "false";
			do{

				paramMap.put("deliveryStatus", deliveryStatus);
				paramMap.put("page", page);
				params.setBody(paramMap);

				result = connector.call(HttpMethod.GET, path, params);
				//주문등록
				if(result != null ){
					if(result.get("items") != null) {
						List<Map<String, Object>> orders =(List<Map<String, Object>>) result.get("items");
						logger.warn(">>> setOrderStatusSync 있음: {} today {} / orders {}", deliveryStatus, today, orders.size());
						for(Map<String, Object> order : orders) {
							try {

								orderService.syncOrderStatus(order);

							} catch (Exception e) {
								e.printStackTrace();
								//logger.warn(">>> requestOrders error {}", e.getMessage());
							}
						}

					} else {
						logger.warn(">>> setOrderStatusSync 없음: deliveryStatus {} ", deliveryStatus);
					}
					hasNext = result.get("hasNext").toString();
				}

				page ++;
			} while (hasNext.equals("true"));
		}

		logger.warn(">>>>>>>>>>> setOrderStatusSync 주문건 상태 동기화 종료");
	}




































	//취소 처리
	//@Scheduled(cron = "0 0/13 * * * *")
	public void cancelConfirmOrders() throws Exception {
		logger.warn(">>취소 처리 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");                //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_07); //주문상태 취소완료건
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);

		//주문 취소
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				String deliveryStatus = orderService.getOrderStatus(order.get("ordercd").toString(),order.get("detail_no").toString());
				try {
					//주문 취소 처리
					orderService.confirmOrders(order);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.info(">>주문조회 주문확인 종료");
	}
	
	

}
