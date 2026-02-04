package com.popcorn.payment.service

import org.springframework.stereotype.Service

/**
 * 결제 전 검증 서비스
 * - 검증 로직은 HybridValidationService(DB 기반)로 위임
 */
@Service
class PaymentValidationService(
    private val hybridValidationService: HybridValidationService
) {

    suspend fun validateUserAddress(userId: Long): Boolean {
        return hybridValidationService.validateUserAddressHybrid(userId)
    }
}
