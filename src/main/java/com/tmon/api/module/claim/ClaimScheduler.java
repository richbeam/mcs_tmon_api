package com.tmon.api.module.claim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.melchi.common.vo.RestParameters;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.melchi.common.constant.BaseConst.OrderStauts;
import com.melchi.common.util.StringUtil;
import com.tmon.api.TmonConnector;

import io.swagger.annotations.Api;

@Api(tags = {"4. Claim Scheduler"})   
@RestController 
@EnableScheduling   
@RequestMapping(value = "/claim/schedule")
public class ClaimScheduler {

	static final Logger logger = LoggerFactory.getLogger(ClaimScheduler.class);
	
	@Autowired
	ClaimService claimService;
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;


	//클레임건 수집 [ 취소 대상 수집, 환불 대상 수집, 교환 대상 수집, 재배송 대상 수집  ]
	//@Scheduled(cron = "0 0/8 * * * *")
	@Scheduled(initialDelay = 2500, fixedDelay = 69000)
	@RequestMapping(value="/claimSearch", method = {RequestMethod.GET})
	public void claimSearch() throws Exception {
		logger.warn(">>>>>>>>>>> claim claimSearch start");
		//취소 수집
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		Map<String, Object> Claims = new HashMap<>();
		Map<String, Object> selMap = new HashMap<>();

		//일자셋팅
		String path = "/cancellations/search";
		String today = StringUtil.getTodayString("yyyy-MM-dd HH:mm");

		String startDate = StringUtil.orderTimeAdd(today, -3070);
		String endDate = StringUtil.orderTimeAdd(today, -2);
		logger.warn("----- startDate : endDate = {} : {}",startDate,endDate);


		paramMap.put("startDate", startDate);  // endDate 기준 7일 이내
		paramMap.put("endDate", endDate);	  //현재시간 - 30 분보다 과거

		String[] searchType ={"cancellations","refunds", "exchanges", "redeliveries"}; //cancellations 취소 , refunds : 환불 (반품) , exchanges : 교환 , redeliveries : 재배송   중 1개
		for(String cliamType : searchType){
			path = "/"+cliamType + "/search";
			logger.warn(">>{} start",cliamType);
			ArrayList<String> statusL = new ArrayList<String>();
			if(cliamType == "cancellations"){
				statusL.add("C1");
				statusL.add("C3");
				 //C1, C2, C3, C4, C8, C9 중 1개
			}else {
				statusL.add("C1");
				 //C1, C2, C3, C4, C8, C9 중 1개
			}

			for(String claimStatus : statusL){
				int page = 1;
				String hasNext = "false";
				do{
					params.setPathVariableParameters(paramMap);

					params.setBody(paramMap);
					paramMap.put("claimStatus", claimStatus);
					paramMap.put("page", page);

					params.setRequestParameters(paramMap);


					Claims = connector.call(HttpMethod.GET, path, params);
					//주문등록
					if(Claims != null ){
						if(Claims.get("items") != null) {
							List<Map<String, Object>> claims =(List<Map<String, Object>>) Claims.get("items");
							if(claims.size() > 0){
								logger.warn(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> " +
                                        "{} 있음: {} today {} / orders {}", cliamType, claimStatus, today, claims.size());
							}
							for(Map<String, Object> claim : claims) {
								try {
									//클레임 등록여부 체크
									boolean isExist = true;
									List<Map<String, Object>> claimDeals =(List<Map<String, Object>>)claim.get("claimDeals");
									for(Map<String, Object> claimDeal : claimDeals){
										selMap.put("ordercd",claim.get("tmonOrderNo").toString());
										selMap.put("detail_no",claimDeal.get("tmonDealNo").toString());
										logger.warn("------클레임 등록여부 체크 : {} , {} ", claim.get("tmonOrderNo").toString(),claimDeal.get("tmonDealNo").toString());
										Map<String, Object> orderMap = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingClaimInfo", selMap);
                                        if(null != orderMap){
                                            if(!orderMap.get("claimno").toString().equals(claim.get("claimNo").toString())|| orderMap.get("claimno").toString().equals("") ||orderMap.get("claimno").toString() == null ){
                                                isExist = false;
                                                break;
                                            }
                                        }

									}

									if(isExist == false){
										if(cliamType.equals("cancellations")){
											//취소 대상 수집
											claimService.cancelRequests(claim);
										}else if(cliamType.equals("refunds")){
											//환불 대상 수집
											claimService.returnRequests(claim);
										}else if(cliamType.equals("exchanges")){
											//교환 대상 수집
											claimService.exchangesRequests(claim);
										}else if(cliamType.equals("redeliveries")){
											//재배송 대상 수집
											claimService.redeliveriesRequests(claim);
										}


									}

								} catch (Exception e) {
									e.printStackTrace();
									//logger.warn(">>> requestOrders error {}", e.getMessage());
								}
							}

						} else {
							logger.warn(">>> cancellations 없음: deliveryStatus {} ", claimStatus);
						}
						hasNext = Claims.get("hasNext").toString();
					}

					page ++;
				} while (hasNext.equals("true"));
			}

			logger.warn(">>>>>>>>>>> {} end",cliamType);
		}




		logger.warn(">>claim claimSearch end");
	}

