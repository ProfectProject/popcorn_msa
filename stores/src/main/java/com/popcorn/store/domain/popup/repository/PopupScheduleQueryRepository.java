package com.popcorn.store.domain.popup.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.popcorn.store.domain.popup.entity.PopupSchedule;
import com.popcorn.store.domain.popup.repository.view.PopupScheduleView;

public interface PopupScheduleQueryRepository extends Repository<PopupSchedule, UUID> {

	@Query(value = """
			SELECT CAST(ps.schedule_id AS VARCHAR) AS scheduleId,
			       ps.start_at AS startAt,
			       ps.end_at AS endAt,
			       ps.price AS price,
			       ps.capacity AS capacity,
			       ps.remaining_capacity AS remainingCapacity,
			       ps.is_active AS isActive
			  FROM store.popup_schedules ps
			 WHERE ps.deleted_at IS NULL
			   AND ps.popup_id = :popupId
			   AND (CAST(:from AS TIMESTAMP) IS NULL OR ps.start_at >= CAST(:from AS TIMESTAMP))
			   AND (CAST(:to AS TIMESTAMP) IS NULL OR ps.end_at <= CAST(:to AS TIMESTAMP))
			 ORDER BY ps.start_at ASC
			""", nativeQuery = true)
	List<PopupScheduleView> findProductSessions(@Param("popupId") UUID popupId,
												@Param("from") LocalDateTime from,
												@Param("to") LocalDateTime to);

	@Query(value = """
			SELECT ps.remaining_capacity
			  FROM store.popup_schedules ps
			 WHERE ps.deleted_at IS NULL
			   AND ps.popup_id = :popupId
			   AND ps.schedule_id = :scheduleId
			""", nativeQuery = true)
	Integer findRemainingCapacity(@Param("popupId") UUID popupId,
								  @Param("scheduleId") UUID scheduleId);

	/**
	 * 세션 ID로 스케줄 정보 조회 (가격 조회용)
	 */
	@Query(value = """
			SELECT CAST(ps.schedule_id AS VARCHAR) AS scheduleId,
			       ps.popup_id AS popupId,
			       ps.start_at AS startAt,
			       ps.end_at AS endAt,
			       ps.price AS price,
			       ps.capacity AS capacity,
			       ps.remaining_capacity AS remainingCapacity,
			       ps.is_active AS isActive
			  FROM store.popup_schedules ps
			 WHERE ps.deleted_at IS NULL
			   AND ps.schedule_id = :scheduleId
			""", nativeQuery = true)
	PopupScheduleView findByScheduleId(@Param("scheduleId") UUID scheduleId);
}
