package com.popcorn.payment

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(scanBasePackages = ["com.popcorn"])
@EnableAsync
@EnableTransactionManagement
@ConfigurationPropertiesScan("com.popcorn.payment.config")
class PaymentApplication

fun main(args: Array<String>) {
    // Load .env file from project root
    try {
        val dotenv = Dotenv.configure()
            .directory("../../")  // Go up to project root
            .ignoreIfMissing()
            .load()

        // Set system properties from .env
        dotenv.entries().forEach { entry ->
            System.setProperty(entry.key, entry.value)
        }
        println("✅ Loaded .env file with ${dotenv.entries().size} variables")
    } catch (e: Exception) {
        println("⚠️ Could not load .env file: ${e.message}")
    }

    runApplication<PaymentApplication>(*args)
}
