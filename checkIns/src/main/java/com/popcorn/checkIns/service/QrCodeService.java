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
import com.popcorn.checkIns.event.standard.StandardCheckinsEventPublisher;
import com.popcorn.checkIns.event.standard.StandardQrGeneratedEvent;
import com.popcorn.checkIns.event.standard.StandardCheckinCreatedEvent;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.repository.QrCodeRepository;
import com.popcorn.checkIns.repository.QrCodeRow;
import com.popcorn.checkIns.checkin.repository.CheckinRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QrCodeService {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

	private final QrCodeRepository qrCodeRepository;
	private final CheckinRepository checkinRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final StandardCheckinsEventPublisher standardCheckinsEventPublisher;

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
		publishStandardQrGeneratedEvent(created);

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
		QrCodeRow row = qrCodeRepository.findLatestByQrCode(qrCode)
				.orElseThrow(QrException::qrNotFound);

		if (row.isExpired(now)) {
			throw QrException.qrExpired();
		}

		String orderStatus = qrCodeRepository.findOrderStatus(row.orderId())
				.orElseThrow(QrException::orderNotFound);

		ensurePaid(orderStatus);
		ensureReservationOrder(row.orderId());

		java.util.Optional<com.popcorn.checkIns.checkin.repository.CheckinRow> existing =
				checkinRepository.findLatestByOrderQrCodeId(row.qrId());
		if (existing.isPresent()) {
			return QrVerifyResponse.builder()
					.valid(true)
					.checkinId(existing.get().checkinId())
					.orderId(row.orderId())
					.qrCode(row.qrCode())
					.expiresAt(row.expiresAt())
					.build();
		}

		// 동기적으로 체크인 처리 (응답 속도를 위해)
		java.util.UUID checkinId = checkinRepository.insert(
				row.orderId(),
				row.qrId(),
				null,
				now
		);

		// 기존 체크인 요청 이벤트 발행 (비동기 후처리)
		eventPublisher.publishEvent(new QrCheckinRequestedEvent(
				this,
				row.qrId(),
				row.orderId(),
				row.qrCode(),
				row.expiresAt(),
				now
		));

		// 표준 체크인 생성 이벤트 발행
		publishStandardCheckinCreatedEvent(checkinId, row, null, null);

		return QrVerifyResponse.builder()
				.valid(true)
				.checkinId(checkinId)
				.orderId(row.orderId())
				.qrCode(row.qrCode())
				.expiresAt(row.expiresAt())
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
	 * 표준 QR 생성 이벤트 발행
	 */
	private void publishStandardQrGeneratedEvent(QrCodeRow qrCodeRow) {
		try {
			StandardQrGeneratedEvent event = StandardQrGeneratedEvent.create(
					qrCodeRow.orderId(),
					null, // orderNo는 현재 조회 불가
					null, // userId는 현재 조회 불가
					null, // storeId는 현재 조회 불가
					null, // popupId는 현재 조회 불가
					qrCodeRow.qrId(),
					qrCodeRow.qrCode(),
					qrCodeRow.expiresAt(),
					DEFAULT_TTL.getSeconds(),
					null  // qrCreatedBy는 현재 조회 불가
			);

			standardCheckinsEventPublisher.publishQrGeneratedEvent(event);
		} catch (Exception e) {
			// 로깅만 수행하고 메인 흐름에 영향을 주지 않음
			System.out.println("표준 QR 생성 이벤트 발행 실패: " + e.getMessage());
		}
	}

	/**
	 * 표준 체크인 생성 이벤트 발행
	 */
	private void publishStandardCheckinCreatedEvent(UUID checkinId, QrCodeRow qrCodeRow,
													String storeId, String checkinLocation) {
		try {
			StandardCheckinCreatedEvent event = StandardCheckinCreatedEvent.create(
					qrCodeRow.orderId(),
					null, // orderNo는 현재 조회 불가
					null, // userId는 현재 조회 불가
					storeId != null ? UUID.fromString(storeId) : null,
					null, // popupId는 현재 조회 불가
					checkinId,
					qrCodeRow.qrId(),
					qrCodeRow.qrCode(),
					null, // checkinCreatedBy는 현재 조회 불가
					checkinLocation
			);

			standardCheckinsEventPublisher.publishCheckinCreatedEvent(event);
		} catch (Exception e) {
			// 로깅만 수행하고 메인 흐름에 영향을 주지 않음
			System.out.println("표준 체크인 생성 이벤트 발행 실패: " + e.getMessage());
		}
	}
}
