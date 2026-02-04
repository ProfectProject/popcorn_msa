package com.popcorn.checkIns.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.checkIns.dto.response.QrCodeResponse;
import com.popcorn.checkIns.dto.response.QrVerifyResponse;
import com.popcorn.checkIns.event.QrCheckinRequestedEvent;
import com.popcorn.checkIns.event.CheckinRedisEventPublisher;
import com.popcorn.checkIns.event.CheckinEvents.QrGeneratedEvent;
import com.popcorn.checkIns.event.CheckinEvents.CheckinCreatedEvent;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.repository.QrCodeRepository;
import com.popcorn.checkIns.repository.QrCodeRow;
import com.popcorn.checkIns.checkin.repository.CheckinRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

	private final QrCodeRepository qrCodeRepository;
	private final CheckinRepository checkinRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final CheckinRedisEventPublisher checkinRedisEventPublisher;

	@Transactional
	public QrCodeResponse issue(UUID orderId) {
		String orderStatus = qrCodeRepository.findOrderStatus(orderId)
				.orElseThrow(QrException::orderNotFound);

		ensurePaid(orderStatus);
		ensureReservationOrder(orderId);

		LocalDateTime now = LocalDateTime.now();
		Optional<QrCodeRow> existing = qrCodeRepository.findLatestByOrderId(orderId)
				.filter(row -> !row.isExpired(now));

		if (existing.isPresent()) {
			return toResponse(existing.get());
		}

		QrCodeRow created = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				UUID.randomUUID().toString(),
				now.plus(DEFAULT_TTL),
				now
		);

		qrCodeRepository.insert(created);

		// 표준 QR 생성 이벤트 발행
		publishQrGeneratedEvent(created);

		return toResponse(created);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public QrCodeResponse get(UUID orderId) {
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = qrCodeRepository.findLatestByOrderId(orderId)
				.orElseThrow(QrException::qrNotFound);

		if (row.isExpired(now)) {
			throw QrException.qrExpired();
		}

		String orderStatus = qrCodeRepository.findOrderStatus(orderId)
				.orElseThrow(QrException::orderNotFound);

		ensurePaid(orderStatus);
		ensureReservationOrder(orderId);

		return toResponse(row);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public QrVerifyResponse verify(String qrCode) {
		LocalDateTime now = LocalDateTime.now();

		// 1. QR 코드 조회 및 기본 검증
		QrCodeRow qrCodeRow = validateAndGetQrCode(qrCode, now);

		// 2. 주문 상태 검증
		validateOrderStatus(qrCodeRow.orderId());

		// 3. 기존 체크인 확인
		java.util.Optional<com.popcorn.checkIns.checkin.repository.CheckinRow> existingCheckin =
				findExistingCheckin(qrCodeRow.qrId());

		if (existingCheckin.isPresent()) {
			return buildResponseForExistingCheckin(existingCheckin.get(), qrCodeRow);
		}

		// 4. 새 체크인 생성
		UUID checkinId = createNewCheckin(qrCodeRow, now);

		// 5. 이벤트 발행
		publishCheckinEvents(checkinId, qrCodeRow, now);

		return buildResponseForNewCheckin(checkinId, qrCodeRow);
	}

	/**
	 * QR 코드 조회 및 기본 검증
	 */
	private QrCodeRow validateAndGetQrCode(String qrCode, LocalDateTime now) {
		QrCodeRow row = qrCodeRepository.findLatestByQrCode(qrCode)
				.orElseThrow(QrException::qrNotFound);

		if (row.isExpired(now)) {
			throw QrException.qrExpired();
		}

		return row;
	}

	/**
	 * 주문 상태 검증
	 */
	private void validateOrderStatus(UUID orderId) {
		String orderStatus = qrCodeRepository.findOrderStatus(orderId)
				.orElseThrow(QrException::orderNotFound);

		ensurePaid(orderStatus);
		ensureReservationOrder(orderId);
	}

	/**
	 * 기존 체크인 조회
	 */
	private java.util.Optional<com.popcorn.checkIns.checkin.repository.CheckinRow> findExistingCheckin(UUID qrId) {
		return checkinRepository.findLatestByOrderQrCodeId(qrId);
	}

	/**
	 * 기존 체크인에 대한 응답 생성
	 */
	private QrVerifyResponse buildResponseForExistingCheckin(
			com.popcorn.checkIns.checkin.repository.CheckinRow existingCheckin,
			QrCodeRow qrCodeRow) {
		return QrVerifyResponse.builder()
				.valid(true)
				.checkinId(existingCheckin.checkinId())
				.orderId(qrCodeRow.orderId())
				.qrCode(qrCodeRow.qrCode())
				.expiresAt(qrCodeRow.expiresAt())
				.build();
	}

	/**
	 * 새로운 체크인 생성
	 */
	private UUID createNewCheckin(QrCodeRow qrCodeRow, LocalDateTime now) {
		return checkinRepository.insert(
				qrCodeRow.orderId(),
				qrCodeRow.qrId(),
				null,
				now
		);
	}

	/**
	 * 체크인 관련 이벤트 발행
	 */
	private void publishCheckinEvents(UUID checkinId, QrCodeRow qrCodeRow, LocalDateTime now) {
		// 기존 체크인 요청 이벤트 발행 (비동기 후처리)
		eventPublisher.publishEvent(new QrCheckinRequestedEvent(
				this,
				qrCodeRow.qrId(),
				qrCodeRow.orderId(),
				qrCodeRow.qrCode(),
				qrCodeRow.expiresAt(),
				now
		));

		// 표준 체크인 생성 이벤트 발행
		publishCheckinCreatedEvent(checkinId, qrCodeRow, null, null);
	}

	/**
	 * 새로운 체크인에 대한 응답 생성
	 */
	private QrVerifyResponse buildResponseForNewCheckin(UUID checkinId, QrCodeRow qrCodeRow) {
		return QrVerifyResponse.builder()
				.valid(true)
				.checkinId(checkinId)
				.orderId(qrCodeRow.orderId())
				.qrCode(qrCodeRow.qrCode())
				.expiresAt(qrCodeRow.expiresAt())
				.build();
	}

	private static QrCodeResponse toResponse(QrCodeRow row) {
		return QrCodeResponse.builder()
				.orderId(row.orderId())
				.qrCode(row.qrCode())
				.expiresAt(row.expiresAt())
				.build();
	}

	private void ensurePaid(String orderStatus) {
		if (!"PAID".equals(orderStatus)) {
			throw QrException.orderNotReserved();
		}
	}

	private void ensureReservationOrder(UUID orderId) {
		// QR 코드는 결제 완료된 예약 주문에만 발급됨
		// 추가적인 예약 주문 검증이 필요한 경우, 별도의 서비스 호출로 처리
		// 현재는 결제 상태만 확인하여 단순화
	}

	/**
	 * BaseEvent 기반 QR 생성 이벤트 발행
	 */
	private void publishQrGeneratedEvent(QrCodeRow qrCodeRow) {
		try {
			QrGeneratedEvent event = QrGeneratedEvent.create(
					qrCodeRow.orderId(),
					null, // orderNo는 현재 조회 불가
					qrCodeRow.qrCode(), // qrToken으로 사용
					null, // qrUrl는 현재 생성하지 않음
					qrCodeRow.expiresAt(),
					DEFAULT_TTL.getSeconds(),
					null  // userId는 현재 조회 불가
			);

			checkinRedisEventPublisher.publishQrGenerated(event);
		} catch (Exception e) {
			// 로깅만 수행하고 메인 흐름에 영향을 주지 않음
			log.error("BaseEvent QR 생성 이벤트 발행 실패: {}", e.getMessage(), e);
		}
	}

	/**
	 * BaseEvent 기반 체크인 생성 이벤트 발행
	 */
	private void publishCheckinCreatedEvent(UUID checkinId, QrCodeRow qrCodeRow,
											String storeId, String checkinLocation) {
		try {
			CheckinCreatedEvent event = CheckinCreatedEvent.create(
					qrCodeRow.orderId(),
					null, // orderGoodsId는 현재 조회 불가
					null, // popupId는 현재 조회 불가
					storeId != null ? UUID.fromString(storeId) : null,
					qrCodeRow.qrId(),
					qrCodeRow.qrCode(), // qrToken으로 사용
					checkinLocation,
					null  // userId는 현재 조회 불가
			);

			checkinRedisEventPublisher.publishCheckinCreated(event);
		} catch (Exception e) {
			// 로깅만 수행하고 메인 흐름에 영향을 주지 않음
			log.error("BaseEvent 체크인 생성 이벤트 발행 실패: {}", e.getMessage(), e);
		}
	}
}
