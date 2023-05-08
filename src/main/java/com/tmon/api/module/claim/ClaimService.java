package com.tmon.api.module.claim;

import java.util.Map;

import com.melchi.common.exception.UserDefinedException;

/**
 * 클래임 서비스
 * 
 * @author jipark
 */
public interface ClaimService {


	/**
	 * 클레임 번호로  상태값 조회
	 * @param type
	 * @param claimNo
	 * @return
	 * @throws UserDefinedException
	 */
	public Map<String, Object> getClaimStatus(String type, String claimNo)throws UserDefinedException;

	/**
	 * 취소건 수집 [ 취소 대상 수집 ]
	 *
	 * @param order
	 */
	public void cancelRequests(Map<String, Object> order);

	/**
	 * 주문취소요청 [ 취소 요청/승인/거절 ]
	 *
	 * @param order
	 */
	public void requestOrderCancel(Map<String, Object> order) throws UserDefinedException;




	/**
	 * 반품건 수집 [ 환불 대상 수집 ]
	 *
	 * @param order
	 */
	public void returnRequests(Map<String, Object> order);

	/**
	 * 반품완료  [ 환불요청 승인 ]
	 *
	 * @param order
	 */
	public void confirmReturnOrders(Map<String, Object> order) throws UserDefinedException;



	/**
	 * 교환건 수집 [ 교환 대상 수집 ]
	 *
	 * @param order
	 */
	public void exchangesRequests(Map<String, Object> order);

	/**
	 * 재배송 수집 [ 재배송 대상 수집 ]
	 *
	 * @param order
	 */
	public void redeliveriesRequests(Map<String, Object> order);


	/**
	 * 교환 배송중 처리 [ 교환 요청 승인  / 재배송요청 승인 ]
	 *
	 * @param order
	 */
	public void confirmExchangeOrders(Map<String, Object> order) throws UserDefinedException;














	/**
	 * 취소조회(Tmon -> 멸치API)
	 * 
	 * @param order
	 */
	public void cancelOrders(Map<String, Object> order);
	

	
	/**
	 * 반품교환회수  
	 * 
	 * @param order
	 */
	public void returnOrders(Map<String, Object> order);
		

	
	/**
	 * 회수 완료
	 * 
	 * @param order
	 */
	public void completeReturnOrders(Map<String, Object> order) throws UserDefinedException;

}
 