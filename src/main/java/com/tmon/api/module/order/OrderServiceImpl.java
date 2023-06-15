package com.tmon.api.module.order;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.melchi.common.constant.BaseConst;
import com.melchi.common.constant.BaseConst.OrderStauts;
import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.util.HolidayUtil;
import com.melchi.common.util.LogUtil;
import com.melchi.common.util.StringUtil;
import com.tmon.api.TmonConnector;

@Service
public class OrderServiceImpl implements OrderService{
	
	static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;
	
	@Autowired
	LogUtil logUtil;


	/**
	 * 주문번호로 배송 상태값 조회
	 * @param tmonOrderNo
	 * @param tmonDealNo
	 * @return
	 * @throws Exception
	 */
	public String getOrderStatus(String tmonOrderNo, String tmonDealNo)throws Exception{
		String deliveryStatus = "D1";

		try{
			//대상조회 - 접수된 주문 조회
			RestParameters params = new RestParameters();
			Map<String, Object> paramMap = new HashMap<>();
			Map<String, Object> order = new HashMap<>();
			List<Map<String, Object>> deals = new ArrayList<>();
			//판매 날짜 설정
			String path = "/orders/"+tmonOrderNo;
			params.setRequestParameters(paramMap);
			params.setBody(paramMap);

			order = connector.call(HttpMethod.GET, path, params);
			if(null != order ){
				if(order.containsKey("deals")){
					deals = (List<Map<String, Object>>)order.get("deals");
					for(Map<String, Object> deal : deals){
						if(deal.get("tmonDealNo").toString().equals(tmonDealNo)){
							deliveryStatus = deal.get("deliveryStatus").toString();
						}
					}
				}
			}

			logger.warn("============ deliveryStatus : {} , {} , {} ",deliveryStatus, tmonOrderNo);

		}catch(UserDefinedException e){
			e.printStackTrace();
		}catch (Exception e1){
			e1.printStackTrace();
		}
		return deliveryStatus;
	}
	
