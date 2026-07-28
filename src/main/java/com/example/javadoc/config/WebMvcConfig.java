package com.example.javadoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.path:static/images/market/}")
    private String uploadPath;

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
        // 使用绝对路径，确保文件能被正确定位
        String projectDir = System.getProperty("user.dir");
        String absolutePath = new File(projectDir, uploadPath).getAbsolutePath().replace("\\", "/");
        registry.addResourceHandler("/static/images/market/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}