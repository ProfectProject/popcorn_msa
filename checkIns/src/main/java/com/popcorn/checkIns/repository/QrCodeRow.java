package com.popcorn.checkIns.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record QrCodeRow(
		UUID qrId,
		UUID orderId,
		String qrCode,
		LocalDateTime expiresAt,
		LocalDateTime createdAt,
		UUID storeId,
		UUID popupId,
		UUID orderGoodsId) {

	public boolean isExpired(LocalDateTime now) {
		if (expiresAt == null) {
			return false;
		}
		return !expiresAt.isAfter(now);
	}
}
