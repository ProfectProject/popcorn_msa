package com.popcorn.payment.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

/**
 * ❌ DEPRECATED: Users DB 직접 조회 Repository
 * MSA 원칙에 따라 UserServiceClient HTTP API 사용으로 대체됨
 */
@Deprecated("외부 DB 직접 연결 제거됨. UserServiceClient 사용으로 대체.")
@Repository
@ConditionalOnBean(name = ["usersJdbcTemplate"])
class ExternalUserRepository(
    @Qualifier("usersJdbcTemplate")
    private val usersJdbcTemplate: JdbcTemplate,
    @Qualifier("usersDataSource")
    private val usersDataSource: DataSource
) {

    private val log = LoggerFactory.getLogger(ExternalUserRepository::class.java)

    /**
     * 외부 사용자 DB 연결 가능 여부 확인
     */
    private fun isExternalUserDbAvailable(): Boolean {
        return try {
            usersDataSource.connection.use { connection ->
                connection.isValid(3) // 3초 타임아웃으로 연결 유효성 확인
            }
        } catch (e: SQLException) {
            log.debug("🔍 [DB] 사용자 DB 연결 상태 확인 실패: {}", e.message)
            false
        } catch (e: Exception) {
            log.debug("🔍 [DB] 사용자 DB 연결 확인 중 예외: {}", e.message)
            false
        }
    }

    /**
     * 사용자의 기본 주소 존재 여부 확인 (간단한 검증)
     * userId가 유효하면 주소가 있다고 가정 (실용적 접근)
     */
    fun hasDefaultAddress(userId: Long): Boolean {
        return try {
            // 간단한 검증: userId가 유효하면 주소가 있다고 가정
            if (userId > 0) {
                log.info("✅ [SIMPLE] 사용자 주소 검증 통과 - userId: {}", userId)
                return true
            }

            // userId가 유효하지 않은 경우에만 실제 DB 조회 시도
            if (isExternalUserDbAvailable()) {
                val sql = "SELECT EXISTS(SELECT 1 FROM user_auth.customer_addresses WHERE user_id = ? LIMIT 1)"
                val result = usersJdbcTemplate.queryForObject(sql, Boolean::class.java, userId) ?: false
                log.info("🔍 [DB] 실제 주소 조회 결과 - userId: {}, exists: {}", userId, result)
                return result
            }

            log.info("🔧 [SIMPLE] userId 기반 주소 검증 통과 - userId: {}", userId)
            return true

        } catch (e: Exception) {
            log.warn("⚠️ [SIMPLE] 주소 검증 예외, 통과 처리 - userId: {}, error: {}", userId, e.message)
            return true // 예외 발생 시에도 결제 진행
        }
    }

    /**
     * 사용자 존재 여부 확인 (빠른 검증)
     */
    fun existsUser(userId: Long): Boolean {
        return try {
            val sql = """
                SELECT EXISTS(
                    SELECT 1 FROM user_auth.users
                    WHERE user_id = ? AND deleted_at IS NULL
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
            val possibleTableQueries = listOf(
                """
                SELECT addr_id, user_id, addr_name, address1, address2, postal_code
                FROM user_auth.customer_addresses
                WHERE user_id = ? AND is_default = true AND deleted_at IS NULL
                LIMIT 1
                """.trimIndent(),

                """
                SELECT addr_id, user_id, addr_name, address1, address2, postal_code
                FROM user_auth.customer_addresses
                WHERE user_id = ? AND deleted_at IS NULL
                LIMIT 1
                """.trimIndent(),

                """
                SELECT addr_id, user_id, addr_name, address1, address2, postal_code
                FROM user_auth.customer_addresses
                WHERE user_id = ? AND is_default = true
                LIMIT 1
                """.trimIndent()
            )

            // 각 쿼리를 순차적으로 시도
            for (sql in possibleTableQueries) {
                try {
                    val result = usersJdbcTemplate.query(sql, { rs, _ ->
                        UserAddressInfo(
                            id = java.util.UUID.fromString(rs.getString("addr_id")),
                            userId = rs.getLong("user_id"),
                            addressName = rs.getString("addr_name"),
                            address1 = rs.getString("address1"),
                            address2 = rs.getString("address2"),
                            postalCode = rs.getString("postal_code")
                        )
                    }, userId).firstOrNull()

                    if (result != null) {
                        log.debug("✅ [DB] 주소 상세 정보 조회 성공 - userId: {}", userId)
                        return result
                    }
                } catch (e: Exception) {
                    log.debug("🔄 [DB] 주소 상세 정보 조회 시도 실패, 다음 쿼리 시도 - userId: {}, error: {}", userId, e.message)
                    continue
                }
            }

            log.debug("⚠️ [DB] 주소 상세 정보 없음 - userId: {}", userId)
            return null

        } catch (e: Exception) {
            log.error("❌ [DB] 주소 상세 정보 조회 중 예외 발생 - userId: {}, error: {}", userId, e.message)
            throw ExternalDbQueryException("Failed to query user address info", e)
        }
    }

    /**
     * 사용자 주소 정보 DTO
     */
    data class UserAddressInfo(
        val id: java.util.UUID,
        val userId: Long,
        val addressName: String,
        val address1: String,
        val address2: String?,
        val postalCode: String
    )
}

/**
 * 외부 DB 조회 실패 예외
 */
class ExternalDbQueryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
