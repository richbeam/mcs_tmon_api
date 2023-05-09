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










	


}
 