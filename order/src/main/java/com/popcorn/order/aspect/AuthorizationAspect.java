package com.popcorn.order.aspect;

import com.popcorn.order.annotation.CheckAuth;
import com.popcorn.order.service.auth.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * 권한 체크 AOP
 *
 * @CheckAuth 애노테이션이 붙은 메소드 실행 전에 권한을 검증합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final AuthorizationService authorizationService;

    /**
     * @CheckAuth 애노테이션이 있는 메소드 실행 전 권한 검증
     */
    @Before("@annotation(checkAuth)")
    public void checkAuthorization(JoinPoint joinPoint, CheckAuth checkAuth) {
        log.debug("권한 체크 시작 - method: {}", joinPoint.getSignature().getName());

        try {
            // 1. 현재 인증 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // 2. 리소스 ID 추출 (매개변수에서)
            UUID resourceId = extractResourceId(joinPoint, checkAuth.resourceParam());

            // 3. 권한 검증
            boolean hasPermission = authorizationService.hasPermission(
                    authentication,
                    checkAuth.roles(),
                    checkAuth.resourceType(),
                    resourceId
            );

            if (!hasPermission) {
                log.warn("권한 검증 실패 - method: {}, resourceType: {}, resourceId: {}, requiredRoles: {}",
                        joinPoint.getSignature().getName(),
                        checkAuth.resourceType(),
                        resourceId,
                        checkAuth.roles());

                throw new AccessDeniedException(checkAuth.message());
            }

            log.debug("권한 검증 성공 - method: {}", joinPoint.getSignature().getName());

        } catch (AccessDeniedException e) {
            throw e; // 권한 예외는 그대로 전파
        } catch (Exception e) {
            log.error("권한 체크 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new AccessDeniedException("권한 검증 중 오류가 발생했습니다.");
        }
    }

    /**
     * 메소드 매개변수에서 리소스 ID 추출
     */
    private UUID extractResourceId(JoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = method.getParameters();

            // 매개변수 이름으로 값 찾기
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];

                // @PathVariable, @RequestParam 애노테이션에서 이름 확인
                String actualParamName = getParameterName(parameter, paramName);

                if (paramName.equals(actualParamName) && args[i] != null) {
                    Object value = args[i];

                    if (value instanceof UUID) {
                        return (UUID) value;
                    } else if (value instanceof String) {
                        return UUID.fromString((String) value);
                    } else {
                        log.warn("지원하지 않는 매개변수 타입: {} ({})", value.getClass(), paramName);
                    }
                }
            }

            log.debug("매개변수 '{}' 를 찾을 수 없습니다", paramName);
            return null;

        } catch (Exception e) {
            log.error("리소스 ID 추출 중 오류: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 매개변수 실제 이름 추출 (애노테이션 고려)
     */
    private String getParameterName(Parameter parameter, String targetName) {
        // @PathVariable 애노테이션 확인
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("PathVariable")) {
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (!value.isEmpty()) {
                        return value;
                    }

                    String name = (String) annotation.annotationType().getMethod("name").invoke(annotation);
                    if (!name.isEmpty()) {
                        return name;
                    }
                } catch (Exception e) {
                    // PathVariable 값 추출 실패시 매개변수 이름 사용
                }
            }

            // @RequestParam 애노테이션 확인
            if (annotation.annotationType().getSimpleName().equals("RequestParam")) {
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (!value.isEmpty()) {
                        return value;
                    }

                    String name = (String) annotation.annotationType().getMethod("name").invoke(annotation);
                    if (!name.isEmpty()) {
                        return name;
                    }
                } catch (Exception e) {
                    // RequestParam 값 추출 실패시 매개변수 이름 사용
                }
            }
        }

        // 애노테이션이 없으면 매개변수 이름 반환 (Java 8 이후 -parameters 컴파일 옵션 필요)
        return parameter.getName();
    }
}