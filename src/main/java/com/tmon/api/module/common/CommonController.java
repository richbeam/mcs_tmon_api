package com.tmon.api.module.common;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.melchi.common.response.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.ExampleProperty;
import lombok.RequiredArgsConstructor;
import springfox.documentation.annotations.ApiIgnore; 


@Api(tags = {"1. Common"})   
@RequiredArgsConstructor  
@RestController 
@RequestMapping(value = "/v1/common")
public class CommonController { 

	static final Logger logger = LoggerFactory.getLogger(CommonController.class);	
	
	@Autowired 
	CommonService commonService;  
	  	
	   
	@ApiOperation(value = "카테고리 조회", notes = "카테고리 조회", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/category", method = {RequestMethod.GET})
	public Response getCategory ( 	 
	 
	) throws Exception {         
		Map<String, Object> result = commonService.getCategory();      
		Response response = new Response(result); 				   
		return response;        
	} 		
	
	
	final String getProdmarketListExample = "{\r\n" + 
			"  \"SearchProduct\": {\r\n" + 
			"    \"category1\": \"\", \r\n" + 
			"    \"category2\": \"\", \r\n" + 
			"    \"category3\": \"\", \r\n" + 
			"    \"category4\": \"\", \r\n" + 
			"    \"prdNo\": \"\", \r\n" + 
			"    \"prdNm\": \"\", \r\n" + 
			"    \"selStatCd\": \"\", \r\n" + 
			"    \"limit\": 5\r\n" + 
			"  }\r\n" + 
			"}";
	
	@ApiImplicitParams({
            @ApiImplicitParam(    
                    name = "paramMap",     
                    dataType = "JSONBody",                             
                    examples = @io.swagger.annotations.Example(
                            value = {
                                    @ExampleProperty(value = getProdmarketListExample, mediaType = "application/json")
                            }))
                            
    })    		 		
	@ApiOperation(value = "다중 상품 조회", notes = "다중 상품 조회", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/prodmarket", method = {RequestMethod.POST})
	public Response getProdmarketList ( 	 
	 @RequestBody Map<String, Object> paramMap	 
	) throws Exception {         
		Map<String, Object> result = commonService.getProdmarketList(paramMap);   
		Response response = new Response(result); 				   
		return response;      
	} 	
	
	
	 
   
	@ApiOperation(value = "단일 상품 조회", notes = "단일 상품 조회", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/prodmarket/{prdNo}", method = {RequestMethod.GET})
	public Response getProdmarket ( 	 
	 @ApiParam(value = "prdNo", name = "상품번호", defaultValue = "2697458540", required = true) @PathVariable String prdNo, 
	 @ApiIgnore @RequestBody(required = false) Map<String, Object> paramMap	 	     
	) throws Exception {          
		if (paramMap == null) {      
			paramMap = new HashMap<String, Object>();      
		} 

		paramMap.put("prdNo", prdNo);  
		Map<String, Object> result = commonService.getProdmarket(paramMap);   
		Response response = new Response(result); 				   
		return response;        
	} 	
	 	
	 	 
	@ApiOperation(value = "셀러 상품 조회", notes = "셀러 상품 조회", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/sellerprodcode/{sellerprdcd}", method = {RequestMethod.GET})
	public Response getSellerprodcode ( 	 
	 @ApiParam(value = "sellerprdcd", name = "셀러상품번호", defaultValue = "2697458540", required = true) @PathVariable(required = true) String sellerprdcd, 
	 @ApiIgnore @RequestBody(required = false) Map<String, Object> paramMap	 	     
	) throws Exception {        
		if (paramMap == null) {  
			paramMap = new HashMap<String, Object>();      
		}

		paramMap.put("sellerprdcd", sellerprdcd);   
		Map<String, Object> result = commonService.getSellerprodcode(paramMap);   
		Response response = new Response(result); 				   
		return response;        
	} 	
	
	@ApiOperation(value = "재고 조회", notes = "재고 조회", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="stackList", method = {RequestMethod.GET})
	public Response getStackList ( 	  
	 @ApiParam(value = "상품번호", name = "prdNo", defaultValue = "2712240330", required = true) @RequestParam(required = true) String prdNo, 
	 @ApiIgnore @RequestParam Map<String, Object> paramMap	 	     
	) throws Exception {         
		//System.out.println(paramMap);
		Map<String, Object> result = commonService.getStackList(paramMap);   
		Response response = new Response(result); 				   
		return response;        
	} 		
	 
	 		 	
	 	
}
