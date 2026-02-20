package com.popcorn.payment.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Value("\${app.gateway.url:\${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private lateinit var gatewayUrl: String

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(apiInfo())
            .servers(listOf(Server().url(gatewayUrl).description("배포된 게이트웨이")))
            .components(
                Components()
                    .addSecuritySchemes("Bearer Authentication",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT 토큰을 입력하세요")
                    )
            )
            .addSecurityItem(
                SecurityRequirement()
                    .addList("Bearer Authentication")
            )
    }

    private fun apiInfo(): Info {
        return Info()
            .title("PopCorn Payment Service API")
            .description("""
                ## 🎬 PopCorn 결제 서비스 API

                ### 📋 주요 기능
                - **토스페이먼츠 연동**: 카드, 간편결제, 가상계좌 등 다양한 결제 수단 지원
                - **멱등성 보장**: Redis 기반 중복 결제 방지
                - **비동기 처리**: Kotlin 코루틴 기반 성능 최적화
                - **장애 복구**: Circuit Breaker, Retry 패턴 적용
                - **실시간 모니터링**: 결제 상태 추적 및 알림

                ### 🚀 API 사용 가이드
                1. **결제 요청**: 주문 생성 후 결제 승인 호출
                2. **결제 상태 확인**: 주문 ID로 결제 진행 상황 조회
                3. **결제 취소**: 필요 시 전액 또는 부분 취소 처리

                ### 🔒 인증 방식
                - JWT Bearer Token 방식 사용
                - Authorization 헤더에 "Bearer {token}" 형태로 전송

                ### 📞 문의
                - 개발팀: dev@popcorn.com
                - 기술지원: support@popcorn.com
            """.trimIndent())
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
