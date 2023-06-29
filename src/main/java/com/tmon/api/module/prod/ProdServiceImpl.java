package com.tmon.api.module.prod;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.melchi.common.util.ApiKafkaClient;
import com.melchi.common.vo.RestParameters;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.melchi.common.constant.BaseConst;
import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.util.LogUtil;
import com.tmon.api.TmonConnector;
import com.tmon.api.module.common.CommonService;

@Service
public class ProdServiceImpl implements ProdService {

	static final Logger logger = LoggerFactory.getLogger(ProdServiceImpl.class);
		
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;
	
	@Autowired
	CommonService commonService;

	@Autowired
	LogUtil logUtil;

	/**
	 * 카프카 클라이언트
	 */
	@Autowired
	private ApiKafkaClient apiKafkaClient;

	/**
	 * 배송지 등록
	 * @param seller
	 * @return
	 * @throws UserDefinedException
	 */
	//@Transactional(value="basicTxManager")
	public Map<String, Object> selectAddresses(Map<String, Object> seller) throws UserDefinedException {
		Map<String, Object> rt = new HashMap<>();
		rt.put("tStatus","SUCCESS");
		//배송지 주소 셋팅
		Map<String, Object> request = new HashMap<>();
		String path = "/partners/addresses";
		RestParameters params = new RestParameters();
		Map<String, Object>  addr	= new HashMap<>();
		Map<String, Object> address = new HashMap<>();
		String tp = "";
		try {
			String[] types = {"D","R"}; //타입(D: 배송지, R: 반송지)
			for(String tps : types){

				//logger.warn("------------------tp : {}", tps);
				tp = tps;







				///////////////////////////////////////////////////////////////////////////////배송지

				seller.put("type",tp);
				request.put("type",tp);					//String	타입(D: 배송지, R: 반송지)	O
				//마리오쇼핑일 경우
				if(seller.get("sellercd").toString().equals("435709")){
					request.put("addressName",seller.get("sellercd").toString()+"_"+seller.get("shippolicy_no").toString()+"_"+tp);			//String	관리주소명	O		다른관리주소명과 동일할 수 없음
				}else{
					request.put("addressName",seller.get("sellercd").toString()+"_"+tp);			//String	관리주소명	O		다른관리주소명과 동일할 수 없음
				}

				if(tp.equals("D")){
					address.put("zipCode",seller.get("postcode1")+""+seller.get("postcode2"));			//String	우편번호	O
					address.put("address",seller.get("address1"));			//String	지번주소	O		우편번호에 해당하는 지번주소
					address.put("addressDetail",seller.get("address2"));	//String	주소지상세	O		상세주소
					address.put("streetAddress","");	//String	도로명주소	X		지번주소에 해당하는 도로명 주소
				}else{
					address.put("zipCode",seller.get("returnpostcode1")+""+seller.get("returnpostcode2"));			//String	우편번호	O
					address.put("address",seller.get("returnaddress1"));			//String	지번주소	O		우편번호에 해당하는 지번주소
					address.put("addressDetail",seller.get("returnaddress2"));	//String	주소지상세	O		상세주소
					address.put("streetAddress",seller.get("returnaddress1").toString() + seller.get("returnaddress2").toString());	//String	도로명주소	X		지번주소에 해당하는 도로명 주소
				}

				request.put("address",address);						//Address	주소	O
				request.put("managerName",seller.get("businame"));			//String	관리자명	O
				request.put("managerPhone",seller.get("tel"));			//String	관리자연락처	O
				request.put("defaultAddress", false);		//Boolean	기본배송지여부	X	false	기본 배송지 설정시 기존내용은 자동으로 설정취소됨

				addr = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonAddress", seller);





				if(addr == null){
					//배송지 ,반품지 신규등록
					path = "/partners/addresses";
					//requestD.put("sellercd",seller.get("sellercd")); //판매자 코드
					params.setBody(request);
					Map<String, Object> result = connector.call(HttpMethod.POST, path, params);
					result.put("sellercd",seller.get("sellercd")); //판매자 코드
					result.put("shippolicy_no",seller.get("shippolicy_no")); // 배송지 코드
					address = ((Map<String, Object>)result.get("address"));

					result.put("zipCode",address.get("zipCode"));
					result.put("address",address.get("address"));
					result.put("addressDetail",address.get("addressDetail"));
					result.put("streetAddress",address.get("streetAddress"));

					basicSqlSessionTemplate.insert("ProdMapper.insertTmonAddress", result);
					rt.put(tp,result.get("no"));

				}else if(addr.get("address") != address.get("address")){
					//배송지 ,반품지  수정
					path = "/partners/addresses/"+addr.get("no");
					request.remove("type");
					request.remove("managerName");
					request.remove("managerPhone");
					request.remove("defaultAddress");
					params.setBody(request);
					Map<String, Object> result = connector.call(HttpMethod.PUT, path, params);
					result.put("sellercd",seller.get("sellercd")); //판매자 코드
					result.put("shippolicy_no",seller.get("shippolicy_no")); // 배송지 코드
					address = ((Map<String, Object>)result.get("address"));

					result.put("zipCode",address.get("zipCode"));
					result.put("address",address.get("address"));
					result.put("addressDetail",address.get("addressDetail"));
					result.put("streetAddress",address.get("streetAddress"));
					basicSqlSessionTemplate.update("ProdMapper.updateTmonAddress", result);
					rt.put(tp,addr.get("no"));
				}else{
					rt.put(tp,addr.get("no"));
				}
			}
		}catch (UserDefinedException a){
			if(a.getMessage().contains("주소지명과 중복될 수 없습니다")){
				path = "/partners/addresses?addressName="+request.get("addressName").toString();
				List<Map<String, Object>> response = connector.callList(HttpMethod.GET, path, params);
				if(response.size() > 0){

					Map<String, Object> result = response.get(0);
					result.put("sellercd",seller.get("sellercd")); //판매자 코드
					result.put("shippolicy_no",seller.get("shippolicy_no")); // 배송지 코드
					address = ((Map<String, Object>)result.get("address"));

					result.put("zipCode",address.get("zipCode"));
					result.put("address",address.get("address"));
					result.put("addressDetail",address.get("addressDetail"));
					result.put("streetAddress",address.get("streetAddress"));

					basicSqlSessionTemplate.insert("ProdMapper.insertTmonAddress", result);
					logger.warn("중복건 등록 ::: {}",result.toString());
					rt.put(tp,result.get("no"));
					return rt;
				}
				rt.put("tStatus","FAIL");
				rt.put("tMessage",a.getMessage());
				return rt;
			}
		}catch (Exception e){
			e.printStackTrace();
			rt.put("tStatus","FAIL");
			rt.put("tMessage",e.getMessage());
			return rt;
		}

		return rt;
	}

