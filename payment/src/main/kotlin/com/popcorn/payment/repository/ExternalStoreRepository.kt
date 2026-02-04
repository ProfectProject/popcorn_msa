package com.popcorn.payment.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

/**
 * Stores DB 직접 조회 Repository
 * 성능 최적화를 위한 DB 직접 접근
 */
@Repository
class ExternalStoreRepository(
    @Qualifier("storesJdbcTemplate")
    private val storesJdbcTemplate: JdbcTemplate
) {

    /**
     * 세션 가격 조회 (중요한 검증)
     * 성능: HTTP 호출 (200-500ms) → DB 조회 (10-50ms)
     */
    fun findSessionPrice(sessionId: UUID): Int? {
        return try {
            val sql = """
                SELECT price
                FROM popup_sessions
                WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()

            storesJdbcTemplate.queryForObject(sql, Int::class.java, sessionId)

        } catch (e: Exception) {
            // 데이터 없음 또는 DB 오류
            null
        }
    }

    /**
     * 굿즈 가격 조회 (중요한 검증)
     */
    fun findGoodsPrice(goodsId: UUID): Int? {
        return try {
            val sql = """
                SELECT price
                FROM goods
                WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()

            storesJdbcTemplate.queryForObject(sql, Int::class.java, goodsId)

        } catch (e: Exception) {
            null
        }
    }

    /**
     * 굿즈 변형 가격 조회 (상세 가격)
     */
    fun findGoodsVariantPrice(goodsId: UUID, variantId: UUID?): Int? {
        return try {
            if (variantId == null) {
                return findGoodsPrice(goodsId)
            }

            val sql = """
                SELECT
                    COALESCE(gv.price, g.price) as final_price
                FROM goods g
                LEFT JOIN goods_variants gv ON gv.goods_id = g.id AND gv.id = ?
                WHERE g.id = ? AND g.deleted_at IS NULL
            """.trimIndent()

            storesJdbcTemplate.queryForObject(sql, Int::class.java, variantId, goodsId)

        } catch (e: Exception) {
            null
        }
    }

    /**
     * 팝업 세션 유효성 확인 (빠른 검증)
     */
    fun isValidSession(sessionId: UUID): Boolean {
        return try {
            val sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM popup_sessions
                    WHERE id = ?
                      AND deleted_at IS NULL
                      AND NOW() BETWEEN start_time AND end_time
                )
            """.trimIndent()

            storesJdbcTemplate.queryForObject(sql, Boolean::class.java, sessionId) ?: false

        } catch (e: Exception) {
            false
        }
    }

    /**
     * 굿즈 재고 확인 (빠른 검증)
     */
    fun checkGoodsStock(goodsId: UUID, quantity: Int): StockCheckResult {
        return try {
            val sql = """
                SELECT
                    stock_quantity,
                    reserved_quantity,
                    (stock_quantity - reserved_quantity) as available_quantity
                FROM goods
                WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()

            storesJdbcTemplate.query(sql, { rs, _ ->
                val stockQuantity = rs.getInt("stock_quantity")
                val reservedQuantity = rs.getInt("reserved_quantity")
                val availableQuantity = rs.getInt("available_quantity")

                StockCheckResult(
                    exists = true,
                    stockQuantity = stockQuantity,
                    reservedQuantity = reservedQuantity,
                    availableQuantity = availableQuantity,
                    isAvailable = availableQuantity >= quantity
                )
            }, goodsId).firstOrNull() ?: StockCheckResult.notFound()

        } catch (e: Exception) {
            StockCheckResult.error()
        }
    }

    /**
     * 팝업 정보 조회 (상세 검증용)
     */
    fun findPopupInfo(popupId: UUID): PopupInfo? {
        return try {
            val sql = """
                SELECT
                    id,
                    store_id,
                    name,
                    status,
                    start_date,
                    end_date
                FROM popups
                WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()

            storesJdbcTemplate.query(sql, { rs, _ ->
                PopupInfo(
                    id = UUID.fromString(rs.getString("id")),
                    storeId = UUID.fromString(rs.getString("store_id")),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    startDate = rs.getTimestamp("start_date").toLocalDateTime(),
                    endDate = rs.getTimestamp("end_date").toLocalDateTime()
                )
            }, popupId).firstOrNull()

        } catch (e: Exception) {
            null
        }
    }

    /**
     * 재고 확인 결과 DTO
     */
    data class StockCheckResult(
        val exists: Boolean,
        val stockQuantity: Int = 0,
        val reservedQuantity: Int = 0,
        val availableQuantity: Int = 0,
        val isAvailable: Boolean = false,
        val error: Boolean = false
    ) {
        companion object {
            fun notFound() = StockCheckResult(exists = false)
            fun error() = StockCheckResult(exists = false, error = true)
        }
    }

    /**
     * 팝업 정보 DTO
     */
    data class PopupInfo(
        val id: UUID,
        val storeId: UUID,
        val name: String,
        val status: String,
        val startDate: java.time.LocalDateTime,
        val endDate: java.time.LocalDateTime
    )
}