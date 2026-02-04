package com.popcorn.payment.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Users DB 직접 조회 Repository
 * 성능 최적화를 위한 DB 직접 접근
 */
@Repository
class ExternalUserRepository(
    @Qualifier("usersJdbcTemplate")
    private val usersJdbcTemplate: JdbcTemplate
) {

    /**
     * 사용자의 기본 주소 존재 여부 확인 (중요한 검증)
     * 성능: HTTP 호출 (200-500ms) → DB 조회 (10-50ms)
     */
    fun hasDefaultAddress(userId: Long): Boolean {
        return try {
            val sql = """
                SELECT EXISTS(
                    SELECT 1 FROM user_addresses
                    WHERE user_id = ? AND is_default = true AND deleted_at IS NULL
                )
            """.trimIndent()

            usersJdbcTemplate.queryForObject(sql, Boolean::class.java, userId) ?: false

        } catch (e: Exception) {
            // DB 조회 실패 시 HTTP 호출로 fallback
            throw ExternalDbQueryException("Failed to query user default address", e)
        }
    }

    /**
     * 사용자 존재 여부 확인 (빠른 검증)
     */
    fun existsUser(userId: Long): Boolean {
        return try {
            val sql = """
                SELECT EXISTS(
                    SELECT 1 FROM users
                    WHERE id = ? AND deleted_at IS NULL
                )
            """.trimIndent()

            usersJdbcTemplate.queryForObject(sql, Boolean::class.java, userId) ?: false

        } catch (e: Exception) {
            throw ExternalDbQueryException("Failed to check user existence", e)
        }
    }

    /**
     * 사용자 기본 주소 정보 조회 (상세 정보 필요 시)
     */
    fun findDefaultAddressInfo(userId: Long): UserAddressInfo? {
        return try {
            val sql = """
                SELECT
                    id,
                    user_id,
                    address_name,
                    road_address,
                    detail_address,
                    postal_code
                FROM user_addresses
                WHERE user_id = ? AND is_default = true AND deleted_at IS NULL
                LIMIT 1
            """.trimIndent()

            usersJdbcTemplate.query(sql, { rs, _ ->
                UserAddressInfo(
                    id = rs.getLong("id"),
                    userId = rs.getLong("user_id"),
                    addressName = rs.getString("address_name"),
                    roadAddress = rs.getString("road_address"),
                    detailAddress = rs.getString("detail_address"),
                    postalCode = rs.getString("postal_code")
                )
            }, userId).firstOrNull()

        } catch (e: Exception) {
            throw ExternalDbQueryException("Failed to query user address info", e)
        }
    }

    /**
     * 사용자 주소 정보 DTO
     */
    data class UserAddressInfo(
        val id: Long,
        val userId: Long,
        val addressName: String,
        val roadAddress: String,
        val detailAddress: String?,
        val postalCode: String
    )
}

/**
 * 외부 DB 조회 실패 예외
 */
class ExternalDbQueryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)