	//배송템플릿 등록
	//@Transactional(value="basicTxManager")
	public Map<String, Object> selectDeliTemplate(Map<String, Object> mProduct,Map<String, Object> seller) throws UserDefinedException {
		Map<String, Object> rt = new HashMap<>();
		rt.put("tStatus","SUCCESS");
		try {
				String path = "/partners/templates";
				RestParameters params = new RestParameters();

				Map<String, Object> template = new HashMap<>();
				//템플릿 셋팅
				Map<String, Object> request = new HashMap<>();

					//배송비 셋팅 수량별 배송비는 무료 배송으로 으로 셋팅

					//템플릿명 셀러코드_배송방법_배송비방식_배송비_도서산간배송비_배송비결제방식_조건부배송금
					String deliveryTemplateName = mProduct.get("sellercd").toString();
							deliveryTemplateName +="_"+mProduct.get("shippingmethod").toString();
							deliveryTemplateName +="_"+mProduct.get("shippingfeetype").toString();
							if(mProduct.get("shippingfeetype").toString().equals("01")){
								deliveryTemplateName +="_"+Integer.parseInt(mProduct.get("returnshippingfee").toString());
							}else{
								deliveryTemplateName +="_"+mProduct.get("shippingfee").toString();
							}
							deliveryTemplateName +="_"+mProduct.get("additionalshippingfee").toString();
							deliveryTemplateName +="_"+mProduct.get("shippingfeepaytype").toString();
							if(mProduct.get("shippingfeetype").toString().equals("04") && mProduct.get("shippingfeepaytype").toString().equals("04")){
								deliveryTemplateName +="_"+mProduct.get("freeshippingamount").toString();
								deliveryTemplateName +="_"+Integer.parseInt(mProduct.get("returnshippingfee").toString());
							}else{
								deliveryTemplateName +="_"+mProduct.get("freeshippingamount").toString();

							}
							if(mProduct.get("quantitycntuseyn").toString().equals("Y")){
								deliveryTemplateName +="_QT";
							}

					//--수량별 배송비 무료배송 처리
					if(mProduct.get("quantitycntuseyn").toString().equals("Y")){
						deliveryTemplateName += "_"+mProduct.get("quantitycntuseyn").toString();
					}
					//마리오쇼핑일경우
					if(seller.get("sellercd").toString().equals("435709")){
						deliveryTemplateName += "_"+mProduct.get("shippolicy_no").toString();
					}

					logger.warn("------------ deliveryTemplateName : {}",deliveryTemplateName);
					request.put("deliveryTemplateName",deliveryTemplateName);					//	String	관리 배송템플릿 명	O
					//멸치쇼핑은 무조건 묶음배송 없음.
					request.put("bundledDeliveryAble",false);					//	Boolean	묶음배송가능여부	O		딜 등록시 사용한 배송템플릿 번호가 같더라도, 묶음배송가능여부가 false 이면, 딜별로 각각 배송비가 발생합니다

					//배송비 셋팅 수량별 배송일 경우 --수량별 배송비 무료배송 처리
					if(mProduct.get("quantitycntuseyn").toString().equals("Y")){
						request.put("deliveryFee",0);							//	Integer+	배송비 금액 (원)	O
					}else{
						request.put("deliveryFee",Integer.parseInt(mProduct.get("shippingfee").toString()));							//	Integer+	배송비 금액 (원)	O
					}
					//배송비 방식 셋팅
					String deliveryFeePolicy ="";
					if(mProduct.get("shippingfeetype").toString().equals("01") || mProduct.get("quantitycntuseyn").toString().equals("Y")){
						deliveryFeePolicy ="FREE";
						request.put("deliveryFee",Integer.parseInt(mProduct.get("returnshippingfee").toString()));							//	Integer+	배송비 금액 (원)	O
					}else if(mProduct.get("shippingfeetype").toString().equals("02")){
						deliveryFeePolicy ="PER";
					}else if(mProduct.get("shippingfeetype").toString().equals("03")){
						deliveryFeePolicy ="AFTER";
					}else if(mProduct.get("shippingfeetype").toString().equals("04") && mProduct.get("shippingfeepaytype").toString().equals("04")){
						deliveryFeePolicy ="CONDITION";
						request.put("deliveryFeeFreePrice",Integer.parseInt(mProduct.get("freeshippingamount").toString()));					//	Integer+	조건부 무료배송 기준 금액
						// (원)	V		배송비정책이 조건부무료배송(CONDITION)일때 필수
						request.put("deliveryFee",Integer.parseInt(mProduct.get("returnshippingfee").toString()));
					}
					request.put("deliveryFeePolicy",deliveryFeePolicy);					//	DeliveryFeePolicy	배송비 정책 타입	O FREE : 무료배송, CONDITION : 조건부무료배송, PER : 선불, AFTER : 착불




					request.put("productType",mProduct.get("productType"));							//	ProductType	배송 상품타입	O		딜 등록시 productType과 반드시 일치
					request.put("deliveryType",mProduct.get("deliveryType"));							//	DeliveryType	배송 타입 (당일/익일/예외/종료)	O
					//request.put("ddayDeliveryTime","");						//	String	당일발송시간	V		배송타입이 당일(DD)배송인 경우 필수

					//도서산간 추가 배송비 셋팅
					if(Integer.parseInt(mProduct.get("additionalshippingfee").toString()) > 0){
						request.put("longDistanceDeliveryAvailable",true);		//	Boolean	도서산간지역 배송가능 여부	X	false
						//착불일 경우 false
						if(mProduct.get("shippingfeetype").toString().equals("03")){
							request.put("longDistanceDeliveryPrepay",false);			//	Boolean	도서산간지역 배송비 주문시 결제 여부	V		도서산간 배송가능여부가 true인 경우 필수
						}else {
							request.put("longDistanceDeliveryPrepay",true);			//	Boolean	도서산간지역 배송비 주문시 결제 여부	V		도서산간 배송가능여부가 true인 경우 필수
						}
						request.put("longDistanceDeliveryFeeJeju",Integer.parseInt(mProduct.get("additionalshippingfee").toString()));			//	Integer+ (0..300000)	도서산간 제주 지역 추가 배송비	V		도서산간 배송가능여부와 도서산간 배송비 주문시결제여부가 true일 경우 필수
						request.put("longDistanceDeliveryFeeExcludingJeju",Integer.parseInt(mProduct.get("additionalshippingfee").toString()));	//	Integer+ (0..300000)	도서간간 제주 제외한 지역 추가 배송비	V		도서산간 배송가능여부와 도서산간 배송비 주문시결제여부가 true일 경우 필수
						//request.put("longDistanceDeliveryDiscriptionMin","");	//	Integer+ (0..300000)	도서산간차등 추가 배송비 최소금액	V		도서산간 배송가능여부가 true이고, 도서산간 배송비 주문시결제여부가 false인경우 필수
						//request.put("longDistanceDeliveryDiscriptionMax","");	//	Integer+ (0..300000)	도서산간차등 추가 배송비 최대금액	V		도서산간 배송가능여부가 true이고, 도서산간 배송비 주문시결제여부가 false인경우 필수
					}else{
						request.put("longDistanceDeliveryAvailable",false);		//	Boolean	도서산간지역 배송가능 여부	X	false
						//request.put("longDistanceDeliveryPrepay",false);			//	Boolean	도서산간지역 배송비 주문시 결제 여부	V		도서산간 배송가능여부가 true인 경우 필수
						//request.put("longDistanceDeliveryFeeJeju",0);			//	Integer+ (0..300000)	도서산간 제주 지역 추가 배송비	V		도서산간 배송가능여부와 도서산간 배송비 주문시결제여부가 true일 경우 필수
						//request.put("longDistanceDeliveryFeeExcludingJeju",0);	//	Integer+ (0..300000)	도서간간 제주 제외한 지역 추가 배송비	V		도서산간 배송가능여부와 도서산간 배송비 주문시결제여부가 true일 경우 필수
						//request.put("longDistanceDeliveryDiscriptionMin","");	//	Integer+ (0..300000)	도서산간차등 추가 배송비 최소금액	V		도서산간 배송가능여부가 true이고, 도서산간 배송비 주문시결제여부가 false인경우 필수
						//request.put("longDistanceDeliveryDiscriptionMax","");	//	Integer+ (0..300000)	도서산간차등 추가 배송비 최대금액	V		도서산간 배송가능여부가 true이고, 도서산간 배송비 주문시결제여부가 false인경우 필수
					}

					if(seller.get("sellercd").toString().equals("435709")){
						seller.put("shippolicy_no",mProduct.get("shippolicy_no").toString());
					}else{
						seller.put("shippolicy_no","0");
					}

					Map<String, Object> address = selectAddresses(seller);
					if(address.get("tStatus").equals("SUCCESS")){
						request.put("partnerDeliveryAddressNo",Long.parseLong(address.get("D").toString()));				//	Long	파트너 배송지 번호	O	false
						request.put("partnerReturnAddressNo",Long.parseLong(address.get("R").toString()));				//	Long	파트너 반송지 번호	O
					}else{
						rt.put("tStatus","FAIL");
						rt.put("tMessage",address.get("tMessage"));
						return rt;
					}

					//request.put("tmonReturnCargoUsing","");					//	Boolean	티몬지정 반품택배	X	false



				template = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonDeliveryTemplate", request);
				if(template == null){
					//배송 템플릿
					path = "/partners/templates";
					//requestD.put("sellercd",seller.get("sellercd")); //판매자 코드
					params.setBody(request);
					Map<String, Object> result = connector.call(HttpMethod.POST, path, params);
					request.put("sellercd",seller.get("sellercd")); //판매자 코드
					request.put("deliveryTemplateNo",result.get("deliveryTemplateNo"));

					basicSqlSessionTemplate.insert("ProdMapper.insertTmonDeliveryTemplate", request);
					rt.put("deliverytemplateno",result.get("deliveryTemplateNo"));

				}else{
					rt.put("deliverytemplateno",template.get("deliverytemplateno"));
				}

		}catch (Exception e){
			e.printStackTrace();
			rt.put("tStatus","FAIL");
			rt.put("tMessage",e.getMessage());
			return rt;
		}

		return rt;
	}


	private void setStopProductManual(String productcd){
		try {
			String path = "";
			RestParameters params = new RestParameters();
			Map<String, Object> response = null;
			//판매 일지 정지
			path = "/deals/"+ productcd +"/pause";
			response = connector.call(HttpMethod.PUT, path, params);


			Map<String, Object> sqlMap = new HashMap<>();
			sqlMap.put("productcd", productcd);
			basicSqlSessionTemplate.insert("ProdMapper.updateTmonProductsPause", sqlMap);
			logger.warn("------수정 실패로 인한 판매일시중지 완료 99 : ");
		}catch (Exception e){
			e.printStackTrace();
		}
	}




	/**
	 * 딜 옵션 재고 수정 매핑 처리
	 * @param mProduct
	 * @param optionGroup
	 * @return
	 */
	private Map<String, Object> getDealOptionDspStock(Map<String, Object> mProduct, List<Map<String, Object>> optionGroup, List<Map<String, Object>> optionList, String tType) {
		Map<String, Object> result = new HashMap<>();
		result.put("tStatus","SUCCESS");
		List<Map<String, Object>> dealOptions = new ArrayList<>();
		try{


			Map<String, Object> DealOption = new HashMap<>();
			if(optionGroup.size() > 0){
				//List<Map<String, Object>> optionList = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductOpts", mProduct);
				int d1 = 0;
				int d2 = 0;
				int d3 = 0;
				for(Map<String, Object> dept1 : optionList){
					if(dept1.get("optiongroupno").toString().equals("1")){
						d2=0;
						d3=0;
						if(optionGroup.size() >= 2){
							for(Map<String, Object> dept2 : optionList){
								d3 = 0;
								if(dept2.get("optiongroupno").toString().equals("2")){
									if(optionGroup.size() >= 3){

										for(Map<String, Object> dept3 : optionList){
											if(dept3.get("optiongroupno").toString().equals("3")){
												//옵션 그룹이 3개일경우
												DealOption = new HashMap<>();
												DealOption.put("vendorDealOptionNo", dept1.get("optionitem").toString()+"_"+d1+d2+d3);		//	String(1..50)	연동업체 상품키	O		unique
												if(tType.equals("D")){
													if(dept1.get("isava").toString().equals("02") && dept2.get("isava").toString().equals("02") && dept3.get("isava").toString().equals("02")){
														DealOption.put("display",true);					//	Boolean	노출여부	X	true
													}else{
														DealOption.put("display",false);					//	Boolean	노출여부	X	true
													}
												}
												if(tType.equals("S")) {
													DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
												}
												dealOptions.add(DealOption);
												d3++;
											}
										}
									}else{
										//옵션 그룹이 2개일경우
										DealOption = new HashMap<>();
										DealOption.put("vendorDealOptionNo", dept1.get("productoptioncd").toString()+"_"+d1+d2);		//	String(1..50)	연동업체 상품키	O		unique
										if(tType.equals("D")){
											if(dept1.get("isava").toString().equals("02") && dept2.get("isava").toString().equals("02")){
												DealOption.put("display",true);					//	Boolean	노출여부	X	true
											}else{
												DealOption.put("display",false);					//	Boolean	노출여부	X	true
											}
										}
										if(tType.equals("S")) {
											DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
										}
										dealOptions.add(DealOption);
									}
									d2++;
								}
							}
						}else{
							//옵션 그룹이 1개일경우
							//sections = new ArrayList<>();
							DealOption = new HashMap<>();
							DealOption.put("vendorDealOptionNo", dept1.get("productoptioncd").toString());		//	String(1..50)	연동업체 상품키	O		unique
							if(tType.equals("D")){
								if(dept1.get("isava").toString().equals("02")){
									DealOption.put("display",true);					//	Boolean	노출여부	X	true
								}else{
									DealOption.put("display",false);					//	Boolean	노출여부	X	true
								}
							}
							if(tType.equals("S")) {
								DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
							}
							dealOptions.add(DealOption);
						}
						d1++;
					}
				}

			}else{
				//옵션이 없는 단일 상품일 경우
				DealOption.put("vendorDealOptionNo",mProduct.get("productcd"));		//	String(1..50)	연동업체 상품키	O		unique
				if(tType.equals("D")){
					if(mProduct.get("productcd").toString().equals("02")){
						DealOption.put("display",true);					//	Boolean	노출여부	X	true
					}else {
						DealOption.put("display",false);					//	Boolean	노출여부	X	true
					}
				}
				if(tType.equals("S")) {
					DealOption.put("stock", Integer.parseInt(mProduct.get("prdqty").toString()));                        //	Integer+	재고	O
				}
				dealOptions.add(DealOption);
			}
		}catch (Exception e){
			result.put("tStatus","FAIL");
			result.put("tMessage",e.getMessage());
		}

		result.put("dealOptions",dealOptions);
		return result;
	}



