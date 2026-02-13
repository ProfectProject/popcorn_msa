package com.popcorn.common.aop;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.popcorn.common.annotation.ApiLogging;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

//test
/**
 * @ApiLogging 어노테이션을 처리하는 AOP 어드바이스
 *
 * API 호출에 대한 상세한 로깅을 수행합니다.
 */
@Aspect
@Component
@Order(10) // Idempotent보다 나중에 실행
@RequiredArgsConstructor
public class ApiLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

    private final ObjectMapper objectMapper;

    /**
     * @ApiLogging 어노테이션이 적용된 메서드를 인터셉트합니다.
     */
    @Around("@annotation(apiLogging)")
    public Object handleApiLogging(ProceedingJoinPoint joinPoint, ApiLogging apiLogging) throws Throwable {

        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();

        try {
            // 요청 로깅
            logRequest(joinPoint, apiLogging, methodName);

            // 메서드 실행
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            // 응답 로깅
            logResponse(result, apiLogging, methodName, executionTime, null);

            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            // 예외 로깅
            if (apiLogging.includeException()) {
                logResponse(null, apiLogging, methodName, executionTime, e);
            }

            throw e;
        }
    }

    /**
     * 요청 정보를 로깅합니다.
     */
    private void logRequest(ProceedingJoinPoint joinPoint, ApiLogging apiLogging, String methodName) {

        if (!apiLogging.includeRequest()) {
            return;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            StringBuilder requestLog = new StringBuilder();
            requestLog.append("API REQUEST: ").append(methodName);

            if (StringUtils.hasText(apiLogging.message())) {
                requestLog.append(" - ").append(apiLogging.message());
            }

            // 파라미터 로깅
            if (paramNames != null && args != null) {
                requestLog.append(" | Params: ");

                IntStream.range(0, Math.min(paramNames.length, args.length))
                    .forEach(i -> {
                        String paramName = paramNames[i];

                        // 제외 파라미터 체크
                        if (shouldExcludeParam(paramName, apiLogging.excludeParams())) {
                            requestLog.append(paramName).append("=***EXCLUDED*** ");
                            return;
                        }

                        try {
                            Object paramValue = args[i];
                            String valueStr;

                            if (paramValue == null) {
                                valueStr = "null";
                            } else if (apiLogging.maskSensitiveData() && isSensitiveParam(paramName)) {
                                valueStr = "***MASKED***";
                            } else {
                                valueStr = serializeParam(paramValue);
                            }

                            requestLog.append(paramName).append("=").append(valueStr).append(" ");

                        } catch (Exception e) {
                            requestLog.append(paramName).append("=***ERROR*** ");
                        }
                    });
            }

            logWithLevel(apiLogging.level(), requestLog.toString());

        } catch (Exception e) {
            log.error("API 요청 로깅 실패: {}", methodName, e);
        }
    }

    /**
     * 응답 정보를 로깅합니다.
     */
    private void logResponse(Object result, ApiLogging apiLogging, String methodName,
                           long executionTime, Exception exception) {

        try {
            StringBuilder responseLog = new StringBuilder();

            if (exception != null) {
                responseLog.append("API ERROR: ").append(methodName);
                responseLog.append(" | Exception: ").append(exception.getClass().getSimpleName())
                          .append(" - ").append(exception.getMessage());
            } else {
                responseLog.append("API RESPONSE: ").append(methodName);
            }

            // 실행 시간 로깅
            if (apiLogging.includeExecutionTime()) {
                responseLog.append(" | ExecutionTime: ").append(executionTime).append("ms");
            }

            // 응답 데이터 로깅
            if (apiLogging.includeResponse() && result != null && exception == null) {
                try {
                    String responseStr = serializeParam(result);
                    responseLog.append(" | Response: ").append(responseStr);
                } catch (Exception e) {
                    responseLog.append(" | Response: ***SERIALIZATION_ERROR***");
                }
            }

            // 성능 경고
            if (executionTime > 5000) {
                responseLog.append(" | ⚠️SLOW_API");
            }

            ApiLogging.LogLevel logLevel = exception != null ? ApiLogging.LogLevel.ERROR : apiLogging.level();
            logWithLevel(logLevel, responseLog.toString());

        } catch (Exception e) {
            log.error("API 응답 로깅 실패: {}", methodName, e);
        }
    }

    /**
     * 파라미터를 직렬화합니다.
     */
    private String serializeParam(Object param) {
        try {
            if (param instanceof String || param instanceof Number || param instanceof Boolean) {
                return param.toString();
            }
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            return param.getClass().getSimpleName() + "@" + System.identityHashCode(param);
        }
    }

    /**
     * 제외할 파라미터인지 확인합니다.
     */
    private boolean shouldExcludeParam(String paramName, String[] excludeParams) {
        if (excludeParams == null || excludeParams.length == 0) {
            return false;
        }
        return Arrays.asList(excludeParams).contains(paramName);
    }

    /**
     * 민감한 파라미터인지 확인합니다.
     */
    private boolean isSensitiveParam(String paramName) {
        String lowerCaseName = paramName.toLowerCase();
        return lowerCaseName.contains("password") ||
               lowerCaseName.contains("token") ||
               lowerCaseName.contains("secret") ||
               lowerCaseName.contains("key");
    }

    /**
     * 지정된 레벨로 로깅합니다.
     */
    private void logWithLevel(ApiLogging.LogLevel level, String message) {
        switch (level) {
            case TRACE:
                if (log.isTraceEnabled()) log.trace(message);
                break;
            case DEBUG:
                if (log.isDebugEnabled()) log.debug(message);
                break;
            case INFO:
                log.info(message);
                break;
            case WARN:
                log.warn(message);
                break;
            case ERROR:
                log.error(message);
                break;
        }
    }
}