package com.popcorn.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(0)
public class TraceMdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

        MDC.put(TRACE_ID, traceId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("path", request.getRequestURI());
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.put("httpStatus", String.valueOf(response.getStatus()));
            MDC.put("elapsedMs", String.valueOf(System.currentTimeMillis() - startTime));
            MDC.clear();
        }
    }
}
