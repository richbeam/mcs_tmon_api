package com.tmon.api.module.claim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.melchi.common.util.StringUtil;
import com.melchi.common.vo.RestParameters;
import com.tmon.api.module.order.OrderService;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.melchi.common.constant.BaseConst;
import com.melchi.common.constant.BaseConst.OrderStauts;
import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.util.LogUtil;
import com.tmon.api.TmonConnector;

@Service
public class ClaimServiceImpl implements ClaimService{
	
static final Logger logger = LoggerFactory.getLogger(ClaimServiceImpl.class);
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;

	@Autowired
	OrderService orderService;

	@Autowired
	LogUtil logUtil;


	/**
	 * 클레임 번호로  상태값 조회
	 * @param type
	 * @param claimNo
	 * @return
	 * @throws UserDefinedException
	 */
	public Map<String, Object> getClaimStatus(String type, String claimNo)throws UserDefinedException{

		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		Map<String, Object> claim = new HashMap<>();
		List<Map<String, Object>> deals = new ArrayList<>();
		try{
			String path = "/"+type+"/"+claimNo;
			params.setRequestParameters(paramMap);
			params.setBody(paramMap);

			claim = connector.call(HttpMethod.GET, path, params);

		}catch(UserDefinedException e){
			e.printStackTrace();
			logger.error("================== 클레임 단건 조회시 오류 발생 {} , {} ", type, claimNo);
			return null;
		}catch (Exception e1){
			e1.printStackTrace();
			logger.error("================== 클레임 단건 조회시 오류 발생 {} , {} ", type, claimNo);
			return null;
		}
		return claim;
	}


	/**
	 * 취소건 수집 [ 취소 대상 수집 ]
	 * @param order
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void cancelRequests(Map<String, Object> order) {

		Map<String, Object> tranDetail = new HashMap<String, Object>();
		Map<String, Object> selMap = new HashMap<String, Object>();
		Map<String, Object> logMap = new HashMap<String, Object>();

		//주문상세조회
		selMap = new HashMap<String, Object>();
		selMap.put("sitename", "TMON"); //사이트명
		selMap.put("ordercd" , order.get("tmonOrderNo").toString()); //원주문번호

		if(order.containsKey("claimDeals") && null != order.get("claimDeals")){
			List<Map<String, Object>> claimDeals = (List<Map<String, Object>>)order.get("claimDeals");
			for(Map<String, Object> claim : claimDeals){
				selMap.put("detail_no" , claim.get("tmonDealNo")); //티몬 딜 번호

				Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderDetail", selMap);

				if(null == detailRow) {
					logger.error(">>> 멸치DB 주문조회 실패 : {}", order);
					return;
				}

				//로그입력용세팅
				logMap.put("sitename" , detailRow.get("sitename"));
				logMap.put("ordercd"  , detailRow.get("ordercd"));
				logMap.put("productcd", detailRow.get("productcd"));

				tranDetail.put("sitename"    , detailRow.get("sitename")); //사이트명
				tranDetail.put("m_ordercd"   , detailRow.get("m_ordercd"));  //멸치 주문번호
				tranDetail.put("ordercd"     , detailRow.get("ordercd"));  //주문번호
				tranDetail.put("detail_no"   , detailRow.get("detail_no"));  //주문상세번호

				tranDetail.put("claimno", order.get("claimNo").toString()); 	//클레임번호
				tranDetail.put("claimtype", order.get("claimType")); 	//클레임타입
				tranDetail.put("claimstatus", order.get("claimStatus")); 	//클레임상태
				tranDetail.put("claimdealoptions", claim.get("claimDealOptions").toString()); 	//클레임 딜 옵션 정보

				if (OrderStauts.STAUTS_06.equals(detailRow.get("status")) || OrderStauts.STAUTS_07.equals(detailRow.get("status")) || OrderStauts.STAUTS_61.equals(detailRow.get("status"))) {
					//클레임 내용 작성
					tranDetail.put("claimno", order.get("claimNo").toString()); 	//클레임번호
					tranDetail.put("claimtype", order.get("claimType")); 	//클레임타입
					tranDetail.put("claimstatus", order.get("claimStatus")); 	//클레임상태
					tranDetail.put("claimdealoptions", claim.get("claimDealOptions").toString()); 	//클레임 딜 옵션 정보
					tranDetail.put("requestreason", "취소완료"); 	//상세취소사유
					basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail);
					logger.warn("취소처리 완료된 주문 skip ==" + selMap.get("ordercd"));
					return;
				}

				String orderStatus = OrderStauts.STAUTS_06;
				String claimStatus = order.get("claimStatus").toString();
				switch (claimStatus) {
					case "C1" :
						//"description":"요청"
						orderStatus = OrderStauts.STAUTS_06;
						break;
					case "C2" :
						//"description":"승인"
						orderStatus = OrderStauts.STAUTS_07;
						break;
					case "C3" :
						//"description":"완료"
						orderStatus = OrderStauts.STAUTS_07;
						break;
					case "C4" :
						//"description":"미입금취소"
						orderStatus = OrderStauts.STAUTS_07;
						break;
					case "C8" :
						//"description":"철회"
						orderStatus = OrderStauts.STAUTS_03;
						return;
						//break;
					case "C9" :
						//"description":"거절"
						orderStatus = OrderStauts.STAUTS_07;
						break;
				}



				if (OrderStauts.STAUTS_03.equals(detailRow.get("status"))) {
					//주문확인일때는 API출고전취소요청
					orderStatus = OrderStauts.STAUTS_61;
				}


				String clmRsnCd = "";
				String clmRsnNm = "";
		/*
			{
			"code":"C1",
			"description":"요청"
			},
			{
			"code":"C2",
			"description":"승인"
			},
			{
			"code":"C3",
			"description":"완료"
			},
			{
			"code":"C4",
			"description":"미입금취소"
			},
			{
			"code":"C8",
			"description":"철회"
			},
			{
			"code":"C9",
			"description":"거절"
			}
		 */
				if (claim.get("requestReason") != null) {
					clmRsnCd = claim.get("requestReason").toString();
					switch (clmRsnCd) {
						case "RCD1" :
							clmRsnNm = "단순변심";
							break;
						case "RCD2" :
							clmRsnNm = "타사이용";
							break;
						case "RCD3" :
							clmRsnNm = "배송지연";
							break;
						case "RCD4" :
							clmRsnNm = "품절 미배송";
							break;
						case "RCD5" :
							clmRsnNm = "옵션 잘못 선택";
							break;
						case "RCD6" :
							clmRsnNm = "상품정보 부족";
							break;
						case "RCD7" :
							clmRsnNm = "가격불만";
							break;
						case "RCD8" :
							clmRsnNm = "기타";
							break;
						case "RCD9" :
							clmRsnNm = "이벤트미당첨";
							break;
						case "RCDa" :
							clmRsnNm = "무통장미입금 취소";
							break;
						case "RCDb" :
							clmRsnNm = "금지거래행위";
							break;
						case "RCDc" :
							clmRsnNm = "상품을 추가하여 재주문";
							break;
						case "RCDd" :
							clmRsnNm = "상품정보 오등록";
							break;
						case "RCDe" :
							clmRsnNm = "단독구매불가";
							break;
						case "RCDf" :
							clmRsnNm = "언론보도/사회적 이슈";
							break;

					}
				}

				logMap.put("status"  , orderStatus);
				logMap.put("api_url" , "/acancellations");

				tranDetail.put("status"      , orderStatus);     //취소상태
				tranDetail.put("apiindexing" , "N");                       //멸치쇼핑 이관여부
				tranDetail.put("cancelReason", clmRsnNm); 	//상세취소사유

				//클레임 사유  내용 작성
				tranDetail.put("requestreason", clmRsnNm); 	//상세취소사유

				//logger.warn(">>cancelRequests tranDetail : {}", tranDetail);
				basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정

