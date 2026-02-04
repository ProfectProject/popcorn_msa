package com.popcorn.payment.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

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
}
