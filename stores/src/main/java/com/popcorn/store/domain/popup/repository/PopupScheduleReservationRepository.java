package com.popcorn.store.domain.popup.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleCapacity;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PopupScheduleReservationRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public PopupScheduleCapacity cancelCapacity(UUID scheduleId, int quantity) {
		String sql = """
			UPDATE popup_schedules
			   SET remaining_capacity = remaining_capacity + :quantity,
			       updated_at = now()
			 WHERE schedule_id = :scheduleId
			   AND deleted_at IS NULL
			   AND (capacity - remaining_capacity) >= :quantity
			   AND (remaining_capacity + :quantity) <= capacity
			RETURNING schedule_id, capacity, remaining_capacity
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("scheduleId", scheduleId)
			.addValue("quantity", quantity);

		return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapCapacity(rs) : null);
	}

	public PopupScheduleCapacity failCapacity(UUID scheduleId, int quantity) {
		return cancelCapacity(scheduleId, quantity);
	}

	public PopupScheduleCapacity completeCapacity(UUID scheduleId, int quantity){
		String sql = """
				UPDATE popup_schedules
				   SET remaining_capacity = remaining_capacity - :quantity,
				       updated_at = now()
				 WHERE schedule_id = :scheduleId
				   AND deleted_at IS NULL
				   AND remaining_capacity >= :quantity
				   AND capacity >= :quantity
				RETURNING schedule_id, capacity, remaining_capacity
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("scheduleId", scheduleId)
				.addValue("quantity", quantity);

		return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapCapacity(rs) : null);
	}

	private PopupScheduleCapacity mapCapacity(ResultSet rs) throws SQLException {
		return new PopupScheduleCapacity(
			UUID.fromString(rs.getString("schedule_id")),
			rs.getInt("capacity"),
			rs.getInt("remaining_capacity")
		);
	}
}