	//티몬 상품 매핑 처리
	@SuppressWarnings("unchecked")
	@Transactional(value="basicTxManager")
	public Map<String, Object> getProducts(Map<String, Object> mProduct, Map<String, Object> tmonProduct, Map<String, Object> seller, List<Map<String, Object>> optionGroup,List<Map<String, Object>> optionList, String tType) {
		//상품map
		Map<String, Object> product = new HashMap<>();
		product.put("tStatus","SUCCESS");
		try {
			/////////////////////////////////////////////////////////////////////////멸치 상품번호
            String productcd = mProduct.get("productcd").toString();
            //등록일때 필수값셋팅

			//배송 타입 설정   냉장냉동/신선식품(DP01), 일반상품(DP07), 해외직배송(DP02), 해외구매대행(DP03), 화물설치(DP04), 주문제작(DP05), 주문후발주(DP06)
			String productType = "DP07";
			//배송 타입 (당일/익일/예외/종료)
			String deliveryType = "ND";
			//출고 소요일 체크
			/*if(Integer.parseInt(mProduct.get("releaseterm").toString()) >2){
				//예외				deliveryType = "ED";
			}*/

			if(mProduct.get("shippingmethod").toString().equals("02")){
				productType = "DP04";
				deliveryType = "ED";
			}

			mProduct.put("deliveryType",deliveryType);
			mProduct.put("productType",productType);

            if(tType.equals("I")){

				//멸치 상품 코드
				product.put("vendorDealNo",productcd);                 	//	String(1..50)   딜번호(티몬전시단위)에 해당 연동업체측 키 O       연동사별로 유일한 50자 이내의 문자 값이어야하며 연동사에서 생성 관리 되는 값
				product.put("productType",productType);               		//	ProductType 배송상품 유형 타입  O       배송템플릿의 상품타입과 일치해야함
				//배송지 셋팅 벤더사 정책번호(vendorPolicyNo) :test 923 real :158
				product.put("vendorPolicyNo",158);                 	//  Integer+    티몬 오퍼레이터를 통해 받은 정책번호    O

				//현재 시간 설정
				LocalDateTime now = LocalDateTime.now();
				String salesStartDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				logger.warn("-------------salesStartDate : {}",salesStartDate);

				//product.put("salesStartDate",salesStartDate);       //  Now+    판매시작일   X   now
				product.put("salesEndDate","2900-12-31T23:59:59");  //	Future  판매종료일   X   2900-12-31T23:59:59

				product.put("adultOnly",false);                   	//	Boolean 성인물여부   X   false   등록이후 false에서 true로만 수정가능

				product.put("extraInstallationCostUsing",false);       //  Boolean 별도 설치비 사용여부 X   false
				product.put("simpleRefundAble",true);                 //  Boolean 단순변심환불 가능 여부    X   true    상품유형에 따라 불가능으로 설정 불가, 등록이후 수정불가
				//product.put("simpleRefundNotAvailableReason","");   //  String  단순변심환불 불가능 사유   V       단순변심 환불 불가능으로 설정시 필수 입력
            }


			//배송 템플릿 셋팅
			String deliveryTemplateNo = "";
			Map<String, Object> template = selectDeliTemplate(mProduct, seller);
			if(template.get("tStatus").equals("SUCCESS")){
				deliveryTemplateNo = template.get("deliverytemplateno").toString();
			}else{
				product.put("tStatus","FAIL");
				product.put("tMessage",template.get("tMessage"));
				return product;
			}
			product.put("deliveryTemplateNo",Integer.parseInt(deliveryTemplateNo));                //  Integer+    배송템플릿번호 O


			///////////////////////////////////////////////////////////////////////// 판매가 셋팅
			int price = 0;
			//멸치 공급가격
			BigDecimal supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString()));
			if(mProduct.get("quantitycntuseyn").toString().equals("Y")){
				supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString())+Integer.parseInt(mProduct.get("shippingfee").toString()));
			}

			//멸치 판매가격
			BigDecimal sellingprice = new BigDecimal(Integer.parseInt(mProduct.get("sellingprice").toString()));
			//수수료율
			BigDecimal commissionRate = sellingprice.subtract(supplyprice).divide(sellingprice, 2, BigDecimal.ROUND_HALF_UP);

			/*// 판매가격 (배송료를 공급가에 더해서 판매가 생성)
			int shippingfee = mProduct.get("shippingfee") != null ? Integer.parseInt(mProduct.get("shippingfee").toString()) : 0;
			String shippingfeetype = mProduct.get("shippingfeetype") != null ? mProduct.get("shippingfeetype").toString().trim() : null;
			if("01".equals(shippingfeetype)) {
				shippingfee = 0;
			}
			supplyprice = supplyprice.add(new BigDecimal(shippingfee));

			supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString()));*/
			BigDecimal tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);

			/*if("73184523,52962712,14702128,75990925,64155688,71641840,75524444,75664851,74862228,74861450,74860578,74230763,74229610,74229916,55265287,64209793,75685132,64155506,76283746,72562665,46666828,54201099,75577088,66133307,66151750,71855605,69663542,69661030,76168123,75822913,53315167,53460679,70019945,72537530,75192225,75175979,67854021,67853906,67853458,67853226,72480942,72481043,72480834,71186915,71187719,71188444,71188229,71193469,71189049,71189594,73961742,73960693,74526907,73959212,73954265,75778736,76136372,76137135,76135934,75728759,76136975,65463950,75752985,75578466,75612511,75612680,75624611,75625897,75630805,75631020,75631325,75631567,75674748,75674229,75673737,68688520,68688510,68165670,68165667,68165668,76206944,75672983,75671982,75670534,75670265,75670242,75669141,75669022,75668813,75565048,75563940,75563899,75563480,75614441,75614361,75611693,75611615,75611332,75611291,74618890,75169530,75840847,59654358,75679489,75798491,75754040,75741055,75740945,75740712,75740707,75680680,75680195,75678783,76123688,75903960,75903865,75842203,75827802,75825297,76271268,76271059,76270806,76270528,76269790,76265941,69921825,75856397,75652493,72101556,54049712,61890304,61932920,68731239,65762955,62576156,55949744,73560921,49546629,57966075,45192730,45269072,74293051,72545480,45486976,73001998,73000021,73233873,73002711,73002368,73002861,51165672,51165333,51165255,51165368,51165190,74924587,74924558,74863110,74863153,75573285,75573303,74295360,75994323,44974768,74924576,74924566,75803806,57142803,75079901,75643181,72198350,62162930,62163022,49151735,49148688,49149481,49148571,50070104,67583390,67583487,62502009,70378053,66937196,66936873,47603561,58897010,73019572,73561902,73966344,73167627,75820801,75147070,71546370,70354229,70864109,70355997,70357978,72385679,75518845,75517685,70395436,70394342,70394156,70393540,72494250,72494173,72493752,72493545,72217419,72217238,72217061,72216656,72216358,72216037,72215885,72215637,75059602,75059450,75053804,75053415,75053174,75053028,75052804,75052637,75653495,76309224,75788314,75788781,76278423,75301832,76354291,65156953,65583885,65583670,65583577,76282750,65863828,61780101,61779820,63634837,61774789,36906312,73356895,74165997,73297709,72871570,72871436,72871398,72196365,61780290,47527444,55117548,48025370,47372480,47371925,47704398,47527319,47521508,48155213,48438269,46836057,46814321,46815296,46815299,76397738,64201784,64201671,75718672,75717958,75717751,75717434,75717052,75716425,75715801,75715790,72435907,40489598,51409013,51408120,65659098,40489661,40489647,70648759,70648934,73773039,73785387,73785934,73792075,73792644,73793051,73795295,75085442,75085663,75085934,75086136,75086598,75086693,75086856,75087089,73799609".contains(mProduct.get("productcd").toString())){


				tmonsellingprice = supplyprice.divide(new BigDecimal(0.78), -1, BigDecimal.ROUND_UP);
			}else{
				tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);
			}*/

			//tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);

			//BigDecimal tmonsellingprice = supplyprice.divide(new BigDecimal(0.78), -1, BigDecimal.ROUND_UP);
			//마리오 가격셋팅
			if(mProduct.get("sellercd").toString().equals("435709")){
				price = Integer.parseInt(mProduct.get("sellingprice").toString());
			}else{
				price = Integer.parseInt(String.valueOf(Math.round(tmonsellingprice.doubleValue())));
			}

			logger.warn("====================tmonsellingprice::"+tmonsellingprice.doubleValue());

			/////////////////////////////////////////////////////////////////////////// 상품명 셋팅
			//상품명 +(플러스), ,(콤마), .(닷), /(슬러쉬), -(하이폰), _(언더바), ( )(괄호), :(콜론), %(퍼센트), ~(물결), '[ ]'(대괄호), &(앤드), ‘(작은따옴표) 제거
			String productNm = mProduct.get("productname").toString();
			productNm.replaceAll("[+,./-_():%~\\[\\]&']","");
			product.put("title",productNm);                   			//	String(60)  판매용 메인 제목(딜명)   O       최대 60자
			//브랜드, 제조자 등등
			product.put("titleDecoration", mProduct.get("brandname").toString());               	//	String(20)  판매용 제목 상단 홍보 문구(딜 홍보 문구)    O       최대 20자

			///////////////////////////////////////////////////////////////////////// 옵션셋팅

			List<String> sections = new ArrayList<>();
			List<Map<String, Object>> dealOptions = new ArrayList<>();
			Map<String, Object> DealOption = new HashMap<>();
			if(optionGroup.size() > 0){
				logger.warn("--------- 옵션 있음..");
				//옵션그룹이 2개일 이상일 경우만 section 사용
				// 신규등록일 경우만 처리
				if(tType.equals("I")) {
					if(optionGroup.size() >1){
						for(Map<String, Object> og : optionGroup){
							sections.add(og.get("optiongroupname").toString());
						}
						//옵션 그룹 셋팅 (초기 값을 수정 할수 없음)
						product.put("sections", sections);                        //	List(String(1..10)) 상품선택정보들 X
					}
				}

				//List<Map<String, Object>> optionList = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductOpts", mProduct);

				int d1 = 0;
				int d2 = 0;
				int d3 = 0;
				for(Map<String, Object> dept1 : optionList){
					if(dept1.get("optiongroupno").toString().equals("1")){
						d2=0;
						d3=0;
						sections = new ArrayList<>();
						if(optionGroup.size() >= 2){

							for(Map<String, Object> dept2 : optionList){
								d3 = 0;
								if(dept2.get("optiongroupno").toString().equals("2")){
									if(optionGroup.size() >= 3){

										for(Map<String, Object> dept3 : optionList){
											if(dept3.get("optiongroupno").toString().equals("3")){
												//옵션 그룹이 3개일경우
												sections = new ArrayList<>();
												DealOption = new HashMap<>();

												sections.add(dept1.get("optionitem").toString()+dept1.get("optionitemdetail").toString());
												sections.add(dept2.get("optionitem").toString()+dept2.get("optionitemdetail").toString());
												sections.add(dept3.get("optionitem").toString()+dept3.get("optionitemdetail").toString());

												DealOption.put("vendorDealOptionNo", dept1.get("optionitem").toString()+"_"+d1+d2+d3);		//	String(1..50)	연동업체 상품키	O		unique
												//DealOption.put("title", dept1.get("optionitem").toString()+dept1.get("optionitemdetail").toString());						//	String(1..100)	상품명	X		생략가능, 단 title 또는 sections 둘중 1개는 필수
												DealOption.put("sections",sections);					//	List<String(1..10)>	분류정보	X		딜 분류와 동일 사이즈 , 1개 섹션당 100자 제한

												/*supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString())+Integer.parseInt(dept1.get("optionprice").toString())+Integer.parseInt(dept2.get("optionprice").toString())+Integer.parseInt(dept3.get("optionprice").toString()));
												tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);
												price = Integer.parseInt(String.valueOf(Math.round(tmonsellingprice.doubleValue())));
												logger.warn("====================tmonsellingprice::"+tmonsellingprice.doubleValue());*/

												DealOption.put("salesPrice",price+Integer.parseInt(dept1.get("optionprice").toString())+Integer.parseInt(dept2.get("optionprice").toString())+Integer.parseInt(dept3.get("optionprice").toString()));				//	Integer+	판매가(단가)	O		0원 등록불가
												//등록일 경우에만 제고 추가
												if(tType.equals("I")) {
													DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
													if(dept1.get("isava").toString().equals("02") && dept2.get("isava").toString().equals("02") && dept3.get("isava").toString().equals("02")){
														DealOption.put("display",true);					//	Boolean	노출여부	X	true
													}else{
														DealOption.put("display",false);					//	Boolean	노출여부	X	true
													}
												}
												dealOptions.add(DealOption);

												d3++;
											}

										}

									}else{
									//옵션 그룹이 2개일경우
										sections = new ArrayList<>();
										DealOption = new HashMap<>();

										sections.add(dept1.get("optionitem").toString()+dept1.get("optionitemdetail").toString());
										sections.add(dept2.get("optionitem").toString()+dept2.get("optionitemdetail").toString());

										DealOption.put("vendorDealOptionNo", dept1.get("productoptioncd").toString()+"_"+d1+d2);		//	String(1..50)	연동업체 상품키	O		unique
										//DealOption.put("title", dept1.get("optionitem").toString()+dept1.get("optionitemdetail").toString());						//	String(1..100)	상품명	X		생략가능, 단 title 또는 sections 둘중 1개는 필수
										DealOption.put("sections",sections);					//	List<String(1..10)>	분류정보	X		딜 분류와 동일 사이즈 , 1개 섹션당 100자 제한

										/*supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString())+Integer.parseInt(dept1.get("optionprice").toString())+Integer.parseInt(dept2.get("optionprice").toString()));
										tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);
										price = Integer.parseInt(String.valueOf(Math.round(tmonsellingprice.doubleValue())));
										logger.warn("====================tmonsellingprice::"+tmonsellingprice.doubleValue());*/
										DealOption.put("salesPrice",price+Integer.parseInt(dept1.get("optionprice").toString())+Integer.parseInt(dept2.get("optionprice").toString()));				//	Integer+	판매가(단가)	O		0원 등록불가
										//등록일 경우에만 제고/노출여부 추가
										if(tType.equals("I")) {
											DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
											if(dept1.get("isava").toString().equals("02") && dept2.get("isava").toString().equals("02")){
												DealOption.put("display",true);					//	Boolean	노출여부	X	true
											}else{
												DealOption.put("display",false);					//	Boolean	노출여부	X	true
											}
										}
										dealOptions.add(DealOption);
									}
									d2++;
								}
							}
						}else{
						//옵션 그룹이 1개일경우
							//sections = new ArrayList<>();
							DealOption = new HashMap<>();
							DealOption.put("vendorDealOptionNo", dept1.get("productoptioncd").toString());		//	String(1..50)	연동업체 상품키	O		unique
							DealOption.put("title", dept1.get("optionitem").toString()+dept1.get("optionitemdetail").toString());						//	String(1..100)	상품명	X		생략가능, 단 title 또는 sections 둘중 1개는 필수
							//DealOption.put("description","");				//	String	파트너커스텀정보	X
							//DealOption.put("sections","");					//	List<String(1..10)>	분류정보	X		딜 분류와 동일 사이즈 , 1개 섹션당 100자 제한
							//DealOption.put("price","");						//	Integer+	가격	X	salesPrice	현정책 기준 salesPrice와 동일해야 함

							/*supplyprice = new BigDecimal(Integer.parseInt(mProduct.get("supplyprice").toString())+Integer.parseInt(dept1.get("optionprice").toString()));
							tmonsellingprice = supplyprice.divide(new BigDecimal(0.89), -1, BigDecimal.ROUND_UP);
							price = Integer.parseInt(String.valueOf(Math.round(tmonsellingprice.doubleValue())));
							logger.warn("====================tmonsellingprice::"+tmonsellingprice.doubleValue());*/

							DealOption.put("salesPrice",price+Integer.parseInt(dept1.get("optionprice").toString()));				//	Integer+	판매가(단가)	O		0원 등록불가
							//등록일 경우에만 제고/노출여부 추가
							if(tType.equals("I")) {
								DealOption.put("stock", Integer.parseInt(dept1.get("optionqty").toString()));                        //	Integer+	재고	O
								if(dept1.get("isava").toString().equals("02")){
									DealOption.put("display",true);					//	Boolean	노출여부	X	true
								}else{
									DealOption.put("display",false);					//	Boolean	노출여부	X	true
								}
							}

							dealOptions.add(DealOption);
						}
						d1++;
					}
				}

			}else{
				//logger.warn("--------- 단일 옵션");
				DealOption = new HashMap<>();
				//옵션이 없는 단일 상품일 경우
				DealOption.put("vendorDealOptionNo",productcd);		//	String(1..50)	연동업체 상품키	O		unique
				DealOption.put("title",productNm);						//	String(1..100)	상품명	X		생략가능, 단 title 또는 sections 둘중 1개는 필수
				//DealOption.put("description","");				//	String	파트너커스텀정보	X
				//DealOption.put("sections","");					//	List<String(1..10)>	분류정보	X		딜 분류와 동일 사이즈 , 1개 섹션당 100자 제한
				DealOption.put("price",price);						//	Integer+	가격	X	salesPrice	현정책 기준 salesPrice와 동일해야 함
				DealOption.put("salesPrice",price);				//	Integer+	판매가(단가)	O		0원 등록불가
				//등록일 경우에만 제고/노출여부 추가
				if(tType.equals("I")) {
					DealOption.put("stock", Integer.parseInt(mProduct.get("prdqty").toString()));                        //	Integer+	재고	O
					DealOption.put("display", true);                    //	Boolean	노출여부	X	true
				}
				dealOptions.add(DealOption);
			}




            //logger.warn("--------옵션 정보 : {}",dealOptions.toString());

			product.put("dealOptions",dealOptions);               		// List<DealOption>(1..200)    옵션들 O       옵션은 200개까지만 가능

			//
			product.put("sellingprice",price);

			//
			String managedTitle = productNm;
			if(managedTitle.length() > 60){
				managedTitle = managedTitle.substring(0,60);
			}
			product.put("managedTitle",managedTitle);                 	//	String(60)  티몬 내부에서 관리될 딜이름 O



			//////////////////////////////////////////////////////////////카테고리 셋팅
			Map<String, Object> category = basicSqlSessionTemplate.selectOne("ProdMapper.selectCategory", mProduct);
			product.put("categoryNo",Long.parseLong(category.get("tmoncatecd").toString()));                 		//  Long+   티몬 카테고리 번호  O
			//product.put("subcategoryNos",0);                 	//  List<Long>  하위 카테고리 X       2개까지만 가능

			product.put("legalPermissionType","NONE");              //	DealLegalPermissionType 법적허가/신고대상 상품코드  V       가전·컴퓨터, 뷰티, 생활·주방, 식품·건강, 출산·유아동 카테고리의 경우 필수
			product.put("importAdvertisementCertificate","");   //  String  광고심의필증  V       법적허가/신고대상 상품인 경우 필수, Full Url

			product.put("maxPurchaseQty",999);                  //  Integer+    1인당 최대 구매가능 수량  X   999
			product.put("purchaseResetPeriod",7);               //	Integer+    1인당 최대 구매가능 수량 리셋 주기    X   7


			///////////////////////////////////////////////////////////////////////////대표이미지 셋팅
			List<String> mainImages = new ArrayList<>();
			String imgPath  ="";
			if(mProduct.get("imgpatha") != null) {
				//자료파일명
				imgPath = mProduct.get("imgpatha").toString().trim();
				if(imgPath.contains(productcd + "_450")) {
					imgPath = imgPath.replace(productcd + "_450", productcd + "_600");
				} else if(imgPath.contains(productcd + "_500")) {
					imgPath = imgPath.replace(productcd + "_500", productcd + "_600");
				} else if(imgPath.contains(productcd + "_650")) {
					imgPath = imgPath.replace(productcd + "_650", productcd + "_600");
				} else if(imgPath.contains(productcd + "_700")) {
					imgPath = imgPath.replace(productcd + "_700", productcd + "_600");
				}
				mainImages.add(imgPath);
			}
			product.put("mainImages",mainImages);                 		//  List<Url>(1..6) 사이트 노출용 메인이미지들  O       720X758(200kb), 동일이미지 등록 불가

			product.put("homeRecommendedImage",imgPath);             //	Url 홈추천 이미지 X   mainImages[0]   756X383(200kb) 앱 메인 특정 영역에 노출되는 이벤트성 이미지
			//product.put("dealMainVideo","");                    //	DealMainVideo   딜 대표이미지 내 동영상 정보    X

			//상세정보 500000 자로 자르기
			String contents = mProduct.get("contents").toString().trim();
			if(contents.length() > 500000){
				contents = contents.substring(0,500000);
			}

			///////////////////////////////////////////////////////////////////////////상세정보 셋팅
			String memo = "";
			if(seller.get("noticename") != null) {
				//memo = "<center><img style=\"margin:10px;max-width:800px;\" src=\"http://image.smel-chi.co.kr/sellers/"+ seller.get("noticename").toString() +"\"  onerror=\"this.src='https://image.smel-chi.co.kr/mcs/img/blank_img.png'\"></center>";
				memo = "<div style=\"text-align: center;\"><img style=\"margin:10px;max-width:800px;\" src=\"http://image.smel-chi.co.kr/sellers/"+ seller.get("noticename").toString() +"\"  onerror=\"this.src='https://image.smel-chi.co.kr/mcs/img/blank_img.png'\"></div>";
			}

			//실 판매자 정보 등록
			String sellerInfo ="<br></p><table class=\"__se_tbl\" border=\"0\" cellpadding=\"0\" cellspacing=\"1\" _se2_tbl_template=\"15\" style=\"background-color: rgb(166, 188, 209);\"><tbody>\n" +
					"<tr><td style=\"padding: 3px 4px 2px; color: rgb(255, 255, 255); text-align: left; font-weight: normal; width: 987px; height: 24px; background-color: rgb(98, 132, 171);\" class=\"\" colspan=\"2\" rowspan=\"1\"><p style=\"text-align: center; \" align=\"center\"><span style=\"font-size: 14pt;\">&nbsp;※ 판매자 정보 ※</span></p></td>\n" +
					"\n" +
					"</tr>\n" +
					"<tr><td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); text-align: left; font-weight: normal; width: 206px; height: 18px; background-color: rgb(246, 248, 250);\" class=\"\"><p style=\"text-align: center; \"><span style=\"font-size: 11pt;\">&nbsp;판매자 명</span></p></td>\n" +
					"<td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); width: 772px; height: 18px; background-color: rgb(255, 255, 255);\" class=\"\"><p><span style=\"font-size: 11pt;\">&nbsp;" + seller.get("businame") + "</span></p></td>\n" +
					"</tr>\n" +
					"<tr><td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); text-align: left; font-weight: normal; width: 206px; height: 18px; background-color: rgb(246, 248, 250);\" class=\"\"><p style=\"text-align: center; \"><span style=\"font-size: 11pt;\">&nbsp;사업자 번호</span></p></td>\n" +
					"<td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); width: 772px; height: 18px; background-color: rgb(255, 255, 255);\" class=\"\"><p><span style=\"font-size: 11pt;\">&nbsp;" + seller.get("businum") + "</span></p></td>\n" +
					"</tr>\n" +
					"<tr><td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); text-align: left; font-weight: normal; width: 206px; height: 18px; background-color: rgb(246, 248, 250);\" class=\"\"><p style=\"text-align: center; \" align=\"center\"><span style=\"font-size: 11pt;\">&nbsp;연락처&nbsp;</span></p></td>\n" +
					"<td style=\"padding: 3px 4px 2px; color: rgb(61, 118, 171); width: 772px; height: 18px; background-color: rgb(255, 255, 255);\" class=\"\"><p><span style=\"font-size: 11pt;\">&nbsp;" + seller.get("tel") + "</span></p></td>\n" +
					"</tr>\n" +
					"</tbody>\n" +
					"</table><p><br>";

			product.put("detailContents", memo + contents + sellerInfo);                   //  String(1..500000)   사이트 노출용 딜상세 내용  O

			product.put("additionalInput",false);               //	Boolean 주문시 부가정보 입력받을지 여부   X
			product.put("additionalInputTitle","");             //	String  주문시 입력받을 부가정보   X       여부가 true일 때 필수
			product.put("originCountryType","E");                //	String  원산지 표기 방식   X   N
			product.put("originCountryDetail","상세설명 참조");              // String  원산지 표기 방식 상세    X       표기방식이 "직접입력"일 때 필수

			/////////////////////////////////////////////////////////////////// 고시정보 설정
			List<Map<String, Object>> productInfos = new ArrayList<>();
				Map<String, Object> productInfo = new HashMap<>();
					List<Map<String, Object>> DealProductInfoItems = new ArrayList<>();
						Map<String, Object> DealProductInfoItem = new HashMap<>();
						List<Map<String, Object>> mnoti = basicSqlSessionTemplate.selectList("ProdMapper.selectTmonNoti", mProduct);
						for(Map<String, Object> noti : mnoti){
							DealProductInfoItem = new HashMap<>();
							DealProductInfoItem.put("section",noti.get("notiname").toString());
							DealProductInfoItem.put("description","상세설명참조");
							DealProductInfoItems.add(DealProductInfoItem);

						}
					//그룹명 설정
					productInfo.put("productType",mnoti.get(0).get("tnotigroupname").toString());
						/*DealProductInfoItem.put("section","법적허가/신고대상상품");
						DealProductInfoItem.put("description","상세설명참조");
						DealProductInfoItems.add(DealProductInfoItem);*/

						productInfo.put("productInfos",DealProductInfoItems);
			productInfos.add(productInfo);
			product.put("productInfos",productInfos);            //	List<DealProductInfo>   상품정보제공고시 정보들    O

			product.put("kcAuthSubmitType","X");                 //	String  KC인증 제출방식   O       코드표 참고 Y(제출), N(대상아님), X(컨텐츠에포함) 값중 1개를 가질 수 있으며,

			// kcAuthSubmitType 필수일 경우 kcAuths 추가
			List<Map<String, Object>> kcAuths = new ArrayList<>();
			//product.put("kcAuths",kcAuths);               			//	List<DealKcAuth>    KC인증들   X       제출타입일 경우 필수

			//택배사명 셋팅
			product.put("deliveryCorp",mProduct.get("deliverycorp").toString());                 	//	String  배송사(택배사)    X       배송상품일 경우 필수
			product.put("search",true);                 			//  Boolean 검색 노출 여부    X   true

			List<String> keywords = new ArrayList<>();
			keywords.add(mProduct.get("brandname").toString());
			product.put("keywords", keywords);                 		//	List<String>(1..5)  검색 키워드  X       5개까지만 가능
			product.put("priceComparison",true);                  //  Boolean 가격비교 노출동의 여부    X   true

			//티몬에 문의후 처리
			product.put("dealProductStatus","NEW");                //  String  상품상태 메타정보   X   NEW(신상품)
			product.put("dealSellMethod","NOT_APPLICABLE");                   //  String  판매방식 메타정보   X   NOT_APPLICABLE(해당없음)
			product.put("useCustomsIdNo",false);                   //  Boolean 주문 시, 개인통관고유번호 입력유무 X   false   productType이 해외직배송(DP02), 해외구매대행(DP03)일 경우 true로 필수, 등록이후 수정불가

			product.put("mainDealOptionNo","");                 //  String(1..50)   대표 가격으로 설정될 메인 딜옵션 번호   X   첫번째 노출 옵션
			product.put("parallelImport",false);                   //  Boolean 병행수입여부  X   false
			product.put("importDeclarationCertificate","");     //  String  수입신고필증  V       병행수입여부를 설정하면 필수, Full Url

			product.put("distanceFeeGradeUsing",false);            //  Boolean 지역별 차등배송비 사용여부  X   false
			product.put("distanceFeeGradeContents","");         //  String  지역별 차등배송비 또는 사유 V       지역별차등배송비를 사용하는 경우 필수입력


			product.put("brandName",mProduct.get("brandname").toString());                   		//   String  브랜드이름   X   (null)  브랜드이름


		}catch (Exception e){
			e.printStackTrace();
			product.put("tStatus","FAIL");
			product.put("tMessage",e.getMessage());
			return product;
		}

		return product;
	}


	/**
	 * 상품 등록 [ 연동 딜 등록 ]
	 * @param newProduct
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="basicTxManager")
	public Map<String, Object> insertProducts(Map<String, Object> newProduct){
		
		//처리결과
		Map<String, Object> result = new HashMap<>();
		//상품코드
		result.put("productcd", newProduct.get("productcd"));
		
		//검색조건
		Map<String, Object> sqlMap = new HashMap<>();
		Map<String, Object> paramMap = new HashMap<>();
		sqlMap.put("productcd", newProduct.get("productcd"));
		paramMap.put("productcd", newProduct.get("productcd"));
		
		//로그 기본값 셋팅
		Map<String, Object> logMap = new HashMap<>();
		logMap.put("sitename", "TMON");
		logMap.put("productcd", newProduct.get("productcd"));
		logMap.put("api_url", "/deals");
		logMap.put("status", BaseConst.ProdStauts.STAUTS_01);
		//멸치 상품조회
		Map<String, Object> mProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectProducts", paramMap);
		//옵션 그룹 정보
		List<Map<String, Object>> optionGroup = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductOptGroup", mProduct);
		try {	

			mProduct.put("deliverycorp",newProduct.get("deliverycorp").toString());
			//Tmon 상품조회
			Map<String, Object> tmonProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonProducts", paramMap);
			if(tmonProduct != null) {
				paramMap.put("productno", tmonProduct.get("productno"));
			}
			//마리오 쇼핑 배송비 분배 처리
			if(mProduct.get("sellercd").toString().equals("435709")){
				paramMap.put("shippolicy_no", mProduct.get("shippolicy_no").toString());
			}else{
				paramMap.put("shippolicy_no", "0");
			}
			// 판매자정보 조회
			paramMap.put("sellercd", mProduct.get("sellercd").toString());
			Map<String, Object> mSeller = basicSqlSessionTemplate.selectOne("ProdMapper.selectSeller", paramMap);

			//옵션정보
			List<Map<String, Object>> optionList = new ArrayList<>();
			if(null != optionGroup){
				optionList = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductOpts", mProduct);
			}


			//상품 매핑 정보가 있고, comm_products 테이블 Tmon column 값이 'N'인 경우 (상품이 이미 등록된 경우) -> mon='U' 로 변경
			//if (tmonProduct != null && "N".equals(mProduct.get("tmon"))) {
				//comm_products 테이블  Api정보 mon = 'U' 로 UPDATE
			//	paramMap.put("tmon", "U");
			//	basicSqlSessionTemplate.update("ProdMapper.updateProducts", paramMap);
			//	return result;
			//}
						
			//상품매핑 처리
			Map<String, Object> product = getProducts(mProduct, tmonProduct, mSeller, optionGroup, optionList,"I");

			result.put("sellingprice",product.get("sellingprice"));
			product.remove("sellingprice");
			//배송지 ,반품지 신규등록

			String path = "/deals";
			RestParameters params = new RestParameters();
			params.setBody(product);
			//logger.warn("::::::::::::::::::::::insert request = "+product.toString());
			Map<String, Object> response = null;
			if(product.get("tStatus").equals("FAIL")){
				logger.error(":::::: insert 상품 매핑시 오류 발생 ::"+product.toString());
			}else{
				response = connector.call(HttpMethod.POST, path, params);
				logger.warn("::::::::::::::::::::::insert response = "+response.toString());
			}


			//상품등록 실패
			if(null == response) {
				//상품상태값 변경
				sqlMap.put("tmon", "E");
				basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
				
				//임시옵션삭제
				//basicSqlSessionTemplate.update("ProdMapper.deleteTempTmonProductOpt", sqlMap);
				
				//로그 생성
				logMap.put("content", product.toString());
				logUtil.insertProdScheduleFailLog(logMap);
				
				//결과생성
				result.put("status", "03"); //오류
				result.put("contents", product.toString());
				this.apiKafkaClient.sendApiSyncProductHistoryMessage(result);
				return result;
			}
			Long dealNo = Long.parseLong(((Map<String, Object>)response.get("dealNo")).get(newProduct.get("productcd").toString()).toString());

			logger.warn("---dealNo : {}", dealNo);
			//멸치 DB에 상품등록
			sqlMap.put("productno", dealNo);
			sqlMap.put("supplyprice", mProduct.get("supplyprice"));
			if(null != optionGroup){
				sqlMap.put("optgroupcnt", optionGroup.size());
			}else{
				sqlMap.put("optgroupcnt", 1);
			}

			basicSqlSessionTemplate.insert("ProdMapper.insertTmonProducts", sqlMap);
			
			//옵션 동기화
			if(response.get("dealOptionNos") != null) {
				List<Map<String, Object>> optList = (List<Map<String, Object>>)product.get("dealOptions");
				Map<String, Object> dealOptionNos = (Map<String, Object>) response.get("dealOptionNos");
				for(Map<String, Object> opt : optList) {
					//맵핑테이블 업데이트
					sqlMap.put("productoptionno", Long.parseLong(dealOptionNos.get(opt.get("vendorDealOptionNo").toString()).toString()));
					sqlMap.put("productoptioncd",opt.get("vendorDealOptionNo").toString());
					sqlMap.put("isava", opt.get("display").toString());
					sqlMap.put("optionprice", Integer.parseInt(opt.get("salesPrice").toString()));
					sqlMap.put("optionqty", Integer.parseInt(opt.get("stock").toString()));

					basicSqlSessionTemplate.insert("ProdMapper.insertTmonProductOpt", sqlMap);
					//basicSqlSessionTemplate.update("ProdMapper.updateTmonProductOptByTempUitemId", sqlMap);
				}
			}
			
			sqlMap.put("tmon", "Y");
			basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
			
			//로그 입력 (상품 등록 성공)
			logMap.put("productno", dealNo.toString());
			logUtil.insertProdScheduleSuccessLog(logMap);
			
			//결과생성
			result.put("productno", dealNo.toString());
			result.put("contents", "상품등록 성공");

			logger.warn("::::: 상품 등록 SUCCESS ::::::: {}",newProduct.get("productcd").toString());
		} catch (UserDefinedException e) {

			logger.error("u상품 등록 예외발생 : {}", e.getMessage().toString());
			if(e.getMessage().toString().contains("이미 등록 완료된 딜입니다.")){
				//상품 조회후 tmon_product 매핑하기
				logger.warn("------상품 중복 발생 : {}",newProduct.get("productcd").toString());
				String path = "/deals/"+newProduct.get("productcd").toString();
				RestParameters params = new RestParameters();
				try{
					Map<String, Object> response = connector.call(HttpMethod.GET, path, params);
					//멸치 DB에 상품등록
					sqlMap.put("productno", response.get("tmonDealNo").toString());
					sqlMap.put("supplyprice", mProduct.get("supplyprice"));
					if(null != optionGroup){
						sqlMap.put("optgroupcnt", optionGroup.size());
					}else{
						sqlMap.put("optgroupcnt", 1);
					}

					basicSqlSessionTemplate.insert("ProdMapper.insertTmonProducts", sqlMap);

					//옵션 동기화
					if(response.get("dealOptionNos") != null) {
						List<Map<String, Object>> optList = (List<Map<String, Object>>)response.get("dealOptions");
						Map<String, Object> dealOptionNos = (Map<String, Object>) response.get("dealOptionNos");
						for(Map<String, Object> opt : optList) {
							//맵핑테이블 업데이트
							sqlMap.put("productoptionno", Long.parseLong(dealOptionNos.get(opt.get("vendorDealOptionNo").toString()).toString()));
							sqlMap.put("productoptioncd",opt.get("vendorDealOptionNo").toString());
							sqlMap.put("isava", opt.get("display").toString());
							sqlMap.put("optionprice", Integer.parseInt(opt.get("salesPrice").toString()));
							sqlMap.put("optionqty", Integer.parseInt(opt.get("stock").toString()));

							basicSqlSessionTemplate.insert("ProdMapper.insertTmonProductOpt", sqlMap);
							//basicSqlSessionTemplate.update("ProdMapper.updateTmonProductOptByTempUitemId", sqlMap);
						}
					}

					sqlMap.put("tmon", "Y");
					basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
				}catch (Exception e1){
					e1.printStackTrace();
                    sqlMap.put("tmon", "N");
                    basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
                    result.put("status", "03"); //오류
                    result.put("contents", "상품등록 (이미 등록된 상품 체크) 오류발생 : " + e.getMessage());
                    return result;
				}



			}else{
				//상품상태값 변경
				sqlMap.put("tmon", "E");
				basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);

				//로그 생성
				logMap.put("content", e.getMessage());
				logUtil.insertProdScheduleFailLog(logMap);

				//결과생성
				result.put("status", "03"); //오류
				result.put("contents", "상품등록 오류발생 : " + e.getMessage());
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("상품 등록 예외발생 : ", e.getMessage());
			//상품상태값 변경
			sqlMap.put("tmon", "E");
			basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
			
			//로그 생성
			logMap.put("content", e.getMessage());
			logUtil.insertProdScheduleFailLog(logMap);
			
			//결과생성
			result.put("status", "03"); //오류
			result.put("contents", "상품등록 오류발생 : " + e.getMessage());
		}

		this.apiKafkaClient.sendApiSyncProductHistoryMessage(result);
		return result;
	}

	/**
	 *  상품 수정 [ 딜 정보 수정 ]
	 * @param newProduct
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="basicTxManager")
	public Map<String, Object> updateProducts(Map<String, Object> newProduct) {

		logger.warn("------editProduct :" + newProduct.toString());
		//처리결과
		Map<String, Object> result = new HashMap<>();
		//상품코드
		result.put("productcd", newProduct.get("productcd"));
		
		//검색조건
		Map<String, Object> sqlMap = new HashMap<>();
		Map<String, Object> paramMap = new HashMap<>();
		sqlMap.put("productcd", newProduct.get("productcd"));
		paramMap.put("productcd", newProduct.get("productcd"));
		
		//로그 기본값 셋팅
		Map<String, Object> logMap = new HashMap<>();
		logMap.put("sitename", "TMON");
		logMap.put("productcd", newProduct.get("productcd"));
		logMap.put("api_url", "/deals/"+newProduct.get("productcd"));
		logMap.put("status", BaseConst.ProdStauts.STAUTS_02);

		String path = "/deals/"+ newProduct.get("productcd").toString()+"/pause";
		RestParameters params = new RestParameters();
		Map<String, Object> response = null;

		try {
			//멸치 상품조회
			Map<String, Object> mProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectProducts", paramMap);
			mProduct.put("deliverycorp", newProduct.get("deliverycorp").toString());
			//Tmon 상품조회
			Map<String, Object> tmonProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonProducts", paramMap);
			if(null != tmonProduct) {
				paramMap.put("productno", tmonProduct.get("productno"));
			}
			//마리오 쇼핑 배송비 분배 처리
			if(mProduct.get("sellercd").toString().equals("435709")){
				paramMap.put("shippolicy_no", mProduct.get("shippolicy_no").toString());
			}else{
				paramMap.put("shippolicy_no", "0");
			}
			// 판매자정보 조회
			paramMap.put("sellercd", mProduct.get("sellercd").toString());
			Map<String, Object> mSeller = basicSqlSessionTemplate.selectOne("ProdMapper.selectSeller", paramMap);
			//옵션 그룹 정보
			List<Map<String, Object>> optionGroup = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductOptGroup", mProduct);
			//옵션정보
			//옵션정보
			List<Map<String, Object>> optionList = new ArrayList<>();
			if(null != optionGroup){
				optionList = basicSqlSessionTemplate.selectList("ProdMapper.selectCommProductEditOpts", mProduct);
			}

			//Tmon 상품이 미등록인경우 신규로 상품을 등록하도록 플래그값 변경
			if (tmonProduct == null && "U".equals(mProduct.get("tmon"))) {
				//comm_products 테이블 Api 정보 ssg = 'N' 로 UPDATE
				paramMap.put("tmon", "N");
				basicSqlSessionTemplate.update("ProdMapper.updateProducts", paramMap);
				return result;
			}

			//상품 판매일시정지 / 판매 재개 처리
			/////////////상태값이 판매중이 아니거나  옵션 그룹이 달라질 경우 판매 중지 처리
			if(!newProduct.get("status").toString().equals("02") || (!newProduct.get("optgroupcnt").toString().equals(tmonProduct.get("optgroupcnt").toString()) && !tmonProduct.get("optgroupcnt").toString().equals("0"))) {
				//판매 일지 정지
				setStopProductManual(newProduct.get("productcd").toString());
				logger.warn("------상태값 및 옵션그룹값 변경으로 인한 판매일시중지 완료: {} ",newProduct.get("productcd").toString());
				sqlMap.put("tmon", "D");
			}else{
				//판매 재개 및 수정
				if(tmonProduct.get("status").toString().equals("D")){
					path = "/deals/"+ newProduct.get("productcd").toString()+"/resume";
					response = connector.call(HttpMethod.PUT, path, params);
					basicSqlSessionTemplate.update("ProdMapper.updateTmonProductsResume", sqlMap);
					//logger.warn("------판매 재개 완료 : ");
				}

				//상품매핑
				Map<String, Object> product = getProducts(mProduct, tmonProduct, mSeller, optionGroup,optionList,"U");
				result.put("sellingprice",product.get("sellingprice"));
				product.remove("sellingprice");
				result.put("supplyprice", mProduct.get("supplyprice"));
				//판매 수정 처리
				path = "/deals/"+ newProduct.get("productcd").toString();
				params.setBody(product);
				//logger.warn("::::::::::::::::::::::update request = "+product.toString());
				if(product.get("tStatus").equals("FAIL")){
					logger.error("::::::update 상품 매핑시 오류 발생 ::"+product.toString());
				}else{
					//logger.warn("::::::update 상품  ::"+product.toString());
					response = connector.call(HttpMethod.PUT, path, params);
					logger.warn("::::::::::::::::::::::update response = "+response.toString());
					Map<String, Object> deal = (Map<String, Object>)response.get("deal");
					Map<String, Object> dealOptions = (Map<String, Object>)response.get("dealOptions");
					if(!response.isEmpty()){
						if(deal.get("success").toString().equals("true") && dealOptions.get("success").toString().equals("true")){
							Map<String, Object> oParam = new HashMap<>();
							//수정이 완료 됬을 경우 1.옵션 재고 수정
							path = "/deals/"+ newProduct.get("productcd").toString()+"/stock";
							Map<String, Object> optionStock = getDealOptionDspStock(mProduct,optionGroup,optionList,"S");
							if(optionStock.get("tStatus").toString().equals("SUCCESS")){
								oParam.put("dealOptions",(List<Map<String, Object>>)optionStock.get("dealOptions"));
								params.setBody(oParam);
								response = connector.call(HttpMethod.PUT, path, params);
								logger.warn("------옵션 재고 수정 결과 : {} : {}",newProduct.get("productcd").toString(), response.toString());
							}else{
								setStopProductManual(newProduct.get("productcd").toString());
								logger.warn("------옵션그룹 재고 변경 오류로 판매일시중지 완료: {} ",newProduct.get("productcd").toString());
								sqlMap.put("tmon", "E");
								basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
								//로그 생성
								logMap.put("content", "옵션그룹 재고 변경 오류로 판매일시중지 완료!");
								logUtil.insertProdScheduleFailLog(logMap);
								//결과생성
								result.put("status", "03"); //오류
								result.put("contents", response.toString());
								return result;
							}
							Map<String, Object> optionDisplay = getDealOptionDspStock(mProduct,optionGroup,optionList,"D");
							if(optionDisplay.get("tStatus").toString().equals("SUCCESS")){
								oParam.put("dealOptions",(List<Map<String, Object>>)optionDisplay.get("dealOptions"));
								path = "/deals/"+ newProduct.get("productcd").toString()+"/options/display";
								params.setBody(oParam);
								//옵션이 한개 이상일 경우만 처리
								if(optionGroup.size() > 0){
									response = connector.call(HttpMethod.PUT, path, params);
									logger.warn("------옵션 상태 수정 결과 : {} ",newProduct.get("productcd").toString());
								}

							}else{
								setStopProductManual(newProduct.get("productcd").toString());
								logger.warn("------옵션그룹 상태 변경 오류로 판매일시중지 완료: {} ",newProduct.get("productcd").toString());
								sqlMap.put("tmon", "E");
								basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
								//로그 생성
								logMap.put("content", "옵션그룹 상태 변경 오류로 판매일시중지 완료!");
								logUtil.insertProdScheduleFailLog(logMap);
								//결과생성
								result.put("status", "03"); //오류
								result.put("contents", response.toString());
								return result;
							}

							//response = connector.call(HttpMethod.PUT, path, params);
							//수정이 완료 됬을 경우 1.옵션 상태 수정
						}else{
							//수정 실패시
							logger.error("::::: 상품 수정 ERROR :::::::"+response.toString());
							sqlMap.put("tmon", "E");
							basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
							//로그 생성
							logMap.put("content", response.toString());
							logUtil.insertProdScheduleFailLog(logMap);
							//결과생성
							result.put("status", "03"); //오류
							result.put("contents", response.toString());
							//판매 일지 정지
							setStopProductManual(newProduct.get("productcd").toString());
							return result;
						}
					}else{
						//수정 실패시
						logger.error("::::: 상품 수정 ERROR :::::::[티몬API] 알수 없는 오류 발생! : {}",newProduct.get("productcd"));
						sqlMap.put("tmon", "E");
						basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
						//로그 생성
						logMap.put("content", "[티몬API] 알수 없는 오류 발생! : "+newProduct.get("productcd"));
						logUtil.insertProdScheduleFailLog(logMap);
						//결과생성
						result.put("status", "03"); //오류
						result.put("contents", response.toString());
						//판매 일지 정지
						setStopProductManual(newProduct.get("productcd").toString());
						logger.warn("------수정 실패로 인한 판매일시중지 완료 1 : ");
						return result;
					}


				}
				//logger.warn("---------------response"+response);
				//수행결과 생성
				result.put("productno", tmonProduct.get("productno"));

				//옵션 동기화
				sqlMap.put("supplyprice", mProduct.get("supplyprice"));
				basicSqlSessionTemplate.insert("ProdMapper.updateTmonProducts", sqlMap);
				logger.warn("::::: 상품 수정 SUCCESS ::::::: {}",newProduct.get("productcd").toString());
			}

			result.put("supplyprice", mProduct.get("supplyprice"));
			result.put("status", "02"); //성공

			sqlMap.put("tmon", "Y");
			basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
			
			//로그 입력 (상품 등록 성공) 
			logMap.put("productno", tmonProduct.get("productno"));
			logUtil.insertProdScheduleSuccessLog(logMap);
			
			//결과생성
			result.put("contents", "상품수정 성공");
		} catch (UserDefinedException e) {
			e.printStackTrace();
			//logger.error("상품 등록/수정 [UserDefinedException]발생 : ", e.getMessage());
			//상품상태값 변경
			sqlMap.put("tmon", "E");
			logger.error("::::: 상품 수정 ERROR :::::::"+e.getMessage());
			basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
			//로그 생성
			logMap.put("content", e.getMessage());
			logUtil.insertProdScheduleFailLog(logMap);

			//결과생성
			result.put("status", "03"); //오류
			result.put("contents", "상품수정 예외발생 : " + e.getMessage());

			//판매 일지 정지
			setStopProductManual(newProduct.get("productcd").toString());
			logger.warn("------수정 실패로 인한 판매일시중지 완료 2 : ");
		} catch (Exception e) {

			logger.error("상품 수정 예외발생 : ", e.getMessage());
			//상품상태값 변경
			sqlMap.put("tmon", "E");
			basicSqlSessionTemplate.update("ProdMapper.updateProducts", sqlMap);
			
			//로그 생성
			logMap.put("content", e.getMessage());
			logUtil.insertProdScheduleFailLog(logMap);
			
			//결과생성
			result.put("status", "03"); //오류
			result.put("contents", "상품수정 오류발생 : " + e.getMessage());

			//판매 일지 정지
			setStopProductManual(newProduct.get("productcd").toString());
			logger.warn("------수정 실패로 인한 판매일시중지 완료 3 : ");

			e.printStackTrace();
		}

		this.apiKafkaClient.sendApiSyncProductHistoryMessage(result);
		return result;
	}


	/**
	 * 딜 판매 일시중지
	 * @param productcd
	 */
	public void setStopProduct(String productcd){
		try {
			String path = "";
			RestParameters params = new RestParameters();
			Map<String, Object> response = null;
			//판매 일지 정지
			path = "/deals/"+ productcd +"/pause";
			response = connector.call(HttpMethod.PUT, path, params);
			logger.warn("------setStopProduct: ");

			Map<String, Object> sqlMap = new HashMap<>();
			sqlMap.put("productcd", productcd);
			basicSqlSessionTemplate.insert("ProdMapper.updateTmonProductsPause", sqlMap);
		}catch (Exception e){
			logger.warn("-----------setStopProduct Error: ");
			e.printStackTrace();

		}
	}

	/**
	 * 상품QnA 등록 [ 상품문의 조회 ]
	 * @param qna
	 */
	@Override
	@Transactional(value="basicTxManager")
	public void insertQna(Map<String, Object> qna) {
		Map<String, Object> sqlMap = new HashMap<String, Object>();
		//글번호
		sqlMap.put("api_id", qna.get("articleNo").toString());
		//사이트
		sqlMap.put("sitename", "TMON");
		// comm_qna_products_question 조회
		Map<String, Object> qnaQuestionMap = basicSqlSessionTemplate.selectOne("ProdMapper.selectQnaQuestionDetail", sqlMap);
		if (qnaQuestionMap == null) {
			//상품번호
			sqlMap.put("productno", qna.get("dealNo"));
			Map<String, Object> tProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonProducts", sqlMap);
			if (tProduct != null) {
				//상품코드(MH)
				sqlMap.put("m_productcd", tProduct.get("productcd"));
			}
			sqlMap.put("api_seq", "");

			//제목
			sqlMap.put("subject", qna.get("dealTitle"));
			//질문내용
			sqlMap.put("contents", "[ "+qna.get("createDate").toString() +" ] "+qna.get("content"));
			//고객이름
			sqlMap.put("username", qna.get("writer"));
			//문의유형
			sqlMap.put("qnatype", "상품");
			//주문번호
			sqlMap.put("ordercd", qna.get("tmonOrderNo"));


			if (qna.get("tmonOrderNo") != null) {
				Map<String, Object> orders = basicSqlSessionTemplate.selectOne("ProdMapper.selectOrders", sqlMap);
				if (orders != null) {
					//멸치주문코드
					sqlMap.put("m_ordercd", orders.get("m_ordercd"));
				}
			}
			//comm_qna_products_question 테이블 INSERT
			basicSqlSessionTemplate.insert("ProdMapper.insertQnaQuestion", sqlMap);
		} else {
			//comm_qna_products_question 테이블 UPDATE
			//basicSqlSessionTemplate.update("ProdMapper.updateQnaQuestion", sqlMap);
		}
	}


	/**
	 *  상품QnA답변 등록 [ 상품문의 답변 ]
	 * @param qnaAnswer
	 * @throws UserDefinedException
	 */
	@Override
	//@Transactional(value="basicTxManager")
	public void updateQnaAnswer(Map<String, Object> qnaAnswer) throws UserDefinedException  {
		/* QNA 답변등록 API호출 */
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		params.setPathVariableParameters(paramMap);
		paramMap.put("content",qnaAnswer.get("contents").toString());//답변내용
		params.setBody(paramMap);
		String path = "/qnas/"+qnaAnswer.get("api_id")+"/replies";
		String result = connector.callString(HttpMethod.POST, path, params);
		if(null != result){
			logger.warn("============ updateQnaAnswer : "+ result.toString());
		}

		Map<String, Object> sqlMap = new HashMap<>();
		//결과저장
		/*
		if(result != null && result.get("resultCode") != null && "00".equals(result.get("resultCode"))){
			sqlMap.put("api_id", result.get("postngId"));
		} else {

		}*/
		sqlMap.put("api_id", qnaAnswer.get("api_id"));
		sqlMap.put("sitename", "TMON");							//싸이트
		basicSqlSessionTemplate.update("ProdMapper.updateQnaAnswer", sqlMap);
	}



	/**
	 * CS 문의 등록 [ 상품문의 조회 ]
	 * @param qna
	 */
	@Override
	//@Transactional(value="basicTxManager")
	public void insertCsQna(Map<String, Object> qna) {
		Map<String, Object> sqlMap = new HashMap<String, Object>();
		try{
			//글번호
			sqlMap.put("api_id", qna.get("csInquirySeqNo").toString());
			//
			sqlMap.put("api_seq", qna.get("csInquiryMessageGroupSeqNo").toString());
			//사이트
			sqlMap.put("sitename", "TMON");

			// comm_qna_products_question 조회
			Map<String, Object> qnaQuestionMap = basicSqlSessionTemplate.selectOne("ProdMapper.selectQnaQuestionDetail", sqlMap);
			if (qnaQuestionMap == null) {
				//상품번호
				sqlMap.put("productno", qna.get("dealNo"));
				Map<String, Object> tProduct = basicSqlSessionTemplate.selectOne("ProdMapper.selectTmonProducts", sqlMap);
				if (tProduct != null) {
					//상품코드(MH)
					sqlMap.put("m_productcd", tProduct.get("productcd"));
				}


				//제목
				sqlMap.put("subject", qna.get("dealTitle"));

				// [ CS문의 상세 조회 ]
				RestParameters params = new RestParameters();
				Map<String, Object> paramMap = new HashMap<>();
				String path = "/cs-inquiry/detail/"+qna.get("csInquirySeqNo");
				params.setPathVariableParameters(paramMap);
				params.setBody(paramMap);

				Map<String, Object> detail = connector.call(HttpMethod.GET, path, params);

				Map<String, Object> categoryInfo = (Map<String, Object>)qna.get("categoryInfo");
				String tContents = "";

				if(qna.get("emergencyYn").toString().equals("true")){
					tContents += "[긴급]";
				}
				if(qna.get("heavyClaimYn").toString().equals("true")){
					tContents += "[중대]";
				}

				//질문내용
				sqlMap.put("contents", "[제휴사 문의] "+tContents +"[ "+categoryInfo.get("name") +" ] "+" / "+detail.get("customerMessage"));
				//고객이름
				sqlMap.put("username", qna.get("writer"));
				//문의유형
				sqlMap.put("qnatype", "CS");
				//주문번호
				sqlMap.put("ordercd", qna.get("orderNo"));


				if (qna.get("orderNo") != null) {
					Map<String, Object> orders = basicSqlSessionTemplate.selectOne("ProdMapper.selectOrders", sqlMap);
					if (orders != null) {
						//멸치주문코드
						sqlMap.put("m_ordercd", orders.get("m_ordercd"));
					}
				}
				//comm_qna_products_question 테이블 INSERT
				basicSqlSessionTemplate.insert("ProdMapper.insertQnaQuestion", sqlMap);
			} else {
				//comm_qna_products_question 테이블 UPDATE
				//basicSqlSessionTemplate.update("ProdMapper.updateQnaQuestion", sqlMap);
			}

		}catch (UserDefinedException e){
			e.printStackTrace();
		}


	}


	/**
	 *  CS 문의 답변 등록 [ 상품문의 답변 ]
	 * @param qnaAnswer
	 * @throws UserDefinedException
	 */
	@Override
	//@Transactional(value="basicTxManager")
	public void updateCsQnaAnswer(Map<String, Object> qnaAnswer) throws UserDefinedException  {
		/* CS 문의 등록 API호출 */
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		params.setPathVariableParameters(paramMap);
		paramMap.put("message",qnaAnswer.get("contents").toString());//답변내용
		params.setBody(paramMap);
		String path = "/cs-inquiry/save/"+qnaAnswer.get("api_id")+"/"+qnaAnswer.get("api_seq");
		Map<String, Object> result = connector.call(HttpMethod.POST, path, params);

		logger.warn("============ updateQnaAnswer : "+ result.toString());

		Map<String, Object> sqlMap = new HashMap<>();
		//결과저장
		/*
		if(result != null && result.get("resultCode") != null && "00".equals(result.get("resultCode"))){
			sqlMap.put("api_id", result.get("postngId"));
		} else {

		}*/
		sqlMap.put("api_id", qnaAnswer.get("api_id"));
		sqlMap.put("sitename", "TMON");							//싸이트
		basicSqlSessionTemplate.update("ProdMapper.updateQnaAnswer", sqlMap);
	}





}
	 