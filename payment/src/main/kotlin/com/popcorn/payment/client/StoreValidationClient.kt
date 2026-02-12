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
import java.util.*

/**
 * 🏪 Store 서비스 가격 검증 클라이언트
 * Store 서비스의 배치 가격 검증 API를 호출
 */
@Component
class StoreValidationClient(
    @Qualifier("defaultWebClient") private val webClient: WebClient,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    private val log = LoggerFactory.getLogger(StoreValidationClient::class.java)

    @Value("\${gateway.base-url:http://popcorn-gateway:8080}")
    private lateinit var gatewayUrl: String

    private val batchValidationPath = "/api/stores/v1/validation/batch-price"
    private val sessionPricePath = "/api/stores/v1/sessions/{sessionId}/price"
    private val goodsPricePath = "/api/stores/v1/goods/{goodsId}/price"
    private val sessionValidationPath = "/api/stores/v1/sessions/{sessionId}/valid"

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

            if (response.code == 200) {
                // 🔍 전체 응답 및 raw JSON 로깅
                log.info("🔍 [Store API] 전체 응답 - orderId: {}, response: {}",
                        request.orderId, objectMapper.writeValueAsString(response))
                log.info("🔍 [Store API] 원본 data 타입 - orderId: {}, dataClass: {}, data: {}",
                        request.orderId, response.data?.javaClass?.simpleName, objectMapper.writeValueAsString(response.data))

                // JSON 문자열을 통한 안전한 변환 (ObjectMapper.convertValue 대신)
                val dataJson = objectMapper.writeValueAsString(response.data)
                log.info("🔍 [Store API] data JSON 문자열 - orderId: {}, json: {}",
                        request.orderId, dataJson)
                val validationResponse = objectMapper.readValue(dataJson, BatchPriceValidationResponse::class.java)
                log.info("✅ [Store API] 변환 후 결과 - orderId: {}, isValid: {}, totalActual: {}, totalExpected: {}",
                        validationResponse.orderId, validationResponse.isValid,
                        validationResponse.totalActualAmount, validationResponse.totalExpectedAmount)
                validationResponse
            } else {
                log.error("❌ [Store API] 응답 실패 - code: {}, message: {}", response.code, response.message)
                throw StoreApiException("Store API 응답 오류: ${response.message}")
            }

        } catch (e: Exception) {
            log.error("💀 [Store API] 배치 가격 검증 실패 - orderId: {}, error: {}",
                    request.orderId, e.message, e)
            throw e
        }
    }

    /**
     * 🎫 세션 가격 조회 API 호출
     */
    suspend fun getSessionPrice(sessionId: UUID): Int? {
        return try {
            log.debug("🎫 [Store API] 세션 가격 조회 - sessionId: {}", sessionId)

            val response = webClient
                .get()
                .uri("$gatewayUrl$sessionPricePath", sessionId)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .bodyToMono(ApiResponse::class.java)
                .awaitSingle()

            if (response.code == 200 && response.data != null) {
                val price = (response.data as Number).toInt()
                log.debug("✅ [Store API] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, price)
                price
            } else {
                log.warn("❌ [Store API] 세션 가격 정보 없음 - sessionId: {}, code: {}", sessionId, response.code)
                null
            }
        } catch (e: Exception) {
            log.warn("⚠️ [Store API] 세션 가격 조회 실패 - sessionId: {}, error: {}", sessionId, e.message)
            null
        }
    }

    /**
     * 🛍️ 굿즈 가격 조회 API 호출
     */
    suspend fun getGoodsPrice(goodsId: UUID): Int? {
        return try {
            log.debug("🛍️ [Store API] 굿즈 가격 조회 - goodsId: {}", goodsId)

            val response = webClient
                .get()
                .uri("$gatewayUrl$goodsPricePath", goodsId)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .bodyToMono(ApiResponse::class.java)
                .awaitSingle()

            if (response.code == 200 && response.data != null) {
                val price = (response.data as Number).toInt()
                log.debug("✅ [Store API] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, price)
                price
            } else {
                log.warn("❌ [Store API] 굿즈 가격 정보 없음 - goodsId: {}, code: {}", goodsId, response.code)
                null
            }
        } catch (e: Exception) {
            log.warn("⚠️ [Store API] 굿즈 가격 조회 실패 - goodsId: {}, error: {}", goodsId, e.message)
            null
        }
    }

    /**
     * 🎫 세션 유효성 검증 API 호출
     */
    suspend fun isValidSession(sessionId: UUID): Boolean {
        return try {
            log.debug("🎫 [Store API] 세션 유효성 검증 - sessionId: {}", sessionId)

            val response = webClient
                .get()
                .uri("$gatewayUrl$sessionValidationPath", sessionId)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .bodyToMono(ApiResponse::class.java)
                .awaitSingle()

            if (response.code == 200 && response.data != null) {
                val isValid = response.data as Boolean
                log.debug("✅ [Store API] 세션 유효성 검증 완료 - sessionId: {}, isValid: {}", sessionId, isValid)
                isValid
            } else {
                log.warn("❌ [Store API] 세션 유효성 검증 실패 - sessionId: {}, code: {}", sessionId, response.code)
                false
            }
        } catch (e: Exception) {
            log.warn("⚠️ [Store API] 세션 유효성 검증 오류 - sessionId: {}, error: {}", sessionId, e.message)
            false
        }
    }

    /**
     * Store API 응답 구조 (BaseResponse와 일치)
     */
    private data class ApiResponse(
        val code: Int,
        val message: String,
        val data: Any?
    )
}

/**
 * Store API 호출 예외
 */
class StoreApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)