package com.popcorn.payment.exception

/**
 * 결제 검증 실패 시 발생하는 예외
 *
 * 보안상 중요한 검증(가격, 주소 등)이 실패했을 때 결제 프로세스를 중단하기 위해 사용됩니다.
 */
class PaymentValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)