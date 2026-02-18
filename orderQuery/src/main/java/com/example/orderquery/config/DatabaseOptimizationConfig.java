package com.example.orderquery.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 🚀 OrderQuery 데이터베이스 최적화 설정
 * - HikariCP 연결 풀 최적화
 * - 쿼리 성능 향상을 위한 설정
 * - 대용량 데이터 처리 최적화
 */
@Configuration
@EnableTransactionManagement
@Slf4j
public class DatabaseOptimizationConfig {
    private final Environment environment;

    public DatabaseOptimizationConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 🎯 최적화된 HikariCP 데이터소스 설정
     * - 대시보드 쿼리 성능 최적화
     * - 동시 접속자 지원 강화
     * - 연결 풀 효율성 향상
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource optimizedDataSource() {
        HikariConfig config = new HikariConfig();

        // === 🔧 기본 연결 설정 ===
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(environment.getProperty(
            "spring.datasource.url",
            "jdbc:postgresql://db:5432/popcorn_db?currentSchema=order_query&stringtype=unspecified"
        ));
        config.setUsername(environment.getProperty("spring.datasource.username", "order_query_app"));
        config.setPassword(environment.getProperty("spring.datasource.password", "quary123"));

        // === 📊 연결 풀 최적화 설정 ===
        config.setMaximumPoolSize(20);          // 최대 연결 수 (대시보드 쿼리 고려)
        config.setMinimumIdle(5);               // 최소 유휴 연결
        config.setConnectionTimeout(30000);     // 연결 타임아웃 30초
        config.setIdleTimeout(600000);          // 유휴 연결 타임아웃 10분
        config.setMaxLifetime(1800000);         // 최대 연결 생존 시간 30분
        config.setLeakDetectionThreshold(60000); // 연결 누수 감지 1분

        // === 🚀 성능 최적화 설정 ===
        config.setPoolName("OrderQueryOptimizedPool");

        // === 📈 PostgreSQL 전용 최적화 ===
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");

        // === 🔍 대시보드 쿼리 전용 최적화 ===
        config.addDataSourceProperty("defaultFetchSize", "1000");     // 배치 페치 사이즈
        config.addDataSourceProperty("ApplicationName", "OrderQuery-Dashboard");
        config.addDataSourceProperty("logUnclosedConnections", "true");
        config.addDataSourceProperty("assumeMinServerVersion", "12");

        // === 📊 집계 쿼리 최적화 ===
        config.addDataSourceProperty("enabledSSLModes", "prefer");    // SSL 설정
        config.addDataSourceProperty("readOnlyMode", "true");         // 읽기 전용 모드

        log.info("🚀 [DB 최적화] HikariCP 연결 풀 설정 완료 - maxPoolSize: {}, minIdle: {}",
            config.getMaximumPoolSize(), config.getMinimumIdle());

        return new HikariDataSource(config);
    }

    /**
     * 🔧 JPA 쿼리 최적화 설정
     */
    @Bean("jpaOptimizationProperties")
    public Map<String, Object> jpaOptimizationProperties() {
        Map<String, Object> properties = new HashMap<>();

        // === 🚀 Hibernate 성능 최적화 ===
        properties.put("hibernate.jdbc.batch_size", "30");
        properties.put("hibernate.jdbc.batch_versioned_data", "true");
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");

        // === 📊 쿼리 캐시 설정 ===
        properties.put("hibernate.cache.use_query_cache", "false");
        properties.put("hibernate.cache.use_second_level_cache", "false");

        // === 🔍 통계 및 로깅 ===
        properties.put("hibernate.generate_statistics", "false"); // 운영 환경
        properties.put("hibernate.jdbc.fetch_size", "50");        // 페치 사이즈
        properties.put("hibernate.default_batch_fetch_size", "16");

        // === 📈 대시보드 쿼리 최적화 ===
        properties.put("hibernate.query.plan_cache_max_size", "2048");
        properties.put("hibernate.query.plan_parameter_metadata_max_size", "128");

        log.info("🚀 [JPA 최적화] Hibernate 쿼리 최적화 설정 완료");

        return properties;
    }
}
