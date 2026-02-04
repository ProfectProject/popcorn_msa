package com.popcorn.payment.config

import com.popcorn.payment.event.base.BasePaymentEventPublisher
import com.popcorn.payment.repository.PaymentRepository
import org.springframework.transaction.annotation.EnableTransactionManagement
import com.popcorn.payment.service.PaymentCommandCoroutineService
import com.popcorn.payment.service.PaymentOrderInfoService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableTransactionManagement
class PaymentServiceConfig {

    @Bean
    @ConditionalOnMissingBean(PaymentCommandCoroutineService::class)
    fun paymentCommandCoroutineService(
        paymentRepository: PaymentRepository,
        paymentEventPublisher: BasePaymentEventPublisher,
        paymentOrderInfoService: PaymentOrderInfoService
    ): PaymentCommandCoroutineService {
        return PaymentCommandCoroutineService(
            paymentRepository,
            paymentEventPublisher,
            paymentOrderInfoService
        )
    }
}
