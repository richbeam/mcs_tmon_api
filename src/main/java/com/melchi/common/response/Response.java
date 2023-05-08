package com.melchi.common.response;
 
import java.io.Serializable;
import java.util.HashMap;

import com.melchi.common.constant.BaseConst;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 		Common API Response 
		{
		  "resultCode": 결과 코드,
		  "resultMessage": "결과 메시지",
		  "data": null or Array or Object
		}
*/ 
  
@Data
public class Response implements Serializable {
	private static final long serialVersionUID = 8847032513833637900L;
	
	@ApiModelProperty(example = "200")
	private int resultCode;
	@ApiModelProperty(example = "정상적으로 처리되었습니다.") 
	private String resultMessage;  
	
	private Object data;  
	  
	public Response() {
		this.resultCode = BaseConst.Code.SUCCESS; 
		this.resultMessage = BaseConst.Code.API_SUCCESS_MSG;
	}
	
	public Response(Object data) {
		this.resultCode = BaseConst.Code.SUCCESS;
		this.resultMessage = BaseConst.Code.API_SUCCESS_MSG;
		this.data = data;
	}	
	
	
	@SuppressWarnings("unchecked")
	public Response(String key, Object value) {
		this.resultCode = BaseConst.Code.SUCCESS;
		this.resultMessage = BaseConst.Code.API_SUCCESS_MSG;
		this.data = new HashMap<String, Object>();		
		((HashMap<String, Object>) this.data).put(key, value);   
	}

	public String getResultMessage() {
		return this.resultMessage;
	}

	public void setResultCode(int rawStatusCode) {
		this.resultCode = rawStatusCode;
	}

	public void setResultMessage(String string) {
		this.resultMessage = string;
	}

	public int getResultCode() {
		return this.resultCode; 
	} 		
	
}
