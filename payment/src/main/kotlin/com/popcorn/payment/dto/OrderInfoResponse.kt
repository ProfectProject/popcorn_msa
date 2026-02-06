package com.popcorn.payment.dto

import java.util.*

/**
 * Order 서비스로부터 받는 주문 정보 응답 DTO
 */
data class OrderInfoResponse(
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val status: String,
    val totalAmount: Int,
    val success: Boolean = true,
    val errorMessage: String? = null,
    val hasGoods: Boolean = false  // 굿즈 포함 여부 (배송 주소 검증용)
) {
    companion object {
        fun failure(orderId: UUID, errorMessage: String): OrderInfoResponse {
            return OrderInfoResponse(
                orderId = orderId,
                orderNo = "",
                customerId = 0L,
                status = "",
                totalAmount = 0,
                success = false,
                errorMessage = errorMessage
            )
        }
    }
}