package com.popcorn.payment.client

import com.popcorn.payment.dto.BatchPriceValidationRequest
import com.popcorn.payment.dto.BatchPriceValidationResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/**
 * 🏪 Store 서비스 가격 검증 클라이언트
 * Store 서비스의 배치 가격 검증 API를 호출
 */
@Component
class StoreValidationClient(
    @Qualifier("defaultWebClient") private val webClient: WebClient
) {
    private val log = LoggerFactory.getLogger(StoreValidationClient::class.java)

    @Value("\${gateway.base-url:http://popcorn-gateway:8080}")
    private lateinit var gatewayUrl: String

    private val batchValidationPath = "/api/stores/v1/validation/batch-price"

    /**
     * 🚀 배치 가격 검증 API 호출
     */
    suspend fun validateBatchPrices(request: BatchPriceValidationRequest): BatchPriceValidationResponse {
        return try {
            log.info("🏪 [Store API] 배치 가격 검증 요청 - orderId: {}, lineItems: {}개",
                    request.orderId, request.lineItems.size)

            val response = webClient
                .post()
                .uri("$gatewayUrl$batchValidationPath")
                .header("Content-Type", "application/json")
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ApiResponse::class.java)
                .onErrorMap { exception ->
                    when (exception) {
                        is WebClientResponseException -> {
                            log.error("❌ [Store API] HTTP 오류 - status: {}, body: {}",
                                    exception.statusCode, exception.responseBodyAsString)
                            StoreApiException("Store API 호출 실패: ${exception.statusText}")
                        }
                        else -> {
                            log.error("❌ [Store API] 네트워크 오류: {}", exception.message, exception)
                            StoreApiException("Store 서비스 통신 실패: ${exception.message}")
                        }
                    }
                }
                .awaitSingle()

            if (response.success) {
                val validationResponse = response.data as BatchPriceValidationResponse
                log.info("✅ [Store API] 배치 가격 검증 응답 - orderId: {}, isValid: {}",
                        validationResponse.orderId, validationResponse.isValid)
                validationResponse
            } else {
                log.error("❌ [Store API] 응답 실패 - message: {}", response.message)
                throw StoreApiException("Store API 응답 오류: ${response.message}")
            }

        } catch (e: Exception) {
            log.error("💀 [Store API] 배치 가격 검증 실패 - orderId: {}, error: {}",
                    request.orderId, e.message, e)
            throw e
        }
    }

    /**
     * Store API 응답 구조
     */
    private data class ApiResponse(
        val success: Boolean,
        val message: String,
        val data: Any?
    )
}

/**
 * Store API 호출 예외
 */
class StoreApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)