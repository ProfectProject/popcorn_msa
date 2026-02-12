package com.popcorn.checkIns.checkin.repository;

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
public class CheckinRepository {

	private final JdbcTemplate jdbcTemplate;

	public UUID insert(UUID orderId, UUID orderQrCodeId, Long createdBy, LocalDateTime createdAt) {
		UUID checkinId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
				INSERT INTO checkins.qr_checkins (checkin_id, order_id, order_qr_code_id, created_at, created_by)
				VALUES (?, ?, ?, ?, ?)
				""",
				checkinId,
				orderId,
				orderQrCodeId,
				toTimestamp(createdAt),
				createdBy
		);
		return checkinId;
	}

	public Optional<CheckinRow> findLatestByOrderQrCodeId(UUID orderQrCodeId) {
		List<CheckinRow> rows = jdbcTemplate.query(
				"""
				SELECT c.checkin_id, c.order_id, c.order_qr_code_id, c.created_at, c.created_by, q.qr_code
				FROM checkins.qr_checkins c
				JOIN checkins.qr_order_qr_codes q ON q.qr_id = c.order_qr_code_id
				WHERE c.order_qr_code_id = ?
				ORDER BY c.created_at DESC
				LIMIT 1
				""",
				(rs, rowNum) -> new CheckinRow(
						UUID.fromString(rs.getString("checkin_id")),
						UUID.fromString(rs.getString("order_id")),
						UUID.fromString(rs.getString("order_qr_code_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("created_at")),
						(Long) rs.getObject("created_by")
				),
				orderQrCodeId
		);
		return rows.stream().findFirst();
	}

	public List<CheckinRow> findAll(int limit) {
		return jdbcTemplate.query(
				"""
				SELECT c.checkin_id, c.order_id, c.order_qr_code_id, c.created_at, c.created_by, q.qr_code
				FROM checkins.qr_checkins c
				JOIN checkins.qr_order_qr_codes q ON q.qr_id = c.order_qr_code_id
				ORDER BY c.created_at DESC, c.checkin_id DESC
				LIMIT ?
				""",
				(rs, rowNum) -> new CheckinRow(
						UUID.fromString(rs.getString("checkin_id")),
						UUID.fromString(rs.getString("order_id")),
						UUID.fromString(rs.getString("order_qr_code_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("created_at")),
						(Long) rs.getObject("created_by")
				),
				limit
		);
	}

	public Optional<CheckinRow> findById(UUID checkinId) {
		List<CheckinRow> rows = jdbcTemplate.query(
				"""
				SELECT c.checkin_id, c.order_id, c.order_qr_code_id, c.created_at, c.created_by, q.qr_code
				FROM checkins.qr_checkins c
				JOIN checkins.qr_order_qr_codes q ON q.qr_id = c.order_qr_code_id
				WHERE c.checkin_id = ?
				""",
				(rs, rowNum) -> new CheckinRow(
						UUID.fromString(rs.getString("checkin_id")),
						UUID.fromString(rs.getString("order_id")),
						UUID.fromString(rs.getString("order_qr_code_id")),
						rs.getString("qr_code"),
						toLocalDateTime(rs.getTimestamp("created_at")),
						(Long) rs.getObject("created_by")
				),
				checkinId
		);
		return rows.stream().findFirst();
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
