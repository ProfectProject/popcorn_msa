package com.popcorn.payment

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(scanBasePackages = ["com.popcorn"])
@EnableTransactionManagement
@ConfigurationPropertiesScan("com.popcorn.payment.config")
class PaymentApplication

fun main(args: Array<String>) {
    // Load .env file from project root
    try {
        val currentDir = System.getProperty("user.dir")
        val dotenv = try {
            Dotenv.configure()
                .directory(currentDir)
                .ignoreIfMissing()
                .load()
        } catch (_: Exception) {
            Dotenv.configure()
                .directory("$currentDir/..")
                .ignoreIfMissing()
                .load()
        }

        dotenv.entries().forEach { entry ->
            System.setProperty(entry.key, entry.value)
        }
        println("✅ Loaded .env file with ${dotenv.entries().size} variables")
    } catch (e: Exception) {
        println("⚠️ Could not load .env file: ${e.message}")
    }

    runApplication<PaymentApplication>(*args)
}
