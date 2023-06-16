package com.tmon.api.module.prod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.melchi.common.exception.UserDefinedException;
import com.melchi.common.vo.RestParameters;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.melchi.common.response.Response;
import com.melchi.common.util.ApiKafkaClient;
import com.melchi.common.util.StringUtil;
import com.tmon.api.TmonConnector;
import com.tmon.api.pool.ProductInsertApiPool;
import com.tmon.api.pool.ProductUpdateApiPool;

import io.swagger.annotations.Api;

@Api(tags = {"2. Prod Scheduler"})   
@RestController
@EnableScheduling
@RequestMapping(value = "/prod/schedule")
public class ProdScheduler {

	static final Logger logger = LoggerFactory.getLogger(ProdScheduler.class);
	
	/**
	 * 상품등록 멀티쓸래드  Excutor
	 */
	private volatile static ThreadPoolExecutor productInsertAPIExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
	
	/**
	 * 상품수정 멀티쓸래드  Excutor
	 */
	private volatile static ThreadPoolExecutor productUpdateAPIExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
	
	@Autowired 
	ProdService prodService;  
	
	@Autowired
	SqlSessionTemplate basicSqlSessionTemplate;
	
	@Autowired
	TmonConnector connector;
	
	@Autowired
	ApiKafkaClient apiKafkaClient;

	/**
	 * 상품 등록 [ 연동 딜 등록 ]
	 * @throws Exception
	 */
	//@Scheduled(cron="0 0/4 * * * ?")
    @Scheduled(initialDelay = 1900, fixedDelay = 148000)
	public void insertProducts() throws Exception {
		logger.warn(">>>>>>>>>>> selectNewProducts start");
		//등록 대상건 조회
		Map<String, Object> paramMap = new HashMap<>();
		List<Map<String, Object>> products = basicSqlSessionTemplate.selectList("ProdMapper.selectNewProducts", paramMap);
		//상품등록
		setProductsInsert(products);
		logger.warn(">>>>>>>>>>> selectNewProducts end");
	}

	/**
	 * 상품 수정 [ 딜 정보 수정 ]
	 * @return
	 * @throws Exception
	 */
	//@Scheduled(cron="40 0/1 * * * ?")
	@Scheduled(initialDelay = 19900, fixedDelay = 145000)
	public Response updateProducts() throws Exception {
		logger.warn(">>>>>>>>>>> selectUpdatedProducts start");
		//수정 대상건 조회
		Map<String, Object> paramMap = new HashMap<>();
		List<Map<String, Object>> products = basicSqlSessionTemplate.selectList("ProdMapper.selectUpdatedProducts", paramMap);

		String rt = setProductsUpdate(products);

		/*if(products != null) {
			for(Map<String, Object> product : products) {
				try {
					Map<String, Object> resultMap = this.prodService.updateProducts(product);
					if(resultMap != null && resultMap.get("status") != null && resultMap.get("contents") != null) {
						this.apiKafkaClient.sendApiSyncProductHistoryMessage(resultMap);	
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
*/
		logger.warn(">>>>>>>>>>> selectUpdatedProducts end::::::"+rt);
		return null;
	}

