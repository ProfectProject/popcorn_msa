package com.popcorn.coupon.exception

/**
 * 쿠폰 관련 비즈니스 예외
 */
open class CouponException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 쿠폰 발급 관련 예외
 */
class CouponIssuanceException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)

/**
 * 쿠폰 사용 관련 예외
 */
class CouponUsageException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)

/**
 * 쿠폰 유효성 검증 예외
 */
class CouponValidationException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)

/**
 * 쿠폰을 찾을 수 없는 예외
 */
class CouponNotFoundException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)

/**
 * 쿠폰 재고 부족 예외
 */
class CouponStockException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)

/**
 * 쿠폰 만료 예외
 */
class CouponExpiredException(
    message: String,
    cause: Throwable? = null
) : CouponException(message, cause)