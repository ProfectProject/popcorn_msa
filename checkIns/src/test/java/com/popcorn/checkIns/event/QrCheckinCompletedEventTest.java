/*package com.popcorn.checkIns.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QR 체크인 완료 이벤트 테스트")
class QrCheckinCompletedEventTest {

	@Test
	@DisplayName("이벤트 생성 및 속성 확인")
	void createEvent_hasCorrectProperties() {
		// Given
		Object source = new Object();
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000001");
		UUID qrId = UUID.fromString("80000000-0000-0000-0000-000000000001");
		UUID orderId = UUID.fromString("40000000-0000-0000-0000-000000000001");
		String qrCode = "test-qr-completed";
		LocalDateTime checkinTime = LocalDateTime.now();

		// When
		QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
				source, checkinId, qrId, orderId, qrCode, checkinTime
		);

		// Then
		assertThat(event.getSource()).isEqualTo(source);
		assertThat(event.getCheckinId()).isEqualTo(checkinId);
		assertThat(event.getQrId()).isEqualTo(qrId);
		assertThat(event.getOrderId()).isEqualTo(orderId);
		assertThat(event.getQrCode()).isEqualTo(qrCode);
		assertThat(event.getCheckinTime()).isEqualTo(checkinTime);
	}

	@Test
	@DisplayName("이벤트는 ApplicationEvent를 상속함")
	void event_extendsApplicationEvent() {
		Object source = new Object();
		UUID checkinId = UUID.randomUUID();
		UUID qrId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		String qrCode = "test-qr-completed";
		LocalDateTime checkinTime = LocalDateTime.now();

		QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
				source, checkinId, qrId, orderId, qrCode, checkinTime
		);

		assertThat(event).isInstanceOf(org.springframework.context.ApplicationEvent.class);
	}

	@Test
	@DisplayName("이벤트 생성 시 null 값 허용")
	void createEvent_allowsNullValues() {
		Object source = new Object();

		QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
				source, null, null, null, null, null
		);

		assertThat(event.getCheckinId()).isNull();
		assertThat(event.getQrId()).isNull();
		assertThat(event.getOrderId()).isNull();
		assertThat(event.getQrCode()).isNull();
		assertThat(event.getCheckinTime()).isNull();
	}

	@Test
	@DisplayName("이벤트 타임스탬프가 생성 시점에 설정됨")
	void event_hasTimestamp() {
		Object source = new Object();
		long before = System.currentTimeMillis();

		QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
				source, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "qr", LocalDateTime.now()
		);

		long after = System.currentTimeMillis();

		assertThat(event.getTimestamp()).isBetween(before, after);
	}

	@Test
	@DisplayName("체크인 ID가 필수 정보임을 확인")
	void checkinId_isRequiredInformation() {
		Object source = new Object();
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000001");

		QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
				source, checkinId, null, null, null, null
		);

		assertThat(event.getCheckinId()).isEqualTo(checkinId);
		assertThat(event.getCheckinId()).isNotNull();
	}
}*/