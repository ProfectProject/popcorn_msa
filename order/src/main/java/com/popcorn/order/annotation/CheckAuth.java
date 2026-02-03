package com.popcorn.order.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 매니저/오너 권한 체크 애노테이션
 *
 * 사용 예시:
 * @CheckAuth(roles = {"MANAGER", "OWNER"}, resourceType = "POPUP", resourceParam = "popupId")
 * @CheckAuth(roles = {"OWNER"}, resourceType = "ORDER", resourceParam = "orderId")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckAuth {

    /**
     * 허용할 역할들
     * MANAGER, OWNER, ADMIN 등
     */
    String[] roles() default {};

    /**
     * 접근할 리소스 타입
     * POPUP, ORDER, STORE 등
     */
    String resourceType() default "";

    /**
     * 리소스 ID가 담긴 매개변수 이름
     * 예: "popupId", "orderId", "storeId"
     */
    String resourceParam() default "";

    /**
     * 권한 체크 실패 시 반환할 에러 메시지
     */
    String message() default "접근 권한이 없습니다.";
}