package com.tmon.api.module.prod;

import java.util.List;
import java.util.Map;

import com.melchi.common.exception.UserDefinedException;

/**
 * 상품 서비스
 * 
 * @author jipark
 */
public interface ProdService {

	/**
	 * 상품 등록 [ 연동 딜 등록 ]
	 * 
	 * @param newProduct
	 * @return
	 */
	public Map<String, Object> insertProducts(Map<String, Object> newProduct);
	
	/**
	 * 상품 수정 [ 딜 정보 수정 ]
	 * 
	 * @param newProduct
	 * @return
	 */
	public Map<String, Object> updateProducts(Map<String, Object> newProduct);


	/**
	 * 상품QnA 등록 [ 상품문의 조회 ]
	 * @param qna
	 */
	public void insertQna(Map<String, Object> qna);

	/**
	 * 상품QnA답변 등록 [ 상품문의 답변 ]
	 * @param qnaAnswer
	 * @throws UserDefinedException
	 */
	public void updateQnaAnswer(Map<String, Object> qnaAnswer) throws UserDefinedException;


	/**
	 *  CS 문의 등록 [ CS 문의 조회 ]
	 * @param qna
	 */
	public void insertCsQna(Map<String, Object> qna);

	/**
	 * CS 문의 답변 등록 [ CS 문의 답변 ]
	 * @param qnaAnswer
	 * @throws UserDefinedException
	 */
	public void updateCsQnaAnswer(Map<String, Object> qnaAnswer) throws UserDefinedException;












	/**
	 * 상품조회
	 * 
	 * @param mProduct
	 * @param tmonProduct
	 * @param seller
	 * @return
	 * @throws UserDefinedException
	 */
	public Map<String, Object> getProducts(Map<String, Object> mProduct, Map<String, Object> tmonProduct, Map<String, Object> seller, List<Map<String, Object>> optionGroup, List<Map<String, Object>> optionList, String tType) throws UserDefinedException;
	
	/**
	 * 상품금액수정(재고, 가격, 상태)변경건 반영
	 * 
	 * @param change
	 * @return
	 */
	public Map<String, Object> updateProductChange(Map<String, Object> change);
	
	/**
	 *셀러 판매/품절건 반영
	 * 
	 * @param change
	 * @return
	 */
	public Map<String, Object> updateModifiedSellerProduct(Map<String, Object> change);
	
	/**
	 *셀러 판매/품절건 반영
	 * 
	 * @param change
	 * @return
	 */
	public Map<String, Object> updateDeletedSellerProduct(Map<String, Object> change);
	
	/**
	 * 출고/반품 주소 설정
	 *  
	 * @param seller
	 * @param gubuncd
	 * @return
	 * @throws UserDefinedException
	 */
	public String setAddrId(Map<String, Object> seller, String gubuncd) throws UserDefinedException;
	
	/**
	 * 배송비 정책 설정
	 * 
	 * @param shppcstPlcyDivCd
	 * @param shppcstAplUnitCd
	 * @param item
	 * @return
	 * @throws UserDefinedException
	 */
	public String setShppcstPlcy(String shppcstPlcyDivCd, String shppcstAplUnitCd, Map<String, Object> item) throws UserDefinedException;
	











	/**
	 * 승인전 상품수정 (연동해지) 보류건 조회
	 * @param change
	 * 
	 */
	public Map<String, Object> getApprovalPengding(Map<String, Object> change);
	
	/**
	 * 승인전 보류건 대상 승인완료 조회
	 * @param change
	 * @return
	 */
	public Map<String, Object> getApprovalComplete(Map<String, Object> change);
	
	/**
	 * 판매중지 상품 대상 상태조회
	 * @param request
	 * @return
	 */
	public Map<String, Object> getProductStatus(Map<String, Object> request);
}
 