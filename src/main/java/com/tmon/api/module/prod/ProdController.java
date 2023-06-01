package com.tmon.api.module.prod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.melchi.common.util.ApiKafkaClient;
import com.tmon.api.TmonCasheConnector;
import com.tmon.api.TmonConnector;
import io.swagger.annotations.ApiParam;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.melchi.common.response.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import lombok.RequiredArgsConstructor;
import springfox.documentation.annotations.ApiIgnore;


@Api(tags = {"2. Prod"})   
@RequiredArgsConstructor  
@RestController 
@RequestMapping(value = "/v1/prod")
public class ProdController { 

	static final Logger logger = LoggerFactory.getLogger(ProdController.class);

	@Autowired
	TmonCasheConnector connCache;

    @Autowired
    TmonConnector connector;

	@Autowired 
	ProdService prodService;

    @Autowired
    ProdScheduler prodScheduler;



	/**
	 * 카프카 클라이언트
	 */
	@Autowired
	private ApiKafkaClient apiKafkaClient;
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;


	/* REST getTmonCode TEST / 코드조회 처리  */
	@ApiOperation(value = "[TMON] token 코드조회 처리 ", notes = "[TMON] Token 코드조회 처리 한다.", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/getTmonCode", method = {RequestMethod.GET})
	public Response getTmonCode (
			@ApiIgnore @RequestParam Map<String, Object> paramMap
	) throws Exception {
		Map<String, Object> resultMap = new HashMap<>();

		Response response = new Response();
		//paramMap.put("productCd",productCd);

		try{
			String code = connCache.getTmonCode();
			//String token = connector.refershToken();
			logger.warn("::::code value : {}",code);

		}catch (Exception e){
			e.printStackTrace();
			response.setResultCode(505);
			response.setResultMessage(e.getMessage());
		}


		return response;
	}

    /* REST TOKEN TEST / 수정 단건 처리  */
    @ApiOperation(value = "[TMON] token 발행 처리 ", notes = "[TMON] Token 발행을 테스트 한다.", authorizations = {@Authorization(value="basicAuth")})
    @RequestMapping(value="/getTokenTest", method = {RequestMethod.GET})
    public Response tokenTest (
            @ApiIgnore @RequestParam Map<String, Object> paramMap
    ) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();

        Response response = new Response();
        //paramMap.put("productCd",productCd);

        try{
            String token = connCache.getToken();
			//String token = connector.refershToken();
            logger.warn("::::token value : {}",token);

        }catch (Exception e){
            e.printStackTrace();
            response.setResultCode(505);
            response.setResultMessage(e.getMessage());
        }


        return response;
    }

    /*  상품등록 테스트   */
    @ApiOperation(value = "[TMON] 신규상품등록  테스트 ", notes = "[TMON] 신규상품 등록 테스트 한다.", authorizations = {@Authorization(value="basicAuth")})
    @RequestMapping(value="/setProductsInsert", method = {RequestMethod.GET})
    public Response setProductsInsertTest (
    ) throws Exception {
        Response response = new Response();
        try{
            logger.warn("selectNewProducts start");
            //등록 대상건 조회
            Map<String, Object> paramMap = new HashMap<>();
            List<Map<String, Object>> products = basicSqlSessionTemplate.selectList("ProdMapper.selectNewProducts", paramMap);
            //상품등록
            prodScheduler.setProductsInsert(products);
            logger.warn("selectNewProducts end");

        }catch (Exception e){
            e.printStackTrace();
            response.setResultCode(505);
            response.setResultMessage(e.getMessage());
        }
        return response;
    }

    /*  상품수정 테스트   */
    @ApiOperation(value = "[TMON] 상품수정 테스트 ", notes = "[TMON] 상품수정 테스트 한다.", authorizations = {@Authorization(value="basicAuth")})
    @RequestMapping(value="/setProductsUpdate", method = {RequestMethod.GET})
    public Response setProductsUpdatetTest (
    ) throws Exception {
        Response response = new Response();
        try{
            logger.warn("selectEditProducts start");
            //등록 대상건 조회
            Map<String, Object> paramMap = new HashMap<>();
            List<Map<String, Object>> products = basicSqlSessionTemplate.selectList("ProdMapper.selectUpdatedProducts", paramMap);
            //상품등록
            prodScheduler.setProductsUpdate(products);
            logger.warn("selectEditProducts end");

        }catch (Exception e){
            e.printStackTrace();
            response.setResultCode(505);
            response.setResultMessage(e.getMessage());
        }
        return response;
    }


