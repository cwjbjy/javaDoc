package com.example.javadoc.config;

import com.example.javadoc.infrastructure.storage.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(UploadProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 为所有 Controller 添加 /api 前缀，替代 server.servlet.context-path
        // 这样 API 路径仍为 /api/xxx，但静态资源不受 context-path 影响
        configurer.addPathPrefix("/api",
                c -> c.getPackageName() != null && c.getPackageName().startsWith("com.example.javadoc.module"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(UploadProperties.PUBLIC_URL_PREFIX + "**")
                .addResourceLocations(uploadProperties.resourceLocation());
    }
}
