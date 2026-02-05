package com.popcorn.checkIns.checkin.repository.owner;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.popcorn.checkIns.checkin.repository.CheckinRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OwnerCheckinRepository {

	private final JdbcTemplate jdbcTemplate;

	public List<CheckinRow> findByPopupId(UUID popupId, Long ownerId, int limit) {
		return jdbcTemplate.query(
				"""
				SELECT DISTINCT c.checkin_id, c.order_id, c.order_qr_code_id, c.created_at, c.created_by, q.qr_code
				FROM qr.qr_checkins c
				JOIN qr.qr_order_qr_codes q ON q.qr_id = c.order_qr_code_id
				JOIN orders.p_orders o ON o.order_id = c.order_id AND o.deleted_at IS NULL
				JOIN p_order_goods og ON og.order_id = o.order_id AND og.deleted_at IS NULL
				LEFT JOIN p_popup_schedules ps ON ps.schedule_id = og.schedule_id AND ps.deleted_at IS NULL
				LEFT JOIN p_goods_variants gv ON gv.goods_id = og.goods_variant_id AND gv.deleted_at IS NULL
				JOIN p_popups p ON p.popup_id = COALESCE(ps.popup_id, gv.popup_id) AND p.deleted_at IS NULL
				JOIN p_stores s ON s.store_id = p.store_id AND s.deleted_at IS NULL
				WHERE p.popup_id = ?
				  AND s.user_id = ?
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
				popupId,
				ownerId,
				limit
		);
	}

	public List<CheckinRow> findByScheduleId(UUID popupId, UUID scheduleId, Long ownerId, int limit) {
		return jdbcTemplate.query(
				"""
				SELECT DISTINCT c.checkin_id, c.order_id, c.order_qr_code_id, c.created_at, c.created_by, q.qr_code
				FROM qr.qr_checkins c
				JOIN qr.qr_order_qr_codes q ON q.qr_id = c.order_qr_code_id
				JOIN orders.p_orders o ON o.order_id = c.order_id AND o.deleted_at IS NULL
				JOIN p_order_goods og ON og.order_id = o.order_id AND og.deleted_at IS NULL
				JOIN p_popup_schedules ps ON ps.schedule_id = og.schedule_id AND ps.deleted_at IS NULL
				JOIN p_popups p ON p.popup_id = ps.popup_id AND p.deleted_at IS NULL
				JOIN p_stores s ON s.store_id = p.store_id AND s.deleted_at IS NULL
				WHERE ps.schedule_id = ?
				  AND p.popup_id = ?
				  AND s.user_id = ?
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
				scheduleId,
				popupId,
				ownerId,
				limit
		);
	}

	private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
		if (timestamp == null) {
			return null;
		}
		return timestamp.toLocalDateTime();
	}
}
