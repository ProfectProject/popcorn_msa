package com.example.orderquery.config;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 🗃️ JPA 설정 - 명시적 EntityManagerFactory 정의
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.orderquery.domain")
@EntityScan(basePackages = "com.example.orderquery.domain")
@EnableTransactionManagement
public class JpaConfig {

    /**
     * EntityManagerFactory 빈을 명시적으로 정의
     * Spring Data JPA Repository가 참조할 수 있도록 함
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("optimizedDataSource") DataSource optimizedDataSource,
            @Qualifier("jpaOptimizationProperties")
            Map<String, Object> jpaProperties
    ) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(optimizedDataSource);
        em.setPackagesToScan("com.example.orderquery.domain");

        // Hibernate JPA Vendor Adapter 설정
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(true);
        em.setJpaVendorAdapter(vendorAdapter);

        // Hibernate를 PersistenceProvider로 명시적 설정
        em.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        em.setJpaPropertyMap(jpaProperties);

        return em;
    }
}
