package com.melchi.common.exception;

//키 값으로 수정 또는 삭제 했는데 해당 항목이 없는 경우
public class SuchNoKeyException extends Exception {

	private static final long serialVersionUID = -2093145268554465868L;
	
	public SuchNoKeyException() {		
		super(); 		
	}
	public SuchNoKeyException(String msg) {		
		super(msg);		
	}
}
   