package com.melchi.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.classmate.TypeResolver;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.ResponseMessageBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.BasicAuth;
import springfox.documentation.service.ResponseMessage;
import springfox.documentation.service.SecurityScheme;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

	@Autowired
	private Environment env;

	@Autowired
	private TypeResolver resolver; 

	@Bean
	public Docket swaggerApi() {

		ModelRef error401 = new ModelRef("Error401");
		ModelRef error403 = new ModelRef("Error403");

		List<ResponseMessage> responseMessages = Arrays.asList(
				new ResponseMessageBuilder().code(401).message("Unauthorized").responseModel(error401).build(),
				new ResponseMessageBuilder().code(403).message("Forbidden").responseModel(error403).build());

		String packageName = env.getProperty("spring.package-name");
		List<SecurityScheme> schemeList = new ArrayList<>();
		schemeList.add(new BasicAuth("basicAuth"));

		Docket docket = new Docket(DocumentationType.SWAGGER_2).apiInfo(swaggerInfo()).select()
				.apis(RequestHandlerSelectors.basePackage("com." + packageName + ".api.module"))
				.paths(PathSelectors.any()).build().useDefaultResponseMessages(false) // 기본으로 세팅되는 200,401,403,404 메시지를
																						// 표시 하지 않음
				.securitySchemes(schemeList) // Basic Auth 사용
				.additionalModels(resolver.resolve(JSONBody.class)).additionalModels(resolver.resolve(Error401.class))
				.additionalModels(resolver.resolve(Error403.class));

		docket.globalResponseMessage(RequestMethod.POST, responseMessages)
				.globalResponseMessage(RequestMethod.PUT, responseMessages)
				.globalResponseMessage(RequestMethod.GET, responseMessages)
				.globalResponseMessage(RequestMethod.DELETE, responseMessages);

		return docket;

	}

	private ApiInfo swaggerInfo() {
		String packageDisplayName = env.getProperty("spring.package-display-name");
		return new ApiInfoBuilder().title("멸치쇼핑 " + packageDisplayName + " API Documentation")
				.description(packageDisplayName + " 연동 API 리스트").license("Melchi Shopping")
				.licenseUrl("http://www.smelchi.com").version("v1").build();
	}

	@Data
	public static class JSONBody {
		private String body;
	}

	@Data
	public static class Error401 {
		@ApiModelProperty(example = "401")
		private int resultCode;
		@ApiModelProperty(example = "허가되지 않은 사용자 입니다.")
		private String resultMessage;
	}

	@Data
	public static class Error403 {
		@ApiModelProperty(example = "403")
		private int resultCode;
		@ApiModelProperty(example = "권한이 없습니다.")
		private String resultMessage;
	}

}
