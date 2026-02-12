package com.popcorn.coupon.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.popcorn.coupon.domain.repository"])
@EnableTransactionManagement
class DatabaseConfig