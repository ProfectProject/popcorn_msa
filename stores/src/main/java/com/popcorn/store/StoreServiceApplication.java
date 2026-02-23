package com.popcorn.store;

import com.popcorn.common.config.DataSourceConfig;
import com.popcorn.common.config.JdbcTemplateConfig;
import com.popcorn.common.config.TransactionManagerConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@Import({
	DataSourceConfig.class,
	TransactionManagerConfig.class,
	JdbcTemplateConfig.class
})
public class StoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(StoreServiceApplication.class, args);
	}
}