	// 주문취소요청 [ 취소 요청/승인/거절 ]
	//@Scheduled(cron = "0 0/8 * * * *")
	@Scheduled(initialDelay = 3500, fixedDelay = 98000)
	@RequestMapping(value="/requestOrderCancel", method = {RequestMethod.GET})
	public void requestOrderCancel() throws Exception {
		logger.warn(">>>>>>>>>>>  requestOrderCancel start");

		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		String[] statusL ={"06","07"}; // 06 , 07  중 1개
		for(String claimStatus : statusL){
			paramMap.put("status"      , claimStatus); //주문상태 배송중
			List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("ClaimMapper.selectCommOrderDetail", paramMap);
			if(orders != null) {
				for(Map<String, Object> order : orders) {
					try {
						claimService.requestOrderCancel(order);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				logger.info(">>> 취소 주문조회 없음: {}", orders);
			}
			logger.warn(">>>>>>>>>>> requestOrderCancel end");
		}

	}

	// 반품완료 [ 환불요청 승인 ]
	//@Scheduled(cron = "0 0/12 * * * *")
	@Scheduled(initialDelay = 3500, fixedDelay = 68000)
	@RequestMapping(value="/confirmReturnOrders", method = {RequestMethod.GET})
	public void confirmReturnOrders() throws Exception {
		logger.warn(">>>>>>>>>>> 반품확인 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_15); //주문상태 반품완료
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("ClaimMapper.selectCommOrderDetail", paramMap);

		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					claimService.confirmReturnOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.warn(">>>>>>>>>>> 반품확인 종료");
	}



	//교환 배송중 처리 [ 교환 요청 승인  / 재배송요청 승인 ]
	//@Scheduled(cron = "0 0/11 * * * *")
	@Scheduled(initialDelay = 2500, fixedDelay = 69000)
	public void confirmExchangeOrders() throws Exception {
		logger.warn(">>>>>>>>>>> 교환배송중 시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		paramMap.put("status"      , OrderStauts.STAUTS_11); //주문상태 반품확인중
		paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
		List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("ClaimMapper.selectCommOrderDetail", paramMap);

		if(orders != null) {
			for(Map<String, Object> order : orders) {
				try {
					claimService.confirmExchangeOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 주문확인 내역없음: {}", orders);
		}
		logger.warn(">>>>>>>>>>> 교환배송중 종료");
	}




	//교환건 완료 처리 [ 티몬 처리 ]
	//@Scheduled(cron = "0 0/12 * * * *")
	@Scheduled(initialDelay = 1000, fixedDelay = 60000)
	public void setIndexingY() throws Exception {
		logger.warn(">>>>>>>>>>> '09', '10', '12', '14' 완료 처리  시작");
		//대상 조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename"    , "TMON");              //사이트명
		String[] searchType ={"09", "10", "12", "14"}; //
		for(String status : searchType){
			paramMap.put("status"      , status); //주문상태 반품확인중
			paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
			basicSqlSessionTemplate.selectList("ClaimMapper.commOrderDetailIndexingY", paramMap);
		}

		logger.warn(">>>>>>>>>>> '09', '10', '12', '14' 완료 처리  종료");
	}



}