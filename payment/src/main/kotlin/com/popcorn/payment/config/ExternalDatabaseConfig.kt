package com.popcorn.payment.config

import org.slf4j.LoggerFactory

/**
 * ❌ 외부 DB 직접 연결 제거됨
 *
 * MSA 원칙에 따라 Payment 서비스는 자체 DB만 사용하고,
 * 다른 서비스 데이터는 HTTP API를 통해 조회합니다.
 *
 * - Users 정보: UserServiceClient를 통한 HTTP 호출
 * - Stores 정보: OrderServiceClient를 통한 HTTP 호출
 */
@Deprecated("외부 DB 직접 연결 제거됨. HTTP API 사용으로 대체.")
class ExternalDatabaseConfig {
    private val log = LoggerFactory.getLogger(ExternalDatabaseConfig::class.java)

    init {
        log.info("⚠️ [DEPRECATED] ExternalDatabaseConfig는 더 이상 사용되지 않습니다. HTTP API 기반으로 변경되었습니다.")
    }
}
