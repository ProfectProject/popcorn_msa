package com.popcorn.order.service;

import com.popcorn.order.dto.idempotency.IdempotencyKeyResponse;
import com.popcorn.order.dto.idempotency.IdempotencyKeyValidationResponse;
import com.popcorn.order.dto.idempotency.IdempotencyStatisticsResponse;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 멱등성 관리 서비스 인터페이스
 *
 * 중복 요청 방지를 위한 멱등성 키 관리를 담당합니다.
 * API 요청의 중복 실행을 방지하고 안전한 재시도를 지원합니다.
 *
 * 주요 기능:
 * - 멱등성 키 생성 및 검증
 * - 요청 중복 방지 및 결과 캐시
 * - 멱등성 관련 통계 및 모니터링
 * - 만료된 키 정리 및 관리
 */
public interface OrderIdempotencyService {

    /**
     * 새로운 멱등성 키 생성
     *
     * 클라이언트가 API 요청 전에 멱등성 키를 미리 발급받을 수 있습니다.
     * 생성된 키는 제한된 시간 동안 유효하며, 한 번만 사용할 수 있습니다.
     *
     * @return 생성된 멱등성 키 정보 (키값, 만료시간, 사용법 등)
     */
    IdempotencyKeyResponse generateKey();

    /**
     * 멱등성 키 검증 및 사용 처리
     *
     * API 요청 시 전달된 멱등성 키의 유효성을 확인하고,
     * 중복 요청인지 판단하여 적절한 응답을 반환합니다.
     *
     * @param idempotencyKey 클라이언트가 제공한 멱등성 키
     * @param requestId 요청 고유 식별자
     * @param userId 요청한 사용자 ID
     * @return 검증 결과 (유효성, 중복 여부, 이전 결과 등)
     */
    IdempotencyKeyValidationResponse validateAndUseKey(String idempotencyKey, String requestId, Long userId);

    /**
     * 이전 요청 결과 조회
     *
     * 중복 요청인 경우 이전에 처리된 결과를 반환합니다.
     * 클라이언트는 동일한 응답을 받아 멱등성이 보장됩니다.
     *
     * @param idempotencyKey 조회할 멱등성 키
     * @return 이전 요청의 처리 결과 (없으면 null)
     */
    Object getPreviousResult(String idempotencyKey);

    /**
     * 요청 결과 저장
     *
     * API 요청 처리 완료 후 결과를 멱등성 키와 함께 저장합니다.
     * 동일한 키로 재요청 시 이 결과가 반환됩니다.
     *
     * @param idempotencyKey 멱등성 키
     * @param result 저장할 결과 객체
     * @param ttlMinutes 결과 보존 시간 (분 단위)
     */
    void storeResult(String idempotencyKey, Object result, int ttlMinutes);

    /**
     * 멱등성 키 무효화
     *
     * 처리 중 오류가 발생하거나 요청을 취소할 때 키를 무효화합니다.
     * 무효화된 키는 더 이상 사용할 수 없습니다.
     *
     * @param idempotencyKey 무효화할 멱등성 키
     * @param reason 무효화 사유
     */
    void invalidateKey(String idempotencyKey, String reason);

    /**
     * 멱등성 통계 조회
     *
     * 멱등성 키 생성, 사용, 중복 요청 등의 통계를 조회합니다.
     * 시스템 모니터링 및 성능 분석에 활용됩니다.
     *
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간
     * @param period 통계 집계 기간 (HOURLY, DAILY, WEEKLY, MONTHLY)
     * @return 통계 데이터
     */
    IdempotencyStatisticsResponse getStatistics(LocalDateTime startTime, LocalDateTime endTime, String period);

    /**
     * 만료된 키 정리
     *
     * 주기적으로 실행되어 만료된 멱등성 키와 결과 데이터를 정리합니다.
     * 메모리 사용량을 최적화하고 성능을 유지합니다.
     *
     * @return 정리된 키의 개수
     */
    int cleanupExpiredKeys();

    /**
     * 멱등성 키 상태 조회
     *
     * 특정 멱등성 키의 현재 상태와 사용 이력을 조회합니다.
     * 디버깅 및 문제 해결에 활용됩니다.
     *
     * @param idempotencyKey 조회할 멱등성 키
     * @return 키 상태 정보 (생성시간, 사용여부, 만료시간 등)
     */
    Map<String, Object> getKeyStatus(String idempotencyKey);

    /**
     * 사용자별 활성 키 조회
     *
     * 특정 사용자가 현재 보유한 유효한 멱등성 키 목록을 조회합니다.
     * 클라이언트 애플리케이션에서 키 관리에 활용됩니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 활성 멱등성 키 목록
     */
    IdempotencyKeyResponse getActiveKeysForUser(Long userId);

}