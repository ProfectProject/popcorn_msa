package com.popcorn.coupon.domain.entity

import com.fasterxml.jackson.databind.JsonNode
import com.popcorn.coupon.domain.entity.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "coupon_outbox_events")
data class CouponOutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 기본 정보
    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    // 이벤트 정보
    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "event_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    @ColumnTransformer(write = "?::json")
    val eventData: JsonNode,

    // 처리 상태
    @Column(name = "processed_at")
    val processedAt: LocalDateTime? = null

) : BaseEntity() {
    /**
     * 처리 완료 표시
     */
    fun markAsProcessed(): CouponOutboxEvent {
        return copy(processedAt = LocalDateTime.now())
    }

    /**
     * 처리 완료 여부
     */
    fun isProcessed(): Boolean {
        return processedAt != null
    }

    companion object {
        fun couponIssued(couponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "COUPON",
                aggregateId = couponId.toString(),
                eventType = "COUPON_ISSUED",
                eventData = eventData
            )
        }

        fun userCouponIssued(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_ISSUED",
                eventData = eventData
            )
        }

        fun userCouponReserved(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_RESERVED",
                eventData = eventData
            )
        }

        fun userCouponUsed(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_USED",
                eventData = eventData
            )
        }

        fun userCouponCancelled(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_CANCELLED",
                eventData = eventData
            )
        }

        fun userCouponExpired(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_EXPIRED",
                eventData = eventData
            )
        }

        fun userCouponRestored(userCouponId: Long, eventData: JsonNode): CouponOutboxEvent {
            return CouponOutboxEvent(
                aggregateType = "USER_COUPON",
                aggregateId = userCouponId.toString(),
                eventType = "USER_COUPON_RESTORED",
                eventData = eventData
            )
        }
    }
}
