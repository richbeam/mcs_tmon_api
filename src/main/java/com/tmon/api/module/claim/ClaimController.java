package com.tmon.api.module.claim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor; 


@Api(tags = {"4. Claim"})   
@RequiredArgsConstructor  
@RestController 
@RequestMapping(value = "/v1/claim")
public class ClaimController { 

	static final Logger logger = LoggerFactory.getLogger(ClaimController.class);	
	
}
