package com.popcorn.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/**
 * 기본 요청 로깅 필터 구현체
 *
 * RequestLogFilter 인터페이스를 구현하는 Spring Filter
 * - RequestLoggingStrategy를 통한 로깅 전략 적용
 * - Spring의 OncePerRequestFilter 확장
 */

@Component
@Order(2)
public class RequestLoggingFilterImpl extends OncePerRequestFilter implements RequestLogFilter {

	private final RequestLoggingStrategy loggingStrategy;

	public RequestLoggingFilterImpl(RequestLoggingStrategy loggingStrategy) {
		this.loggingStrategy = loggingStrategy;
	}

	@Override
	public boolean shouldSkipRequest(HttpServletRequest request) {
		return loggingStrategy.shouldSkipLogging(request);
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		return shouldSkipRequest(request);
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		// RequestLogFilter의 doFilter(...)를 호출하는 기존 구조 유지
		doFilter(request, response, filterChain);
	}

	@Override
	public void doFilter(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {

		long startTime = System.currentTimeMillis();

		loggingStrategy.logRequestStart(request, startTime);

		try {
			filterChain.doFilter(request, response);

			long elapsedMs = System.currentTimeMillis() - startTime;


			MDC.put("httpStatus", String.valueOf(response.getStatus()));
			MDC.put("elapsedMs", String.valueOf(elapsedMs));

			loggingStrategy.logRequestComplete(request, response, startTime, elapsedMs);

		} catch (Exception e) {
			long elapsedMs = System.currentTimeMillis() - startTime;

			MDC.put("httpStatus", String.valueOf(response.getStatus()));
			MDC.put("elapsedMs", String.valueOf(elapsedMs));

			loggingStrategy.logRequestError(request, response, startTime, elapsedMs, e);
			throw e;

		} finally {
			MDC.remove("httpStatus");
			MDC.remove("elapsedMs");
		}
	}
}