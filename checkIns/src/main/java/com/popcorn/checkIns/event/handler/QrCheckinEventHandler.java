package com.popcorn.checkIns.event.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.checkIns.checkin.repository.CheckinRepository;
import com.popcorn.checkIns.event.domain.QrCheckinRequestedEvent;
import com.popcorn.checkIns.event.domain.QrCheckinCompletedEvent;

import lombok.RequiredArgsConstructor;

/**
 * QR 체크인 이벤트 핸들러
 * QR 체크인 관련 이벤트를 비동기로 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class QrCheckinEventHandler {

	private static final Logger log = LoggerFactory.getLogger(QrCheckinEventHandler.class);

	private final CheckinRepository checkinRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * QR 체크인 요청 처리 (비동기 후처리)
	 */
	@Async("qrCheckinExecutor")
	@EventListener
	public void handleQrCheckinRequested(QrCheckinRequestedEvent event) {
		log.info("📱 QR 체크인 후처리 시작 - 주문ID: {}, QR코드: {}",
				event.getOrderId(), event.getQrCode());

		try {
			// QR 체크인 후 추가 비즈니스 로직 처리
			// - 외부 시스템 연동 (POS, 키오스크 등)
			// - 실시간 알림 발송
			// - 통계 데이터 업데이트
			// - 로그 기록

			// 체크인 완료 이벤트 발행 (실제 체크인 ID는 DB에서 조회)
			java.util.Optional<com.popcorn.checkIns.checkin.repository.CheckinRow> checkinRow =
					checkinRepository.findLatestByOrderQrCodeId(event.getQrId());

			if (checkinRow.isPresent()) {
				eventPublisher.publishEvent(new QrCheckinCompletedEvent(
						this,
						checkinRow.get().checkinId(),
						event.getQrId(),
						event.getOrderId(),
						event.getQrCode(),
						event.getCheckinTime()
				));
			}

			log.info("✅ QR 체크인 후처리 완료 - 주문ID: {}", event.getOrderId());

		} catch (Exception e) {
			log.error("QR 체크인 후처리 실패 - 주문ID: {}", event.getOrderId(), e);
		}
	}

	/**
	 * QR 체크인 완료 처리
	 */
	@Async("qrCheckinExecutor")
	@EventListener
	public void handleQrCheckinCompleted(QrCheckinCompletedEvent event) {
		log.info("🎉 QR 체크인 완료 처리 - 체크인ID: {}, 주문ID: {}",
				event.getCheckinId(), event.getOrderId());

		try {
			// 체크인 완료 후 추가 처리
			// - 고객 알림 발송 (푸시 알림, SMS 등)
			// - 실시간 대시보드 업데이트
			// - 체크인 통계 집계
			// - 외부 시스템 연동 (CRM, 마케팅 등)
			// - 이벤트 로그 기록

			log.info("✅ QR 체크인 완료 처리 완료 - 체크인ID: {}", event.getCheckinId());

		} catch (Exception e) {
			log.error("QR 체크인 완료 처리 실패 - 체크인ID: {}", event.getCheckinId(), e);
		}
	}
}