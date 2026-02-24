package com.popcorn.store.domain.popup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.popcorn.store.domain.popup.entity.Popup;
import com.popcorn.store.domain.popup.repository.view.PopupListView;

public interface PopupQueryRepository extends Repository<Popup, UUID> {

	@Query(value = """
			SELECT CAST(p.popup_id AS VARCHAR) AS id,
			       CAST(p.store_id AS VARCHAR) AS storeId,
			       p.title AS title,
			       p.description AS description,
			       p.category AS category,
			       p.status AS status,
			       p.reservation_open_at AS reservationOpenAt,
			       p.address_road AS addressRoad,
			       p.address_detail AS addressDetail,
			       COALESCE(sched.eventStartAt, p.reservation_open_at) AS eventStartAt,
			       COALESCE(sched.eventEndAt, p.reservation_open_at) AS eventEndAt
			  FROM store.popups p
			  LEFT JOIN (
			      SELECT ps.popup_id,
			             MIN(ps.start_at) AS eventStartAt,
			             MAX(ps.end_at) AS eventEndAt
			        FROM store.popup_schedules ps
			       WHERE ps.deleted_at IS NULL
			       GROUP BY ps.popup_id
			  ) sched ON sched.popup_id = p.popup_id
			 WHERE p.deleted_at IS NULL
			   AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS store.popup_category))
			   AND (:keyword IS NULL OR p.title ILIKE CONCAT('%', :keyword, '%') OR p.description ILIKE CONCAT('%', :keyword, '%'))
			   AND (:storeId IS NULL OR p.store_id = :storeId)
			 ORDER BY p.created_at DESC
			 LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<PopupListView> findPopups(@Param("regionId") Long regionId,
			@Param("category") String category,
			@Param("keyword") String keyword,
			@Param("storeId") UUID storeId,
			@Param("limit") int limit,
			@Param("offset") long offset);

	@Query(value = """
			SELECT COUNT(1)
			  FROM store.popups p
			 WHERE p.deleted_at IS NULL
			   AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS store.popup_category))
			   AND (:keyword IS NULL OR p.title ILIKE CONCAT('%', :keyword, '%') OR p.description ILIKE CONCAT('%', :keyword, '%'))
			   AND (:storeId IS NULL OR p.store_id = :storeId)
			""", nativeQuery = true)
	long countPopups(@Param("regionId") Long regionId,
			@Param("category") String category,
			@Param("keyword") String keyword,
			@Param("storeId") UUID storeId);

	@Query(value = """
			SELECT CAST(p.popup_id AS VARCHAR) AS id,
			       CAST(p.store_id AS VARCHAR) AS storeId,
			       p.title AS title,
			       p.description AS description,
			       p.category AS category,
			       p.status AS status,
			       p.reservation_open_at AS reservationOpenAt,
			       p.address_road AS addressRoad,
			       p.address_detail AS addressDetail,
			       COALESCE(sched.eventStartAt, p.reservation_open_at) AS eventStartAt,
			       COALESCE(sched.eventEndAt, p.reservation_open_at) AS eventEndAt
			  FROM store.popups p
			  LEFT JOIN (
			      SELECT ps.popup_id,
			             MIN(ps.start_at) AS eventStartAt,
			             MAX(ps.end_at) AS eventEndAt
			        FROM store.popup_schedules ps
			       WHERE ps.deleted_at IS NULL
			         AND ps.popup_id = :popupId
			       GROUP BY ps.popup_id
			  ) sched ON sched.popup_id = p.popup_id
			 WHERE p.deleted_at IS NULL
			   AND p.popup_id = :popupId
			""", nativeQuery = true)
	Optional<PopupListView> findPopupDetail(@Param("popupId") UUID popupId);

	@Query(value = """
			SELECT CAST(p.popup_id AS VARCHAR) AS id,
			       CAST(p.store_id AS VARCHAR) AS storeId,
			       p.title AS title,
			       p.description AS description,
			       p.category AS category,
			       p.status AS status,
			       p.reservation_open_at AS reservationOpenAt,
			       p.address_road AS addressRoad,
			       p.address_detail AS addressDetail,
			       COALESCE(sched.eventStartAt, p.reservation_open_at) AS eventStartAt,
			       COALESCE(sched.eventEndAt, p.reservation_open_at) AS eventEndAt
			  FROM store.popups p
			  LEFT JOIN (
			      SELECT ps.popup_id,
			             MIN(ps.start_at) AS eventStartAt,
			             MAX(ps.end_at) AS eventEndAt
			        FROM store.popup_schedules ps
			       WHERE ps.deleted_at IS NULL
			       GROUP BY ps.popup_id
			  ) sched ON sched.popup_id = p.popup_id
			 WHERE p.deleted_at IS NULL
			   AND p.status = CAST(:status AS store.popup_status)
			 ORDER BY p.created_at DESC
			 LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<PopupListView> findPopupsByStatus(@Param("status") String status,
			@Param("limit") int limit,
			@Param("offset") long offset);

	@Query(value = """
			SELECT COUNT(1)
			  FROM store.popups p
			 WHERE p.deleted_at IS NULL
			   AND p.status = CAST(:status AS store.popup_status)
			""", nativeQuery = true)
	long countPopupsByStatus(@Param("status") String status);

	/**
	 * 캐시 워밍용: 활성 상태 팝업 ID 목록 조회 (인기순)
	 */
	@Query(value = """
			SELECT p.popup_id
			  FROM store.popups p
			 WHERE p.deleted_at IS NULL
			   AND p.status IN ('OPEN')
			   AND p.event_end_at > CURRENT_TIMESTAMP
			 ORDER BY p.created_at DESC, p.updated_at DESC
			 LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findActivePopupIds(@Param("limit") int limit);
}
