package com.melchi.common.util;

import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiKafkaClient {
	
	/**
	 * 로거 
	 */
	static final Logger logger = LoggerFactory.getLogger(ApiKafkaClient.class);	
	
	/**
	 * 카프카 프로퍼티
	 */
	private Properties props;
	
	/**
	 * 상품호출결과 토픽
	 */
	private static final String apiSyncProductHistory = "api_sync_product_history";

	/**
	 * 생성자
	 * @param props
	 */
	public ApiKafkaClient(Properties props) {
		super();
		this.props = props;
	}
	
	/**
	 * 데이터 전송
	 * 
	 * @param productCd
	 * @param productNo
	 * @param sellingprice
	 * @param supplyprice
	 * @param status
	 * @param url
	 */
	public void sendApiSyncProductHistoryMessage(String productCd, String productNo, String sellingprice, String supplyprice, String status, String url, String contents) {
		//응답결과 생성 {버전|상품코드|싸이트|싸이트상품번호|판매가|공급가|상품상태|URL}
		String[] responseMessage = {"V1", productCd, "SSG", productNo, sellingprice, supplyprice, status, url, contents};
		String kafkaMessage = StringUtils.join(responseMessage, "|");
		
		Producer<String, String> producer = new KafkaProducer<String, String>(this.props);
		producer.send(new ProducerRecord<String, String>(apiSyncProductHistory, kafkaMessage));
		producer.close();
	}
	
	/**
	 * 데이터 전송
	/**
	 * 
	 * @param paramMap
	 */
	public void sendApiSyncProductHistoryMessage(Map<String, Object> paramMap) {
		//응답결과 생성 {버전|상품코드|싸이트|싸이트상품번호|판매가|공급가|상품상태|URL|내용}
		String[] responseMessage = {"V1", "", "SSG", "", "0", "0", "", "", ""};
		//상품코드
		if(paramMap.get("productcd") != null) {
			responseMessage[1] = paramMap.get("productcd").toString().trim();	
		}
		//싸이트상품번호
		if(paramMap.get("productno") != null) {
			responseMessage[3] = paramMap.get("productno").toString().trim();	
		}
		//판매가
		if(paramMap.get("sellingprice") != null) {
			responseMessage[4] = paramMap.get("sellingprice").toString().trim();	
		}
		//공급가
		if(paramMap.get("supplyprice") != null) {
			responseMessage[5] = paramMap.get("supplyprice").toString().trim();	
		}
		//상품상태
		if(paramMap.get("status") != null) {
			responseMessage[6] = paramMap.get("status").toString().trim();	
		}
		//URL
		if(paramMap.get("url") != null) {
			responseMessage[7] = paramMap.get("url").toString().trim();	
		}
		//URL
		if(paramMap.get("contents") != null) {
			responseMessage[8] = paramMap.get("contents").toString().trim();	
		}
		String kafkaMessage = StringUtils.join(responseMessage, "|");
		
		logger.warn("logger=" + kafkaMessage);
		Producer<String, String> producer = new KafkaProducer<String, String>(this.props);
		producer.send(new ProducerRecord<String, String>(apiSyncProductHistory, kafkaMessage));
		producer.flush();
		producer.close();
	}
}
