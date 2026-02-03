package com.popcorn.order.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.popcorn.order.annotation.PerformanceMonitoring;
import com.popcorn.order.dto.audit.ApiLogEntry;
import com.popcorn.order.dto.audit.BusinessEventLog;
import com.popcorn.order.service.monitor.AuditLogService;

import lombok.extern.slf4j.Slf4j;

/**
 * 감사 로그 서비스 구현체
 *
 * 실제 운영 환경에서는 데이터베이스나 전용 로그 저장소를 사용해야 하지만,
 * 여기서는 데모 목적으로 메모리 기반 구현을 제공합니다.
 *
 * 운영 환경 고려사항:
 * 1. 데이터베이스 저장: 영구 보관과 복잡한 쿼리 지원
 * 2. ELK 스택: Elasticsearch + Logstash + Kibana로 로그 분석
 * 3. 분산 로깅: Kafka, Fluentd 등으로 대용량 로그 처리
 * 4. 압축 및 아카이브: 장기 보관을 위한 데이터 압축
 * 5. 백업 및 복구: 중요한 감사 데이터의 안전한 보관
 */
@Service
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    /** API 로그 저장소 (실제로는 데이터베이스 사용) */
    private final Map<String, ApiLogEntry> apiLogs = new ConcurrentHashMap<>();

    /** 비즈니스 이벤트 로그 저장소 */
    private final Map<String, BusinessEventLog> businessEventLogs = new ConcurrentHashMap<>();

    /** 보안 이벤트 로그 저장소 */
    private final Map<String, Map<String, Object>> securityEventLogs = new ConcurrentHashMap<>();

    /** 시스템 이벤트 로그 저장소 */
    private final Map<String, Map<String, Object>> systemEventLogs = new ConcurrentHashMap<>();

    @Override
    @PerformanceMonitoring(threshold = 50, category = "audit")
    public void logApiRequest(ApiLogEntry logEntry) {
        try {
            apiLogs.put(logEntry.getRequestId(), logEntry);

            log.info("API 요청 로그 기록 - ID: {}, URL: {}, 사용자: {}",
                    logEntry.getRequestId(), logEntry.getFullUrl(), logEntry.getUserId());

        } catch (Exception e) {
            log.error("API 요청 로그 기록 실패 - ID: {}, 오류: {}",
                     logEntry.getRequestId(), e.getMessage());
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 50, category = "audit")
    public void logApiCompletion(String requestId, Integer responseStatus, Long processingTime, Exception exception) {
        try {
            ApiLogEntry logEntry = apiLogs.get(requestId);
            if (logEntry != null) {
                // 기존 로그 엔트리 업데이트 (실제로는 빌더 패턴이나 setter 사용)
                ApiLogEntry updatedEntry = ApiLogEntry.builder()
                        .requestId(logEntry.getRequestId())
                        .method(logEntry.getMethod())
                        .url(logEntry.getUrl())
                        .queryString(logEntry.getQueryString())
                        .clientIp(logEntry.getClientIp())
                        .userAgent(logEntry.getUserAgent())
                        .sessionId(logEntry.getSessionId())
                        .userId(logEntry.getUserId())
                        .requestTime(logEntry.getRequestTime())
                        .responseTime(LocalDateTime.now())
                        .processingTimeMs(processingTime)
                        .responseStatus(responseStatus)
                        .controllerClass(logEntry.getControllerClass())
                        .methodName(logEntry.getMethodName())
                        .errorMessage(exception != null ? exception.getMessage() : null)
                        .stackTrace(exception != null ? Arrays.toString(exception.getStackTrace()) : null)
                        .build();

                apiLogs.put(requestId, updatedEntry);

                log.info("API 완료 로그 기록 - ID: {}, 상태: {}, 처리시간: {}ms",
                        requestId, responseStatus, processingTime);
            }

        } catch (Exception e) {
            log.error("API 완료 로그 기록 실패 - ID: {}, 오류: {}", requestId, e.getMessage());
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 50, category = "audit")
    public void logBusinessEvent(BusinessEventLog eventLog) {
        try {
            businessEventLogs.put(eventLog.getEventId(), eventLog);

            log.info("비즈니스 이벤트 로그 기록 - ID: {}, 타입: {}, 주문: {}, 사용자: {}",
                    eventLog.getEventId(), eventLog.getEventType(),
                    eventLog.getOrderNo(), eventLog.getUserId());

            // 중요한 이벤트인 경우 추가 처리
            if (eventLog.isCritical()) {
                log.warn("중요 비즈니스 이벤트 발생 - {}", eventLog.getSummary());
                // TODO: 실시간 알림 전송
            }

        } catch (Exception e) {
            log.error("비즈니스 이벤트 로그 기록 실패 - ID: {}, 오류: {}",
                     eventLog.getEventId(), e.getMessage());
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 50, category = "audit")
    public void logSecurityEvent(String eventType, Long userId, String clientIp,
                                String description, Map<String, Object> metadata) {
        try {
            String eventId = "SEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Map<String, Object> securityEvent = new HashMap<>();
            securityEvent.put("eventId", eventId);
            securityEvent.put("eventType", eventType);
            securityEvent.put("eventTime", LocalDateTime.now());
            securityEvent.put("userId", userId);
            securityEvent.put("clientIp", clientIp);
            securityEvent.put("description", description);
            securityEvent.put("metadata", metadata);
            securityEvent.put("severity", getSeverityForSecurityEvent(eventType));

            securityEventLogs.put(eventId, securityEvent);

            log.warn("보안 이벤트 로그 기록 - ID: {}, 타입: {}, 사용자: {}, IP: {}",
                    eventId, eventType, userId, clientIp);

            // 심각한 보안 이벤트인 경우 즉시 알림
            if (isCriticalSecurityEvent(eventType)) {
                log.error("심각한 보안 이벤트 발생 - 타입: {}, 설명: {}", eventType, description);
                // TODO: 즉시 관리자 알림
            }

        } catch (Exception e) {
            log.error("보안 이벤트 로그 기록 실패 - 타입: {}, 오류: {}", eventType, e.getMessage());
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 50, category = "audit")
    public void logSystemEvent(String eventType, String severity, String description,
                              Map<String, Object> metadata) {
        try {
            String eventId = "SYS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Map<String, Object> systemEvent = new HashMap<>();
            systemEvent.put("eventId", eventId);
            systemEvent.put("eventType", eventType);
            systemEvent.put("eventTime", LocalDateTime.now());
            systemEvent.put("severity", severity);
            systemEvent.put("description", description);
            systemEvent.put("metadata", metadata);
            systemEvent.put("hostname", getHostname());
            systemEvent.put("serviceVersion", getServiceVersion());

            systemEventLogs.put(eventId, systemEvent);

            log.info("시스템 이벤트 로그 기록 - ID: {}, 타입: {}, 심각도: {}",
                    eventId, eventType, severity);

        } catch (Exception e) {
            log.error("시스템 이벤트 로그 기록 실패 - 타입: {}, 오류: {}", eventType, e.getMessage());
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 200, category = "audit")
    public Page<ApiLogEntry> searchApiLogs(LocalDateTime startTime, LocalDateTime endTime,
                                          Long userId, String method, Integer status, Pageable pageable) {
        try {
            List<ApiLogEntry> filteredLogs = apiLogs.values().stream()
                    .filter(log -> isInTimeRange(log.getRequestTime(), startTime, endTime))
                    .filter(log -> userId == null || Objects.equals(log.getUserId(), userId))
                    .filter(log -> method == null || method.equals(log.getMethod()))
                    .filter(log -> status == null || Objects.equals(log.getResponseStatus(), status))
                    .sorted((a, b) -> b.getRequestTime().compareTo(a.getRequestTime()))
                    .collect(Collectors.toList());

            return createPage(filteredLogs, pageable);

        } catch (Exception e) {
            log.error("API 로그 검색 실패", e);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 200, category = "audit")
    public Page<BusinessEventLog> searchBusinessEventLogs(LocalDateTime startTime, LocalDateTime endTime,
                                                          String eventType, Long userId, Pageable pageable) {
        try {
            List<BusinessEventLog> filteredLogs = businessEventLogs.values().stream()
                    .filter(log -> isInTimeRange(log.getEventTime(), startTime, endTime))
                    .filter(log -> eventType == null || eventType.equals(log.getEventType()))
                    .filter(log -> userId == null || Objects.equals(log.getUserId(), userId))
                    .sorted((a, b) -> b.getEventTime().compareTo(a.getEventTime()))
                    .collect(Collectors.toList());

            return createPage(filteredLogs, pageable);

        } catch (Exception e) {
            log.error("비즈니스 이벤트 로그 검색 실패", e);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 200, category = "audit")
    public Page<Object> searchSecurityEventLogs(LocalDateTime startTime, LocalDateTime endTime,
                                               String eventType, Long userId, String clientIp, Pageable pageable) {
        try {
            List<Object> filteredLogs = securityEventLogs.values().stream()
                    .filter(log -> isInTimeRange((LocalDateTime) log.get("eventTime"), startTime, endTime))
                    .filter(log -> eventType == null || eventType.equals(log.get("eventType")))
                    .filter(log -> userId == null || Objects.equals(log.get("userId"), userId))
                    .filter(log -> clientIp == null || clientIp.equals(log.get("clientIp")))
                    .sorted((a, b) -> ((LocalDateTime) b.get("eventTime"))
                            .compareTo((LocalDateTime) a.get("eventTime")))
                    .collect(Collectors.toList());

            return createPage(filteredLogs, pageable);

        } catch (Exception e) {
            log.error("보안 이벤트 로그 검색 실패", e);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
    }

    @Override
    @PerformanceMonitoring(threshold = 500, category = "audit")
    public Map<String, Object> getLogStatistics(LocalDateTime startTime, LocalDateTime endTime, String logType) {
        Map<String, Object> stats = new HashMap<>();

        try {
            switch (logType.toUpperCase()) {
                case "API" -> {
                    long totalRequests = apiLogs.values().stream()
                            .filter(log -> isInTimeRange(log.getRequestTime(), startTime, endTime))
                            .count();
                    long successRequests = apiLogs.values().stream()
                            .filter(log -> isInTimeRange(log.getRequestTime(), startTime, endTime))
                            .filter(ApiLogEntry::isSuccess)
                            .count();
                    double avgResponseTime = apiLogs.values().stream()
                            .filter(log -> isInTimeRange(log.getRequestTime(), startTime, endTime))
                            .filter(log -> log.getProcessingTimeMs() != null)
                            .mapToLong(ApiLogEntry::getProcessingTimeMs)
                            .average()
                            .orElse(0.0);

                    stats.put("totalRequests", totalRequests);
                    stats.put("successRequests", successRequests);
                    stats.put("successRate", totalRequests > 0 ? (double) successRequests / totalRequests * 100 : 0);
                    stats.put("averageResponseTime", avgResponseTime);
                }
                case "BUSINESS" -> {
                    long totalEvents = businessEventLogs.values().stream()
                            .filter(log -> isInTimeRange(log.getEventTime(), startTime, endTime))
                            .count();
                    long successfulEvents = businessEventLogs.values().stream()
                            .filter(log -> isInTimeRange(log.getEventTime(), startTime, endTime))
                            .filter(BusinessEventLog::isSuccessful)
                            .count();

                    stats.put("totalEvents", totalEvents);
                    stats.put("successfulEvents", successfulEvents);
                    stats.put("successRate", totalEvents > 0 ? (double) successfulEvents / totalEvents * 100 : 0);
                }
                default -> {
                    stats.put("error", "지원하지 않는 로그 타입: " + logType);
                }
            }

            stats.put("period", startTime + " ~ " + endTime);
            stats.put("generatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("로그 통계 생성 실패 - 로그타입: {}", logType, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    @Override
    @PerformanceMonitoring(threshold = 1000, category = "audit")
    public Map<String, Object> generatePerformanceReport(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> report = new HashMap<>();

        try {
            // API 성능 분석
            List<ApiLogEntry> apiLogsInRange = apiLogs.values().stream()
                    .filter(log -> isInTimeRange(log.getRequestTime(), startTime, endTime))
                    .filter(log -> log.getProcessingTimeMs() != null)
                    .collect(Collectors.toList());

            if (!apiLogsInRange.isEmpty()) {
                DoubleSummaryStatistics responseTimeStats = apiLogsInRange.stream()
                        .mapToDouble(ApiLogEntry::getProcessingTimeMs)
                        .summaryStatistics();

                report.put("totalApiCalls", apiLogsInRange.size());
                report.put("averageResponseTime", responseTimeStats.getAverage());
                report.put("minResponseTime", responseTimeStats.getMin());
                report.put("maxResponseTime", responseTimeStats.getMax());

                // 느린 API 찾기
                List<String> slowApis = apiLogsInRange.stream()
                        .filter(log -> log.getProcessingTimeMs() > 1000)
                        .map(log -> log.getMethod() + " " + log.getUrl())
                        .distinct()
                        .collect(Collectors.toList());
                report.put("slowApis", slowApis);

                // 오류율 계산
                long errorCount = apiLogsInRange.stream()
                        .filter(log -> log.isServerError() || log.isClientError())
                        .count();
                report.put("errorRate", (double) errorCount / apiLogsInRange.size() * 100);
            }

            report.put("period", startTime + " ~ " + endTime);
            report.put("generatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("성능 리포트 생성 실패", e);
            report.put("error", e.getMessage());
        }

        return report;
    }

    @Override
    @PerformanceMonitoring(threshold = 300, category = "audit")
    public List<Map<String, Object>> detectSuspiciousActivities(int lookbackHours) {
        List<Map<String, Object>> suspiciousActivities = new ArrayList<>();

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(lookbackHours);

            // 1. 짧은 시간 내 대량 요청 (DDoS 의심)
            Map<String, Long> ipRequestCounts = apiLogs.values().stream()
                    .filter(log -> log.getRequestTime().isAfter(cutoffTime))
                    .collect(Collectors.groupingBy(
                            ApiLogEntry::getClientIp,
                            Collectors.counting()
                    ));

            ipRequestCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 100) // 1시간에 100회 이상
                    .forEach(entry -> {
                        Map<String, Object> activity = new HashMap<>();
                        activity.put("type", "EXCESSIVE_REQUESTS");
                        activity.put("clientIp", entry.getKey());
                        activity.put("requestCount", entry.getValue());
                        activity.put("severity", "HIGH");
                        suspiciousActivities.add(activity);
                    });

            // 2. 연속된 인증 실패
            // TODO: 보안 이벤트 로그에서 LOGIN_FAILED 이벤트 분석

            // 3. 비정상적인 API 접근 패턴
            // TODO: 일반적이지 않은 API 호출 패턴 분석

        } catch (Exception e) {
            log.error("의심스러운 활동 탐지 실패", e);
        }

        return suspiciousActivities;
    }

    @Override
    @PerformanceMonitoring(threshold = 2000, category = "audit")
    public long cleanupOldLogs(int retentionDays, String logType) {
        long cleanedCount = 0;

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);

            switch (logType.toUpperCase()) {
                case "API", "ALL" -> {
                    List<String> oldApiLogKeys = apiLogs.entrySet().stream()
                            .filter(entry -> entry.getValue().getRequestTime().isBefore(cutoffTime))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());

                    oldApiLogKeys.forEach(apiLogs::remove);
                    cleanedCount += oldApiLogKeys.size();
                }
                case "BUSINESS" -> {
                    List<String> oldBusinessLogKeys = businessEventLogs.entrySet().stream()
                            .filter(entry -> entry.getValue().getEventTime().isBefore(cutoffTime))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());

                    oldBusinessLogKeys.forEach(businessEventLogs::remove);
                    cleanedCount += oldBusinessLogKeys.size();
                }
            }

            log.info("오래된 로그 정리 완료 - 타입: {}, 보관기간: {}일, 정리된 로그: {}개",
                    logType, retentionDays, cleanedCount);

        } catch (Exception e) {
            log.error("로그 정리 실패 - 타입: {}", logType, e);
        }

        return cleanedCount;
    }

    @Override
    public long archiveLogs(int archiveBeforeDays, String logType) {
        // TODO: 실제 구현에서는 로그를 압축하여 아카이브 스토리지로 이동
        log.info("로그 아카이브 요청 - 타입: {}, 기준: {}일 전", logType, archiveBeforeDays);
        return 0;
    }

    @Override
    @PerformanceMonitoring(threshold = 200, category = "audit")
    public List<Map<String, Object>> getEventsRequiringNotification(int minutes) {
        List<Map<String, Object>> notificationEvents = new ArrayList<>();

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(minutes);

            // 중요한 비즈니스 이벤트 확인
            businessEventLogs.values().stream()
                    .filter(log -> log.getEventTime().isAfter(cutoffTime))
                    .filter(BusinessEventLog::isCritical)
                    .forEach(log -> {
                        Map<String, Object> event = new HashMap<>();
                        event.put("type", "BUSINESS_EVENT");
                        event.put("eventType", log.getEventType());
                        event.put("orderId", log.getOrderId());
                        event.put("description", log.getDescription());
                        event.put("severity", log.getSeverity());
                        notificationEvents.add(event);
                    });

            // 심각한 보안 이벤트 확인
            securityEventLogs.values().stream()
                    .filter(log -> ((LocalDateTime) log.get("eventTime")).isAfter(cutoffTime))
                    .filter(log -> "CRITICAL".equals(log.get("severity")))
                    .forEach(log -> {
                        Map<String, Object> event = new HashMap<>();
                        event.put("type", "SECURITY_EVENT");
                        event.put("eventType", log.get("eventType"));
                        event.put("clientIp", log.get("clientIp"));
                        event.put("description", log.get("description"));
                        notificationEvents.add(event);
                    });

        } catch (Exception e) {
            log.error("알림 필요 이벤트 확인 실패", e);
        }

        return notificationEvents;
    }

    // ========================= 유틸리티 메서드 =========================

    private boolean isInTimeRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null) return false;
        return (start == null || !time.isBefore(start)) && (end == null || !time.isAfter(end));
    }

    private <T> Page<T> createPage(List<T> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());

        if (start >= items.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, items.size());
        }

        List<T> pageContent = items.subList(start, end);
        return new PageImpl<>(pageContent, pageable, items.size());
    }

    private String getSeverityForSecurityEvent(String eventType) {
        return switch (eventType) {
            case "LOGIN_FAILED", "INVALID_TOKEN" -> "WARN";
            case "BRUTE_FORCE_ATTACK", "SQL_INJECTION", "XSS_ATTEMPT" -> "CRITICAL";
            case "UNAUTHORIZED_ACCESS", "PRIVILEGE_ESCALATION" -> "ERROR";
            default -> "INFO";
        };
    }

    private boolean isCriticalSecurityEvent(String eventType) {
        return Arrays.asList("BRUTE_FORCE_ATTACK", "SQL_INJECTION", "XSS_ATTEMPT", "PRIVILEGE_ESCALATION")
                .contains(eventType);
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getServiceVersion() {
        return "1.0.0"; // 실제로는 application.properties에서 읽어옴
    }

}