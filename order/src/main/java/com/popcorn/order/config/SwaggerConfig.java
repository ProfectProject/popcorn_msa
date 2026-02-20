package com.popcorn.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Order 마이크로서비스 Swagger/OpenAPI 설정
 */
@Configuration
public class SwaggerConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.gateway.url:${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private String gatewayUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addServersItem(new Server()
                        .url(gatewayUrl)
                        .description("배포된 게이트웨이"))
                .components(new Components()
                        .addSecuritySchemes("bearer-token",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요 (Bearer 접두사 제외)")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearer-token"));
    }

    private Info apiInfo() {
        return new Info()
                .title("PopCorn Order Service API")
                .description("""
                        ## 🛒 PopCorn 주문 서비스 API

                        ### 📋 주요 기능
                        - **주문 생성**: 예약형(RESERVATION) / 구매형(GOODS) 주문 생성
                        - **주문 조회**: 사용자별, 상태별 주문 목록 및 상세 조회
                        - **주문 관리**: 상태 변경, 취소, 환불 처리
                        - **CQRS 패턴**: 명령과 조회 분리로 성능 최적화
                        - **이벤트 기반**: 주문 생성/변경 시 도메인 이벤트 발행

                        ### 🚀 API 사용 가이드
                        1. **주문 생성**: 팝업 예약 또는 굿즈 구매 주문 등록
                        2. **주문 조회**: 주문 ID나 사용자 ID로 주문 정보 확인
                        3. **주문 관리**: 주문 상태 변경, 취소 등 관리 기능

                        ### 🔒 인증 방식
                        - JWT Bearer Token 방식 사용
                        - Authorization 헤더에 "Bearer {token}" 형태로 전송

                        ### 📞 문의
                        - 개발팀: dev@popcorn.com
                        - 기술지원: support@popcorn.com
                        """)
                .version("v1.0.0")
                .contact(new Contact()
                        .name("PopCorn Development Team")
                        .email("dev@popcorn.com")
                        .url("https://github.com/popcorn-team"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

}
