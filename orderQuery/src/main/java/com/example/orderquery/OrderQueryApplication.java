package com.example.orderquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.popcorn.common.config.TransactionManagerConfig;
import com.popcorn.common.config.DataSourceConfig;
import com.popcorn.common.config.JdbcTemplateConfig;

@SpringBootApplication
public class OrderQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderQueryApplication.class, args);
    }

}