	/* REST 상품등록 / 수정 단건 처리  */
	@ApiOperation(value = "[TMON] 상품 연동 처리 ", notes = "[11번가] 상품을 단위별로 처리 한다.", authorizations = {@Authorization(value="basicAuth")})
	@RequestMapping(value="/sendAPIProduct", method = {RequestMethod.GET})
	public Response sendAPIProduct (
			@ApiParam(value = "상품번호", name = "productCd", defaultValue = "0", required = true) @RequestParam String productCd,
			@ApiIgnore @RequestParam Map<String, Object> paramMap
	) throws Exception {
		Map<String, Object> resultMap = new HashMap<>();

		Response response = new Response();
		paramMap.put("productCd",productCd);

		try{
			//신규인지 수정인지 체
			logger.warn("단건 상품 수정/등록 ::"+productCd);
			List<Map<String, Object>> mhResult = basicSqlSessionTemplate.selectList("ProdMapper.selectNewProducts", paramMap);
			if(mhResult.size() > 0 ){ // 신규 처리
				logger.warn("단건 상품 수정/등록 1::"+productCd);
				int cnt = 0;
				while (cnt < mhResult.size()) {
					resultMap = prodService.insertProducts(mhResult.get(cnt));
					if(resultMap != null && resultMap.get("status") != null && resultMap.get("contents") != null) {
						apiKafkaClient.sendApiSyncProductHistoryMessage(resultMap);
					}
					cnt ++;
				}
				logger.warn("단건 상품 수정/등록2 ::"+productCd);
				response.setResultCode(200);
				response.setResultMessage("신규등록 : " + resultMap.toString());
				logger.warn("단건 상품 수정/등록 :: 신규등록  ::" + productCd + resultMap.toString());

			}else{ // 수정인지 아직 등록 할수 없는지 체크
				logger.warn("단건 상품 수정/등록 3::"+productCd);
				List<Map<String, Object>> mhEResult = basicSqlSessionTemplate.selectList("ProdMapper.selectUpdatedProducts", paramMap);
				paramMap.put("tmon","Y");
				if(mhEResult.size() > 0){
					//basicSqlSessionTemplate.update("ProdMapper.updateProductStatus", paramMap);
					int cnt = 0;
					while (cnt < mhEResult.size()) {
						resultMap = prodService.updateProducts(mhEResult.get(cnt));
						if(resultMap != null && resultMap.get("status") != null && resultMap.get("contents") != null) {
							apiKafkaClient.sendApiSyncProductHistoryMessage(resultMap);
						}
						cnt ++;
					}
					logger.warn("단건 상품 수정/등록 4::"+productCd);
					response.setResultCode(200);
					response.setResultMessage("상품수정 : " +resultMap.toString());
					logger.warn("단건 상품 수정/등록 :: 상품수정 ::" + productCd + resultMap.toString());
				}else{ // 오류

                    List<Map<String, Object>> delResult = basicSqlSessionTemplate.selectList("ProdMapper.selectUpdatedProductsDelChk", paramMap);
                    if(delResult.size() > 0){
                        int cnt = 0;
                        while (cnt < delResult.size()) {
                            prodService.setStopProduct(delResult.get(cnt).get("productcd").toString());
                            cnt ++;
                        }
                        response.setResultCode(200);
                        response.setResultMessage(productCd + " 상품이 판매 일시중지 되었습니다.[티몬연동 해제]");
                        logger.warn("단건 상품 수정/등록 :: 상품이 판매 일시중지 되었습니다.[티몬연동 해제]::" + productCd + resultMap.toString());
                    }else{
                        response.setResultCode(200);
                        response.setResultMessage(productCd + " 상품이 아직 등록할수 없는 상태 입니다.");
                        logger.warn("단건 상품 수정/등록 :: 상품이 아직 등록할수 없는 상태 입니다.::" + productCd + resultMap.toString());
                    }



				}
			}
		}catch (Exception e){
			e.printStackTrace();
			response.setResultCode(505);
			response.setResultMessage(e.getMessage());
		}


		return response;
	}
}