	/**
	 * 결제완료 주문건 신규 생성처리 [배송 대상 수집]
	 * 
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager") 
	public void registOrders(Map<String, Object> order) throws UserDefinedException {
		logger.warn(">>> OrderScheduler > OrderService > registOrders start : ordNo[" + order.get("tmonOrderNo") + "]");
		try {
			//주문로그
			Map<String, Object> logMap = new HashMap<String, Object>();

			logMap.put("sitename", "TMON");
			logMap.put("ordercd" , order.get("tmonOrderNo"));
			//주문상태[ORDERSTATUS - 01:입금대기,02:결제완료,03:주문확인,04:배송중,05:배송완료,06:취소요청,07:취소완료,08:교환요청,09:교환 회수중,10:교환 회수완료,11:교환 배송중,12:교환 배송완료,13:반품요청,14:반품 회수중,15:반품 회수완료,16:환불완료(이머니),17:환불요청(계좌),18:환불완료(계좌),19:환불완료(신용카드),20:결재완료]'
			logMap.put("status"  , BaseConst.OrderStauts.STAUTS_02);
			logMap.put("api_url" , "/orders");

			//주문에 필요한 값 셋팅
			//배송비 합산금액
			int shipamount = 0;
			//도서산간 배송비
			int outpostshipamount = 0;
			//결재금액 합산금액
			int amount = 0;
			//comm_orders 사용할 금액 체
			int tAmount = 0;
			int tShipamount = 0;

			for(Map<String, Object> deals_ : (List<Map<String, Object>>)order.get("deals")) {
				//티몬 배송비 정보
				Map<String, Object> deliveryFee_ = (Map<String, Object>) deals_.get("deliveryFee");
				shipamount += deals_.get("deliveryFee") != null ? Integer.parseInt(deliveryFee_.get("amount").toString().trim()) + Integer.parseInt(deliveryFee_.get("longDistanceAmount").toString().trim()) : 0;
				outpostshipamount += deals_.get("longDistanceAmount") != null ? Integer.parseInt(deliveryFee_.get("longDistanceAmount").toString().trim()) : 0;
				List<Map<String, Object>> dealOptions_ = (List<Map<String, Object>>) deals_.get("dealOptions");
				for(Map<String, Object> opt_ : dealOptions_){
					amount += Integer.valueOf(opt_.get("purchasePrice").toString().trim());
				}
			}

			/////////////////////////////////////////////////////////////////comm_orders Setting
			//멸치 주문코드 조회
			Map<String, Object> mOrderParam = new HashMap<>();
			String ordNo = order.get("tmonOrderNo").toString().trim();
			String sellercd = "";
			mOrderParam.put("ordercd", ordNo);
			Map<String, Object> mOrder = basicSqlSessionTemplate.selectOne("OrderMapper.selectCommOrderCdChek", mOrderParam);
			String mOrderCd = null;
			if(mOrder != null && mOrder.get("m_ordercd") != null) {
				mOrderCd = mOrder.get("m_ordercd").toString();
			} else {
				mOrderCd = "M" + StringUtil.getTodayString("yyyyMMddHHmmssSSS");
				mOrderParam.put("m_ordercd", mOrderCd);
				basicSqlSessionTemplate.insert("OrderMapper.insertTmonOrders", mOrderParam);
			}
			//================================================
			// 멸치 주문  Start
			//================================================
			Map<String, Object> commOrderMap = new HashMap<>();
			List<Map<String, Object>> commOrderDetails = new ArrayList<>();
			List<Map<String, Object>> commOrderDeOpts = new ArrayList<>();
			//멸치주문코드
			commOrderMap.put("m_ordercd", mOrderCd);
			//판매자코드 나중에 셋팅
			//commOrderMap.put("sellercd", mProduct.get("sellercd"));
			//주문코드
			commOrderMap.put("ordercd", ordNo);

			//할인총액
			commOrderMap.put("discountamount", "0");
			//사용한쇼핑포인트금액
			commOrderMap.put("usedshoppingpointamount" , 0);
			//사용한멸치포인트금액
			commOrderMap.put("usedanchovypointamount"  , 0);
			//사용한이머니금액
			commOrderMap.put("usedemoneyamount"        , 0);
			//받는사람
			commOrderMap.put("recvperson", StringUtil.decryption(order.get("receiverName").toString()));
			//연락처
			commOrderMap.put("recvtel", StringUtil.decryption(order.get("receiverPhone").toString()));
			//휴대폰
			commOrderMap.put("recvhp", StringUtil.decryption(order.get("receiverPhone").toString()));

			////////////배송지 셋팅
			Map<String, Object> receiverAddress = (Map<String,Object>)order.get("receiverAddress");
			if(receiverAddress != null && null != receiverAddress.get("zipCode").toString() && StringUtil.decryption(receiverAddress.get("zipCode").toString()).length() > 5){
				commOrderMap.put("recvpostcode1", StringUtil.decryption(receiverAddress.get("zipCode").toString()).substring(0,3));   //우편번호 (기존 RECVPOSTCODE1(3자리)-RECVPOSTCODE2(3자리) 에서 RECVPOSTCODE1:(5자리)로 변경
				commOrderMap.put("recvpostcode2", StringUtil.decryption(receiverAddress.get("zipCode").toString()).substring(3));    //우편번호2
			}else {
				if(null == StringUtil.decryption(receiverAddress.get("zipCode").toString())){
					commOrderMap.put("recvpostcode1", "00000");
				}else{
					commOrderMap.put("recvpostcode1", StringUtil.decryption(receiverAddress.get("zipCode").toString()));
				}

			}
			commOrderMap.put("recvaddress1", StringUtil.decryption(receiverAddress.get("address").toString()));
			commOrderMap.put("recvaddress2", StringUtil.decryption(receiverAddress.get("addressDetail").toString()));
			if(receiverAddress.containsKey("streetAddress")){
				commOrderMap.put("recvrnaddress1", StringUtil.decryption(receiverAddress.get("streetAddress").toString()));
				commOrderMap.put("recvrnaddress2", StringUtil.decryption(receiverAddress.get("addressDetail").toString()));
			}else{
				commOrderMap.put("recvrnaddress1", StringUtil.decryption(receiverAddress.get("address").toString()));
				commOrderMap.put("recvrnaddress2", StringUtil.decryption(receiverAddress.get("addressDetail").toString()));
			}



			//배송메세지
			if(order.containsKey("deliveryMemo")){
				commOrderMap.put("recvmessage", order.get("additionalMessage").toString() + " / " + order.get("deliveryMemo").toString());
			}else {
				commOrderMap.put("recvmessage", order.get("additionalMessage").toString());
			}
			//주문일자
			commOrderMap.put("orderdate", order.get("orderDate").toString());
			//신용카드취소금액
			commOrderMap.put("refundamount1",0);
			//가상계좌취소금액
			commOrderMap.put("refundamount2",0);
			//이머니취소금액
			commOrderMap.put("refundamount3",0);
			//멸치포인트취소금액
			commOrderMap.put("refundamount4",0);
			//쇼핑포인트취소금액
			commOrderMap.put("refundamount5",0);
			//도서산간 배송 여부 (0: 일반 배송지 1: 도서산간 배송지)
			String outsidepost = "0";
			if(outpostshipamount > 0) {
				outsidepost = "1";
			}
			commOrderMap.put("outsidepost", outsidepost);
			////////////////////////////////////////////////////////////////comm_orders Setting End


			////////////////////////////////////////////////////////////////상품 정보 Setting Start
			if(order.get("deals") != null){
				commOrderDetails = new ArrayList<>();
				for(Map<String, Object> deals : (List<Map<String, Object>>)order.get("deals")){

					//티몬 배송비 정보
					Map<String, Object> deliveryFee = (Map<String, Object>)deals.get("deliveryFee");
					List<Map<String,Object>> dealOptions = (List<Map<String,Object>>)deals.get("dealOptions");
					//상품조회
					Map<String, Object> mProduct = basicSqlSessionTemplate.selectOne("OrderMapper.selectProducts", deals);

					//멸치상품이 없을경우 티몬상품 조회를 해서 매핑테이블을 생성해 준다.
					if (mProduct == null) {
						logger.error(">>> 상품 매칭안됨 조회후 설정: {}", order.get("ordNo"),order.get("itemId"));
						Map<String, Object> tProduct = new HashMap<>();
						String path = "/deals/"+deals.get("tmonDealNo").toString()+"?dealNoType=TMON_DEAL_NUMBER";
						RestParameters params = new RestParameters();
						Map<String, Object> oParam = new HashMap<>();
						params.setBody(oParam);
						tProduct = connector.call(HttpMethod.GET, path, params);

						Map<String, Object> sqlMap = new HashMap<>();
						//티몬 매핑 정보가 없을경우 매핑 정보를 생성해 준다.
						sqlMap.put("productcd", tProduct.get("vendorDealNo").toString());
						sqlMap.put("productno", deals.get("tmonDealNo").toString());

						//상품 전체 값을 가져온다.
						mProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectProducts", sqlMap);
						mProduct.put("productno", deals.get("tmonDealNo").toString());

						sqlMap.put("supplyprice", mProduct.get("supplyprice"));
						List<Map<String,Object>> dealOptions_ = (List<Map<String,Object>>)tProduct.get("dealOptions");
						sqlMap.put("optgroupcnt", dealOptions_.size());
						basicSqlSessionTemplate.insert("ProdMapper.insertTmonProducts", sqlMap);
						//옵션 동기화
						if(dealOptions_.size() > 0) {
							for(Map<String, Object> opt : dealOptions_) {
								//맵핑테이블 업데이트
								sqlMap.put("productoptionno", opt.get("tmonDealNo").toString());
								sqlMap.put("productoptioncd",opt.get("vendorDealOptionNo").toString());
								sqlMap.put("isava", opt.get("display").toString());
								sqlMap.put("optionprice", Integer.parseInt(opt.get("salesPrice").toString()));
								sqlMap.put("optionqty", 999);
								basicSqlSessionTemplate.insert("ProdMapper.insertTmonProductOpt", sqlMap);
								//basicSqlSessionTemplate.update("ProdMapper.updateTmonProductOptByTempUitemId", sqlMap);
							}
						}
					}


					sellercd = mProduct.get("sellercd").toString();


					////////////////////////////////////////////////////////////////comm_order_details Setting Start
					//================================================
					// 멸치 주문상세  Start
					//================================================
					Map<String, Object> commOrderDetailMap  = new HashMap<>();
					//멸치주문코드
					commOrderDetailMap.put("m_ordercd", mOrderCd);
					//상품코드
					commOrderDetailMap.put("productno", mProduct.get("productno"));
					//주문코드
					commOrderDetailMap.put("ordercd", ordNo);
					//주문순번(상세번호)
					commOrderDetailMap.put("detail_no", deals.get("tmonDealNo").toString());
					//상품코드
					commOrderDetailMap.put("productcd", mProduct.get("productcd"));
					//티몬 배송 번호 셋팅
					commOrderDetailMap.put("apishippingid", deals.get("deliveryNo").toString());

					//도서산간 추가배송비
					int additionalshippingfee = 0;
					if(Integer.parseInt(deliveryFee.get("longDistanceAmount").toString()) > 0) {
						additionalshippingfee = Integer.parseInt(deliveryFee.get("longDistanceAmount").toString());
					}
					commOrderDetailMap.put("additionalshippingfee", additionalshippingfee);

					//해외택배인경우 - 배송유형상세코드(25 해외택배배송)
					commOrderDetailMap.put("int_deliv_yn", "N");

					//데이터에 상품코드를 입력
					commOrderDetailMap.put("productcd", mProduct.get("productcd"));
					//데이터에 택배사코드 입력
					commOrderDetailMap.put("delicomcd", mProduct.get("shippingcompanycd"));
					//데이터에 배송비방식 입력
					commOrderDetailMap.put("shippingfeetype", mProduct.get("shippingfeetype"));
					//배송방법
					commOrderDetailMap.put("shippingmethod"    , mProduct.get("shippingmethod"));
					//배송비결제방식
					commOrderDetailMap.put("shippingfeepaytype", mProduct.get("shippingfeepaytype"));

					int sellingprice = 0;
					int qty = 0;
					amount = 0;


					for(Map<String, Object> opt : dealOptions){
						amount += Integer.valueOf(opt.get("purchasePrice").toString().trim());
						sellingprice = Integer.valueOf(opt.get("salesPrice").toString().trim());
						qty += Integer.valueOf(opt.get("qty").toString().trim());
					}

					//수량별 배송일 경우 배송비 분리
					if(mProduct.get("quantitycntuseyn").toString().equals("Y")){
						int cnt = Integer.parseInt(mProduct.get("salelimitcnt").toString());
						if(cnt == 0){
							cnt = 1;
						}
						int ShipCnt = 0;
						if(qty/cnt == 0 ){
							ShipCnt = 1;
						}else{
							ShipCnt = qty/cnt;
						}
						shipamount = (Integer.parseInt(mProduct.get("shippingfee").toString()) * ShipCnt) + Integer.parseInt(deliveryFee.get("longDistanceAmount").toString().trim());
						amount  = amount - shipamount;
						sellingprice  = sellingprice - Integer.parseInt(mProduct.get("shippingfee").toString());
					}else {
						//배송비 셋팅
						shipamount = deals.get("deliveryFee") != null ? Integer.parseInt(deliveryFee.get("amount").toString().trim()) + Integer.parseInt(deliveryFee.get("longDistanceAmount").toString().trim()) : 0;
						outpostshipamount = deals.get("longDistanceAmount") != null ? Integer.parseInt(deliveryFee.get("longDistanceAmount").toString().trim()) : 0;
					}
					//배송비
					commOrderDetailMap.put("shippingfee", shipamount);
					commOrderDetailMap.put("chargedshippingfee", shipamount);

					//상품단가
					commOrderDetailMap.put("sellingprice", sellingprice);
					//판매가
					commOrderDetailMap.put("amount", amount);
					//수량
					commOrderDetailMap.put("qty", qty);
					//공급가 입력
					commOrderDetailMap.put("supplyprice", mProduct.get("supplyprice"));


					/*commOrderDetailMap.put("apisellingprice",sellingprice);
					commOrderDetailMap.put("apiamount",amount);
					commOrderDetailMap.put("apidiscountamt",0);*/

