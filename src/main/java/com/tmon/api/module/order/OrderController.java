package com.tmon.api.module.order;

import com.melchi.common.constant.BaseConst;
import com.melchi.common.response.Response;
import com.melchi.common.util.StringUtil;
import com.melchi.common.vo.RestParameters;
import com.tmon.api.TmonConnector;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Api(tags = {"3. Order"})   
@RequiredArgsConstructor  
@RestController 
@RequestMapping(value = "/v1/order")
public class OrderController { 

	static final Logger logger = LoggerFactory.getLogger(OrderController.class);

	@Autowired
	OrderService orderService;

	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;

	@Autowired
	TmonConnector connector;


	/* REST 주문확인 처리 단건  */
	@ApiOperation(value = "[TMON] 신규 주문 조회처리 Manual", notes = "[TMON] Manual 신규주문을 조회 한다.", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/getTmonOrderListManual", method = {RequestMethod.GET})
	public Response getTmonOrderList (
			@ApiParam(value = "조회시작일", name = "startDate", defaultValue = "2023-04-10T10:00:00", required = false) @RequestParam String startDate,
			@ApiParam(value = "조회종료일", name = "endDate", defaultValue = "2023-04-12T10:00:00", required = false) @RequestParam String endDate
	) throws Exception {
		Response response = new Response();
		logger.warn(">>getTmonOrderListTest Manual 결제완료 주문건조회 시작");
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		String path = "/orders";

		List<Map<String, Object>> orders = null;

		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);
		paramMap.put("startDate", startDate);
		paramMap.put("endDate", endDate);
		paramMap.put("deliveryStatus", "D1");   //D1, D2 만 가능
		params.setRequestParameters(paramMap);

		orders = connector.callList(HttpMethod.GET, path, params);

		logger.warn("------------주문 테스트 ::::{}",orders.toString());
		//주문등록
		if(orders.size() > 0) {
			//logger.warn(">>> requestOrders 있음: today {} / orders {}", today, orders);
			for(Map<String, Object> order : orders) {
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
					logger.warn(">>> requestOrders error {}", e.getMessage());
				}
			}
		} else {
			logger.warn(">>> requestOrders 없음: {}", orders);
		}
		logger.warn(">>getTmonOrderListTest Manual 결제완료 주문건조회 종료");


		return response;
	}

	/* REST 주문확인 처리 단건  */
	@ApiOperation(value = "[TMON] 신규 주문 조회처리", notes = "[TMON] 신규주문을 조회 한다.", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/getTmonOrderList", method = {RequestMethod.GET})
	public Response getTmonOrderListTest (
			/*@ApiParam(value = "조회시작일", name = "perdStrDts", defaultValue = "20220921", required = false) @RequestParam String perdStrDts,
			@ApiParam(value = "조회종료일", name = "perdEndDts", defaultValue = "20220921", required = false) @RequestParam String perdEndDts*/
	) throws Exception {
		Response response = new Response();
		logger.warn(">>getTmonOrderListTest 결제완료 주문건조회 시작");
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		String path = "/orders";

		List<Map<String, Object>> orders = null;

		String today = StringUtil.getTodayString("yyyy-MM-dd HH:mm");

		String startDate = StringUtil.orderTimeAdd(today, -1530);
		String endDate = StringUtil.orderTimeAdd(today, -30);
		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);
		paramMap.put("startDate", startDate);
		paramMap.put("endDate", endDate);
		paramMap.put("deliveryStatus", "D1");   //D1, D2 만 가능
		params.setRequestParameters(paramMap);
		orders = connector.callList(HttpMethod.GET, path, params);

		logger.warn("------------주문 테스트 ::::{}",orders.toString());
		//주문등록
		if(orders.size() > 0) {
			//logger.warn(">>> requestOrders 있음: today {} / orders {}", today, orders);
			for(Map<String, Object> order : orders) {
				try {
					orderService.registOrders(order);
				} catch (Exception e) {
					e.printStackTrace();
					logger.warn(">>> requestOrders error {}", e.getMessage());
				}
			}
		} else {
			logger.warn(">>> requestOrders 없음: {}", orders);
		}
		logger.warn(">>getTmonOrderListTest 결제완료 주문건조회 종료");


		return response;
	}



	/* REST 주문확인 처리 단건  */
	@ApiOperation(value = "[TMON] 상태값 연동 테스트(03,04) ", notes = "[TMON] 상태값 연동 테스트(03,04).", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/orderStatusChange", method = {RequestMethod.GET})
	public Response orderStatusChange (
			@ApiParam(value = "상태값", name = "status", defaultValue = "03", required = false) @RequestParam String status
	) throws Exception {
		Response response = new Response();

		if(status.equals("03")){

			logger.warn(">>주문확인 처리 시작 test");
			//대상 조회
			Map<String, Object> paramMap = new HashMap<>();
			paramMap.put("sitename"    , "TMON");                //사이트명
			paramMap.put("status"      , BaseConst.OrderStauts.STAUTS_03); //주문상태 주문확인
			paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
			List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetail", paramMap);

			//주문 확인처리
			if(orders != null) {
				for(Map<String, Object> order : orders) {
					try {
						orderService.confirmOrders(order);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				logger.warn(">>> 주문확인 내역없음: {}", orders);
			}
			logger.warn(">>주문조회 주문확인 종료 test");


		}else if(status.equals("04")){

			logger.warn(">>송장등록 처리 시작");
			//대상 조회
			Map<String, Object> paramMap = new HashMap<>();
			paramMap.put("sitename"    , "TMON");              //사이트명
			paramMap.put("status"      , BaseConst.OrderStauts.STAUTS_04); //주문상태 배송중
			paramMap.put("apiindexing" , "U");                   //멸치쇼핑 이관여부
			List<Map<String, Object>> orders = basicSqlSessionTemplate.selectList("OrderMapper.selectCommOrderDetailShip", paramMap);

			//발송처리
			if(orders != null) {
				for(Map<String, Object> order : orders) {
					try {
						orderService.shipOrders(order);
					} catch (Exception e) {
						logger.warn("shipOrders ::: {}", e.getMessage());
						e.printStackTrace();
					}
				}
			} else {
				logger.warn(">>> 주문확인 내역없음: {}", orders);
			}
			logger.warn(">>송장등록 처리 종료");


		}


		return response;
	}
}
