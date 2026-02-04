package com.popcorn.payment.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import java.sql.SQLException

/**
 * 외부 서비스 DB 직접 조회를 위한 설정
 * 성능 최적화를 위한 Hybrid 접근 방식
 *
 * 필요한 환경변수가 설정되어 있을 때만 활성화됩니다.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "external.database",
    name = ["users.jdbc-url", "stores.jdbc-url"],
    matchIfMissing = false
)
class ExternalDatabaseConfig {

    private val log = LoggerFactory.getLogger(ExternalDatabaseConfig::class.java)

    /**
     * Users DB 직접 접근용 DataSource
     */
    @Bean("usersDataSource")
    @ConfigurationProperties("external.database.users")
    fun usersDataSource(): DataSource {
        return DataSourceBuilder.create().build()
    }

    /**
     * Stores DB 직접 접근용 DataSource
     */
    @Bean("storesDataSource")
    @ConfigurationProperties("external.database.stores")
    fun storesDataSource(): DataSource {
        return DataSourceBuilder.create().build()
    }

    /**
     * Users DB 조회용 JdbcTemplate
     */
    @Bean("usersJdbcTemplate")
    fun usersJdbcTemplate(): JdbcTemplate {
        return JdbcTemplate(usersDataSource())
    }

    /**
     * Stores DB 조회용 JdbcTemplate
     */
    @Bean("storesJdbcTemplate")
    fun storesJdbcTemplate(): JdbcTemplate {
        return JdbcTemplate(storesDataSource())
    }

    /**
     * 애플리케이션 시작 후 외부 DB 연결 테스트
     */
    @EventListener(ContextRefreshedEvent::class)
    fun testExternalDatabaseConnections() {
        log.info("🔧 [외부 DB] ExternalDatabaseConfig 활성화됨 - 외부 DB 연결 테스트 시작")

        // Users DB 연결 테스트
        testUsersDatabase()

        // Stores DB 연결 테스트
        testStoresDatabase()
    }

    private fun testUsersDatabase() {
        try {
            val usersTemplate = usersJdbcTemplate()

            // 1. 기본 연결 테스트
            val result = usersTemplate.queryForObject("SELECT 1", Int::class.java)
            log.info("✅ [외부 DB] Users DB 기본 연결 성공")

            // 2. 스키마 존재 확인
            val schemaExists = usersTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'user_auth')",
                Boolean::class.java
            )
            log.info("🔍 [외부 DB] user_auth 스키마 존재: {}", schemaExists)

            // 3. customer_addresses 테이블 존재 확인
            val tableExists = usersTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = 'user_auth' AND table_name = 'customer_addresses')",
                Boolean::class.java
            )
            log.info("🔍 [외부 DB] customer_addresses 테이블 존재: {}", tableExists)

            if (tableExists == true) {
                // 4. 테이블 구조 확인
                val columns = usersTemplate.query(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = 'user_auth' AND table_name = 'customer_addresses'"
                ) { rs, _ -> rs.getString("column_name") }
                log.info("🔍 [외부 DB] customer_addresses 컬럼들: {}", columns)

                // 5. 샘플 데이터 확인
                val rowCount = usersTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_auth.customer_addresses",
                    Long::class.java
                )
                log.info("🔍 [외부 DB] customer_addresses 행 수: {}", rowCount)
            }

        } catch (e: SQLException) {
            log.error("❌ [외부 DB] Users DB 연결 실패 - SQL 오류: {}", e.message, e)
        } catch (e: Exception) {
            log.error("❌ [외부 DB] Users DB 테스트 실패: {}", e.message, e)
        }
    }

    private fun testStoresDatabase() {
        try {
            val storesTemplate = storesJdbcTemplate()

            // 기본 연결 테스트
            val result = storesTemplate.queryForObject("SELECT 1", Int::class.java)
            log.info("✅ [외부 DB] Stores DB 기본 연결 성공")

            // goods 테이블 존재 확인
            val goodsTableExists = storesTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = 'goods')",
                Boolean::class.java
            )
            log.info("🔍 [외부 DB] goods 테이블 존재: {}", goodsTableExists)

        } catch (e: Exception) {
            log.error("❌ [외부 DB] Stores DB 테스트 실패: {}", e.message, e)
        }
    }
}
