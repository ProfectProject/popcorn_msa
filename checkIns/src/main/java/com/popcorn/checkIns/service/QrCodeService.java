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
import com.popcorn.checkIns.event.domain.QrCheckinRequestedEvent;
import com.popcorn.checkIns.event.domain.BaseCheckinEvent;
import com.popcorn.checkIns.event.publisher.CheckinRedisEventPublisher;
import com.popcorn.checkIns.event.kafka.CheckinsKafkaEventPublisher;
import com.popcorn.checkIns.event.domain.CheckinEvents.QrGeneratedEvent;
import com.popcorn.checkIns.event.domain.CheckinEvents.CheckinCreatedEvent;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.metrics.CheckInsMetricsService;
import com.popcorn.checkIns.repository.QrCodeRepository;
import com.popcorn.checkIns.repository.QrCodeRow;
import com.popcorn.checkIns.checkin.repository.CheckinRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class QrCodeService {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

	private final QrCodeRepository qrCodeRepository;
	private final CheckinRepository checkinRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final CheckinRedisEventPublisher checkinRedisEventPublisher;
	private final CheckInsMetricsService metricsService;
	private final com.popcorn.checkIns.outbox.OutboxWriter outboxWriter;

	// 점진적 전환을 위해 Optional로 주입 (kafka.enabled=true일 때만 활성화)
	private final Optional<CheckinsKafkaEventPublisher> checkinKafkaEventPublisher;

	// 생성자에서 Optional 처리
	public QrCodeService(QrCodeRepository qrCodeRepository,
						 CheckinRepository checkinRepository,
						 ApplicationEventPublisher eventPublisher,
						 CheckinRedisEventPublisher checkinRedisEventPublisher,
						 CheckInsMetricsService metricsService,
						 com.popcorn.checkIns.outbox.OutboxWriter outboxWriter,
						 Optional<CheckinsKafkaEventPublisher> checkinKafkaEventPublisher) {
		this.qrCodeRepository = qrCodeRepository;
		this.checkinRepository = checkinRepository;
		this.eventPublisher = eventPublisher;
		this.checkinRedisEventPublisher = checkinRedisEventPublisher;
		this.metricsService = metricsService;
		this.outboxWriter = outboxWriter;
		this.checkinKafkaEventPublisher = checkinKafkaEventPublisher;
	}

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

	/**
	 * 결제 이벤트로부터 QR 발급 (Payment → CheckIns 직결 경로)
	 * - 주문 존재 여부만 확인하고, 결제 상태 검증은 생략
	 */
	@Transactional
	public QrCodeResponse issueFromPaymentEvent(UUID orderId) {
		// 주문 존재 여부 확인 (상태는 검증하지 않음)
		qrCodeRepository.findOrderStatus(orderId)
				.orElseThrow(QrException::orderNotFound);

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

	/**
	 * QR 코드 무효화 처리
	 * Payment 서비스로부터 결제 취소/실패 시 QR 코드를 무효화
	 */
	@Transactional(transactionManager = "jdbcTransactionManager")
	public void invalidateByOrderId(UUID orderId, String reason) {
		try {
			log.info("❌ [QR-INVALIDATE] QR 코드 무효화 시작: orderId={} reason={}", orderId, reason);

			// 해당 주문의 모든 QR 코드를 만료 처리
			var qrCodes = qrCodeRepository.findAllByOrderId(orderId);

			if (qrCodes.isEmpty()) {
				log.warn("⚠️ [QR-INVALIDATE] 무효화할 QR 코드가 없음: orderId={}", orderId);
				return;
			}

			LocalDateTime now = LocalDateTime.now();
			int invalidatedCount = 0;

			for (var qrCode : qrCodes) {
				if (!qrCode.isExpired(now)) {
					// QR 코드를 즉시 만료 처리 (expiresAt을 현재 시간으로 설정)
					qrCodeRepository.updateExpiresAt(qrCode.qrId(), now);
					invalidatedCount++;

					log.debug("🔒 [QR-INVALIDATE] QR 코드 만료 처리: qrId={} token={}",
							qrCode.qrId(), qrCode.qrCode());
				}
			}

			log.info("✅ [QR-INVALIDATE] QR 코드 무효화 완료: orderId={} invalidatedCount={} reason={}",
					orderId, invalidatedCount, reason);

		} catch (Exception e) {
			log.error("❌ [QR-INVALIDATE] QR 코드 무효화 실패: orderId={} reason={} error={}",
					orderId, reason, e.getMessage(), e);
			throw e;
		}
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
	 * BaseEvent 기반 QR 생성 이벤트 발행 (점진적 이중 처리)
	 * Redis (기존) + Kafka (신규) 동시 발행
	 */
	private void publishQrGeneratedEvent(QrCodeRow qrCodeRow) {
		QrGeneratedEvent event = QrGeneratedEvent.create(
				qrCodeRow.orderId(),
				null, // orderNo는 현재 조회 불가
				qrCodeRow.qrCode(), // qrToken으로 사용
				null, // qrUrl는 현재 생성하지 않음
				qrCodeRow.expiresAt(),
				DEFAULT_TTL.getSeconds(),
				null  // userId는 현재 조회 불가
		);

		// 메트릭 기록
		metricsService.recordRedisEventPublished(event.getEventType(), "checkin-events");

		// 공통 이중 발행 로직 사용
		publishEventToBothSystems(
			event,
			() -> checkinRedisEventPublisher.publishQrGenerated(event),
			"QR 생성",
			qrCodeRow.orderId().toString(),
			null
		);
	}

	/**
	 * BaseEvent 기반 체크인 생성 이벤트 발행 (점진적 이중 처리)
	 * Redis (기존) + Kafka (신규) 동시 발행
	 */
	private void publishCheckinCreatedEvent(UUID checkinId, QrCodeRow qrCodeRow,
											String storeId, String checkinLocation) {
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

		// 메트릭 기록
		metricsService.recordRedisEventPublished(event.getEventType(), "checkin-events");

		// 공통 이중 발행 로직 사용
		publishEventToBothSystems(
			event,
			() -> checkinRedisEventPublisher.publishCheckinCreated(event),
			"체크인 생성",
			qrCodeRow.orderId().toString(),
			checkinId.toString()
		);
	}

	/**
	 * Redis + Kafka 이중 발행 공통 로직 (중복 제거)
	 */
	private void publishEventToBothSystems(
		BaseCheckinEvent event,
		Runnable redisPublisher,
		String eventDescription,
		String orderId,
		String additionalId
	) {
		long startTime = System.currentTimeMillis();

		// 0. Outbox 기록 (트랜잭션 내부)
		outboxWriter.record(event);

		// 1. Redis 발행 (기존 방식 유지)
		try {
			redisPublisher.run();
			long processingTime = System.currentTimeMillis() - startTime;
			metricsService.recordRedisEventProcessed(event.getEventType(), processingTime);

			String logMessage = additionalId != null
				? "✅ [DUAL-PUBLISH] Redis {} 이벤트 발행 완료: {} orderId={}"
				: "✅ [DUAL-PUBLISH] Redis {} 이벤트 발행 완료: orderId={}";

			if (additionalId != null) {
				log.debug(logMessage, eventDescription, additionalId, orderId);
			} else {
				log.debug(logMessage, eventDescription, orderId);
			}
		} catch (Exception e) {
			String logMessage = additionalId != null
				? "❌ [DUAL-PUBLISH] Redis {} 이벤트 발행 실패: {} orderId={} error={}"
				: "❌ [DUAL-PUBLISH] Redis {} 이벤트 발행 실패: orderId={} error={}";

			if (additionalId != null) {
				log.error(logMessage, eventDescription, additionalId, orderId, e.getMessage(), e);
			} else {
				log.error(logMessage, eventDescription, orderId, e.getMessage(), e);
			}
		}

		// 2. Kafka 발행 (점진적 추가) - 비동기로 실패 시에도 메인 흐름에 영향 없음
		checkinKafkaEventPublisher.ifPresentOrElse(
			kafkaPublisher -> {
				try {
					kafkaPublisher.publishAsync(event);
					String logMessage = additionalId != null
						? "✅ [DUAL-PUBLISH] Kafka {} 이벤트 발행 완료: {} orderId={}"
						: "✅ [DUAL-PUBLISH] Kafka {} 이벤트 발행 완료: orderId={}";

					if (additionalId != null) {
						log.debug(logMessage, eventDescription, additionalId, orderId);
					} else {
						log.debug(logMessage, eventDescription, orderId);
					}
				} catch (Exception e) {
					String logMessage = additionalId != null
						? "⚠️ [DUAL-PUBLISH] Kafka {} 이벤트 발행 실패 (Redis는 성공): {} orderId={} error={}"
						: "⚠️ [DUAL-PUBLISH] Kafka {} 이벤트 발행 실패 (Redis는 성공): orderId={} error={}";

					if (additionalId != null) {
						log.warn(logMessage, eventDescription, additionalId, orderId, e.getMessage());
					} else {
						log.warn(logMessage, eventDescription, orderId, e.getMessage());
					}
					// Kafka 실패는 경고 로깅만 하고 메인 흐름 계속 진행
				}
			},
			() -> {
				String logMessage = additionalId != null
					? "🔄 [DUAL-PUBLISH] Kafka 비활성화 상태 - Redis만 발행: {} orderId={}"
					: "🔄 [DUAL-PUBLISH] Kafka 비활성화 상태 - Redis만 발행: orderId={}";

				if (additionalId != null) {
					log.debug(logMessage, additionalId, orderId);
				} else {
					log.debug(logMessage, orderId);
				}
			}
		);
	}
}
