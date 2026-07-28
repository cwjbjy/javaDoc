package com.example.javadoc.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 每次 HTTP 请求进入时：生成 traceId 放入 MDC，
 * 同一请求内所有日志自动携带同一个 traceId。
 * 请求结束时清理 MDC，防止线程回池后串数据。
 */
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 优先读取请求头中的 traceId（通常由前端在请求链起始处生成），没有则自己生成
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
