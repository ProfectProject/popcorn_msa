package com.popcorn.checkIns.event.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * QR 체크인 완료 이벤트
 * 체크인이 성공적으로 완료되었을 때 발생하는 이벤트
 */
@Getter
public class QrCheckinCompletedEvent extends ApplicationEvent {

	private final UUID checkinId;
	private final UUID qrId;
	private final UUID orderId;
	private final String qrCode;
	private final LocalDateTime checkinTime;

	public QrCheckinCompletedEvent(
			Object source,
			UUID checkinId,
			UUID qrId,
			UUID orderId,
			String qrCode,
			LocalDateTime checkinTime) {
		super(source);
		this.checkinId = checkinId;
		this.qrId = qrId;
		this.orderId = orderId;
		this.qrCode = qrCode;
		this.checkinTime = checkinTime;
	}
}