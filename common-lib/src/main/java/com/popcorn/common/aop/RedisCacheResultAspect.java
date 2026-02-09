package com.popcorn.common.aop;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.common.annotation.RedisCacheResult;
import com.popcorn.common.dto.BaseResponse;

import lombok.RequiredArgsConstructor;

/**
 * @RedisCacheResult 어노테이션을 처리하는 AOP 어드바이스
 *
 * 메서드 실행 결과를 Redis에 캐싱하여 성능을 향상시킵니다.
 */
@Aspect
@Component
@Order(3) // RateLimit 다음에 실행
@RequiredArgsConstructor
public class RedisCacheResultAspect {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheResultAspect.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * @RedisCacheResult 어노테이션이 적용된 메서드를 인터셉트합니다.
     */
    @Around("@annotation(cacheResult)")
    public Object handleRedisCacheResult(ProceedingJoinPoint joinPoint, RedisCacheResult cacheResult) throws Throwable {
        boolean responseEntityReturn = isResponseEntityReturn(joinPoint);
        Class<?> returnType = determineReturnType(joinPoint);

        // 캐시 키 생성
        String cacheKey = generateCacheKey(joinPoint, cacheResult);

        if (!StringUtils.hasText(cacheKey)) {
            log.debug("Redis 캐시 키가 비어있음. 직접 실행: {}", joinPoint.getSignature());
            return joinPoint.proceed();
        }

        // 캐시 조건 확인
        if (!shouldCache(joinPoint, null, cacheResult)) {
            log.debug("Redis 캐시 조건 불만족. 직접 실행: key={}", cacheKey);
            return joinPoint.proceed();
        }

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Redis 캐시 히트: key={}, method={}", cacheKey, joinPoint.getSignature());
                logCacheStatus(cacheResult.cacheName(), cacheKey, "캐시 히트");
                if (responseEntityReturn) {
                    return buildResponseEntityFromCache(cached);
                }
                return convertCachedValue(cached, returnType);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 연결 실패로 캐시 우회: key={}, error={}", cacheKey, e.getMessage());
            return joinPoint.proceed();
        }

        // 캐시 미스 - 메서드 실행
        log.debug("Redis 캐시 미스: key={}, method={}", cacheKey, joinPoint.getSignature());
        logCacheStatus(cacheResult.cacheName(), cacheKey, "캐시 미스");

        Object result;
        try {
            result = joinPoint.proceed();

            // unless 조건 확인 (결과가 있는 경우)
            if (!shouldCacheResult(joinPoint, result, cacheResult)) {
                log.debug("Redis 캐시 제외 조건 만족. 캐싱하지 않음: key={}", cacheKey);
                return result;
            }

            // 결과 캐싱
            Object cacheValue = result;
            if (responseEntityReturn && result instanceof ResponseEntity<?> responseEntity) {
                if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                    return result;
                }
                Object body = responseEntity.getBody();
                if (body instanceof BaseResponse<?> baseResponse) {
                    cacheValue = baseResponse.getData();
                } else {
                    cacheValue = body;
                }
            }

