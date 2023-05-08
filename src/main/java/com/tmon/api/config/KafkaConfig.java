package com.tmon.api.config;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.melchi.common.util.ApiKafkaClient;

@Configuration
public class KafkaConfig {
	
	static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);	
	
	@Value("${kafka.servers}")
	private String kafkaServers;
	
	/**
	 * 카프카 클라이언트 빈생성
	 * @return
	 */
	@Bean(name = "kafkaClient")
	public ApiKafkaClient kafkaClient() {
		Properties props = new Properties();
		props.put("bootstrap.servers", kafkaServers);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		
		//logger.info("kafka clinet bean created.");
		return new ApiKafkaClient(props);
	}
}
