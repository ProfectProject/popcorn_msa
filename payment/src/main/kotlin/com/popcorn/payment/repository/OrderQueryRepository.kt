package com.popcorn.payment.repository

import com.popcorn.payment.entity.OrderQueryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Payment 서비스에서 Order 정보를 직접 조회하기 위한 Repository
 *
 * 기존 이벤트 기반 통신의 타임아웃 문제를 해결하기 위해
 * Order 테이블을 직접 쿼리합니다.
 */
@Repository
interface OrderQueryRepository : JpaRepository<OrderQueryEntity, UUID> {

    /**
     * Order ID로 주문 정보 조회 (결제 검증용)
     */
    override fun findById(orderId: UUID): Optional<OrderQueryEntity>

    /**
     * 결제 가능한 상태의 주문만 조회
     */
    @Query("""
        SELECT o FROM OrderQueryEntity o
        WHERE o.id = :orderId
        AND o.status IN ('REQUESTED', 'RESERVED', 'PAYMENT_PENDING')
    """)
    fun findPayableOrderById(@Param("orderId") orderId: UUID): Optional<OrderQueryEntity>

    /**
     * 고객 ID와 Order ID로 주문 정보 조회 (권한 검증 포함)
     */
    @Query("""
        SELECT o FROM OrderQueryEntity o
        WHERE o.id = :orderId
        AND o.customerId = :customerId
    """)
    fun findByIdAndCustomerId(
        @Param("orderId") orderId: UUID,
        @Param("customerId") customerId: Long
    ): Optional<OrderQueryEntity>
}