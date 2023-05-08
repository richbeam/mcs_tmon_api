package com.melchi.common.exception;

//NULL 제약 조건을 위반
public class NullConstraintException extends Exception {

	private static final long serialVersionUID = -2093145268554465868L;
	
	public NullConstraintException() {		
		super(); 		
	}
	public NullConstraintException(String msg) {		
		super(msg);		
	}
}
 