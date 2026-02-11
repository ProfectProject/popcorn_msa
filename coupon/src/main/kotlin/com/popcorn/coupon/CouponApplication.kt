package com.popcorn.coupon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
class CouponApplication

fun main(args: Array<String>) {
    runApplication<CouponApplication>(*args)
}
