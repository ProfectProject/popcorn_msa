package com.popcorn.order.interceptor;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.popcorn.order.annotation.PerformanceMonitoring;
import com.popcorn.order.service.monitor.AuditLogService;
import com.popcorn.order.dto.audit.ApiLogEntry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API 호출 로깅 인터셉터
 *
 * 모든 HTTP 요청과 응답을 가로채서 로그로 기록합니다.
 * Spring MVC의 HandlerInterceptor를 구현하여 요청 전후 처리를 담당합니다.
 *
 * 인터셉터의 동작 순서:
 * 1. preHandle: 컨트롤러 실행 전
 * 2. postHandle: 컨트롤러 실행 후, 뷰 렌더링 전
 * 3. afterCompletion: 뷰 렌더링 후 (예외 발생 시에도 실행)
 *
 * 로깅 정보:
 * - 요청 URL, HTTP 메서드, 사용자 정보
 * - 요청/응답 시간, 처리 시간
 * - 응답 상태 코드, 오류 정보
 * - 클라이언트 IP, User-Agent
 * - 세션 정보, 요청 ID (추적용)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private final AuditLogService auditLogService;

    /** 요청별 추적 ID를 저장하는 ThreadLocal */
    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> REQUEST_START_TIME = new ThreadLocal<>();

    /**
     * 컨트롤러 실행 전 처리
     *
     * 요청 시작 시간을 기록하고 기본 정보를 로그에 남깁니다.
     * 요청별 고유 ID를 생성하여 전체 처리 과정을 추적할 수 있도록 합니다.
     */
    @Override
    @PerformanceMonitoring(threshold = 10, category = "interceptor")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // 요청 추적 ID 생성 및 저장
        String requestId = generateRequestId();
        REQUEST_ID_HOLDER.set(requestId);
        REQUEST_START_TIME.set(System.currentTimeMillis());

        // MDC에 요청 ID 설정 (로그에서 추적 가능)
        org.slf4j.MDC.put("requestId", requestId);

        try {
            // API 로그 엔트리 생성
            ApiLogEntry logEntry = createApiLogEntry(request, handler, requestId);

            // 요청 정보 로깅
            logRequestInfo(request, handler, requestId);

            // 감사 로그 서비스에 요청 시작 기록
            auditLogService.logApiRequest(logEntry);

        } catch (Exception e) {
            log.error("API 로깅 인터셉터 실행 중 오류 발생 - 요청ID: {}, 오류: {}",
                     requestId, e.getMessage(), e);
        }

        return true; // 계속 진행
    }

    /**
     * 컨트롤러 실행 후 처리
     *
     * 컨트롤러가 정상적으로 실행된 후 호출됩니다.
     * 실행 결과와 성능 정보를 기록합니다.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, ModelAndView modelAndView) {

        String requestId = REQUEST_ID_HOLDER.get();
        if (requestId == null) return;

        try {
            long processingTime = System.currentTimeMillis() - REQUEST_START_TIME.get();

            // 응답 정보 로깅
            logResponseInfo(request, response, requestId, processingTime);

        } catch (Exception e) {
            log.error("API 로깅 postHandle 중 오류 발생 - 요청ID: {}, 오류: {}",
                     requestId, e.getMessage());
        }
    }

    /**
     * 요청 처리 완료 후 정리
     *
     * 정상 처리든 예외 발생이든 반드시 실행됩니다.
     * 최종 결과를 기록하고 ThreadLocal을 정리합니다.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {

        String requestId = REQUEST_ID_HOLDER.get();
        if (requestId == null) return;

        try {
            long processingTime = System.currentTimeMillis() - REQUEST_START_TIME.get();

            // 최종 결과 로깅
            logCompletionInfo(request, response, requestId, processingTime, ex);

            // 감사 로그에 완료 정보 기록
            auditLogService.logApiCompletion(requestId, response.getStatus(), processingTime, ex);

        } catch (Exception e) {
            log.error("API 로깅 afterCompletion 중 오류 발생 - 요청ID: {}, 오류: {}",
                     requestId, e.getMessage());
        } finally {
            // ThreadLocal 정리 (메모리 누수 방지)
            REQUEST_ID_HOLDER.remove();
            REQUEST_START_TIME.remove();
            org.slf4j.MDC.clear();
        }
    }

    /**
     * 요청 추적용 고유 ID 생성
     *
     * UUID를 사용하여 전역적으로 고유한 ID를 생성합니다.
     * 마이크로서비스 환경에서 분산 추적에 활용할 수 있습니다.
     */
    private String generateRequestId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * API 로그 엔트리 생성
     */
    private ApiLogEntry createApiLogEntry(HttpServletRequest request, Object handler, String requestId) {
        return ApiLogEntry.builder()
                .requestId(requestId)
                .method(request.getMethod())
                .url(request.getRequestURL().toString())
                .queryString(request.getQueryString())
                .clientIp(getClientIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .sessionId(request.getSession(false) != null ? request.getSession().getId() : null)
                .userId(extractUserId(request))
                .requestTime(LocalDateTime.now())
                .controllerClass(getControllerClassName(handler))
                .methodName(getMethodName(handler))
                .build();
    }

    /**
     * 요청 정보 로깅
     */
    private void logRequestInfo(HttpServletRequest request, Object handler, String requestId) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("API 요청 시작 - ")
                  .append("ID: ").append(requestId)
                  .append(", 메서드: ").append(request.getMethod())
                  .append(", URL: ").append(request.getRequestURL());

        if (request.getQueryString() != null) {
            logMessage.append("?").append(request.getQueryString());
        }

        logMessage.append(", IP: ").append(getClientIpAddress(request))
                  .append(", 컨트롤러: ").append(getControllerClassName(handler))
                  .append(".").append(getMethodName(handler));

        log.info(logMessage.toString());
    }

    /**
     * 응답 정보 로깅
     */
    private void logResponseInfo(HttpServletRequest request, HttpServletResponse response,
                                String requestId, long processingTime) {
        log.info("API 응답 - ID: {}, 상태: {}, 처리시간: {}ms, URL: {}",
                requestId, response.getStatus(), processingTime, request.getRequestURL());
    }

    /**
     * 완료 정보 로깅
     */
    private void logCompletionInfo(HttpServletRequest request, HttpServletResponse response,
                                  String requestId, long processingTime, Exception ex) {
        if (ex != null) {
            log.error("API 요청 실패 - ID: {}, URL: {}, 처리시간: {}ms, 오류: {}",
                     requestId, request.getRequestURL(), processingTime, ex.getMessage());
        } else {
            String logLevel = determineLogLevel(response.getStatus(), processingTime);
            String message = String.format("API 요청 완료 - ID: %s, 상태: %d, 처리시간: %dms, URL: %s",
                                          requestId, response.getStatus(), processingTime, request.getRequestURL());

            switch (logLevel) {
                case "WARN" -> log.warn(message + " [느린 응답]");
                case "ERROR" -> log.error(message + " [오류 응답]");
                default -> log.info(message);
            }
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * 프록시나 로드밸런서를 거치는 경우를 고려하여
     * 다양한 헤더에서 실제 클라이언트 IP를 찾습니다.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 헤더는 쉼표로 구분된 여러 IP를 포함할 수 있음
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 요청에서 사용자 ID 추출
     *
     * JWT 토큰이나 세션에서 사용자 정보를 추출합니다.
     * 보안상 민감한 정보는 로그에 포함하지 않습니다.
     */
    private Long extractUserId(HttpServletRequest request) {
        try {
            // Authorization 헤더에서 JWT 토큰 추출 시도
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // TODO: JWT 토큰 파싱하여 사용자 ID 추출
                // String token = authHeader.substring(7);
                // return jwtTokenUtil.getUserIdFromToken(token);
            }

            // 세션에서 사용자 ID 추출 시도
            var session = request.getSession(false);
            if (session != null) {
                Object userId = session.getAttribute("userId");
                if (userId instanceof Number) {
                    return ((Number) userId).longValue();
                }
            }

        } catch (Exception e) {
            log.debug("사용자 ID 추출 실패 - 오류: {}", e.getMessage());
        }

        return null; // 인증되지 않은 요청
    }

    /**
     * 컨트롤러 클래스명 추출
     */
    private String getControllerClassName(Object handler) {
        if (handler instanceof HandlerMethod) {
            return ((HandlerMethod) handler).getBeanType().getSimpleName();
        }
        return "Unknown";
    }

    /**
     * 메서드명 추출
     */
    private String getMethodName(Object handler) {
        if (handler instanceof HandlerMethod) {
            return ((HandlerMethod) handler).getMethod().getName();
        }
        return "unknown";
    }

    /**
     * 응답 상태와 처리 시간에 따른 로그 레벨 결정
     */
    private String determineLogLevel(int status, long processingTime) {
        if (status >= 500) {
            return "ERROR";  // 서버 오류
        } else if (status >= 400) {
            return "WARN";   // 클라이언트 오류
        } else if (processingTime > 3000) {
            return "WARN";   // 느린 응답 (3초 초과)
        } else {
            return "INFO";   // 정상 응답
        }
    }

}