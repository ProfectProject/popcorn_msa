package com.popcorn.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.popcorn.order.dto.audit.ApiLogEntry;
import com.popcorn.order.dto.audit.BusinessEventLog;

/**
 * 감사 로그 서비스 인터페이스
 *
 * 시스템의 모든 중요한 이벤트를 기록하고 관리합니다.
 * 컴플라이언스, 보안, 디버깅, 성능 분석에 활용됩니다.
 *
 * 감사 로그의 종류:
 * 1. API 호출 로그: 모든 REST API 요청/응답 기록
 * 2. 비즈니스 이벤트 로그: 주문 생성/변경/취소 등 중요 업무 이벤트
 * 3. 보안 이벤트 로그: 인증 실패, 권한 위반, 의심스러운 접근
 * 4. 시스템 이벤트 로그: 서버 시작/종료, 오류, 성능 임계값 초과
 */
public interface AuditLogService {

    /**
     * API 요청 시작 로그 기록
     *
     * @param logEntry API 로그 엔트리
     */
    void logApiRequest(ApiLogEntry logEntry);

    /**
     * API 요청 완료 로그 기록
     *
     * @param requestId 요청 추적 ID
     * @param responseStatus HTTP 응답 상태 코드
     * @param processingTime 처리 소요 시간 (밀리초)
     * @param exception 발생한 예외 (없으면 null)
     */
    void logApiCompletion(String requestId, Integer responseStatus, Long processingTime, Exception exception);

    /**
     * 비즈니스 이벤트 로그 기록
     *
     * @param eventLog 비즈니스 이벤트 로그
     */
    void logBusinessEvent(BusinessEventLog eventLog);

    /**
     * 보안 이벤트 로그 기록
     *
     * @param eventType 이벤트 타입 (LOGIN_FAILED, UNAUTHORIZED_ACCESS 등)
     * @param userId 사용자 ID (알 수 없으면 null)
     * @param clientIp 클라이언트 IP 주소
     * @param description 상세 설명
     * @param metadata 추가 메타데이터
     */
    void logSecurityEvent(String eventType, Long userId, String clientIp, String description, Map<String, Object> metadata);

    /**
     * 시스템 이벤트 로그 기록
     *
     * @param eventType 이벤트 타입 (SERVER_START, ERROR_OCCURRED 등)
     * @param severity 심각도 (INFO, WARN, ERROR, CRITICAL)
     * @param description 상세 설명
     * @param metadata 추가 메타데이터
     */
    void logSystemEvent(String eventType, String severity, String description, Map<String, Object> metadata);

    /**
     * API 로그 검색
     *
     * @param startTime 검색 시작 시간
     * @param endTime 검색 종료 시간
     * @param userId 사용자 ID 필터 (선택적)
     * @param method HTTP 메서드 필터 (선택적)
     * @param status 응답 상태 필터 (선택적)
     * @param pageable 페이징 정보
     * @return 페이징된 API 로그 목록
     */
    Page<ApiLogEntry> searchApiLogs(LocalDateTime startTime, LocalDateTime endTime,
                                   Long userId, String method, Integer status, Pageable pageable);

    /**
     * 비즈니스 이벤트 로그 검색
     *
     * @param startTime 검색 시작 시간
     * @param endTime 검색 종료 시간
     * @param eventType 이벤트 타입 필터 (선택적)
     * @param userId 사용자 ID 필터 (선택적)
     * @param pageable 페이징 정보
     * @return 페이징된 비즈니스 이벤트 로그 목록
     */
    Page<BusinessEventLog> searchBusinessEventLogs(LocalDateTime startTime, LocalDateTime endTime,
                                                   String eventType, Long userId, Pageable pageable);

    /**
     * 보안 이벤트 로그 검색
     *
     * @param startTime 검색 시작 시간
     * @param endTime 검색 종료 시간
     * @param eventType 이벤트 타입 필터 (선택적)
     * @param userId 사용자 ID 필터 (선택적)
     * @param clientIp 클라이언트 IP 필터 (선택적)
     * @param pageable 페이징 정보
     * @return 페이징된 보안 이벤트 로그 목록
     */
    Page<Object> searchSecurityEventLogs(LocalDateTime startTime, LocalDateTime endTime,
                                        String eventType, Long userId, String clientIp, Pageable pageable);

    /**
     * 로그 통계 정보 조회
     *
     * @param startTime 통계 시작 시간
     * @param endTime 통계 종료 시간
     * @param logType 로그 타입 (API, BUSINESS, SECURITY, SYSTEM)
     * @return 통계 정보 맵
     */
    Map<String, Object> getLogStatistics(LocalDateTime startTime, LocalDateTime endTime, String logType);

    /**
     * 성능 분석 리포트 생성
     *
     * @param startTime 분석 시작 시간
     * @param endTime 분석 종료 시간
     * @return 성능 분석 결과
     */
    Map<String, Object> generatePerformanceReport(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 의심스러운 활동 탐지
     *
     * @param lookbackHours 탐지할 과거 시간 (시간 단위)
     * @return 의심스러운 활동 목록
     */
    List<Map<String, Object>> detectSuspiciousActivities(int lookbackHours);

    /**
     * 로그 데이터 정리 (오래된 로그 삭제)
     *
     * @param retentionDays 보관 기간 (일 단위)
     * @param logType 정리할 로그 타입 (ALL, API, BUSINESS, SECURITY, SYSTEM)
     * @return 정리된 로그 개수
     */
    long cleanupOldLogs(int retentionDays, String logType);

    /**
     * 로그 데이터 아카이브 (압축하여 장기 보관)
     *
     * @param archiveBeforeDays 아카이브할 기준 일수
     * @param logType 아카이브할 로그 타입
     * @return 아카이브된 로그 개수
     */
    long archiveLogs(int archiveBeforeDays, String logType);

    /**
     * 실시간 알림이 필요한 이벤트 확인
     *
     * 심각한 보안 이벤트나 시스템 오류 발생 시
     * 즉시 관리자에게 알림을 전송해야 하는 이벤트들을 식별합니다.
     *
     * @param minutes 확인할 최근 시간 범위 (분 단위)
     * @return 알림이 필요한 이벤트 목록
     */
    List<Map<String, Object>> getEventsRequiringNotification(int minutes);

}