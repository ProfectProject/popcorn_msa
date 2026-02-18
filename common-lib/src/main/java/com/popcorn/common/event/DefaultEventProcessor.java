package com.popcorn.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 🎯 기본 이벤트 처리기
 * - Spring 애플리케이션 이벤트로 발행
 * - 도메인별 이벤트 리스너가 실제 처리 담당
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultEventProcessor implements EventOrderingService.EventProcessor {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public EventProcessingResult process(OrderedEvent event) {
        if (event == null) {
            log.warn("⚠️ [이벤트 처리] NULL 이벤트 무시");
            return EventProcessingResult.failed("NULL 이벤트");
        }

        try {
            log.debug("🚀 [이벤트 처리] 이벤트 발행 시작 - {}", event.getSummary());

            // Spring 애플리케이션 이벤트로 발행
            // 각 서비스의 @EventListener가 이를 처리함
            eventPublisher.publishEvent(event);

            log.debug("✅ [이벤트 처리] 이벤트 발행 완료 - {}", event.getSummary());
            return EventProcessingResult.success(event.getEventId());

        } catch (Exception e) {
            log.error("❌ [이벤트 처리] 이벤트 발행 실패 - {}", event.getSummary(), e);
            return EventProcessingResult.failed(event.getEventId(), "이벤트 발행 실패: " + e.getMessage());
        }
    }
}