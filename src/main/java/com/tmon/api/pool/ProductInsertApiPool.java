package com.tmon.api.pool;

import java.util.Map;

import com.melchi.common.util.ApiKafkaClient;
import com.tmon.api.module.prod.ProdService;

public class ProductInsertApiPool implements Runnable {
	
	/**
	 * 상품서비스
	 */
	private ProdService prodService = null;
	
	/**
	 * 상품
	 */
	private Map<String, Object> mhItem = null;
	
	/**
	 * 카프카 클라이언트
	 */
	private ApiKafkaClient apiKafkaClient;

	/**
	 *
	 * @param prodService
	 * @param apiKafkaClient
	 * @param mhItem
	 */
	public ProductInsertApiPool(ProdService prodService, ApiKafkaClient apiKafkaClient, Map<String, Object> mhItem) {
		super();
		this.prodService = prodService;
		this.mhItem = mhItem;
		this.apiKafkaClient = apiKafkaClient;
	}
	
	/**
	 * 서비스 수행
	 */
	public void run() {
		try {
			Map<String, Object> resultMap = this.prodService.insertProducts(this.mhItem);
			if(resultMap != null && resultMap.get("status") != null && resultMap.get("contents") != null) {
				this.apiKafkaClient.sendApiSyncProductHistoryMessage(resultMap);	
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
