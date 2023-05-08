package com.tmon.api.module.order;

import java.util.Map;

import com.melchi.common.exception.UserDefinedException; 

/**
 * 주문서비스
 * 
 * @author jipark
 */
public interface OrderService {


	/**
	 * 주문번호로 배송 상태값 조회
	 * @param tmonOrderNo
	 * @param tmonDealNo
	 * @return
	 * @throws Exception
	 */
	public String getOrderStatus(String tmonOrderNo, String tmonDealNo)throws Exception;

	
	/**
	 * 결제완료 주문건 신규 생성처리 [배송 대상 수집]
	 * 
	 * @param order
	 * @throws UserDefinedException
	 */
	public void registOrders(Map<String, Object> order) throws UserDefinedException;

	/**
	 * 주문확인 연동 처리 [ 배송 대상 확인 ]
	 *
	 * @param order
	 * @throws UserDefinedException
	 */
	public void confirmOrders(Map<String, Object> order) throws UserDefinedException;

	/**
	 * 배송중 - 송장등록 처리 [ 송장 등록/수정 ]
	 * 
	 * @param order
	 * @throws UserDefinedException
	 */
	public void shipOrders(Map<String, Object> order) throws UserDefinedException;


	/**
	 * 주문건 상태값 동기화 [배송 정보 검색]
	 *
	 * @param order
	 * @throws UserDefinedException
	 */
	public void syncOrderStatus(Map<String, Object> order) throws Exception;



	/**
	 * 배송지연
	 *
	 * @param order
	 * @throws UserDefinedException
	 */
	public void delayOrders(Map<String, Object> order) throws UserDefinedException;

	/**
	 * 배송완료
	 * 
	 * @param order
	 * @throws UserDefinedException
	 */
	public void compShipOrders(Map<String, Object> order) throws UserDefinedException;
	
	/**
	 * 배송완료
	 * 
	 * @param order
	 */
	public void compShipOrdersByTmon(Map<String, Object> order);
	
	/**
	 * 교환발송처리
	 * 
	 * @param order
	 * @throws UserDefinedException
	 */
	public void shipChangeOrders(Map<String, Object> order) throws UserDefinedException;
}