    /**
     * 상품 수정 [ 딜 판매 일시 중지 ]
     * @return
     * @throws Exception
     */
    //@Scheduled(cron="40 0/1 * * * ?")
    @Scheduled(initialDelay = 19900, fixedDelay = 65000)
    public Response setStopProducts() throws Exception {
        logger.warn(">>>>>>>>>>> setStopProducts start");
        //수정 대상건 조회
        Map<String, Object> paramMap = new HashMap<>();
        List<Map<String, Object>> products = basicSqlSessionTemplate.selectList("ProdMapper.selectSetStopProduct", paramMap);

		if(products != null) {
			for(Map<String, Object> product : products) {
				try {
					prodService.setStopProduct(product.get("productcd").toString());
					logger.warn(":::::::::::::setStopProduct : {}",product.get("productcd").toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

        logger.warn(">>>>>>>>>>> setStopProducts end::::::");
        return null;
    }


	/**
	 * 상품QnA 등록 [ 상품문의 조회 ]
	 * @throws Exception
	 */
	//@Scheduled(cron="0 0/6 * * * ?")
	@Scheduled(initialDelay = 97500, fixedDelay = 528000)
	public void insertQna () throws Exception {
		logger.warn(">>>>>>>>>>> 상품QnA등록 시작");
		//대상조회 - 등록된 QNA 조회
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		Map<String, Object> qnas = new HashMap<>();
		Map<String, Object> selMap = new HashMap<>();

		//일자셋팅
		String path = "/qnas";
		String today = StringUtil.getTodayString("yyyy-MM-dd");

		String startDate = StringUtil.orderDateAdd(today, -1440);
		String endDate = StringUtil.orderDateAdd(today, -1);
		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);

		params.setRequestParameters(paramMap);
		paramMap.put("startDate", startDate);  // endDate 기준 7일 이내
		paramMap.put("endDate", endDate);	  //현재시간 - 30 분보다 과거
		paramMap.put("answered", false);	  //답변 여부
		paramMap.put("per", 50);				//페이지당 노출개수
		try{
			int page = 1;
			String hasNext = "false";
			do{
				paramMap.put("page", page);
				params.setPathVariableParameters(paramMap);
				params.setBody(paramMap);

				qnas = connector.call(HttpMethod.GET, path, params);
				//주문등록
				if(qnas != null ){

					List<Map<String, Object>> qnaList =(List<Map<String, Object>>)qnas.get("items");
					for(Map<String, Object> qna : qnaList) {
						try {
							prodService.insertQna(qna);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					hasNext = qnas.get("hasNext").toString();
				}else{
					logger.info(">>> QNA조회 없음: {}", paramMap.toString());
				}

				page ++;
			} while (hasNext.equals("true"));
		}catch (UserDefinedException e){
			e.printStackTrace();
		}


		logger.warn(">>>>>>>>>>> 상품QnA등록 종료");
	}

	/**
	 * 상품QnA답변 등록 [ 상품문의 답변 ]
	 * @throws Exception
	 */
	//@Scheduled(cron="0 0/4 * * * ?")
	@Scheduled(initialDelay = 96500, fixedDelay = 290000)
	public void insertQnaAnswer () throws Exception {
		logger.warn(">>>>>>>>>>> 상품QnA답변 등록 시작");
		//셀러 상품 삭제건 대상조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename", "TMON");
		List<Map<String, Object>> qnaAnswers = basicSqlSessionTemplate.selectList("ProdMapper.selectQnaAnswerList", paramMap);
		//답변등록
		if(qnaAnswers != null) {
			for(Map<String, Object> answer : qnaAnswers) {
				try {
					prodService.updateQnaAnswer(answer);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>> 답변등록 대상건 없음: {}", qnaAnswers);
		}
		logger.warn(">>>>>>>>>>> 상품QnA답변 등록 종료");
	}



	/**
	 * CS 문의 등록 [ CS문의 조회 ]
	 * @throws Exception
	 */
	//@Scheduled(cron="0 0/6 * * * ?")
	@Scheduled(initialDelay = 19500, fixedDelay = 252000)
	public void insertCSQna () throws Exception {
		logger.warn(">>>>>>>>>>> CS문의 등록 시작");
		//대상조회 - 등록된 QNA 조회
		RestParameters params = new RestParameters();
		Map<String, Object> paramMap = new HashMap<>();
		Map<String, Object> qnas = new HashMap<>();
		Map<String, Object> selMap = new HashMap<>();

		//일자셋팅
		String path = "/cs-inquiry/list";
		String today = StringUtil.getTodayString("yyyy-MM-dd");

		String startDate = StringUtil.orderDateAdd(today, -1440);
		String endDate = StringUtil.orderDateAdd(today, -1);
		logger.warn("-----startDate : endDate = {} : {}",startDate,endDate);

		params.setRequestParameters(paramMap);
		paramMap.put("startDate", startDate);  // endDate 기준 7일 이내
		paramMap.put("endDate", endDate);	  //현재시간 - 30 분보다 과거
		paramMap.put("answered", false);	  //답변 여부
		paramMap.put("per", 50);				//페이지당 노출개수
		try{
			int page = 1;
			String hasNext = "false";
			do{
				paramMap.put("page", page);
				params.setPathVariableParameters(paramMap);
				params.setBody(paramMap);

				qnas = connector.call(HttpMethod.GET, path, params);
				//주문등록
				if(qnas != null ){

					List<Map<String, Object>> qnaList =(List<Map<String, Object>>)qnas.get("items");
					for(Map<String, Object> qna : qnaList) {
						try {
							prodService.insertCsQna(qna);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					hasNext = qnas.get("hasNext").toString();
				}else{
					logger.info(">>> QNA조회 없음: {}", paramMap.toString());
				}

				page ++;
			} while (hasNext.equals("true"));
		}catch (UserDefinedException e){
			e.printStackTrace();
		}


		logger.warn(">>>>>>>>>>> CS문의 등록 종료");
	}

	/**
	 * CS문의 답변 등록 [ 상품문의 답변 ]
	 * @throws Exception
	 */
	//@Scheduled(cron="0 0/4 * * * ?")
	@Scheduled(initialDelay = 19500, fixedDelay = 253000)
	public void insertCSQnaAnswer () throws Exception {
		logger.warn(">>>>>>>>>>> CS문의 답변 등록 시작");
		//셀러 상품 삭제건 대상조회
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("sitename", "TMON");
		List<Map<String, Object>> qnaAnswers = basicSqlSessionTemplate.selectList("ProdMapper.selectCsQnaAnswerList", paramMap);
		//답변등록
		if(qnaAnswers != null) {
			for(Map<String, Object> answer : qnaAnswers) {
				try {
					prodService.updateCsQnaAnswer(answer);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info(">>>>>>>>>>> CS문의 답변등록 대상건 없음: {}", qnaAnswers);
		}
		logger.warn("CS문의 답변 등록 종료");
	}











































	/* 상품 수정 */
	public String setProductsUpdate(List<Map<String, Object>> mResult) throws Exception {
		//정합성 검증
		if(mResult == null) return "ERROR";
		
		int cnt = 0;
		while (cnt < mResult.size()){
			
			//전체 풀 싸이즈
			int maximumPoolSize = productUpdateAPIExecutor.getMaximumPoolSize();
			//수행중인 테스크 수
			int activeCount = productUpdateAPIExecutor.getActiveCount();
			//수행가능한 상태인경우 태스크 수행
			if(maximumPoolSize > activeCount) {
				logger.info("상품수정 - " + mResult.get(cnt));
				productUpdateAPIExecutor.submit(new ProductUpdateApiPool(prodService, apiKafkaClient, mResult.get(cnt)));
				cnt++;
			} else {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
				}
			}
		}
		return "SUCCESS";
	}
	
	/* 상품 등록 */
	public void setProductsInsert(List<Map<String, Object>> mResult) throws Exception {
		//정합성 검증
		if(mResult == null) return;
		
		int cnt = 0;
		while (cnt < mResult.size()){
			
			//전체 풀 싸이즈
			int maximumPoolSize = productInsertAPIExecutor.getMaximumPoolSize();
			//수행중인 테스크 수
			int activeCount = productInsertAPIExecutor.getActiveCount();
			//수행가능한 상태인경우 태스크 수행
			if(maximumPoolSize > activeCount) {
				logger.warn("상품등록 - " + mResult.get(cnt));
				productInsertAPIExecutor.submit(new ProductInsertApiPool(prodService, apiKafkaClient, mResult.get(cnt)));
				cnt++;
			} else {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
				}
			}
		}
	}
}