				//매핑 테이블 업데이트
				basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail);
				//클레임 history
				basicSqlSessionTemplate.selectOne("ClaimMapper.insertTmonMappingHistory", tranDetail);

				//변경테이블
				Map<String, Object> changeMap = new HashMap<String, Object>();
				changeMap.put("sitename"   , detailRow.get("sitename"));               //사이트명
				changeMap.put("m_ordercd"  , detailRow.get("m_ordercd"));
				changeMap.put("ordercd"    , detailRow.get("ordercd"));
				changeMap.put("productcd"  , detailRow.get("productcd"));
				changeMap.put("statuscd"   , orderStatus);
				changeMap.put("apistatuscd", 6); //6:취소요청,41 부분교환요청,42 부분반품요청,43 부분취소요청
				changeMap.put("reason"     , "취소요청 : " + clmRsnNm + " / clamNo : " + order.get("claimNo")); //취소요청 사유

				Map<String, Object> changeRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderChange", changeMap);
				//logger.warn(">>cancelRequests changeMap : {}", changeMap);
				if(null == changeRow) {
					basicSqlSessionTemplate.insert("ClaimMapper.insertCommOrderChange", changeMap);

					logMap.put("content", "[취소요청 - 신규] ");
					logMap.put("status", orderStatus);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				} else {

					changeMap.put("seq", changeRow.get("seq"));
					changeMap.put("apiindexing"   , "U");
					basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderChange", changeMap);

					logMap.put("content", "[취소요청 - 업데이트] ");
					logMap.put("status", orderStatus);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				}





				logger.warn("================== 취소요청 생성 : {} , {}", detailRow.get("m_ordercd"), order.get("claimNo"));
			}
		}
	}


	/**
	 * 주문취소요청 [ 취소 요청/승인/거절 ]
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void requestOrderCancel(Map<String, Object> order) throws UserDefinedException {
		Map<String, Object> orderMappingMap = new HashMap<>();

		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		String path = "/cancellations";

		//주문로그
		Map<String, Object> logMap = new HashMap<String, Object>();
		//처리결과
		Map<String, Object> tranDetail = new HashMap<String, Object>();
		try{
			//주문번호
			orderMappingMap.put("ordercd", order.get("ordercd"));
			//티몬 딜 번호
			orderMappingMap.put("detail_no", order.get("detail_no"));
			//List<Map<String, Object>> mppingList = basicSqlSessionTemplate.selectList("ClaimMapper.selectTmonMappingInfo", orderMappingMap);
			Map<String, Object> mapingInfo = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingInfo", orderMappingMap);


			logMap.put("ordercd" , order.get("ordercd"));
			logMap.put("m_ordercd" , order.get("m_ordercd"));
			logMap.put("detail_no", order.get("detail_no"));
			logMap.put("productcd", order.get("productcd"));
			logMap.put("productno", order.get("productno"));
			logMap.put("sitename", "TMON");


			tranDetail.put("ordercd"    , order.get("ordercd").toString());
			tranDetail.put("productcd"  , order.get("productcd"));
			tranDetail.put("detail_no"  , order.get("detail_no"));

			//대상건이 없으면 종료
			if(mapingInfo == null) {
				logger.error(">>> 멸치DB 주문맵핑정보 조회 실패 requestOrderCancel : {}", order);
				logMap.put("content", "[취소처리 실패] 멸치DB 주문맵핑정보 조회 실패" );
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
				return;
			}

			//주문상세조회
			Map<String, Object> selMap = new HashMap<String, Object>();
			selMap.put("sitename", "TMON"); //사이트명
			selMap.put("ordercd" , order.get("ordercd")); //주문번호
			selMap.put("detail_no" , order.get("detail_no")); //딜번호
			Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderDetail", selMap);

			if(null == detailRow) {
				logger.error(">>> 멸치DB 주문조회 실패 : {}", order);
				logMap.put("content", "[취소처리 실패] 멸치DB 주문맵핑정보 조회 실패" );
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
				return;
			}



			//멸치쇼핑에서 판매자나 관리자가 취소요청을 한경우 D3 이전 상태까지는 취소가 가능 [ 즉시 취소 ]
			if( mapingInfo.get("claimno").toString().equals("") || null == mapingInfo.get("claimno").toString()){
				String tmonDeliveryStatus = orderService.getOrderStatus(order.get("ordercd").toString(),order.get("detail_no").toString());

				if(tmonDeliveryStatus.equals("D1")||tmonDeliveryStatus.equals("D2") ){
					List<Map<String, Object>> optList = basicSqlSessionTemplate.selectList("ClaimMapper.selectTmonMappingList", selMap);

					//params.setRequestParameters(paramMap);
					//params.setPathVariableParameters(paramMap);
					paramMap.put("tmonOrderNo",Long.parseLong(order.get("ordercd").toString()));
					paramMap.put("deliveryNo",mapingInfo.get("deliveryno").toString());
					List<Map<String, Object>> tmonDeals = new ArrayList<>();
					Map<String, Object> tmonDeal = new HashMap<>();
					tmonDeal.put("tmonDealNo",Long.parseLong(order.get("detail_no").toString()));
					tmonDeal.put("reason","RCD4");
					tmonDeal.put("reasonDetail","품절 미배송");
					List<Map<String, Object>> tmonDealOptions = new ArrayList<>();
					for(Map<String,Object> opt : optList){
						Map<String, Object> tmonDealOption = new HashMap<>();
						tmonDealOption.put("tmonDealOptionNo",Long.parseLong(opt.get("tmondealoptionno").toString()));
						tmonDealOption.put("qty",Long.parseLong(opt.get("qty").toString()));
						tmonDealOptions.add(tmonDealOption);
					}
					tmonDeal.put("tmonDealOptions",tmonDealOptions);
					tmonDeals.add(tmonDeal);
					paramMap.put("tmonDeals",tmonDeals);
					logger.warn("====== {}",paramMap.toString());
					params.setBody(paramMap);

					path = "/cancellations";
					try{
						String result = connector.callString(HttpMethod.POST, path, params);
						if(null != result){
							logger.warn(">>> 즉시 취소처리 성공: {}, {}", order.get("ordercd"), result);
							tranDetail.put("status", OrderStauts.STAUTS_07);     //취소상태
							tranDetail.put("apiindexing", "N");                  //멸치쇼핑 이관여부
							basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
							logMap.put("content", "[즉시 취소처리 성공] " + result);
							logMap.put("status", order.get("status"));
							logUtil.insertOrderScheduleSuccessLog(logMap);
						}else{
							logger.error(">>> 즉시 취소처리 (결과값 없음): 실패  , {}", order.get("ordercd"));
							//처리결과
							tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
							tranDetail.put("apiindexing", "N");
							basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

							//비정상 결과
							logMap.put("content", "[ 즉시 취소처리 (결과값 없음) 실패]  : " + order.get("ordercd").toString());
							logMap.put("status", order.get("status"));
							logUtil.insertOrderScheduleFailLog(logMap);
						}
					}catch (UserDefinedException e){
						e.printStackTrace();
						logger.error(">>> 즉시 취소처리 : 실패  , {}, {}", order.get("ordercd"), e.getMessage().toString());
						//처리결과
						tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
						tranDetail.put("apiindexing", "N");
						basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

						//비정상 결과
						logMap.put("content", "[즉시 취소처리 실패]  : " + order.get("ordercd").toString() + "결과 : "+ e.getMessage().toString());
						logMap.put("status", order.get("status"));
						logUtil.insertOrderScheduleFailLog(logMap);
					}
				}else {
					String mStatus = "02";
					if (tmonDeliveryStatus.equals("D1")) {
						mStatus = OrderStauts.STAUTS_02;
					} else if (tmonDeliveryStatus.equals("D2")) {
						mStatus = OrderStauts.STAUTS_03;
					} else if (tmonDeliveryStatus.equals("D3")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (tmonDeliveryStatus.equals("D4")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (tmonDeliveryStatus.equals("D5")) {
						mStatus = OrderStauts.STAUTS_05;
					}

					//처리결과
					tranDetail.put("status", mStatus);     //취소상태
					tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
					basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
					basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트
				}




			// 취소 완료 처리 [ 취소요청 승인 ]
			}else if(mapingInfo.get("claimtype").toString().equals("C") && mapingInfo.get("claimstatus").toString().equals("C1")){
				Map<String, Object> tClaim = getClaimStatus("cancellations",mapingInfo.get("claimno").toString());
				if(null != tClaim){
					if(tClaim.get("claimType").toString().equals("C")) {
						if (tClaim.get("claimStatus").toString().equals("C1")) {
							params.setRequestParameters(paramMap);
							params.setBody(paramMap);

							path = "/cancellations/" + mapingInfo.get("claimno").toString() + "/approve";
							try{
								Map<String, Object> result = connector.call(HttpMethod.PUT, path, params);

								if (null == result) {

									tranDetail.put("claimstatus", "C2");
									//처리결과
									tranDetail.put("status", OrderStauts.STAUTS_07);     //취소상태
									tranDetail.put("apiindexing", "Y");                       //멸치쇼핑 이관여부
									basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
									basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

									logMap.put("content", "[취소처리 성공] - 취소요청건 승인 완");
									logMap.put("status", order.get("status"));
									logUtil.insertOrderScheduleSuccessLog(logMap);
								}else {
									logger.error(">>> 취소처리 : 실패  , {}, {}", order.get("ordercd"), result.toString());
									//처리결과
									tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
									tranDetail.put("apiindexing", "N");
									basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

									//비정상 결과
									logMap.put("content", "[ 취소처리 실패]  : " + order.get("ordercd").toString() + "결과 : "+ result.toString());
									logMap.put("status", order.get("status"));
									logUtil.insertOrderScheduleFailLog(logMap);
								}
							}catch (UserDefinedException e){
								e.printStackTrace();

								logger.error(">>> 취소처리 실패: {}", order.get("ordercd"));
								//처리결과
								tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
								tranDetail.put("apiindexing", "N");
								basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

								//비정상 결과
								logMap.put("content", "[취소처리 실패]" + e.getMessage().toString());
								logMap.put("status", order.get("status"));
								logUtil.insertOrderScheduleFailLog(logMap);
							}


						}else if(tClaim.get("claimStatus").toString().equals("C2") || tClaim.get("claimStatus").toString().equals("C3") || tClaim.get("claimStatus").toString().equals("C4")){
							//취소가 반영 됬을경우
							tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());
							//처리결과
							tranDetail.put("status", OrderStauts.STAUTS_07);     //취소상태
							tranDetail.put("apiindexing", "Y");                       //멸치쇼핑 이관여부
							basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
							basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

							logMap.put("content", "[취소처리 성공] - 취소요청건 승인 완료");
							logMap.put("status", order.get("status"));
							logUtil.insertOrderScheduleSuccessLog(logMap);

						}else if (tClaim.get("claimStatus").toString().equals("C8") || tClaim.get("claimStatus").toString().equals("C9")) {
							//철회 했을경우
							//현재 주문 상태값 조회
							String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
							String mStatus = "02";
							if (dStatus.equals("D1")) {
								mStatus = OrderStauts.STAUTS_02;
							} else if (dStatus.equals("D2")) {
								mStatus = OrderStauts.STAUTS_03;
							} else if (dStatus.equals("D3")) {
								mStatus = OrderStauts.STAUTS_04;
							} else if (dStatus.equals("D4")) {
								mStatus = OrderStauts.STAUTS_04;
							} else if (dStatus.equals("D5")) {
								mStatus = OrderStauts.STAUTS_05;
							}
							tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());

							//처리결과
							tranDetail.put("status", mStatus);     //취소상태
							tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
							basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
							basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

						}else{
							logger.error(">>> 취소처리 실패: 취소 클레임 상태 확인 , {}", order.get("ordercd"));
							//처리결과
							tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
							tranDetail.put("apiindexing", "N");
							basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

							//비정상 결과
							logMap.put("content", "[취소처리 실패] 취소 클레임 상태 확인 : " + order.get("ordercd").toString());
							logMap.put("status", order.get("status"));
							logUtil.insertOrderScheduleFailLog(logMap);

						}


					}else{
						logger.error(">>> 취소처리 실패: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
						//현재 주문 상태값 조회
						String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
						String mStatus = "02";
						if (dStatus.equals("D1")) {
							mStatus = OrderStauts.STAUTS_02;
						} else if (dStatus.equals("D2")) {
							mStatus = OrderStauts.STAUTS_03;
						} else if (dStatus.equals("D3")) {
							mStatus = OrderStauts.STAUTS_04;
						} else if (dStatus.equals("D4")) {
							mStatus = OrderStauts.STAUTS_04;
						} else if (dStatus.equals("D5")) {
							mStatus = OrderStauts.STAUTS_05;
						}
						//처리결과
						tranDetail.put("status", mStatus);
						tranDetail.put("apiindexing", "N");
						basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

						//비정상 결과
						logMap.put("content", "[취소처리 실패] - 클레임 정보가 없습니다.");
						logMap.put("status", order.get("status"));
						logUtil.insertOrderScheduleFailLog(logMap);
					}

				}else{
					logger.error(">>> 취소처리 실패: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
					//현재 주문 상태값 조회
					String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
					String mStatus = "02";
					if (dStatus.equals("D1")) {
						mStatus = OrderStauts.STAUTS_02;
					} else if (dStatus.equals("D2")) {
						mStatus = OrderStauts.STAUTS_03;
					} else if (dStatus.equals("D3")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D4")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D5")) {
						mStatus = OrderStauts.STAUTS_05;
					}
					//처리결과
					tranDetail.put("status", mStatus);
					tranDetail.put("apiindexing", "N");
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

					//비정상 결과
					logMap.put("content", "[취소처리 실패] - 클레임 정보가 없습니다.");
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleFailLog(logMap);
				}
			}else if(mapingInfo.get("claimtype").toString().equals("R") && mapingInfo.get("claimstatus").toString().equals("C1")){
				logger.warn(":::::::::::::::::::::::::::::::::::::::::::::::::::::::: 반품 회수 완료 처리 ");
			}

		}catch (UserDefinedException e){
			e.printStackTrace();
			logger.error(">>> 취소처리 실패: {}", order.get("ordercd"));
			//처리결과
			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
			tranDetail.put("apiindexing", "N");
			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			//비정상 결과
			logMap.put("content", "[취소처리 실패]" + e.getMessage().toString());
			logMap.put("status", order.get("status"));
			logUtil.insertOrderScheduleFailLog(logMap);


		}catch (Exception e1){
			e1.printStackTrace();
			logger.error("============================== 주문건 체크 필요 (클레임 취소 처리시 오류 발생 - requestOrderCancel ) , {} ", order.get("ordercd"));
		}

	}



	/**
	 * 반품건 수집 [ 환불 대상 수집 ]
	 * @param order
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void returnRequests(Map<String, Object> order) {

		Map<String, Object> tranDetail = new HashMap<String, Object>();
		Map<String, Object> selMap = new HashMap<String, Object>();
		Map<String, Object> logMap = new HashMap<String, Object>();

		//주문상세조회
		selMap = new HashMap<String, Object>();
		selMap.put("sitename", "TMON"); //사이트명
		selMap.put("ordercd" , order.get("tmonOrderNo").toString()); //원주문번호

		if(order.containsKey("claimDeals") && null != order.get("claimDeals")){
			List<Map<String, Object>> claimDeals = (List<Map<String, Object>>)order.get("claimDeals");
			for(Map<String, Object> claim : claimDeals){
				selMap.put("detail_no" , claim.get("tmonDealNo")); //티몬 딜 번호

				Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderDetail", selMap);

				if(null == detailRow) {
					logger.error(">>> 멸치DB 주문조회 실패 returnRequests : {}", order);
					return;
				}

				//로그입력용세팅
				logMap.put("sitename" , detailRow.get("sitename"));
				logMap.put("ordercd"  , detailRow.get("ordercd"));
				logMap.put("productcd", detailRow.get("productcd"));

				if (OrderStauts.STAUTS_13.equals(detailRow.get("status")) || OrderStauts.STAUTS_14.equals(detailRow.get("status")) || OrderStauts.STAUTS_15.equals(detailRow.get("status")) || OrderStauts.STAUTS_07.equals(detailRow.get("status"))) {
					logger.warn("반품요청 완료된 주문 skip ==" + selMap.get("ordercd"));
					return;
				}

				String orderStatus = OrderStauts.STAUTS_13;
				String claimStatus = order.get("claimStatus").toString();
				switch (claimStatus) {
					case "C1" :
						//"description":"요청"
						orderStatus = OrderStauts.STAUTS_13;
						break;
					case "C2" :
						//"description":"승인"
						orderStatus = OrderStauts.STAUTS_14;
						break;
					case "C3" :
						//"description":"완료"
						orderStatus = OrderStauts.STAUTS_07;
						break;
					case "C4" :
						//"description":"미입금취소"
						orderStatus = OrderStauts.STAUTS_07;
						break;
					case "C8" :
						//"description":"철회"
						orderStatus = OrderStauts.STAUTS_03;
						return;
					//break;
					case "C9" :
						//"description":"거절"
						orderStatus = OrderStauts.STAUTS_07;
						break;
				}


				String clmRsnCd = "";
				String clmRsnNm = "";
		/*
			{"C1":"요청"	},{"C2""승인"},{"C3":"완료"},{"C4""미입금취소"},{"C8":"철회"},{"C9""거절"}
		 */
				if (claim.get("requestReason") != null) {
					clmRsnCd = claim.get("requestReason").toString();
					switch (clmRsnCd) {
						case "RRD1"	:
							clmRsnNm = "단순변심/사이즈불만";
							break;
						case "RRD2"	:
							clmRsnNm = "품질불만";
							break;
						case "RRD3"	:
							clmRsnNm = "제품상이";
							break;
						case "RRD4"	:
							clmRsnNm = "제품하자";
							break;
						case "RRD5"	:
							clmRsnNm = "상품누락/오배송";
							break;
						case "RRD6"	:
							clmRsnNm = "배송지연";
							break;
						case "RRD7"	:
							clmRsnNm = "기타";
							break;
						case "RRD8"	:
							clmRsnNm = "재고품절";
							break;
						case "RRD9"	:
							clmRsnNm = "CU귀책";
							break;
						case "RRDa"	:
							clmRsnNm = "이벤트미당첨";
							break;
						case "RRDb"	:
							clmRsnNm = "배송사고(분실/파손)";
							break;
						case "RRDc"	:
							clmRsnNm = "금지거래행위";
							break;
						case "RRDd"	:
							clmRsnNm = "포장과 상품이 파손됨 (배송파손)";
							break;
						case "RRDe"	:
							clmRsnNm = "상품정보 오등록";
							break;
						case "RRDf"	:
							clmRsnNm = "언론보도/사회적 이슈";
							break;
						case "RRDg"	:
							clmRsnNm = "색상/사이즈가 기대와 다름";
							break;
						case "RRDh"	:
							clmRsnNm = "다른 상품이 배송됨 (오배송)";
							break;

					}
				}

				//요청사유상세 정보가 있을경우
				if(claim.containsKey("requestReasonDetail")){
					clmRsnNm = clmRsnNm + ":" + claim.get("requestReasonDetail").toString();
				}

				logMap.put("status"  , orderStatus);
				logMap.put("api_url" , "/acancellations");

				tranDetail.put("sitename"    , detailRow.get("sitename")); //사이트명
				tranDetail.put("m_ordercd"   , detailRow.get("m_ordercd"));  //멸치 주문번호
				tranDetail.put("ordercd"     , detailRow.get("ordercd"));  //주문번호
				tranDetail.put("detail_no"   , detailRow.get("detail_no"));  //주문상세번호
				tranDetail.put("status"      , orderStatus);     //취소상태
				tranDetail.put("apiindexing" , "N");                       //멸치쇼핑 이관여부
				tranDetail.put("cancelReason", clmRsnNm); 	//상세사유

				//클레임 내용 작성
				tranDetail.put("claimno", order.get("claimNo").toString()); 	//클레임번호
				tranDetail.put("claimtype", order.get("claimType")); 	//클레임타입
				tranDetail.put("claimstatus", order.get("claimStatus")); 	//클레임상태
				tranDetail.put("claimdealoptions", claim.get("claimDealOptions").toString()); 	//클레임 딜 옵션 정보
				tranDetail.put("requestreason", clmRsnNm); 	//상세사유

				//logger.warn(">>cancelRequests tranDetail : {}", tranDetail);
				basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정

				//매핑 테이블 업데이트
				basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail);
				//클레임 history
				basicSqlSessionTemplate.selectOne("ClaimMapper.insertTmonMappingHistory", tranDetail);

				//변경테이블
				Map<String, Object> changeMap = new HashMap<String, Object>();
				changeMap.put("sitename"   , detailRow.get("sitename"));               //사이트명
				changeMap.put("m_ordercd"  , detailRow.get("m_ordercd"));
				changeMap.put("ordercd"    , detailRow.get("ordercd"));
				changeMap.put("productcd"  , detailRow.get("productcd"));
				changeMap.put("statuscd"   , orderStatus);
				changeMap.put("apistatuscd", 7); //6:취소요청,41 부분교환요청,42 부분반품요청,43 부분취소요청
				changeMap.put("reason"     , "반품요청 : " + clmRsnNm + " / claimNo : " + order.get("claimNo")); //취소요청 사유

				Map<String, Object> changeRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderChange", changeMap);
				//logger.warn(">>cancelRequests changeMap : {}", changeMap);
				if(null == changeRow) {
					basicSqlSessionTemplate.insert("ClaimMapper.insertCommOrderChange", changeMap);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				} else {
					changeMap.put("seq", changeRow.get("seq"));
					changeMap.put("apiindexing"   , "U");
					basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderChange", changeMap);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				}

				logger.warn("================== 반품요청 생성 : {} , {}", detailRow.get("m_ordercd"), order.get("claimNo"));
			}
		}
	}


	/**
	 * 반품완료 확인 [ 환불요청 승인 ]
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void confirmReturnOrders(Map<String, Object> order) throws UserDefinedException {
		Map<String, Object> orderMappingMap = new HashMap<>();
		//주문로그
		Map<String, Object> logMap = new HashMap<String, Object>();
		logMap.put("ordercd" , order.get("ordercd"));
		logMap.put("m_ordercd" , order.get("m_ordercd"));
		logMap.put("detail_no", order.get("detail_no"));
		logMap.put("productcd", order.get("productcd"));
		logMap.put("productno", order.get("productno"));
		logMap.put("sitename", "TMON");

		//처리결과
		Map<String, Object> tranDetail = new HashMap<String, Object>();
		tranDetail.put("ordercd"    , order.get("ordercd").toString());
		tranDetail.put("productcd"  , order.get("productcd"));
		tranDetail.put("sitename"    , "TMON"); //사이트명

		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		String path = "/cancellations";


		try{
			//주문번호
			orderMappingMap.put("ordercd", order.get("ordercd"));
			//티몬 딜 번호
			orderMappingMap.put("detail_no", order.get("detail_no"));
			//List<Map<String, Object>> mppingList = basicSqlSessionTemplate.selectList("ClaimMapper.selectTmonMappingInfo", orderMappingMap);
			Map<String, Object> mapingInfo = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingInfo", orderMappingMap);

			//대상건이 없으면 종료
			if(mapingInfo == null) {
				logger.error(">>> 멸치DB 주문맵핑정보 조회 실패 confirmReturnOrders : {}", order);
				logMap.put("content", "[반품완료 실패] 멸치DB 주문맵핑정보 조회 실패" );
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
				return;
			}

			Map<String, Object> tClaim = getClaimStatus("refunds",mapingInfo.get("claimno").toString());
			if(null != tClaim){
				if(tClaim.get("claimType").toString().equals("R")) {
					if(tClaim.get("claimStatus").toString().equals("C1")) {
						params.setRequestParameters(paramMap);
						params.setBody(paramMap);
						path = "/refunds/" + mapingInfo.get("claimno").toString() + "/approve";
						String result = connector.callString(HttpMethod.PUT, path, params);
							if(null == result){
								logger.warn(">>> 반품완료 처리 성공: {}", order.get("ordercd"));
								tranDetail.put("status", OrderStauts.STAUTS_07);     //반품완료 --> 취소완료
								tranDetail.put("apiindexing", "N");                  //멸치쇼핑 이관여부
								tranDetail.put("claimstatus", "C3");
								basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
								basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트
								logMap.put("content", "[반품완료 성공] " + result);
								logMap.put("status", order.get("status"));
								logUtil.insertOrderScheduleSuccessLog(logMap);
							}else{
								logger.error(">>> 반품완료 처리 : 실패  , {}, {}", order.get("ordercd"), result);
								//처리결과
								tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
								tranDetail.put("apiindexing", "N");
								basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

								//비정상 결과
								logMap.put("content", "[반품완료 처리 실패]  : " + order.get("ordercd").toString() + "결과 : "+ result);
								logMap.put("status", order.get("status"));
								logUtil.insertOrderScheduleFailLog(logMap);
							}

					}else if(tClaim.get("claimStatus").toString().equals("C2") || tClaim.get("claimStatus").toString().equals("C3") || tClaim.get("claimStatus").toString().equals("C4")){
						//취소가 반영 됬을경우
						tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());
						//처리결과
						tranDetail.put("status", OrderStauts.STAUTS_07);     //취소상태
						tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
						basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
						basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

						logMap.put("content", "[반품완료 성공] - 환불 승인 승인 완료");
						logMap.put("status", order.get("status"));
						logUtil.insertOrderScheduleSuccessLog(logMap);

					}else if (tClaim.get("claimStatus").toString().equals("C8") || tClaim.get("claimStatus").toString().equals("C9")) {
						//철회 했을경우
						//현재 주문 상태값 조회
						String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
						String mStatus = "02";
						if (dStatus.equals("D1")) {
							mStatus = OrderStauts.STAUTS_02;
						} else if (dStatus.equals("D2")) {
							mStatus = OrderStauts.STAUTS_03;
						} else if (dStatus.equals("D3")) {
							mStatus = OrderStauts.STAUTS_04;
						} else if (dStatus.equals("D4")) {
							mStatus = OrderStauts.STAUTS_04;
						} else if (dStatus.equals("D5")) {
							mStatus = OrderStauts.STAUTS_05;
						}
						tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());

						//처리결과
						tranDetail.put("status", mStatus);     //취소상태
						tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
						basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
						basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

					}else{
						logger.error(">>> 반품완료 실패: 환불 클레임 상태 확인 , {}", order.get("ordercd"));
						//처리결과
						tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
						tranDetail.put("apiindexing", "N");
						basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

						//비정상 결과
						logMap.put("content", "[반품완료 실패] 환불 클레임 상태 확인 : " + order.get("ordercd").toString());
						logMap.put("status", order.get("status"));
						logUtil.insertOrderScheduleFailLog(logMap);

					}
				}else{

					logger.error(">>> 반품완료 실패: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
					//현재 주문 상태값 조회
					String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
					String mStatus = "02";
					if (dStatus.equals("D1")) {
						mStatus = OrderStauts.STAUTS_02;
					} else if (dStatus.equals("D2")) {
						mStatus = OrderStauts.STAUTS_03;
					} else if (dStatus.equals("D3")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D4")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D5")) {
						mStatus = OrderStauts.STAUTS_05;
					}
					//처리결과
					tranDetail.put("status", mStatus);
					tranDetail.put("apiindexing", "N");
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

					//비정상 결과
					logMap.put("content", "[반품완료 실패] - 클레임 정보가 없습니다.");
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleFailLog(logMap);
				}

			}else {
				logger.error(">>> 반품완료 실패: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
				//현재 주문 상태값 조회
				String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
				String mStatus = "02";
				if (dStatus.equals("D1")) {
					mStatus = OrderStauts.STAUTS_02;
				} else if (dStatus.equals("D2")) {
					mStatus = OrderStauts.STAUTS_03;
				} else if (dStatus.equals("D3")) {
					mStatus = OrderStauts.STAUTS_04;
				} else if (dStatus.equals("D4")) {
					mStatus = OrderStauts.STAUTS_04;
				} else if (dStatus.equals("D5")) {
					mStatus = OrderStauts.STAUTS_05;
				}
				//처리결과
				tranDetail.put("status", mStatus);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

				//비정상 결과
				logMap.put("content", "[반품완료 실패] - 클레임 정보가 없습니다.");
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
			}


		}catch (UserDefinedException e){
			e.printStackTrace();
			logger.error(">>> 반품완료 연계 실패: {}", order.get("ordercd"));
			//처리결과
			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
			tranDetail.put("apiindexing", "N");
			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			//비정상 결과
			logMap.put("content", "[반품완료 처리실패]"+ e.getMessage().toString());
			logMap.put("status", order.get("status"));
			logUtil.insertOrderScheduleFailLog(logMap);
		}catch (Exception e1){
			e1.printStackTrace();
			logger.error("============================== 주문건 체크 필요 (클레임 반품완료 처리시 오류 발생 - requestOrderCancel ) , {} ", order.get("ordercd"));
		}

	}



	/**
	 * 교환건 수집 [ 교환 대상 수집 ]
	 * @param order
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void exchangesRequests(Map<String, Object> order) {

		Map<String, Object> tranDetail = new HashMap<String, Object>();
		Map<String, Object> selMap = new HashMap<String, Object>();
		Map<String, Object> logMap = new HashMap<String, Object>();

		//주문상세조회
		selMap = new HashMap<String, Object>();
		selMap.put("sitename", "TMON"); //사이트명
		selMap.put("ordercd" , order.get("tmonOrderNo").toString()); //원주문번호

		if(order.containsKey("claimDeals") && null != order.get("claimDeals")){
			List<Map<String, Object>> claimDeals = (List<Map<String, Object>>)order.get("claimDeals");
			for(Map<String, Object> claim : claimDeals){
				selMap.put("detail_no" , claim.get("tmonDealNo")); //티몬 딜 번호환

				Map<String, Object> mapingInfo = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingInfo", selMap);

				Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderDetail", selMap);

				if(null == detailRow || null == mapingInfo) {
					logger.error(">>> 멸치DB 주문조회 실패 exchangesRequests : {}", order);
					return;
				}

				//로그입력용세팅
				logMap.put("sitename" , detailRow.get("sitename"));
				logMap.put("ordercd"  , detailRow.get("ordercd"));
				logMap.put("productcd", detailRow.get("productcd"));

				if (OrderStauts.STAUTS_08.equals(detailRow.get("status")) || OrderStauts.STAUTS_09.equals(detailRow.get("status")) || OrderStauts.STAUTS_10.equals(detailRow.get("status")) || OrderStauts.STAUTS_11.equals(detailRow.get("status")) || OrderStauts.STAUTS_12.equals(detailRow.get("status"))) {
					logger.warn("교환요청 완료된 주문 skip ==" + selMap.get("ordercd"));
					return;
				}

				String orderStatus = OrderStauts.STAUTS_08;
				String claimStatus = order.get("claimStatus").toString();
				switch (claimStatus) {
					case "C1" :
						//"description":"요청"
						orderStatus = OrderStauts.STAUTS_08;
						break;
					case "C2" :
						//"description":"승인"
						orderStatus = OrderStauts.STAUTS_11;
						break;
					case "C3" :
						//"description":"완료"
						orderStatus = OrderStauts.STAUTS_12;
						break;
					case "C4" :
						//"description":"미입금취소"
						orderStatus = OrderStauts.STAUTS_05;
						break;
					case "C8" :
						//"description":"철회"
						orderStatus = OrderStauts.STAUTS_05;
						return;
					//break;
					case "C9" :
						//"description":"거절"
						orderStatus = OrderStauts.STAUTS_05;
						break;
				}


				String clmRsnCd = "";
				String clmRsnNm = "";
		/*
			{"C1":"요청"	},{"C2""승인"},{"C3":"완료"},{"C4""미입금취소"},{"C8":"철회"},{"C9""거절"}
		 */
				if (claim.get("requestReason") != null) {
					clmRsnCd = claim.get("requestReason").toString();
					switch (clmRsnCd) {
						case "RXD1" :
							clmRsnNm = "사이즈/색상 교환";
							break;
						case "RXD2" :
							clmRsnNm = "배송사고(파손)";
							break;
						case "RXD3" :
							clmRsnNm = "제품하자";
							break;
						case "RXD4" :
							clmRsnNm = "오배송";
							break;
						case "RXD5" :
							clmRsnNm = "기타";
							break;
						case "RXD6" :
							clmRsnNm = "배송 도중 상품이 분실됨 (배송분실)";
							break;
					}
				}

				//요청사유상세 정보가 있을경우
				if(claim.containsKey("requestReasonDetail")){
					clmRsnNm = clmRsnNm + ":" + claim.get("requestReasonDetail").toString();
				}

				logMap.put("status"  , orderStatus);
				logMap.put("api_url" , "/acancellations");

				tranDetail.put("sitename"    , detailRow.get("sitename")); //사이트명
				tranDetail.put("m_ordercd"   , detailRow.get("m_ordercd"));  //멸치 주문번호
				tranDetail.put("ordercd"     , detailRow.get("ordercd"));  //주문번호
				tranDetail.put("detail_no"   , detailRow.get("detail_no"));  //주문상세번호
				tranDetail.put("status"      , orderStatus);     //취소상태
				tranDetail.put("apiindexing" , "N");                       //멸치쇼핑 이관여부
				tranDetail.put("cancelReason", clmRsnNm); 	//상세사유

				//클레임 내용 작성
				tranDetail.put("claimno", order.get("claimNo").toString()); 	//클레임번호
				tranDetail.put("claimtype", order.get("claimType")); 	//클레임타입
				tranDetail.put("claimstatus", order.get("claimStatus")); 	//클레임상태
				tranDetail.put("claimdealoptions", claim.get("claimDealOptions").toString()); 	//클레임 딜 옵션 정보
				tranDetail.put("requestreason", clmRsnNm); 	//상세사유

				//logger.warn(">>cancelRequests tranDetail : {}", tranDetail);
				basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정

				//매핑 테이블 업데이트
				basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail);
				//클레임 history
				basicSqlSessionTemplate.selectOne("ClaimMapper.insertTmonMappingHistory", tranDetail);

				//변경테이블
				Map<String, Object> changeMap = new HashMap<String, Object>();
				changeMap.put("sitename"   , detailRow.get("sitename"));               //사이트명
				changeMap.put("m_ordercd"  , detailRow.get("m_ordercd"));
				changeMap.put("ordercd"    , detailRow.get("ordercd"));
				changeMap.put("productcd"  , detailRow.get("productcd"));
				changeMap.put("statuscd"   , orderStatus);
				changeMap.put("apistatuscd", 7); //6:취소요청,41 부분교환요청,42 부분반품요청,43 부분취소요청
				//changeMap.put("reason"     , "교환요청 : " + clmRsnNm + " / " + order.get("claimNo") + " / " + detailRow.get("m_ordercd") + " / " + detailRow.get("ordercd") + " / " + detailRow.get("productcd")); //취소요청 사유
				changeMap.put("reason"     , "교환요청 : " + clmRsnNm + " / claimNo : " + order.get("claimNo")); //취소요청 사유

				Map<String, Object> changeRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderChange", changeMap);
				//logger.warn(">>cancelRequests changeMap : {}", changeMap);
				if(null == changeRow) {
					basicSqlSessionTemplate.insert("ClaimMapper.insertCommOrderChange", changeMap);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				} else {
					changeMap.put("seq", changeRow.get("seq"));
					changeMap.put("apiindexing"   , "U");
					basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderChange", changeMap);
					logUtil.insertOrderScheduleSuccessLog(logMap);
				}

				logger.warn("================== 교환요청 생성 : {} , {}", detailRow.get("m_ordercd"), order.get("claimNo"));
			}
		}
	}



	/**
	 * 재배송건 수집 [ 재배송 대상 수집 ]
	 * @param order
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void redeliveriesRequests(Map<String, Object> order) {

		Map<String, Object> tranDetail = new HashMap<String, Object>();
		Map<String, Object> selMap = new HashMap<String, Object>();
		Map<String, Object> logMap = new HashMap<String, Object>();

		//주문상세조회
		selMap = new HashMap<String, Object>();
		selMap.put("sitename", "TMON"); //사이트명
		selMap.put("ordercd" , order.get("tmonOrderNo").toString()); //원주문번호
		try{
			if(order.containsKey("claimDeals") && null != order.get("claimDeals")){
				List<Map<String, Object>> claimDeals = (List<Map<String, Object>>)order.get("claimDeals");
				for(Map<String, Object> claim : claimDeals){
					selMap.put("detail_no" , claim.get("tmonDealNo")); //티몬 딜 번호환

					Map<String, Object> mapingInfo = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingInfo", selMap);

					Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderDetail", selMap);

					if(null == detailRow || null == mapingInfo) {
						logger.error(">>> 멸치DB 주문조회 실패 redeliveriesRequests : {}", order);
						return;
					}

					//로그입력용세팅
					logMap.put("sitename" , detailRow.get("sitename"));
					logMap.put("ordercd"  , detailRow.get("ordercd"));
					logMap.put("productcd", detailRow.get("productcd"));

					if (OrderStauts.STAUTS_10.equals(detailRow.get("status")) || OrderStauts.STAUTS_11.equals(detailRow.get("status")) || OrderStauts.STAUTS_12.equals(detailRow.get("status"))) {
						logger.warn("재배송 완료된 주문 skip ==" + selMap.get("ordercd"));
						return;
					}

					String orderStatus = OrderStauts.STAUTS_10;
					String claimStatus = claim.get("claimStatus").toString();
					switch (claimStatus) {
						case "C1" :
							//"description":"요청"
							orderStatus = OrderStauts.STAUTS_08;
							break;
						case "C2" :
							//"description":"승인"
							orderStatus = OrderStauts.STAUTS_11;
							break;
						case "C3" :
							//"description":"완료"
							orderStatus = OrderStauts.STAUTS_12;
							break;
						case "C4" :
							//"description":"미입금취소"
							orderStatus = OrderStauts.STAUTS_05;
							break;
						case "C8" :
							//"description":"철회"
							orderStatus = OrderStauts.STAUTS_05;
							return;
						//break;
						case "C9" :
							//"description":"거절"
							orderStatus = OrderStauts.STAUTS_05;
							break;
					}


					String clmRsnCd = "";
					String clmRsnNm = "";
		/*
			{"C1":"요청"	},{"C2""승인"},{"C3":"완료"},{"C4""미입금취소"},{"C8":"철회"},{"C9""거절"}
		 */
					if (claim.get("requestReason") != null) {
						clmRsnCd = claim.get("requestReason").toString();
						switch (clmRsnCd) {
							case "RDD1" :
								clmRsnNm = "주문상품 중 일부가 배송되지 않음";
								break;
							case "RDD2" :
								clmRsnNm = "배송 도중 상품이 분실됨 (배송분실)";
								break;
							case "RDD3" :
								clmRsnNm = "기타";
								break;
							case "RDD4" :
								clmRsnNm = "품절 미배송";
								break;
							case "RCD5" :
								clmRsnNm = "포장과 상품이 파손됨 (배송파손)";
								break;

						}
					}

					//요청사유상세 정보가 있을경우
					if(claim.containsKey("requestReasonDetail")){
						clmRsnNm = clmRsnNm + ":" + claim.get("requestReasonDetail").toString();
					}

					logMap.put("status"  , orderStatus);
					logMap.put("api_url" , "/acancellations");

					tranDetail.put("sitename"    , detailRow.get("sitename")); //사이트명
					tranDetail.put("m_ordercd"   , detailRow.get("m_ordercd"));  //멸치 주문번호
					tranDetail.put("ordercd"     , detailRow.get("ordercd"));  //주문번호
					tranDetail.put("detail_no"   , detailRow.get("detail_no"));  //주문상세번호
					tranDetail.put("status"      , orderStatus);     //취소상태
					tranDetail.put("apiindexing" , "N");                       //멸치쇼핑 이관여부
					tranDetail.put("cancelReason", clmRsnNm); 	//상세사유

					//클레임 내용 작성
					tranDetail.put("claimno", order.get("claimNo").toString()); 	//클레임번호
					tranDetail.put("claimtype", order.get("claimType")); 	//클레임타입
					tranDetail.put("claimstatus", order.get("claimStatus")); 	//클레임상태
					tranDetail.put("claimdealoptions", claim.get("claimDealOptions").toString()); 	//클레임 딜 옵션 정보
					tranDetail.put("requestreason", clmRsnNm); 	//상세사유

					//logger.warn(">>cancelRequests tranDetail : {}", tranDetail);
					basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정

					//매핑 테이블 업데이트
					basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail);
					//클레임 history
					basicSqlSessionTemplate.selectOne("ClaimMapper.insertTmonMappingHistory", tranDetail);

					//변경테이블
					Map<String, Object> changeMap = new HashMap<String, Object>();
					changeMap.put("sitename"   , detailRow.get("sitename"));               //사이트명
					changeMap.put("m_ordercd"  , detailRow.get("m_ordercd"));
					changeMap.put("ordercd"    , detailRow.get("ordercd"));
					changeMap.put("productcd"  , detailRow.get("productcd"));
					changeMap.put("statuscd"   , orderStatus);
					changeMap.put("apistatuscd", 9); //6:취소요청,41 부분교환요청,42 부분반품요청,43 부분취소요청
					//changeMap.put("reason"     , "재배송 요청 (회수 불가) : " + clmRsnNm + " / " + order.get("claimNo") + " / " + detailRow.get("m_ordercd") + " / " + detailRow.get("ordercd") + " / " + detailRow.get("productcd")); //취소요청 사유
					changeMap.put("reason"     , "재배송 요청 (회수 불가) : " + clmRsnNm + " / claimNo : " + order.get("claimNo")); //취소요청 사유

					Map<String, Object> changeRow = basicSqlSessionTemplate.selectOne("ClaimMapper.selectCommOrderChange", changeMap);
					//logger.warn(">>cancelRequests changeMap : {}", changeMap);
					if(null == changeRow) {
						basicSqlSessionTemplate.insert("ClaimMapper.insertCommOrderChange", changeMap);
						logUtil.insertOrderScheduleSuccessLog(logMap);
					} else {
						changeMap.put("seq", changeRow.get("seq"));
						changeMap.put("apiindexing"   , "U");
						basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderChange", changeMap);
						logUtil.insertOrderScheduleSuccessLog(logMap);
					}

					logger.warn("================== 재배송 생성 : {} , {}", detailRow.get("m_ordercd"), order.get("claimNo"));
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}

	}





	/**
	 * 교환 배송중 처리 [ 교환 요청 승인  / 재배송요청 승인 ]
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void confirmExchangeOrders(Map<String, Object> order) throws UserDefinedException {
		Map<String, Object> orderMappingMap = new HashMap<>();
		//주문로그
		Map<String, Object> logMap = new HashMap<String, Object>();
		logMap.put("ordercd" , order.get("ordercd"));
		logMap.put("m_ordercd" , order.get("m_ordercd"));
		logMap.put("detail_no", order.get("detail_no"));
		logMap.put("productcd", order.get("productcd"));
		logMap.put("productno", order.get("productno"));
		logMap.put("sitename", "TMON");

		//처리결과
		Map<String, Object> tranDetail = new HashMap<String, Object>();
		tranDetail.put("ordercd"    , order.get("ordercd").toString());
		tranDetail.put("productcd"  , order.get("productcd"));
		tranDetail.put("sitename"    , "TMON"); //사이트명

		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		String path = "/cancellations";


		try{
			//주문번호
			orderMappingMap.put("ordercd", order.get("ordercd"));
			//티몬 딜 번호
			orderMappingMap.put("detail_no", order.get("detail_no"));
			//List<Map<String, Object>> mppingList = basicSqlSessionTemplate.selectList("ClaimMapper.selectTmonMappingInfo", orderMappingMap);
			Map<String, Object> mapingInfo = basicSqlSessionTemplate.selectOne("ClaimMapper.selectTmonMappingInfo", orderMappingMap);

			//대상건이 없으면 종료
			if(mapingInfo == null) {
				logger.error(">>> 멸치DB 주문맵핑정보 조회 실패 confirmExchangeOrders : {}", order);
				logMap.put("content", "[교환배송중  실패] 멸치DB 주문맵핑정보 조회 실패" );
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
				return;
			}

			if(!mapingInfo.get("claimno").toString().equals("") || null != mapingInfo.get("claimno").toString()){
				String cType = mapingInfo.get("claimtype").toString();

				//교환 배송
				if(cType.equals("X")){
					Map<String, Object> tClaim = getClaimStatus("exchanges",mapingInfo.get("claimno").toString());

								if(null != tClaim){
											if(tClaim.get("claimType").toString().equals("X")) {
																		if(tClaim.get("claimStatus").toString().equals("C1")) {
																			//params.setRequestParameters(paramMap);
																			//params.setPathVariableParameters(paramMap);
																			//택배회사 코드 조회
																			Map<String, Object> deliveryCompany = new HashMap<>();
                                                                            Map<String, Object> redeliveryInvoice = new HashMap<>();
                                                                            //택배일경우
                                                                            if(order.get("shippingmethod").toString().equals("01")){
                                                                                if(order.get("delicomcd") != null) {
                                                                                    //code:"10099",description:"자체배송"
                                                                                    tranDetail.put("mdeliverycorp", Long.parseLong(order.get("delicomcd").toString()));
                                                                                    deliveryCompany = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonDelivery", tranDetail);
                                                                                }
                                                                                redeliveryInvoice.put("deliveryCorp",Long.parseLong(deliveryCompany.get("deliverycorpcd").toString()));
                                                                                redeliveryInvoice.put("invoiceNo",order.get("shippingno").toString());
                                                                            }else{
                                                                                //직배송일경우

                                                                                redeliveryInvoice.put("deliveryCorp",10099);
                                                                                redeliveryInvoice.put("invoiceNo",10099);
                                                                                //발송예정일 포맷 yyyy-MM-dd자체배송은 송장등록시 송장번호가 아닌 발송예정일을 입력합니다.
                                                                                String today = StringUtil.getTodayString("yyyy-MM-dd");
                                                                                redeliveryInvoice.put("deliveryScheduledDate",today);

                                                                              /*  invoice.put("deliveryCorp",10099);
                                                                                order.put("shippingno",10099);
                                                                                tranDetail.put("invoiceNo","10099");
                                                                                //발송예정일 포맷 yyyy-MM-dd자체배송은 송장등록시 송장번호가 아닌 발송예정일을 입력합니다.
                                                                                String today = StringUtil.getTodayString("yyyy-MM-dd");
                                                                                paramMap.put("deliveryScheduledDate",today);*/
                                                                            }

																			paramMap.put("redeliveryInvoice",redeliveryInvoice);
																			params.setBody(paramMap);
																			path = "/exchanges/" + mapingInfo.get("claimno").toString() + "/approve";
																			String result = connector.callString(HttpMethod.PUT, path, params);
																			if(null == result){
																				logger.warn(">>> 교환배송중 처리 성공: {} ", order.get("ordercd"));
																				tranDetail.put("status", OrderStauts.STAUTS_11);     //교환배소중
																				tranDetail.put("apiindexing", "N");                  //멸치쇼핑 이관여부
																				basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																				logMap.put("content", "[교환배송중 성공] " + result);
																				logMap.put("status", order.get("status"));
																				logUtil.insertOrderScheduleSuccessLog(logMap);
																			}else{
																				logger.error(">>> 교환배송중 처리 : 실패  , {}, {}", order.get("ordercd"), result.toString());
																				//처리결과
																				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
																				tranDetail.put("apiindexing", "N");
																				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

																				//비정상 결과
																				logMap.put("content", "[교환배송중 처리 실패]  : " + order.get("ordercd").toString() + "결과 : "+ result.toString());
																				logMap.put("status", order.get("status"));
																				logUtil.insertOrderScheduleFailLog(logMap);
																			}

																		}else if(tClaim.get("claimStatus").toString().equals("C2") || tClaim.get("claimStatus").toString().equals("C3") || tClaim.get("claimStatus").toString().equals("C4")){
																			//취소가 반영 됬을경우
																			tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());
																			//처리결과
																			tranDetail.put("status", OrderStauts.STAUTS_11);     //교환 배송중
																			tranDetail.put("apiindexing", "Y");                  //멸치쇼핑 이관여부
																			basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																			basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

																			logMap.put("content", "[교환배송중 성공] - 교환 요청건 승인 완료");
																			logMap.put("status", order.get("status"));
																			logUtil.insertOrderScheduleSuccessLog(logMap);

																		}else if (tClaim.get("claimStatus").toString().equals("C8") || tClaim.get("claimStatus").toString().equals("C9")) {
																			//철회 했을경우
																			//현재 주문 상태값 조회
																			String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
																			String mStatus = "02";
																			if (dStatus.equals("D1")) {
																				mStatus = OrderStauts.STAUTS_02;
																			} else if (dStatus.equals("D2")) {
																				mStatus = OrderStauts.STAUTS_03;
																			} else if (dStatus.equals("D3")) {
																				mStatus = OrderStauts.STAUTS_04;
																			} else if (dStatus.equals("D4")) {
																				mStatus = OrderStauts.STAUTS_04;
																			} else if (dStatus.equals("D5")) {
																				mStatus = OrderStauts.STAUTS_05;
																			}
																			tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());

																			//처리결과
																			tranDetail.put("status", mStatus);     //취소상태
																			tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
																			basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																			basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

																		}else{
																			logger.error(">>> 교환배송중 실패: 교환 클레임 상태 확인 , {}", order.get("ordercd"));
																			//처리결과
																			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
																			tranDetail.put("apiindexing", "N");
																			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

																			//비정상 결과
																			logMap.put("content", "[교환배송중 실패] 교환 클레임 상태 확인 : " + order.get("ordercd").toString());
																			logMap.put("status", order.get("status"));
																			logUtil.insertOrderScheduleFailLog(logMap);

																		}
											}else{

												logger.error(">>> 교환배송중 실패: claimType 정보가 없습니다. , {}", order.get("ordercd"));
												//현재 주문 상태값 조회
												String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
												String mStatus = "02";
												if (dStatus.equals("D1")) {
													mStatus = OrderStauts.STAUTS_02;
												} else if (dStatus.equals("D2")) {
													mStatus = OrderStauts.STAUTS_03;
												} else if (dStatus.equals("D3")) {
													mStatus = OrderStauts.STAUTS_04;
												} else if (dStatus.equals("D4")) {
													mStatus = OrderStauts.STAUTS_04;
												} else if (dStatus.equals("D5")) {
													mStatus = OrderStauts.STAUTS_05;
												}
												//처리결과
												tranDetail.put("status", mStatus);
												tranDetail.put("apiindexing", "N");
												basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

												//비정상 결과
												logMap.put("content", "[교환배송중 실패] - claimType 정보가 없습니다.");
												logMap.put("status", order.get("status"));
												logUtil.insertOrderScheduleFailLog(logMap);
											}

								}else {
									logger.error(">>> 교환배송중 실패1: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
									//현재 주문 상태값 조회
									String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
									String mStatus = "11";
									if (dStatus.equals("D1")) {
										mStatus = OrderStauts.STAUTS_02;
									} else if (dStatus.equals("D2")) {
										mStatus = OrderStauts.STAUTS_03;
									} else if (dStatus.equals("D3")) {
										mStatus = OrderStauts.STAUTS_04;
									} else if (dStatus.equals("D4")) {
										mStatus = OrderStauts.STAUTS_04;
									} else if (dStatus.equals("D5")) {
										mStatus = OrderStauts.STAUTS_05;
									}
									//처리결과
									tranDetail.put("status", mStatus);
									tranDetail.put("apiindexing", "U");
									basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

									//비정상 결과
									logMap.put("content", "[교환배송중 실패1] - 클레임 정보가 없습니다.");
									logMap.put("status", order.get("status"));
									logUtil.insertOrderScheduleFailLog(logMap);
								}
					//재배송
					}else if(cType.equals("D")){

						Map<String, Object> tClaim = getClaimStatus("redeliveries",mapingInfo.get("claimno").toString());

						if(null != tClaim){
										if(tClaim.get("claimType").toString().equals("D")) {
																	if(tClaim.get("claimStatus").toString().equals("C1")) {
																		params.setRequestParameters(paramMap);
																		//택배회사 코드 조회
																		Map<String, Object> deliveryCompany = new HashMap<>();
																		if(order.get("delicomcd") != null) {
																			//code:"10099",description:"자체배송"
																			tranDetail.put("mdeliverycorp", order.get("delicomcd").toString());
																			deliveryCompany = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonDelivery", tranDetail);
																		}
																		Map<String, Object> redeliveryInvoice = new HashMap<>();
																		redeliveryInvoice.put("deliveryCorp",Long.parseLong(deliveryCompany.get("deliverycorpcd").toString()));
																		redeliveryInvoice.put("invoiceNo",order.get("shippingno").toString());
																		paramMap.put("redeliveryInvoice",redeliveryInvoice);

																		params.setBody(paramMap);
																		path = "/refunds/" + mapingInfo.get("claimno").toString() + "/approve";
																		Map<String, Object> result = connector.call(HttpMethod.PUT, path, params);
																		if(null != result){
																			logger.warn(">>> 재배송 송장등록 처리 성공: {}, {}", order.get("ordercd"), result);
																			tranDetail.put("status", OrderStauts.STAUTS_14);     //반품 확인중
																			tranDetail.put("apiindexing", "N");                  //멸치쇼핑 이관여부
																			basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																			logMap.put("content", "[재배송 송장등록 성공] " + result);
																			logMap.put("status", order.get("status"));
																			logUtil.insertOrderScheduleSuccessLog(logMap);
																		}else{
																			logger.error(">>> 재배송 송장등록 처리 : 실패  , {}, {}", order.get("ordercd"), result.toString());
																			//처리결과
																			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
																			tranDetail.put("apiindexing", "N");
																			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

																			//비정상 결과
																			logMap.put("content", "[재배송 송장등록 처리 실패]  : " + order.get("ordercd").toString() + "결과 : "+ result.toString());
																			logMap.put("status", order.get("status"));
																			logUtil.insertOrderScheduleFailLog(logMap);
																		}

																	}else if(tClaim.get("claimStatus").toString().equals("C2") || tClaim.get("claimStatus").toString().equals("C3") || tClaim.get("claimStatus").toString().equals("C4")){
																		//취소가 반영 됬을경우
																		tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());
																		//처리결과
																		tranDetail.put("status", OrderStauts.STAUTS_11);     //교환배송중
																		tranDetail.put("apiindexing", "Y");                       //멸치쇼핑 이관여부
																		basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																		basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

																		logMap.put("content", "[재배송 송장등록 성공] - 환불 요청건 승인 완료");
																		logMap.put("status", order.get("status"));
																		logUtil.insertOrderScheduleSuccessLog(logMap);

																	}else if (tClaim.get("claimStatus").toString().equals("C8") || tClaim.get("claimStatus").toString().equals("C9")) {
																		//철회 했을경우
																		//현재 주문 상태값 조회
																		String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
																		String mStatus = "02";
																		if (dStatus.equals("D1")) {
																			mStatus = OrderStauts.STAUTS_02;
																		} else if (dStatus.equals("D2")) {
																			mStatus = OrderStauts.STAUTS_03;
																		} else if (dStatus.equals("D3")) {
																			mStatus = OrderStauts.STAUTS_04;
																		} else if (dStatus.equals("D4")) {
																			mStatus = OrderStauts.STAUTS_04;
																		} else if (dStatus.equals("D5")) {
																			mStatus = OrderStauts.STAUTS_05;
																		}
																		tranDetail.put("claimstatus", tClaim.get("claimStatus").toString());

																		//처리결과
																		tranDetail.put("status", mStatus);     //취소상태
																		tranDetail.put("apiindexing", "N");                       //멸치쇼핑 이관여부
																		basicSqlSessionTemplate.update("ClaimMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 수정
																		basicSqlSessionTemplate.update("ClaimMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트

																	}else{
																		logger.error(">>> 재배송 송장등록 실패:  클레임 상태 확인 , {}", order.get("ordercd"));
																		//처리결과
																		tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
																		tranDetail.put("apiindexing", "N");
																		basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

																		//비정상 결과
																		logMap.put("content", "[재배송 송장등록 실패]  클레임 상태 확인 : " + order.get("ordercd").toString());
																		logMap.put("status", order.get("status"));
																		logUtil.insertOrderScheduleFailLog(logMap);

																	}
										}else{

											logger.error(">>> 재배송 송장등록 실패1: claimType 정보가 없습니다. , {}", order.get("ordercd"));
											//현재 주문 상태값 조회
											String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
											String mStatus = "02";
											if (dStatus.equals("D1")) {
												mStatus = OrderStauts.STAUTS_02;
											} else if (dStatus.equals("D2")) {
												mStatus = OrderStauts.STAUTS_03;
											} else if (dStatus.equals("D3")) {
												mStatus = OrderStauts.STAUTS_04;
											} else if (dStatus.equals("D4")) {
												mStatus = OrderStauts.STAUTS_04;
											} else if (dStatus.equals("D5")) {
												mStatus = OrderStauts.STAUTS_05;
											}
											//처리결과
											tranDetail.put("status", mStatus);
											tranDetail.put("apiindexing", "N");
											basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

											//비정상 결과
											logMap.put("content", "[재배송 송장등록 실패1] - claimType 정보가 없습니다.");
											logMap.put("status", order.get("status"));
											logUtil.insertOrderScheduleFailLog(logMap);
										}

						}else {
							logger.error(">>> 재배송 송장등록 실패1: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
							//현재 주문 상태값 조회
							String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
							String mStatus = "02";
							if (dStatus.equals("D1")) {
								mStatus = OrderStauts.STAUTS_02;
							} else if (dStatus.equals("D2")) {
								mStatus = OrderStauts.STAUTS_03;
							} else if (dStatus.equals("D3")) {
								mStatus = OrderStauts.STAUTS_04;
							} else if (dStatus.equals("D4")) {
								mStatus = OrderStauts.STAUTS_04;
							} else if (dStatus.equals("D5")) {
								mStatus = OrderStauts.STAUTS_05;
							}
							//처리결과
							tranDetail.put("status", mStatus);
							tranDetail.put("apiindexing", "N");
							basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

							//비정상 결과
							logMap.put("content", "[재배송 송장등록 실패1] - 클레임 정보가 없습니다.");
							logMap.put("status", order.get("status"));
							logUtil.insertOrderScheduleFailLog(logMap);
						}
				}else{
					logger.error(">>> 재배송 송장등록 실패2: 클레임 정보가 없습니다. , {}", order.get("ordercd"));
					//현재 주문 상태값 조회
					String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
					String mStatus = "02";
					if (dStatus.equals("D1")) {
						mStatus = OrderStauts.STAUTS_02;
					} else if (dStatus.equals("D2")) {
						mStatus = OrderStauts.STAUTS_03;
					} else if (dStatus.equals("D3")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D4")) {
						mStatus = OrderStauts.STAUTS_04;
					} else if (dStatus.equals("D5")) {
						mStatus = OrderStauts.STAUTS_05;
					}
					//처리결과
					tranDetail.put("status", mStatus);
					tranDetail.put("apiindexing", "N");
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

					//비정상 결과
					logMap.put("content", "[재배송 송장등록 실패2] - 클레임 정보가 없습니다.");
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleFailLog(logMap);
				}


			}else{
				logger.error(">>> 교환배송중 실패 99: 클레임 번호가 없습니다. , {}", order.get("ordercd"));
				//현재 주문 상태값 조회
				String dStatus = orderService.getOrderStatus(order.get("ordercd").toString(), order.get("detail_no").toString());
				String mStatus = "02";
				if (dStatus.equals("D1")) {
					mStatus = OrderStauts.STAUTS_02;
				} else if (dStatus.equals("D2")) {
					mStatus = OrderStauts.STAUTS_03;
				} else if (dStatus.equals("D3")) {
					mStatus = OrderStauts.STAUTS_04;
				} else if (dStatus.equals("D4")) {
					mStatus = OrderStauts.STAUTS_04;
				} else if (dStatus.equals("D5")) {
					mStatus = OrderStauts.STAUTS_05;
				}
				//처리결과
				tranDetail.put("status", mStatus);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

				//비정상 결과
				logMap.put("content", "[교환배송중 실패99] - 클레임 번호가  없습니다.");
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
			}




		}catch (UserDefinedException e){
			e.printStackTrace();
			logger.error(">>> 반품확인중 연계 실패: {}", order.get("ordercd"));
			//처리결과
			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
			tranDetail.put("apiindexing", "N");
			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			//비정상 결과
			logMap.put("content", "[반품확인중 처리실패]"+ e.getMessage().toString());
			logMap.put("status", order.get("status"));
			logUtil.insertOrderScheduleFailLog(logMap);
		}catch (Exception e1){
			e1.printStackTrace();
			logger.error("============================== 주문건 체크 필요 (클레임 반품 확인중처리시 오류 발생 - requestOrderCancel ) , {} ", order.get("ordercd"));
		}

	}







}