            if (cacheResult.cacheNull() || cacheValue != null) {
                try {
                    // LocalDateTime 직렬화 이슈 방지를 위해 ObjectMapper로 사전 직렬화 검증
                    String jsonString = objectMapper.writeValueAsString(cacheValue);
                    log.debug("Redis 캐시 직렬화 검증 완료: key={}, json length={}", cacheKey, jsonString.length());

                    if (cacheResult.ttlSeconds() > 0) {
                        redisTemplate.opsForValue()
                            .set(cacheKey, cacheValue, Duration.ofSeconds(cacheResult.ttlSeconds()));
                    } else {
                        redisTemplate.opsForValue().set(cacheKey, cacheValue);
                    }
                    log.debug("Redis 캐시 저장 완료: key={}, method={}", cacheKey, joinPoint.getSignature());
                    logCacheStatus(cacheResult.cacheName(), cacheKey, "캐시 저장");
                } catch (Exception serializationException) {
                    log.error("Redis 캐시 직렬화 실패: key={}, error={}", cacheKey, serializationException.getMessage());
                    // 직렬화 실패해도 메서드 결과는 정상 반환
                }
            }
        } catch (DataAccessException e) {
            log.warn("Redis 캐시 저장 실패로 캐시 우회: key={}, error={}", cacheKey, e.getMessage());
            return joinPoint.proceed();
        } catch (Exception e) {
            log.debug("메서드 실행 실패로 캐싱 안 함: key={}, exception={}",
                     cacheKey, e.getClass().getSimpleName());
            throw e;
        }

        return result;
    }

    /**
     * 캐시 키를 생성합니다.
     */
    private String generateCacheKey(ProceedingJoinPoint joinPoint, RedisCacheResult cacheResult) {

        // SpEL 표현식이 있는 경우 우선 사용
        if (StringUtils.hasText(cacheResult.keyExpression())) {
            try {
                String dynamicKey = evaluateSpelExpression(joinPoint, null, cacheResult.keyExpression());
                return buildFinalKey(cacheResult.keyPrefix(), cacheResult.cacheName(), dynamicKey);
            } catch (Exception e) {
                log.error("Redis 캐시 키 SpEL 표현식 평가 실패: {}", cacheResult.keyExpression(), e);
            }
        }

        // 정적 키가 있는 경우 사용
        if (StringUtils.hasText(cacheResult.staticKey())) {
            return buildFinalKey(cacheResult.keyPrefix(), cacheResult.cacheName(), cacheResult.staticKey());
        }

        // 둘 다 없는 경우 메서드 시그니처와 파라미터 기반 키 생성
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(signature.getMethod().getName());

        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            keyBuilder.append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) keyBuilder.append(",");
                keyBuilder.append(args[i] != null ? args[i].toString() : "null");
            }
            keyBuilder.append(")");
        }

        return buildFinalKey(cacheResult.keyPrefix(), cacheResult.cacheName(), keyBuilder.toString());
    }

    /**
     * SpEL 표현식을 평가합니다.
     */
    private String evaluateSpelExpression(ProceedingJoinPoint joinPoint, Object result, String expression) {
        EvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 컨텍스트에 추가
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            Object arg = args[i];
            context.setVariable(paramNames[i], arg);

            if ("authentication".equals(paramNames[i]) && arg == null) {
                log.debug("Redis Cache: Authentication이 null입니다. 익명 사용자로 처리");
                context.setVariable("authentication", new SafeAuthenticationWrapper());
            }
        }

        if (result != null) {
            context.setVariable("result", result);
        }

        try {
            Object expressionResult = expressionParser.parseExpression(expression).getValue(context);
            return expressionResult != null ? expressionResult.toString() : "";
        } catch (Exception e) {
            log.warn("Redis 캐시 SpEL 표현식 평가 실패: {} (expression: {})", e.getMessage(), expression);
            return "anonymous";
        }
    }

    /**
     * 최종 캐시 키를 생성합니다.
     */
    private String buildFinalKey(String prefix, String cacheName, String key) {
        String base = StringUtils.hasText(prefix) ? prefix + ":" + key : key;
        return cacheName + ":" + base;
    }

    /**
     * 캐시 조건을 확인합니다.
     */
    private boolean shouldCache(ProceedingJoinPoint joinPoint, Object result, RedisCacheResult cacheResult) {
        if (!StringUtils.hasText(cacheResult.condition())) {
            return true;
        }

        try {
            Boolean condition = evaluateConditionExpression(joinPoint, result, cacheResult.condition());
            return condition == null || condition;
        } catch (Exception e) {
            log.error("Redis 캐시 조건 평가 실패: {}", cacheResult.condition(), e);
            return true;
        }
    }

    /**
     * 캐시 결과 저장 여부를 확인합니다 (unless 조건).
     */
    private boolean shouldCacheResult(ProceedingJoinPoint joinPoint, Object result, RedisCacheResult cacheResult) {
        if (!StringUtils.hasText(cacheResult.unless())) {
            return true;
        }

        try {
            Boolean unless = evaluateConditionExpression(joinPoint, result, cacheResult.unless());
            return unless == null || !unless;
        } catch (Exception e) {
            log.error("Redis 캐시 제외 조건 평가 실패: {}", cacheResult.unless(), e);
            return true;
        }
    }

    private void logCacheStatus(String cacheName, String cacheKey, String status) {
        if ("popup".equals(cacheName)) {
            log.info("캐시 상태 안내 - cacheName={}, key={}, status={}", cacheName, cacheKey, status);
        }
    }

    /**
     * 조건 표현식을 평가합니다.
     */
    private Boolean evaluateConditionExpression(ProceedingJoinPoint joinPoint, Object result, String expression) {
        EvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 컨텍스트에 추가
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            Object arg = args[i];
            context.setVariable(paramNames[i], arg);

            if ("authentication".equals(paramNames[i]) && arg == null) {
                log.debug("Redis Cache Condition: Authentication이 null입니다. 익명 사용자로 처리");
                context.setVariable("authentication", new SafeAuthenticationWrapper());
            }
        }

        if (result != null) {
            context.setVariable("result", result);
        }

        try {
            Object expressionResult = expressionParser.parseExpression(expression).getValue(context);
            return expressionResult instanceof Boolean ? (Boolean) expressionResult : null;
        } catch (Exception e) {
            log.warn("Redis 캐시 조건 표현식 평가 실패: {} (expression: {})", e.getMessage(), expression);
            return null;
        }
    }

    private boolean isResponseEntityReturn(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return ResponseEntity.class.isAssignableFrom(signature.getReturnType());
    }

    private Class<?> determineReturnType(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getReturnType();
    }

    private Object convertCachedValue(Object cached, Class<?> returnType) {
        if (cached == null || returnType == Object.class || returnType.isInstance(cached)) {
            return cached;
        }

        try {
            return objectMapper.convertValue(cached, returnType);
        } catch (IllegalArgumentException e) {
            log.warn("Redis 캐시 타입 변환 실패: cachedType={}, returnType={}",
                    cached.getClass().getSimpleName(), returnType.getSimpleName());
            return cached;
        }
    }

    private ResponseEntity<?> buildResponseEntityFromCache(Object cached) {
        if (cached instanceof BaseResponse<?>) {
            return ResponseEntity.ok(cached);
        }
        return ResponseEntity.ok(BaseResponse.success(cached));
    }

    private static class SafeAuthenticationWrapper {
        private final SafePrincipal principal = new SafePrincipal();

        public SafePrincipal getPrincipal() {
            return principal;
        }

        public String getName() {
            return "anonymous";
        }

        public boolean isAuthenticated() {
            return false;
        }
    }

    private static class SafePrincipal {
        public String getUserId() {
            return "anonymous";
        }

        public String getUsername() {
            return "anonymous";
        }

        public String getRole() {
            return "anonymous";
        }
    }
}
