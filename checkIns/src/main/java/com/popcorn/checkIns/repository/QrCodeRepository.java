package com.popcorn.checkIns.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.popcorn.checkIns.client.OrderApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class QrCodeRepository {

	private final JdbcTemplate jdbcTemplate;
	private final OrderApiClient orderApiClient;

	/**
	 * 주문 상태 조회 (HTTP API 방식)
	 * MSA 원칙에 따라 Order 서비스 API를 호출
	 */
	public Optional<String> findOrderStatus(UUID orderId) {
		try {
			String orderStatus = orderApiClient.getOrderStatus(orderId);
			return Optional.of(orderStatus);
		} catch (OrderApiClient.OrderNotFoundException e) {
			log.debug("🔍 [QR-REPO] 주문을 찾을 수 없음: orderId={}", orderId);
			return Optional.empty();
		} catch (Exception e) {
			log.error("❌ [QR-REPO] 주문 상태 조회 실패: orderId={} error={}", orderId, e.getMessage());
			// API 호출 실패 시 빈 Optional 반환 (서비스 계층에서 적절히 처리)
			return Optional.empty();
		}
	}

	public Optional<QrCodeRow> findLatestByOrderId(UUID orderId) {
		List<QrCodeRow> rows = jdbcTemplate.query(
				"""
				SELECT qr_id, order_id, qr_code, expires_at, created_at, store_id, popup_id, order_goods_id
				FROM checkins.qr_order_qr_codes
				WHERE order_id = ?
				ORDER BY created_at DESC
				LIMIT 1
				""",
				(rs, rowNum) -> new QrCodeRow(
						UUID.fromString(rs.getString("qr_id")),
						UUID.fromString(rs.getString("order_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("expires_at")),
						toLocalDateTime(rs.getTimestamp("created_at")),
						rs.getString("store_id") != null ? UUID.fromString(rs.getString("store_id")) : null,
						rs.getString("popup_id") != null ? UUID.fromString(rs.getString("popup_id")) : null,
						rs.getString("order_goods_id") != null ? UUID.fromString(rs.getString("order_goods_id")) : null
				),
				orderId
		);

		return rows.stream().findFirst();
	}

	public Optional<QrCodeRow> findLatestByQrCode(String qrCode) {
		List<QrCodeRow> rows = jdbcTemplate.query(
				"""
				SELECT qr_id, order_id, qr_code, expires_at, created_at, store_id, popup_id, order_goods_id
				FROM checkins.qr_order_qr_codes
				WHERE qr_code = ?
				ORDER BY created_at DESC
				LIMIT 1
				""",
				(rs, rowNum) -> new QrCodeRow(
						UUID.fromString(rs.getString("qr_id")),
						UUID.fromString(rs.getString("order_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("expires_at")),
						toLocalDateTime(rs.getTimestamp("created_at")),
						rs.getString("store_id") != null ? UUID.fromString(rs.getString("store_id")) : null,
						rs.getString("popup_id") != null ? UUID.fromString(rs.getString("popup_id")) : null,
						rs.getString("order_goods_id") != null ? UUID.fromString(rs.getString("order_goods_id")) : null
				),
				qrCode
		);

		return rows.stream().findFirst();
	}

	public void insert(QrCodeRow row) {
		jdbcTemplate.update(
				"""
				INSERT INTO checkins.qr_order_qr_codes
					(qr_id, order_id, qr_code, expires_at, created_at, created_by, store_id, popup_id, order_goods_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				row.qrId(),
				row.orderId(),
				row.qrCode(),
				toTimestamp(row.expiresAt()),
				toTimestamp(row.createdAt()),
				null,
				row.storeId(),
				row.popupId(),
				row.orderGoodsId()
		);
	}

	/**
	 * 주문 ID로 모든 QR 코드 조회 (무효화용)
	 */
	public List<QrCodeRow> findAllByOrderId(UUID orderId) {
		return jdbcTemplate.query(
				"""
				SELECT qr_id, order_id, qr_code, expires_at, created_at, store_id, popup_id, order_goods_id
				FROM checkins.qr_order_qr_codes
				WHERE order_id = ?
				ORDER BY created_at DESC
				""",
				(rs, rowNum) -> new QrCodeRow(
						UUID.fromString(rs.getString("qr_id")),
						UUID.fromString(rs.getString("order_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("expires_at")),
						toLocalDateTime(rs.getTimestamp("created_at")),
						rs.getString("store_id") != null ? UUID.fromString(rs.getString("store_id")) : null,
						rs.getString("popup_id") != null ? UUID.fromString(rs.getString("popup_id")) : null,
						rs.getString("order_goods_id") != null ? UUID.fromString(rs.getString("order_goods_id")) : null
				),
				orderId
		);
	}

	/**
	 * QR 코드 만료시간 업데이트 (무효화용)
	 */
	public int updateExpiresAt(UUID qrId, LocalDateTime expiresAt) {
		return jdbcTemplate.update(
				"""
				UPDATE checkins.qr_order_qr_codes
				SET expires_at = ?, updated_at = CURRENT_TIMESTAMP
				WHERE qr_id = ?
				""",
				toTimestamp(expiresAt),
				qrId
		);
	}

	private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
		if (timestamp == null) {
			return null;
		}
		return timestamp.toLocalDateTime();
	}

	private static Timestamp toTimestamp(LocalDateTime value) {
		if (value == null) {
			return null;
		}
		return Timestamp.valueOf(value);
	}
}
