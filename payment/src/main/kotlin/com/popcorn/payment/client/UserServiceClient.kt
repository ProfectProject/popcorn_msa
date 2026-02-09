package com.popcorn.payment.client

import com.popcorn.payment.util.SystemPassportGenerator
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * User 서비스와 HTTP 통신하는 클라이언트
 *
 * 사용자 주소 정보를 HTTP API로 조회
 */
@Service
class UserServiceClient(
    @Qualifier("defaultWebClient")
    private val webClient: WebClient,
    private val systemPassportGenerator: SystemPassportGenerator
) {
    private val log = LoggerFactory.getLogger(UserServiceClient::class.java)

    @Value("\${user.service.base-url:http://popcorn-gateway:8080}")
    private lateinit var userServiceBaseUrl: String

    /**
     * 사용자 주소 목록 조회 (HTTP API 호출)
     */
    suspend fun getUserAddresses(userId: Long): List<UserAddressResponse> {
        return try {
            log.debug("🔍 [HTTP] User 서비스 주소 API 호출 시작 - userId: {}", userId)

            // 실제 사용자 ID로 Passport 생성
            val userPassport = systemPassportGenerator.generateUserPassport(userId)

            val response = webClient
                .get()
                .uri("$userServiceBaseUrl/api/users/v1/users/{userId}/addresses", userId)
                .header("X-Passport", userPassport)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .bodyToFlux(UserAddressResponse::class.java)
                .collectList()
                .awaitSingle()

            log.info("✅ [HTTP] 사용자 주소 목록 조회 성공 - userId: {}, 주소 개수: {}",
                userId, response.size)

            response
        } catch (ex: WebClientResponseException) {
            log.error("❌ [HTTP] User API 호출 실패 - userId: {}, status: {}, error: {}",
                userId, ex.statusCode, ex.message)
            emptyList()
        } catch (e: Exception) {
            log.error("❌ [HTTP] User 서비스 통신 실패 - userId: {}, error: {}",
                userId, e.message, e)
            emptyList()
        }
    }

    /**
     * 기본 주소 존재 여부 확인
     */
    suspend fun hasDefaultAddress(userId: Long): Boolean {
        return try {
            val addresses = getUserAddresses(userId)
            val hasDefault = addresses.any { it.isDefault == true }  // nullable Boolean 처리

            log.debug("🔍 [HTTP] 기본 주소 존재 여부 - userId: {}, 전체주소: {}개, hasDefault: {}",
                userId, addresses.size, hasDefault)

            hasDefault
        } catch (e: Exception) {
            log.error("❌ [HTTP] 기본 주소 확인 실패 - userId: {}, error: {}", userId, e.message, e)
            false
        }
    }

    /**
     * 사용자 존재 여부 확인
     */
    suspend fun existsUser(userId: Long): Boolean {
        return try {
            log.debug("🔍 [HTTP] 사용자 존재 확인 - userId: {}", userId)

            // 실제 사용자 ID로 Passport 생성
            val userPassport = systemPassportGenerator.generateUserPassport(userId)

            val response = webClient
                .head()
                .uri("$userServiceBaseUrl/api/users/v1/users/{userId}", userId)
                .header("X-Passport", userPassport)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()

            val exists = response.statusCode.is2xxSuccessful
            log.debug("✅ [HTTP] 사용자 존재 확인 완료 - userId: {}, exists: {}", userId, exists)
            exists

        } catch (ex: WebClientResponseException) {
            val exists = ex.statusCode.value() != 404
            log.debug("⚠️ [HTTP] 사용자 존재 확인 - userId: {}, status: {}, exists: {}",
                userId, ex.statusCode, exists)
            exists
        } catch (e: Exception) {
            log.warn("❌ [HTTP] 사용자 존재 확인 실패 - userId: {}, error: {}", userId, e.message)
            true // fallback: 사용자가 존재한다고 가정
        }
    }
}

/**
 * User 주소 응답 DTO
 */
data class UserAddressResponse(
    val addrId: String,        // UUID는 String으로 받음
    val userId: Long,
    val addrName: String,
    val address1: String,
    val address2: String?,
    val postalCode: String,
    val isDefault: Boolean?    // nullable Boolean
)