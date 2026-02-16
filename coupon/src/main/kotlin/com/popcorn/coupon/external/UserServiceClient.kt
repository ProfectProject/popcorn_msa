package com.popcorn.coupon.external

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactive.awaitFirst
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class UserServiceClient(
    private val userServiceWebClient: WebClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 모든 CUSTOMER 역할 사용자 ID 목록 조회
     */
    suspend fun getAllCustomerUserIds(): List<Long> {
        return try {
            logger.info { "🔍 User 서비스에서 모든 CUSTOMER 사용자 ID 조회 시작" }

            val response = userServiceWebClient
                .get()
                .uri("/api/v1/internal/users/customer-ids")
                .header("Authorization", "Bearer system-token") // 시스템 토큰
                .retrieve()
                .awaitBody<List<Long>>()

            logger.info { "✅ CUSTOMER 사용자 ID 조회 완료: ${response.size}명" }
            response

        } catch (e: Exception) {
            logger.error(e) { "❌ CUSTOMER 사용자 ID 조회 실패" }
            emptyList()
        }
    }

    /**
     * 특정 사용자 존재 여부 확인
     */
    suspend fun checkUserExists(userId: Long): Boolean {
        return try {
            userServiceWebClient
                .get()
                .uri("/api/v1/internal/users/{userId}/exists", userId)
                .header("Authorization", "Bearer system-token")
                .retrieve()
                .awaitBody<Map<String, Boolean>>()
                .get("exists") ?: false

        } catch (e: Exception) {
            logger.warn(e) { "사용자 존재 확인 실패: userId=$userId" }
            false
        }
    }
}