package com.melchi.common.vo;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import lombok.Data;

@Data
public class RestParameters {
	private Map<String, Object> pathVariableParameters;
	private Map<String, Object> requestParameters;
	private Map<String, Object> bodyParameters;
	
	public RestParameters() {
		this.pathVariableParameters = new HashMap<String, Object>();
		this.requestParameters = new HashMap<String, Object>();
		this.bodyParameters = new HashMap<String, Object>(); 
	}
	
	public RestParameters(Map<String, Object> body) {
		this.pathVariableParameters = new HashMap<String, Object>();
		this.requestParameters = new HashMap<String, Object>();
		this.bodyParameters = body; 
	}
	
	public void addPathVariableParameter(String key, Object value) {		
		this.pathVariableParameters.put(key, value);  
	}
	
	public void addRequestParameter(String key, Object value) {
		this.requestParameters.put(key, value);
	}
	
	public void addBodyParameter(String key, Object value) {
		this.bodyParameters.put(key, value);
	} 
	
	public void setBody(Map<String, Object> body) {
		this.bodyParameters = body;
	}
	
	@Override 
	public String toString() {
		String str = "\n" + 
				"pathVariableParameters : " + this.pathVariableParameters + "\n" + 
				"requestParameters : " + this.requestParameters + "\n" +
				"bodyParameters : " + this.bodyParameters;  
		return str; 
	}

	public Map<String, Object> getPathVariableParameters() {
		return pathVariableParameters;
	}

	public void setPathVariableParameters(Map<String, Object> pathVariableParameters) {
		this.pathVariableParameters = pathVariableParameters;
	}

	public Map<String, Object> getRequestParameters() {
		return requestParameters;
	}

	public void setRequestParameters(Map<String, Object> requestParameters) {
		this.requestParameters = requestParameters;
	}

	public Map<String, Object> getBodyParameters() {
		return bodyParameters;
	}

	public void setBodyParameters(Map<String, Object> bodyParameters) {
		this.bodyParameters = bodyParameters;
	}		 
}
