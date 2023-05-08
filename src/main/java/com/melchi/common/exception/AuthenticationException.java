package com.melchi.common.exception;

public class AuthenticationException extends Exception {

	private static final long serialVersionUID = -2093145268554465868L;
	
	public AuthenticationException() {		
		super(); 		
	}
	public AuthenticationException(String msg) {		
		super(msg);		
	}
}
  