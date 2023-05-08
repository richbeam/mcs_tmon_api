package com.melchi.common.exception;

/**
 * 사용자 정의예외
 * 
 * @author user
 *
 */
public class UserDefinedException extends Exception {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 100 : 카테고리 미등록
	 */
	public final static String CATEGORY_ERROR = "100";
	
	/**
	 * 700 : API호출 중에 타임아웃 오류가 발생했습니다.
	 */
	public final static String TIMEOUT_ERROR = "700";
	/**
	 * 999 : 알수없는에러
	 */
	public final static String ETC_ERROR = "999";
	
	/**
	 * 에러코드
	 */
	private String code;
	
	/**
	 * 에러메세지
	 */
	private String message;
	
	/**
	 * 생성자
	 */
	public UserDefinedException() {		 
		super(); 		
	}
	
	/**
	 * 생성자
	 * @param code
	 * @param message
	 */
	public UserDefinedException(String code, String message) {
		super();
		this.code = code;
		this.message = message;
	}

	/**
	 * @return the code
	 */
	public String getCode() {
		return code;
	}

	/**
	 * @param code the code to set
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}
}