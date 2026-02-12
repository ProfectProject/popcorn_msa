package com.popcorn.payment.entity

import jakarta.persistence.*
import java.util.*

/**
 * Order 정보를 Payment 서비스에서 직접 조회하기 위한 읽기 전용 Entity
 *
 * 기존 이벤트 기반 통신의 타임아웃 문제를 해결하기 위해
 * Payment 서비스에서 Order 정보를 직접 DB 조회할 수 있도록 함
 *
 * 주의: 이 Entity는 읽기 전용입니다. 수정은 Order 서비스를 통해서만 가능합니다.
 */
@Entity
@Table(name = "p_orders", schema = "orders")
data class OrderQueryEntity(
    @Id
    @Column(name = "order_id")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_no")
    val orderNo: String = "",

    @Column(name = "user_id")
    val customerId: Long = 0L,

    @Column(name = "popup_id")
    val popupId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "orders.orderstatus")
    val status: OrderStatus = OrderStatus.REQUESTED,

    @Column(name = "total_price")
    val totalAmount: Int = 0
)

/**
 * Order 상태 enum (Payment 서비스용 - Order 서비스와 동일한 값)
 */
enum class OrderStatus {
    REQUESTED,       // 주문 요청됨
    ACCEPTED,        // 주문 수락됨
    REJECTED,        // 주문 거절됨
    RESERVED,        // 예약 확정됨
    PAYMENT_PENDING, // 결제 대기
    PAID,            // 결제 완료됨
    COMPLETED,       // 완료
    CANCELLED        // 취소됨
}