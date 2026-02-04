package com.popcorn.common.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DataSourceConfig {

	// JDBC/Flyway/JdbcTemplate 등 동기 작업용 DataSource 설정입니다.
	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	public DataSource dataSource(DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}
}
