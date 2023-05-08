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
		String endDate = StringUtil.orderTimeAdd(today, -30);
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
	@Scheduled(initialDelay = 2500, fixedDelay = 63000)
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
	@Scheduled(initialDelay = 3500, fixedDelay = 65000)
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
	
	
	
	
	
	
	

















	
	//주문등록
	//@Scheduled(cron = "0 0/10 * * * *")
	public void requestOrders() throws Exception {
		logger.warn(">>requestOrders 결제완료 시작");
		//대상조회 - 접수된 주문 조회 
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("perdType", "02");   //기간타입 - 02 주문완료일
		String today = StringUtil.getTodayString("yyyyMMdd");
		paramMap.put("perdStrDts", today);
		paramMap.put("perdEndDts", today);
		//paramMap.put("perdStrDts", "20220919");
		//paramMap.put("perdEndDts", "20220921");
		List<Map<String, Object>> orders = connector.listShppDirection(paramMap);
		
		//주문등록
		if(orders != null) {
			//logger.warn(">>> requestOrders 있음: today {} / orders {}", today, orders);
			for(Map<String, Object> order : orders) {
				// 주문완료 시 배송진행상태가 배송지시로 배송데이터가 생성되나
				// 아직 결제가 완료되지 않은 주문(입금대기)은 (shppStatCd) 배송상태코드가 대기(30 대기) 상태로 조회되며 결제 후 정상으로 전환
				// 따라서 정상적으로 결재가 되지 않은건은 결재가 될때까지 기다림
				if(order.get("shppStatCd") != null && "30".equals(order.get("shppStatCd").toString().trim())) {
					logger.warn("shppStatCd 30 -- :: " +  order.get("ordNo"));
					continue;
				}
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
					//logger.warn(">>> requestOrders error {}", e.getMessage());
				}
			}
		} else {
			//logger.warn(">>> requestOrders 없음: {}", orders);
		}
		logger.warn(">>requestOrders 결제완료 종료");
	}
	
	//주문확인 이후 주문건 누락
	//@Scheduled(cron = "0 0 0/4 * * *")
	public void requestOrdersChecked() throws Exception {
		logger.warn(">>requestOrdersChecked 주문확인 누락건 시작");
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("perdType", "02");   //기간타입 - 02 주문완료일
		String today = StringUtil.getTodayString("yyyyMMdd");
		paramMap.put("perdStrDts", today);
		paramMap.put("perdEndDts", today);
		
		List<Map<String, Object>> orders = connector.listWarehouseOut(paramMap);
		
		if(orders != null) {
			//logger.warn(">>> requestOrders 있음: today {} / orders {}", today, orders);
			for(Map<String, Object> order : orders) {
				paramMap.put("orderCd", order.get("orordNo"));
				paramMap.put("detail_no", order.get("orordItemSeq"));
				paramMap.put("shppNo", order.get("shppno"));
				Map<String,Object> orderMapping = basicSqlSessionTemplate.selectOne("OrderMapper.selectMapping", paramMap);
				if(orderMapping != null) {
					continue;
				} else {
					try {
						orderService.registOrders(order);
					} catch (Exception e) {
						e.printStackTrace();
						logger.warn(">>> requestOrders error {}", e.getMessage());
					}
				}
			}
		}else {
			logger.warn(">>> requestOrdersChecked 주문확인 누락건 없음 :{}", orders);
		}
		logger.warn(">>requestOrdersChecked 주문확인 누락건 종료");
	}

	//@Scheduled(cron = "0 0 0/1 * * *")
	public void requestOrdersPast() throws Exception {
		logger.warn(">>requestOrdersPast 결제완료 시작");
		//대상조회 - 접수된 주문 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("perdType", "02");   //기간타입 - 02 주문완료일
		String today = StringUtil.getTodayString("yyyyMMdd");
		paramMap.put("perdStrDts", StringUtil.dateAdd(today, -1));
		paramMap.put("perdEndDts", StringUtil.dateAdd(today, -1));
		List<Map<String, Object>> orders = connector.listShppDirection(paramMap);

		//주문등록
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				// 주문완료 시 배송진행상태가 배송지시로 배송데이터가 생성되나
				// 아직 결제가 완료되지 않은 주문(입금대기)은 (shppStatCd) 배송상태코드가 대기(30 대기) 상태로 조회되며 결제 후 정상으로 전환
				// 따라서 정상적으로 결재가 되지 않은건은 결재가 될때까지 기다림
				if(order.get("shppStatCd") != null && "30".equals(order.get("shppStatCd").toString().trim())) continue;
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.warn(">>> requestOrdersPast 없음: {}", orders);
		}
		logger.warn(">>requestOrdersPast 결제완료 종료");
	}

	//@Scheduled(initialDelay = 1000, fixedDelay = 235000)
	public void requestOrdersOrderNo() throws Exception {
		String orderno = "D2482445630";
		logger.warn(">>orderNo로 조회 :" + orderno);
		//대상조회 - 접수된 주문 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("perdType", "02");   //기간타입 - 02 주문완료일
		paramMap.put("perdStrDts", "2022-12-20");
		paramMap.put("perdEndDts", "2022-12-20");
		paramMap.put("commType","02"); //01 : 원주문번호 , 02:배송번호
		paramMap.put("commValue",orderno);

		List<Map<String, Object>> orders = connector.listShppDirection(paramMap);

		//주문등록
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				// 주문완료 시 배송진행상태가 배송지시로 배송데이터가 생성되나
				// 아직 결제가 완료되지 않은 주문(입금대기)은 (shppStatCd) 배송상태코드가 대기(30 대기) 상태로 조회되며 결제 후 정상으로 전환
				// 따라서 정상적으로 결재가 되지 않은건은 결재가 될때까지 기다림
				if(order.get("shppStatCd") != null && "30".equals(order.get("shppStatCd").toString().trim())) continue;
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.warn(">>> requestOrdersOrderNo 없음: {}", orders);
		}
		logger.warn(">>orderno 조회 결제완료 종료");
	}
	

	
	//배송지연처리 
	/*@Scheduled(cron = "0 0 12 * * *")
	public void delayOrders() throws Exception {
		logger.warn(">>delayOrders start");

		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		String today = StringUtil.getTodayString("yyyyMMdd");
		paramMap.put("shpprsvtdt"    , today);              //배송예정일자
		paramMap.put("status"      , OrderStauts.STAUTS_03); //주문상태 주문확인
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectShippingDelay", paramMap);
		
		//배송지연처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.delayOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.warn(">>> delayOrders none: {}", orders);
		}
		logger.warn(">>delayOrders end");
	}*/
	
	

	//발송처리 누락건 - 로그 이력 기준으로 재호출
	//@Scheduled(cron = "0 0 0/1 * * *")
	public void shipOrdersRepeat() throws Exception {
		logger.warn("shipOrdersRepeat start " );
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("api_url"	   , "/api/pd/1/saveWblNo.ssg");
		paramMap.put("status"      , OrderStauts.STAUTS_04); //주문상태 배송중
		paramMap.put("apiindexing" , "Y");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetailByLog", paramMap);

		//발송처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.shipOrders(order);
				} catch (Exception e) {
					logger.warn("shipOrdersRepeat ::: {}", e.getMessage());
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.warn("shipOrdersRepeat end" );
		logger.info(">>발송처리 종료");
	}
	
	// 배송완료
	//@Scheduled(cron = "0 0/18 * * * *")
	public void compShipOrders() throws Exception {
		logger.info(">>주문조회 배송완료 시작");
		
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_05); //주문상태 배송완료
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);
		
		//배송완료처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.compShipOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.info(">>주문조회 배송완료 종료");
	}
	
	// 배송완료
	//@Scheduled(cron = "0 0/18 * * * *")
	public void compShipOrdersBySsg() throws Exception {
		logger.info(">>배송완료(ssg->멸치) 시작");
		
		//대상조회 - 접수된 주문 조회 
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("perdType", "02");   //기간타입 - 02 주문완료일
		String today = StringUtil.getTodayString("yyyyMMdd");
		paramMap.put("perdStrDts", StringUtil.dateAdd(today, -1));
		paramMap.put("perdEndDts", today);
		paramMap.put("commType", null);
		paramMap.put("commValue", null);
		List<Map<String, Object>> orders = connector.listDeliveryEnd(paramMap);
		
		//배송완료처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.compShipOrdersByTmon(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 배송완료 내역없음: {}", orders);
		}
		logger.info(">>배송완료(ssg->멸치) 종료");
	}
	
	//교환배송중 처리 
	//@Scheduled(cron = "0 0/13 * * * *")
	public void shipChangeOrders() throws Exception {
		logger.info(">>교환배송중 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_11); //주문상태 교환배소중
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);
		
		//교환배송중처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.shipChangeOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 교환배송중 대상 내역없음: {}", orders);
		}
		logger.info(">>교환배송중 시작");
	}
	
	//교환배송완료 처리 
	//@Scheduled(cron = "0 0/13 * * * *")
	public void compShipChangeOrders() throws Exception {
		logger.info(">>교환배송완료 시작");
		
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_12); //주문상태 배송완료
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);
		
		//교환배송완료 처리
		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					orderService.compShipOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 교환배송완료 내역없음: {}", orders);
		}
		logger.info(">>교환배송완료 종료");
	}
}
