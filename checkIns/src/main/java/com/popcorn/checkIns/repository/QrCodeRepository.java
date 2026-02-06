package com.popcorn.checkIns.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QrCodeRepository {

	private final JdbcTemplate jdbcTemplate;

	public Optional<String> findOrderStatus(UUID orderId) {
		List<String> statuses = jdbcTemplate.query(
				"SELECT status FROM orders.p_orders WHERE order_id = ?",
				(rs, rowNum) -> rs.getString("status"),
				orderId
		);

		return statuses.stream().findFirst();
	}

	public Optional<QrCodeRow> findLatestByOrderId(UUID orderId) {
		List<QrCodeRow> rows = jdbcTemplate.query(
				"""
				SELECT qr_id, order_id, qr_code, expires_at, created_at, store_id, popup_id, order_goods_id
				FROM qr.qr_order_qr_codes
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
				FROM qr.qr_order_qr_codes
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
				INSERT INTO qr.qr_order_qr_codes
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
				FROM qr.qr_order_qr_codes
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
				UPDATE qr.qr_order_qr_codes
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
