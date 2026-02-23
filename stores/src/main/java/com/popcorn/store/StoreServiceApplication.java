package com.popcorn.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.popcorn.common.config.TransactionManagerConfig;
import com.popcorn.common.config.DataSourceConfig;
import com.popcorn.common.config.JdbcTemplateConfig;

@SpringBootApplication
@ComponentScan(basePackages = {"com.popcorn.store", "com.popcorn.common"})
@EnableScheduling
public class StoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreServiceApplication.class, args);
    }
}