					tAmount += amount;
					tShipamount += shipamount;
					//주문상태[ORDERSTATUS - 01:입금대기,02:결제완료,03:주문확인,04:배송중,05:배송완료,06:취소요청,07:취소완료,08:교환요청,09:교환 회수중,10:교환 회수완료,11:교환 배송중,12:교환 배송완료,13:반품요청,14:반품 회수중,15:반품 회수완료,16:환불완료(이머니),17:환불요청(계좌),18:환불완료(계좌),19:환불완료(신용카드),20:결재완료]'
					commOrderDetailMap.put("status", BaseConst.OrderStauts.STAUTS_02);
					//api index
					commOrderDetailMap.put("apiindexing", "N");

					//주문자 정보
					String apibuyer = "("+StringUtil.decryption(order.get("userId").toString())+")"+ StringUtil.decryption(order.get("userName").toString()) + " | | " + StringUtil.decryption(order.get("userPhone").toString());
					commOrderDetailMap.put("apibuyer", apibuyer);

					String orderfullcontents = order.toString();
					if(order.toString().length() > 4500) {
						orderfullcontents = order.toString().substring(0,4500);
					}

					commOrderDetailMap.put("orderfullcontents", orderfullcontents);


					//리스트에 추가
					commOrderDetails.add(commOrderDetailMap);



