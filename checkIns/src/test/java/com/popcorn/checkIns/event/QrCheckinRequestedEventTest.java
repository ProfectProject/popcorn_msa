/*package com.popcorn.checkIns.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QR 체크인 요청 이벤트 테스트")
class QrCheckinRequestedEventTest {

	@Test
	@DisplayName("이벤트 생성 및 속성 확인")
	void createEvent_hasCorrectProperties() {
		// Given
		Object source = new Object();
		UUID qrId = UUID.fromString("90000000-0000-0000-0000-000000000001");
		UUID orderId = UUID.fromString("40000000-0000-0000-0000-000000000001");
		String qrCode = "test-qr-code";
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = now.plusMinutes(10);
		LocalDateTime checkinTime = now;

		// When
		QrCheckinRequestedEvent event = new QrCheckinRequestedEvent(
				source, qrId, orderId, qrCode, expiresAt, checkinTime
		);

		// Then
		assertThat(event.getSource()).isEqualTo(source);
		assertThat(event.getQrId()).isEqualTo(qrId);
		assertThat(event.getOrderId()).isEqualTo(orderId);
		assertThat(event.getQrCode()).isEqualTo(qrCode);
		assertThat(event.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(event.getCheckinTime()).isEqualTo(checkinTime);
	}

	@Test
	@DisplayName("이벤트는 ApplicationEvent를 상속함")
	void event_extendsApplicationEvent() {
		Object source = new Object();
		UUID qrId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		String qrCode = "test-qr";
		LocalDateTime now = LocalDateTime.now();

		QrCheckinRequestedEvent event = new QrCheckinRequestedEvent(
				source, qrId, orderId, qrCode, now.plusMinutes(10), now
		);

		assertThat(event).isInstanceOf(org.springframework.context.ApplicationEvent.class);
	}

	@Test
	@DisplayName("이벤트 생성 시 null 값 허용")
	void createEvent_allowsNullValues() {
		Object source = new Object();

		QrCheckinRequestedEvent event = new QrCheckinRequestedEvent(
				source, null, null, null, null, null
		);

		assertThat(event.getQrId()).isNull();
		assertThat(event.getOrderId()).isNull();
		assertThat(event.getQrCode()).isNull();
		assertThat(event.getExpiresAt()).isNull();
		assertThat(event.getCheckinTime()).isNull();
	}

	@Test
	@DisplayName("이벤트 타임스탬프가 생성 시점에 설정됨")
	void event_hasTimestamp() {
		Object source = new Object();
		long before = System.currentTimeMillis();

		QrCheckinRequestedEvent event = new QrCheckinRequestedEvent(
				source, UUID.randomUUID(), UUID.randomUUID(), "qr", LocalDateTime.now(), LocalDateTime.now()
		);

		long after = System.currentTimeMillis();

		assertThat(event.getTimestamp()).isBetween(before, after);
	}
}*/