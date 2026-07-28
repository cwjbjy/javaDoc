package com.example.demo1.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("demo1 菜品订单系统 API")
                        .description("基于 Spring Boot 4.x + MongoDB 的菜品管理和订单系统")
                        .version("1.0.0"));
    }

    /**
     * 全局注册统一成功响应格式 {code, message, data}
     * 遍历所有接口的 200 响应，将原始 DTO Schema 包裹进统一格式
     */
    @Bean
    public OpenApiCustomizer responseWrapperCustomizer() {
        return openApi -> {
            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    ApiResponse okResponse = responses.get("200");
                    if (okResponse == null) return;

                    Content originalContent = okResponse.getContent();
                    Schema<?> originalSchema = originalContent != null
                            ? originalContent.get("application/json").getSchema()
                            : new Schema<>().type("object");

                    Schema<?> wrappedSchema = new Schema<>()
                            .type("object")
                            .addProperty("code", new Schema<>().type("integer").example(200))
                            .addProperty("message", new Schema<>().type("string").example("success"))
                            .addProperty("data", originalSchema);

                    okResponse.setContent(new Content().addMediaType("application/json",
                            new MediaType().schema(wrappedSchema)));
                });
            });
        };
    }

    @Bean
    public GroupedOpenApi marketApi() {
        return GroupedOpenApi.builder()
                .group("市场管理")
                .pathsToMatch("/api/market/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder()
                .group("订单管理")
                .pathsToMatch("/api/order/**")
                .build();
    }
}