					///////////////////////////////////////////////////////////////comm_order_de_opts Setting
					//================================================
					// 멸치 주문옵션  Start
					//================================================
					int optionprice = 0;
					commOrderDeOpts = new ArrayList<>();
					int optionseq = 1;
					for(Map<String, Object> opt : dealOptions) {

						mOrderParam.put("productcd", mProduct.get("productcd"));
						//Map<String, Object> tmonProdOptMap = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonProductOpt", mOrderParam);

						Map<String, Object> commOrderDeOptsMap = new HashMap<>();
						//멸치주문번호
						commOrderDeOptsMap.put("m_ordercd", mOrderCd);
						//상품코드
						commOrderDeOptsMap.put("productcd", mProduct.get("productcd"));
						//주문코드
						commOrderDeOptsMap.put("ordercd", ordNo);
						//주문상세번호
						commOrderDeOptsMap.put("detail_no", deals.get("tmonDealNo").toString());
						//수량
						commOrderDeOptsMap.put("qty", opt.get("qty"));
						//옵션가격 -- 티몬은 deal가격에 옵션 추가금을 합산하여 판매 하기때문에 멸치에서는 추가금액이 보이지 않음
						opt.put("productcd", mProduct.get("productcd"));
						Map<String, Object> mProductOpt = basicSqlSessionTemplate.selectOne("OrderMapper.selectProductOpt", opt);
						if(mProductOpt != null){
							commOrderDeOptsMap.put("optionprice", mProductOpt.get("optionprice").toString());
						}else{
							commOrderDeOptsMap.put("optionprice", "0");
						}
						// 옵션명
						commOrderDeOptsMap.put("optionitem", opt.get("dealOptionTitle").toString());
						//옵션순번
						commOrderDeOptsMap.put("optionseq", optionseq);
						//옵션순번
						commOrderDeOptsMap.put("tmondealoptionno", opt.get("tmonDealOptionNo").toString());

						optionseq ++;

						commOrderDeOpts.add(commOrderDeOptsMap);


					}
					/////////////////////////////////////////////////////////comm_order_de_opts End


				}

				/////////////////////////////////////////////////////주문정보 생성후 등록한다.
				//1.주문 등록 및 매핑 테이블 등록
				//기등록 주문건인지 체크
				Map<String, Object> oOrderMap = new HashMap<String, Object>();
				oOrderMap.put("ordercd", ordNo);	//티몬주문코드
				oOrderMap.put("m_ordercd", mOrderCd);	//멸치주문코드
				Map<String, Object> commOrderRow = basicSqlSessionTemplate.selectOne("OrderMapper.selectCommOrders", oOrderMap); //comm_order 에 데이터가 존재하는지 체크

				if (commOrderRow == null) {
					String orderfullcontents = order.toString();
					if(order.toString().length() > 4500) {
						orderfullcontents = order.toString().substring(0,4500);
					}
					//주문총금액
					commOrderMap.put("amount" , tAmount);
					//배송비총액
					commOrderMap.put("shipamount",  tShipamount);

					commOrderMap.put("orderfullcontents", orderfullcontents);
					commOrderMap.put("sellercd", sellercd);
					//comm_order 등록
					basicSqlSessionTemplate.insert("OrderMapper.insertCommOrders", commOrderMap);
					logger.warn("-----commOrderRow INSERT : {}",commOrderMap.toString() );

					//로그 입력
					logMap.put("m_ordercd", mOrderCd);
					logMap.put("productcd", order.get("itemId"));
					logUtil.insertOrderScheduleSuccessLog(logMap);
				} else {
					boolean isChanged = false;
					//받는사람 이름
					String beforeRecvperson = commOrderRow.get("recvperson") != null ? commOrderRow.get("recvperson").toString().trim() : "";
					String afterRecvperson = commOrderMap.get("recvperson") != null ? commOrderMap.get("recvperson").toString().trim() : "";
					if(!beforeRecvperson.equals(afterRecvperson)) {
						isChanged = true;
					}

					//받는사람 연락처
					String beforeRecvtel = commOrderRow.get("recvtel") != null ? commOrderRow.get("recvtel").toString().trim() : "";
					String afterRecvtel = commOrderMap.get("recvtel") != null ? commOrderMap.get("recvtel").toString().trim() : "";
					if(!beforeRecvtel.equals(afterRecvtel)) {
						isChanged = true;
					}

					//받는사람 핸드폰 번호
					String beforeRecvhp = commOrderRow.get("recvhp") != null ? commOrderRow.get("recvhp").toString().trim() : "";
					String afterRecvhp = commOrderMap.get("recvhp") != null ? commOrderMap.get("recvhp").toString().trim() : "";
					if(!beforeRecvhp.equals(afterRecvhp)) {
						isChanged = true;
					}

					//지번주소1
					String beforeRecvaddress1 = commOrderRow.get("recvaddress1") != null ? commOrderRow.get("recvaddress1").toString().trim() : "";
					String afterRecvaddress1 = commOrderMap.get("recvaddress1") != null ? commOrderMap.get("recvaddress1").toString().trim() : "";
					if(!beforeRecvaddress1.equals(afterRecvaddress1)) {
						isChanged = true;
					}

					//지번주소2
					String beforeRecvaddress2 = commOrderRow.get("recvaddress2") != null ? commOrderRow.get("recvaddress2").toString().trim() : "";
					String afterRecvaddress2 = commOrderMap.get("recvaddress2") != null ? commOrderMap.get("recvaddress2").toString().trim() : "";
					if(!beforeRecvaddress2.equals(afterRecvaddress2)) {
						isChanged = true;
					}

					//도로명주소1
					String beforeRecvrnaddress1 = commOrderRow.get("recvrnaddress1") != null ? commOrderRow.get("recvrnaddress1").toString().trim() : "";
					String afterRecvrnaddress1 = commOrderMap.get("recvrnaddress1") != null ? commOrderMap.get("recvrnaddress1").toString().trim() : "";
					if(!beforeRecvrnaddress1.equals(afterRecvrnaddress1)) {
						isChanged = true;
					}

					//도로명주소2
					String beforeRecvrnaddress2 = commOrderRow.get("recvrnaddress2") != null ? commOrderRow.get("recvrnaddress2").toString().trim() : "";
					String afterRecvrnaddress2 = commOrderMap.get("recvrnaddress2") != null ? commOrderMap.get("recvrnaddress2").toString().trim() : "";
					if(!beforeRecvrnaddress2.equals(afterRecvrnaddress2)) {
						isChanged = true;
					}

					//우편번호1
					String beforeRecvpostcode1 = commOrderRow.get("recvpostcode1") != null ? commOrderRow.get("recvpostcode1").toString().trim() : "";
					String afterRecvpostcode1 = commOrderMap.get("recvpostcode1") != null ? commOrderMap.get("recvpostcode1").toString().trim() : "";
					if(!beforeRecvpostcode1.equals(afterRecvpostcode1)) {
						isChanged = true;
					}

					//우편번호2
					String beforeRecvpostcode2 = commOrderRow.get("recvpostcode2") != null ? commOrderRow.get("recvpostcode2").toString().trim() : "";
					String afterRecvpostcode2 = commOrderMap.get("recvpostcode2") != null ? commOrderMap.get("recvpostcode2").toString().trim() : "";
					if(!beforeRecvpostcode2.equals(afterRecvpostcode2)) {
						isChanged = true;
					}

					//배송메세지
					String beforeRecvmessage = commOrderRow.get("recvmessage") != null ? commOrderRow.get("recvmessage").toString().trim() : "";
					String afterRecvmessage = commOrderMap.get("recvmessage") != null ? commOrderMap.get("recvmessage").toString().trim() : "";
					if(!beforeRecvmessage.equals(afterRecvmessage)) {
						isChanged = true;
					}

					if(isChanged) {
						basicSqlSessionTemplate.insert("OrderMapper.updateCommOrders", commOrderMap); //comm_order 수정(배송지)
						logger.warn("-----commOrderRow UPDATE : {}",commOrderMap.toString() );
					}
				}
				//2.comm_order_details 저장
				for(Map<String,Object> commOrderDetail : commOrderDetails ){
					//기등록여부 체크
					Map<String, Object> detailRow = basicSqlSessionTemplate.selectOne("OrderMapper.selectCommOrderDetailNew", commOrderDetail);

					if(null == detailRow){
						//comm_order_details 등록
						basicSqlSessionTemplate.insert("OrderMapper.insertCommOrderDetail", commOrderDetail);

						//주문 상세 매핑 데이터 처리
						Map<String, Object> orderMappingMap = new HashMap<>();
						//주문코드
						orderMappingMap.put("m_ordercd", mOrderCd);
						//주문코드
						orderMappingMap.put("ordercd", ordNo);
						//주문순번(상세번호)
						orderMappingMap.put("detail_no", commOrderDetail.get("detail_no"));
						//배송번호
						orderMappingMap.put("deliveryno", commOrderDetail.get("apishippingid"));

						Map<String, Object> orderMappingRow = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonMappingInfoNew", orderMappingMap);
						if (orderMappingRow == null) {
							basicSqlSessionTemplate.insert("OrderMapper.insertTmonMappingInfo", orderMappingMap);
							logger.warn("-----commOrderDetail INSERT : {}",commOrderDetail.toString() );
						}
					}

				}
				//3.comm_order_de_opts 저장
				for(Map<String,Object> opt_ : commOrderDeOpts ){
					Map<String, Object> commOrderdeOptsRow = basicSqlSessionTemplate.selectOne("OrderMapper.selectCommOrderDeOpts", opt_);

					if (commOrderdeOptsRow == null) {
						//comm_order_de_opts 등록
						basicSqlSessionTemplate.insert("OrderMapper.insertCommOrderDeOpts", opt_);
						//매핑 상세정보
						Map<String, Object> mappingListCnt = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonMappingListCnt",opt_);
						if(Integer.parseInt(mappingListCnt.get("cnt").toString()) == 0 ){
							basicSqlSessionTemplate.insert("OrderMapper.insertTmonMappingList", opt_);
						}


						logger.warn("-----opt_ INSERT : {}",opt_.toString() );
					}
				}
			logger.warn(":::::::::::::::::::::::::::::::; {} : {} 등록 완료.",mOrderCd,ordNo);
			}else{
				//주문상세 없음.
			}


		}catch (Exception e){
			logger.warn(":::::::::::::::::::::::::::::::; {}  등록 실패.",order.get("tmonOrderNo"));
			e.printStackTrace();
		}

	}


	/**
	 *  주문확인 연동 처리 [ 배송 대상 확인 ]
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void confirmOrders(Map<String, Object> order) throws UserDefinedException {
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
		try{



			Map<String, Object> orderMappingMap = new HashMap<>();
			//주문코드
			orderMappingMap.put("ordercd", order.get("ordercd"));
			//티몬 Deal 번호
			orderMappingMap.put("detail_no", order.get("detail_no"));
			List<Map<String, Object>> orderList = basicSqlSessionTemplate.selectList("OrderMapper.selectTmonMappingList", orderMappingMap);

			//대상건이 없으면 종료
			if(orderList == null) {
				logger.error(">>> DB 맵핑데이터 없음 : {} , {}", order.get("m_ordercd"), order.get("ordercd") );
				//처리결과
				tranDetail.put("apiindexing", "Y");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

				//로그생성
				logMap.put("content", "[주문확인] DB 맵핑데이터 없음");
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
				return;
			}

			Map<String, Object> result =  new HashMap<>();
			RestParameters params = new RestParameters();
			Map<String, Object> paramMap = new HashMap<>();
			String path = "/orders/"+order.get("ordercd")+"/confirm";


			List<Map<String, Object>> tmonDealOptions = new ArrayList<>();
			paramMap.put("tmonDealNo",Long.parseLong(order.get("detail_no").toString()));
			for(Map<String, Object> orderMap : orderList) {
				Map<String, Object> tmonDealOption = new HashMap<>();
				tmonDealOption.put("tmonDealOptionNo", Long.parseLong(orderMap.get("tmondealoptionno").toString()));
				tmonDealOption.put("qty", Integer.parseInt(orderMap.get("qty").toString()));
				tmonDealOptions.add(tmonDealOption);
			}
			paramMap.put("tmonDealOptions",tmonDealOptions);
			params.setBody(paramMap);

			result = connector.call(HttpMethod.PUT, path, params);
			//처리결과
			if(null == result){
				logger.warn(">>>> 주문확인 처리 성공 {} {}",order.get("ordercd").toString(), order.get("detail_no"));

				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_03);
				tranDetail.put("apiindexing", "Y");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
				basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 업데이트
				//로그 생성
				logMap.put("content", "[주문확인 처리완료] ");
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleSuccessLog(logMap);
			}else{
				logger.error(">>> 주문확인 실패: {} : {}", order.get("ordercd") ,result.toString());
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_02);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

				//로그생성
				logMap.put("content", "[주문확인] TMON API 실패 : "+result.toString());
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
			}

		}catch (UserDefinedException e){
			if(e.getMessage().contains("배송주문 확인처리가 불가능한 배송상태입니다.")){
				logger.error(">>> [발송처리] API 주문확인 실패 : {}", e.getMessage().toString() );
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

				//로그생성
				logMap.put("content", "[주문확인처리] TMON API 호출 실패");
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
			}

		}catch (Exception e){
			e.printStackTrace();
			logger.error(">>> 주문확인 API 호출실패: {}", order.get("ordercd") );
			//처리결과
			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_02);
			tranDetail.put("apiindexing", "N");
			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			//로그생성
			logMap.put("content", "[주문확인] TMON API 호출 실패");
			logMap.put("status", order.get("status"));
			logUtil.insertOrderScheduleFailLog(logMap);
		}

	}


	/**
	 * 배송중 - 송장등록 처리 [ 송장 등록/수정 ]
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void shipOrders(Map<String, Object> order) throws UserDefinedException {

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
		tranDetail.put("detail_no", order.get("detail_no"));

		try{

			//택배 주문 연동건인경우 송장번호나 택배사 코드가 없을경우 주문확인으로 되돌려 준다.
			if(order.get("ordercd").toString().equals("01") && (null == order.get("delicomcd") || "".equals(order.get("delicomcd")) || null == order.get("shippingno") || "".equals(order.get("shippingno")))){
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_03);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
				return;
			}

			//택배회사 코드 조회
			Map<String, Object> deliveryCompany = new HashMap<>();
			if(order.get("delicomcd") != null) {
				//code:"10099",description:"자체배송"
				tranDetail.put("mdeliverycorp", order.get("delicomcd").toString());
				deliveryCompany = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonDelivery", tranDetail);
			}

			//주문상세 매핑 정보
			Map<String, Object> orderMap = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonMappingInfo", tranDetail);
			//주문옵션 포함 매핑 정보
			List<Map<String, Object>> orderList = basicSqlSessionTemplate.selectList("OrderMapper.selectTmonMappingList", tranDetail);

			//발송 파라미터
			Map<String, Object> result =  new HashMap<>();
			RestParameters params = new RestParameters();
			Map<String, Object> paramMap = new HashMap<>();
			//params.setRequestParameters(paramMap);
			//params.setPathVariableParameters(paramMap);
			String path = "/orders/"+order.get("ordercd")+"/invoices";
			Map<String, Object> invoice = new HashMap<>();

			//취소요청 건에 대한 이미 출고 처리 [ 취소요청 거절 ] PUT https://interworkapi.tmon.co.kr/api/{vendorId}/cancellations/{claimNo}/reject
			if(!orderMap.get("claimno").toString().equals("") && null != orderMap.get("claimtype").toString() && "C".equals(orderMap.get("claimtype").toString()) && "C1".equals(orderMap.get("claimstatus").toString())){
				paramMap.put("reason","JCD1");

				Map<String, Object> deliveryInvoice =  new HashMap<>();
					//택배일경우
					if(order.get("shippingmethod").toString().equals("01")){
						deliveryInvoice.put("deliveryCorp", Long.parseLong(deliveryCompany.get("deliverycorpcd").toString()));
						deliveryInvoice.put("invoiceNo",order.get("shippingno").toString());

					}else{
						//직배송일경우
						deliveryInvoice.put("deliveryCorp",10099);
						//deliveryInvoice.put("invoiceNo","10099");
						order.put("shippingno",10099);
						//tranDetail.put("invoiceNo","10099");
						//발송예정일 포맷 yyyy-MM-dd자체배송은 송장등록시 송장번호가 아닌 발송예정일을 입력합니다. deliveryScheduledDate
						String today = StringUtil.getTodayString("yyyyMMdd");
						deliveryInvoice.put("invoiceNo",today);
					}

				paramMap.put("deliveryInvoice",deliveryInvoice);
				params.setBody(paramMap);
				path = "/cancellations/"+orderMap.get("claimno").toString()+"/reject";
				result = connector.call(HttpMethod.PUT, path, params);
				if(null == result){
					//처리결과
					tranDetail.put("claimstatus","C5");
					tranDetail.put("apiindexing", "N");
					tranDetail.put("status", OrderStauts.STAUTS_04);
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
					basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 송장등록일 업데이트

					//로그 생성
					logMap.put("content", "[발송처리완료 - 이미출고 처리 ]");
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleSuccessLog(logMap);
					basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 송장등록일 업데이트
					logger.warn(":::::::::::::::::::::::: 이미출고 처리 완료 : {}, {}, {}",order.get("m_ordercd").toString(), order.get("ordercd").toString(), orderMap.get("claimno").toString() );
				}


				return;
			}

			Map<String, Object> saveWhOutCompleteProcessParamMap = new HashMap<>();
			List<Map<String, Object>> tmonDealOptions = new ArrayList<>();
			//배송번호
			paramMap.put("deliveryNo",orderMap.get("deliveryno").toString());
			tranDetail.put("deliveryNo",orderMap.get("deliveryno").toString());
			//티몬딜번호
			paramMap.put("tmonDealNo", Long.parseLong(order.get("detail_no").toString()));
				for(Map<String, Object> orderInfo : orderList) {
					Map<String, Object> tmonDealOption = new HashMap<>();
					tmonDealOption.put("tmonDealOptionNo", Long.parseLong(orderInfo.get("tmondealoptionno").toString()));
					tmonDealOption.put("qty", Integer.parseInt(orderInfo.get("qty").toString()));
					tmonDealOptions.add(tmonDealOption);
				}
			//티몬딜옵션정보들
			paramMap.put("tmonDealOptions",tmonDealOptions);

			try{
				//택배일경우
				if(order.get("shippingmethod").toString().equals("01")){
					invoice.put("deliveryCorp", Long.parseLong(deliveryCompany.get("deliverycorpcd").toString()));
					invoice.put("invoiceNo",order.get("shippingno").toString());
					tranDetail.put("invoiceNo",order.get("shippingno").toString());
					//invoice.put("additionalInvoices",""); //  "additionalInvoices" : ["12536354323"]
				}else{
					//직배송일경우
					invoice.put("deliveryCorp",10099);
					order.put("shippingno",10099);
					tranDetail.put("invoiceNo","10099");
					//발송예정일 포맷 yyyy-MM-dd자체배송은 송장등록시 송장번호가 아닌 발송예정일을 입력합니다.
					String today = StringUtil.getTodayString("yyyy-MM-dd");
					paramMap.put("deliveryScheduledDate",today);
				}
			}catch (Exception e){
				logger.error("------택배사 오류로 인한 직배송 처리 :::::::::::::{}, {}",order.get("m_ordercd").toString(),order.get("ordercd").toString());
				//직배송일경우
				invoice.put("deliveryCorp",10099);
				order.put("shippingno",10099);
				tranDetail.put("invoiceNo","10099");
				//발송예정일 포맷 yyyy-MM-dd자체배송은 송장등록시 송장번호가 아닌 발송예정일을 입력합니다.
				String today = StringUtil.getTodayString("yyyy-MM-dd");
				paramMap.put("deliveryScheduledDate",today);
			}

			paramMap.put("invoice",invoice);
			params.setBody(paramMap);

				logger.warn("-----  deliverydt {}",orderMap.get("deliverydt").toString().trim());
				//송장 신규 등록
				if(orderMap.get("deliverydt").toString().trim().equals("") || null == orderMap.get("deliverydt")){
					result = connector.call(HttpMethod.POST, path, params);
				}else{
				//송장 등록 내용 수정
					result = connector.call(HttpMethod.PUT, path, params);
				}
				if(null == result){
					logger.warn(":::::::::::::::::::::::: 배송중 처리 성공 {} {}",order.get("ordercd").toString(), order.get("shippingno").toString());
					//처리결과
					tranDetail.put("apiindexing", "Y");
					tranDetail.put("status", OrderStauts.STAUTS_04);
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
					basicSqlSessionTemplate.update("OrderMapper.updateTmonMappingInfo", tranDetail); //매핑 테이블 송장등록일 업데이트

					//로그 생성
					logMap.put("content", "[발송처리완료]");
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleSuccessLog(logMap);
				}else{
					logger.error(":::::::::::::::::::::::: 발송처리-운송장등록 실패: {}", order.get("ordercd"));

					//처리결과
					tranDetail.put("status", BaseConst.OrderStauts.STAUTS_03);
					tranDetail.put("apiindexing", "N");
					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

					//비정상 결과
					logMap.put("content", "[발송처리-운송장등록실패]"+ result.toString());
					logMap.put("status", order.get("status"));
					logUtil.insertOrderScheduleFailLog(logMap);
				}
		}catch (UserDefinedException e){

			if(e.getMessage().contains("current_state: D1")){
				logger.error(">>>>>>>>> [발송처리] API 호출실패 상태값 결제완료 : {}", e.getMessage().toString() );
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_02);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
			}else if(e.getMessage().contains("current_state: D4")){
				logger.error(">>>>>>>>>> [발송처리] API 오류발생 배송상태 : {}", e.getMessage().toString() );
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_04);
				tranDetail.put("apiindexing", "Y");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			}else if(e.getMessage().contains("동일 송장 등록 요청 오류")){
				logger.error(">>>>>>>> [발송처리] API 오류발생 동일송장등록 : {}", e.getMessage().toString() );
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_04);
				tranDetail.put("apiindexing", "Y");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
			}else{
				e.printStackTrace();
				//처리결과
				tranDetail.put("status", BaseConst.OrderStauts.STAUTS_77);
				tranDetail.put("apiindexing", "N");
				basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록
				//로그생성
				logMap.put("content", "[발송처리] TMON API 호출 실패 - " + e.getMessage().toString());
				logMap.put("status", order.get("status"));
				logUtil.insertOrderScheduleFailLog(logMap);
			}
			logger.error(">>>>>>>>>> [발송처리] API 호출실패 UserDefinedException : {}", e.getMessage().toString() );

		}
		catch (Exception e){
			e.printStackTrace();
			logger.error(">>>>>>>> [발송처리] API 호출실패: {}", order.get("ordercd") );
			//처리결과
			tranDetail.put("status", BaseConst.OrderStauts.STAUTS_03);
			tranDetail.put("apiindexing", "N");
			basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetail", tranDetail); //comm_order_detail 등록

			//로그생성
			logMap.put("content", "[발송처리] TMON API 호출 실패");
			logMap.put("status", order.get("status"));
			logUtil.insertOrderScheduleFailLog(logMap);
		}


	}



	/**
	 * 주문건 상태값 동기화 처리 (주문확인, 배송중, 배송완료) [배송 대상 수집]
	 *
	 * @param order
	 * @throws UserDefinedException
	 */
	@Override
	//@Transactional(value="basicTxManager")
	public void syncOrderStatus(Map<String, Object> order) throws Exception {
		try{

			for(Map<String, Object> deals_ : (List<Map<String, Object>>)order.get("deals")) {
				//티몬 정보
				//주문 상세 매핑 데이터 처리
				Map<String, Object> sqlMap = new HashMap<>();
				//주문코드
				sqlMap.put("ordercd", order.get("tmonOrderNo").toString());
				//주문순번(상세번호)
				sqlMap.put("detail_no", deals_.get("tmonDealNo").toString());

				//주문상세 매핑 정보
				Map<String, Object> orderMap = basicSqlSessionTemplate.selectOne("OrderMapper.selectTmonMappingInfo", sqlMap);

				if(orderMap.get("claimstatus").toString().equals("") || orderMap.get("claimstatus").toString().equals("C8") || orderMap.get("claimstatus").toString().equals("C9")){
					if(deals_.get("deliveryStatus").toString().equals("D2")){
						//주문확인
						sqlMap.put("status", OrderStauts.STAUTS_03);
					}else if(deals_.get("deliveryStatus").toString().equals("D3")){
						//배송중
						sqlMap.put("status", OrderStauts.STAUTS_04);
					}else if(deals_.get("deliveryStatus").toString().equals("D4")){
						//배송중
						sqlMap.put("status", OrderStauts.STAUTS_04);
					}else if(deals_.get("deliveryStatus").toString().equals("D5")){
						//배송완료
						sqlMap.put("status", OrderStauts.STAUTS_05);
					}

					basicSqlSessionTemplate.update("OrderMapper.updateCommOrderDetailStatusSync", sqlMap);
				}


			}

		}catch (Exception e){
			e.printStackTrace();
			logger.warn(":::::::::::::::::::::::::::::::; {}  동기화 실패.",order.get("tmonOrderNo"));
		}
	}



}
