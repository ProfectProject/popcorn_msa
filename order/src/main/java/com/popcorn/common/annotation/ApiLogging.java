package com.popcorn.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 로깅을 위한 어노테이션
 * 메서드에 적용되어 API 호출 로그를 자동으로 기록합니다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiLogging {

    /**
     * 로그 레벨
     */
    String level() default "INFO";

    /**
     * 로그 메시지
     */
    String value() default "";

    /**
     * 민감한 정보 마스킹 여부
     */
    boolean maskSensitiveInfo() default true;
}