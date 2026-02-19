/*package com.popcorn.checkIns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.popcorn.checkIns.checkin.repository.CheckinRepository;
import com.popcorn.checkIns.checkin.repository.CheckinRow;
import com.popcorn.checkIns.dto.response.QrCodeResponse;
import com.popcorn.checkIns.dto.response.QrVerifyResponse;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.repository.QrCodeRepository;
import com.popcorn.checkIns.repository.QrCodeRow;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("QR 서비스 테스트")
class QrCodeServiceTest {

	private QrCodeRepository qrCodeRepository;
	private CheckinRepository checkinRepository;
	private ApplicationEventPublisher eventPublisher;
	private QrCodeService qrCodeService;

	@BeforeEach
	void setUp() {
		qrCodeRepository = Mockito.mock(QrCodeRepository.class);
		checkinRepository = Mockito.mock(CheckinRepository.class);
		eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
		qrCodeService = new QrCodeService(qrCodeRepository, checkinRepository, eventPublisher);
	}

	@Test
	@DisplayName("PAID 예약 주문은 QR 신규 발급")
	void issue_createsQr_whenPaidReservation() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001001");
		LocalDateTime before = LocalDateTime.now();

		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
		when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

		QrCodeResponse response = qrCodeService.issue(orderId);

		assertThat(response.getOrderId()).isEqualTo(orderId);
		assertThat(response.getQrCode()).isNotBlank();
		assertThat(response.getExpiresAt()).isAfter(before);
		verify(qrCodeRepository).insert(any(QrCodeRow.class));
	}

	@Test
	@DisplayName("만료되지 않은 QR이 있으면 재발급하지 않음")
	void issue_returnsExisting_whenNotExpired() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001002");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				"qr-exist-001",
				now.plusMinutes(5),
				now.minusMinutes(1)
		);

		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
		when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(row));

		QrCodeResponse response = qrCodeService.issue(orderId);

		assertThat(response.getQrCode()).isEqualTo("qr-exist-001");
		verify(qrCodeRepository, never()).insert(any(QrCodeRow.class));
	}

	@Test
	@DisplayName("PAID가 아니면 QR 발급 실패")
	void issue_throws_whenNotPaid() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001003");

		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("REQUESTED"));

		assertThatThrownBy(() -> qrCodeService.issue(orderId))
				.isInstanceOf(QrException.class);
	}

	@Test
	@DisplayName("QR 검증 시 체크인 생성")
	void verify_createsCheckin() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001004");
		UUID qrId = UUID.fromString("90000000-0000-0000-0000-000000000001");
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000002");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = new QrCodeRow(
				qrId,
				orderId,
				"qr-verify-001",
				now.plusMinutes(5),
				now.minusMinutes(1)
		);

		when(qrCodeRepository.findLatestByQrCode("qr-verify-001")).thenReturn(Optional.of(row));
		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
		when(checkinRepository.findLatestByOrderQrCodeId(qrId)).thenReturn(Optional.empty());
		when(checkinRepository.insert(eq(orderId), eq(qrId), isNull(), any(LocalDateTime.class)))
				.thenReturn(checkinId);

		QrVerifyResponse response = qrCodeService.verify("qr-verify-001");

		assertThat(response.isValid()).isTrue();
		assertThat(response.getCheckinId()).isEqualTo(checkinId);
	}

	@Test
	@DisplayName("QR 검증 시 기존 체크인 재사용")
	void verify_returnsExistingCheckin() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001005");
		UUID qrId = UUID.fromString("90000000-0000-0000-0000-000000000003");
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000004");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = new QrCodeRow(
				qrId,
				orderId,
				"qr-verify-002",
				now.plusMinutes(5),
				now.minusMinutes(1)
		);
		CheckinRow existing = new CheckinRow(
				checkinId,
				orderId,
				qrId,
				"qr-verify-002",
				now.minusMinutes(1),
				null
		);

		when(qrCodeRepository.findLatestByQrCode("qr-verify-002")).thenReturn(Optional.of(row));
		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
		when(checkinRepository.findLatestByOrderQrCodeId(qrId)).thenReturn(Optional.of(existing));

		QrVerifyResponse response = qrCodeService.verify("qr-verify-002");

		assertThat(response.getCheckinId()).isEqualTo(checkinId);
		verify(checkinRepository, never()).insert(any(), any(), any(), any());
	}

	@Test
	@DisplayName("주문을 찾을 수 없으면 QR 발급 실패")
	void issue_throws_whenOrderNotFound() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000999999");

		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> qrCodeService.issue(orderId))
				.isInstanceOf(QrException.class);
	}

	@Test
	@DisplayName("만료된 QR 코드는 재발급됨")
	void issue_createsNewQr_whenExpired() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001006");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow expiredRow = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				"qr-expired-001",
				now.minusMinutes(1), // 만료됨
				now.minusMinutes(10)
		);

		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
		when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(expiredRow));

		QrCodeResponse response = qrCodeService.issue(orderId);

		assertThat(response.getQrCode()).isNotEqualTo("qr-expired-001");
		verify(qrCodeRepository).insert(any(QrCodeRow.class));
	}

	@Test
	@DisplayName("QR 조회 - 주문 ID로 성공")
	void get_success_withOrderId() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001007");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				"qr-get-001",
				now.plusMinutes(5),
				now.minusMinutes(1)
		);

		when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(row));
		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));

		QrCodeResponse response = qrCodeService.get(orderId);

		assertThat(response.getOrderId()).isEqualTo(orderId);
		assertThat(response.getQrCode()).isEqualTo("qr-get-001");
	}

	@Test
	@DisplayName("QR 조회 실패 - QR 코드를 찾을 수 없음")
	void get_throws_whenQrNotFound() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000999998");

		when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> qrCodeService.get(orderId))
				.isInstanceOf(QrException.class);
	}

	@Test
	@DisplayName("QR 검증 실패 - QR 코드를 찾을 수 없음")
	void verify_throws_whenQrCodeNotFound() {
		when(qrCodeRepository.findLatestByQrCode("invalid-qr-code")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> qrCodeService.verify("invalid-qr-code"))
				.isInstanceOf(QrException.class);
	}

	@Test
	@DisplayName("QR 검증 실패 - QR 코드 만료됨")
	void verify_throws_whenQrExpired() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001008");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow expiredRow = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				"qr-expired-verify",
				now.minusMinutes(1), // 만료됨
				now.minusMinutes(10)
		);

		when(qrCodeRepository.findLatestByQrCode("qr-expired-verify")).thenReturn(Optional.of(expiredRow));

		assertThatThrownBy(() -> qrCodeService.verify("qr-expired-verify"))
				.isInstanceOf(QrException.class);
	}

	@Test
	@DisplayName("QR 검증 실패 - 주문 상태가 PAID가 아님")
	void verify_throws_whenOrderNotPaid() {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001009");
		LocalDateTime now = LocalDateTime.now();
		QrCodeRow row = new QrCodeRow(
				UUID.randomUUID(),
				orderId,
				"qr-not-paid-verify",
				now.plusMinutes(5),
				now.minusMinutes(1)
		);

		when(qrCodeRepository.findLatestByQrCode("qr-not-paid-verify")).thenReturn(Optional.of(row));
		when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("CANCELLED"));

		assertThatThrownBy(() -> qrCodeService.verify("qr-not-paid-verify"))
				.isInstanceOf(QrException.class);
	}
}
*/