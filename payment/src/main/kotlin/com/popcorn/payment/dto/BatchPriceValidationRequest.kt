package com.popcorn.payment.dto

import java.util.*

/**
 * Store 서비스 배치 가격 검증 요청 DTO
 */
data class BatchPriceValidationRequest(
    val orderId: UUID,
    val lineItems: List<LineItemPriceRequest>,
    val totalExpectedAmount: Int
)

/**
 * 라인 아이템 가격 검증 요청 DTO
 */
data class LineItemPriceRequest(
    val itemId: UUID,
    val itemType: String, // "SESSION" 또는 "GOODS"
    val expectedPrice: Int,
    val quantity: Int
)