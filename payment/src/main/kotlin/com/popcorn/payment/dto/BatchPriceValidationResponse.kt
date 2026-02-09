package com.popcorn.payment.dto

import java.util.*

/**
 * Store 서비스 배치 가격 검증 응답 DTO
 */
data class BatchPriceValidationResponse(
    val orderId: UUID,
    val isValid: Boolean,
    val totalActualAmount: Int,
    val totalExpectedAmount: Int,
    val itemResults: List<LineItemValidationResult>,
    val failureReason: String?
)

/**
 * 개별 아이템 검증 결과
 */
data class LineItemValidationResult(
    val itemId: UUID,
    val itemType: String,
    val isValid: Boolean,
    val actualPrice: Int?,
    val expectedPrice: Int,
    val quantity: Int,
    val actualLineAmount: Int,
    val expectedLineAmount: Int,
    val failureReason: String?
)