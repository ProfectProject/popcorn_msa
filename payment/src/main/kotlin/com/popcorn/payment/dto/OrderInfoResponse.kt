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
    val hasGoods: Boolean = false,  // 굿즈 포함 여부 (배송 주소 검증용)
    val lineItems: List<OrderLineItem>? = null,  // 라인 아이템 정보 (가격 검증용)
    val popupId: UUID? = null,  // 팝업스토어 ID
    val storeId: UUID? = null   // 스토어 ID
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

/**
 * 주문 라인 아이템 정보 (Store 가격 검증용)
 */
data class OrderLineItem(
    val itemId: UUID,
    val itemType: String,  // "SESSION" 또는 "GOODS"
    val quantity: Int,
    val unitPrice: Int,
    val lineAmount: Int,
    val sessionOptionId: UUID? = null,
    val goodsId: UUID? = null
)