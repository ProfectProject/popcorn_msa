package com.popcorn.coupon.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Value("\${app.gateway.url:\${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private lateinit var gatewayUrl: String

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(apiInfo())
            .servers(listOf(Server().url(gatewayUrl).description("배포된 게이트웨이")))
            .components(
                Components().addSecuritySchemes(
                    "Bearer Authentication",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 토큰을 입력하세요")
                )
            )
            .addSecurityItem(SecurityRequirement().addList("Bearer Authentication"))
    }

    private fun apiInfo(): Info {
        return Info()
            .title("PopCorn Coupon Service API")
            .description(
                """
                ## 🎫 PopCorn 쿠폰 시스템 API 문서

                ### 🎯 서비스 개요
                PopCorn MSA 기반 쿠폰 마이크로서비스입니다. 쿠폰 생성부터 발급, 사용, 통계까지 전체 쿠폰 라이프사이클을 관리합니다.

                ### 📋 핵심 기능

                **🔧 관리자 기능 (MANAGER/OWNER 권한)**
                - 쿠폰 템플릿 생성 및 관리
                - 쿠폰 활성화/비활성화
                - 특정 사용자 직접 발급
                - 전체 쿠폰 운영 통계 조회
                - 쿠폰 사용 확정/취소 처리

                **👤 사용자 기능 (CUSTOMER 권한)**
                - 발급 가능한 쿠폰 조회 및 다운로드
                - 내 쿠폰 목록 조회 (상태별 필터링)
                - 주문 시 적용 가능한 쿠폰 조회
                - 쿠폰 사용 예약 (주문 진행 시)
                - 내 쿠폰 사용 통계 및 이력 조회

                ### 💰 할인 유형
                - **정액 할인**: 고정 금액 할인 (예: 5,000원 할인)
                - **정률 할인**: 주문 금액 대비 퍼센트 할인 (예: 10% 할인, 최대 3,000원)

                ### 📊 쿠폰 상태 관리
                - **DRAFT**: 초안 상태 (발급 불가)
                - **ACTIVE**: 활성 상태 (발급 가능)
                - **INACTIVE**: 비활성 상태 (발급 중단)
                - **EXPIRED**: 만료 상태

                ### 👥 사용자 쿠폰 상태
                - **ISSUED**: 발급됨 (사용 가능)
                - **RESERVED**: 예약됨 (주문 진행 중)
                - **USED**: 사용 완료
                - **EXPIRED**: 만료됨

                ### 🔄 쿠폰 사용 플로우 (2단계 커밋)
                1. **예약 단계**: 주문 시 쿠폰을 RESERVED 상태로 변경
                2. **확정 단계**: 결제 완료 시 USED 상태로 최종 확정
                3. **취소 처리**: 결제 실패 시 ISSUED 상태로 복원

                ### 🏗️ 기술 스택
                - **Language**: Kotlin + Coroutines
                - **Framework**: Spring Boot 3.x + WebFlux
                - **Database**: PostgreSQL (coupons 스키마)
                - **Cache**: Redis (성능 최적화)
                - **Messaging**: Kafka (이벤트 기반 MSA)
                - **CDC**: Debezium (Outbox 패턴)

                ### 🔒 인증 & 권한
                - **인증 방식**: JWT Bearer Token
                - **헤더 형식**: `Authorization: Bearer {JWT_TOKEN}`
                - **권한 유형**:
                  - `CUSTOMER`: 일반 사용자 (본인 쿠폰 관리)
                  - `MANAGER`: 매장 관리자 (쿠폰 운영)
                  - `OWNER`: 사업자 (전체 권한)

                ### 🚀 API 사용법
                1. **JWT 토큰 획득**: 인증 서비스에서 로그인 후 토큰 발급
                2. **헤더 설정**: 모든 API 요청에 Authorization 헤더 포함
                3. **권한 확인**: API별 필요 권한 확인 후 호출
                4. **응답 처리**: 표준 HTTP 상태 코드 기반 처리

                ### 📱 연동 가이드
                - **주문 서비스**: 쿠폰 적용 및 할인 금액 계산
                - **결제 서비스**: 쿠폰 사용 확정/취소 처리
                - **사용자 서비스**: 사용자 권한 및 정보 조회
                - **알림 서비스**: 쿠폰 발급/만료 알림

                ---
                📧 **문의사항**: dev@popcorn.com | 🐛 **버그 신고**: [GitHub Issues](https://github.com/popcorn-team/issues)
                """.trimIndent()
            )
            .version("v1.0.0")
            .contact(
                Contact()
                    .name("PopCorn Development Team")
                    .email("dev@popcorn.com")
                    .url("https://github.com/popcorn-team")
            )
            .license(
                License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")
            )
    }
}
