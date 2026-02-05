package com.popcorn.checkIns.event.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * QR 체크인 요청 이벤트
 * QR 코드 스캔 시 체크인 처리를 위한 이벤트
 */
@Getter
public class QrCheckinRequestedEvent extends ApplicationEvent {

	private final UUID qrId;
	private final UUID orderId;
	private final String qrCode;
	private final LocalDateTime expiresAt;
	private final LocalDateTime checkinTime;

	public QrCheckinRequestedEvent(
			Object source,
			UUID qrId,
			UUID orderId,
			String qrCode,
			LocalDateTime expiresAt,
			LocalDateTime checkinTime) {
		super(source);
		this.qrId = qrId;
		this.orderId = orderId;
		this.qrCode = qrCode;
		this.expiresAt = expiresAt;
		this.checkinTime = checkinTime;
	}
}