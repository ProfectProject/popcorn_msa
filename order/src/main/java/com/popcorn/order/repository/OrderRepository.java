package com.popcorn.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;

/**
 * 주문 Repository 인터페이스
 *
 * [초보자 가이드]
 * Repository 패턴: 데이터 액세스 로직을 캡슐화하는 패턴
 *
 * JpaRepository를 확장하면 기본적인 CRUD 메서드들이 자동으로 제공됩니다:
 * - save(entity): 엔티티 저장/수정
 * - findById(id): ID로 엔티티 조회
 * - findAll(): 모든 엔티티 조회
 * - delete(entity): 엔티티 삭제
 * - count(): 전체 개수 조회 등
 *
 * 추가로 필요한 쿼리 메서드들을 정의할 수 있습니다.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * OrderItem들과 함께 주문 조회 (Payment 서비스용)
     *
     * @param orderId 주문 ID
     * @return 주문 정보와 OrderItem 목록
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") UUID orderId);

    /**
     * 주문 번호로 주문 조회
     *
     * @param orderNo 주문 번호
     * @return 주문 정보
     *
     * [초보자 가이드]
     * 메서드 네이밍 규칙:
     * - findBy[필드명]: 해당 필드로 검색
     * - Optional<T>: 결과가 없을 수도 있음을 명시
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 사용자별 주문 목록 조회
     *
     * @param customerId 고객 ID
     * @return 주문 목록
     *
     * [초보자 가이드]
     * 자동 생성되는 쿼리: SELECT * FROM orders WHERE customer_id = ?
     */
    List<Order> findByCustomerId(Long customerId);

    /**
     * 주문 상태별 조회
     *
     * @param status 주문 상태
     * @return 주문 목록
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * 사용자별 특정 상태 주문 조회
     *
     * @param customerId 고객 ID
     * @param status 주문 상태
     * @return 주문 목록
     *
     * [초보자 가이드]
     * 복합 조건 검색: And로 여러 조건을 연결
     * 자동 생성 쿼리: SELECT * FROM orders WHERE customer_id = ? AND status = ?
     */
    List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);

    /**
     * 사용자별 주문 개수 조회
     *
     * @param customerId 고객 ID
     * @return 주문 개수
     */
    long countByCustomerId(Long customerId);

    /**
     * 사용자별 최근 주문 조회 (페이징)
     *
     * @param customerId 고객 ID
     * @param limit 조회 개수
     * @return 최근 주문 목록
     *
     * [초보자 가이드]
     * @Query: 직접 JPQL(또는 SQL)을 작성할 때 사용
     * ORDER BY ... DESC: 최신순 정렬
     * LIMIT: 조회 개수 제한 (MySQL 문법, PostgreSQL에서는 다를 수 있음)
     */
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId " +
           "ORDER BY o.createdAt DESC LIMIT :limit")
    List<Order> findRecentOrdersByCustomerId(@Param("customerId") Long customerId,
                                           @Param("limit") int limit);

    /**
     * 취소 가능한 주문 목록 조회
     *
     * @return 취소 가능한 주문 목록
     *
     * [초보자 가이드]
     * CURRENT_TIMESTAMP: 현재 시각
     * 취소 가능 시간이 현재 시각보다 이후이고, 상태가 취소/완료가 아닌 주문들
     */
    @Query("SELECT o FROM Order o WHERE o.cancelableUntil > CURRENT_TIMESTAMP " +
           "AND o.status NOT IN ('CANCELLED', 'COMPLETED')")
    List<Order> findCancelableOrders();

    // ================ 페이징 지원 메서드들 ================

    /**
     * 사용자별 주문 목록 조회 (페이징, 최신순)
     */
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /**
     * 팝업별 주문 목록 조회 (페이징, 최신순)
     */
    Page<Order> findByPopupIdOrderByCreatedAtDesc(UUID popupId, Pageable pageable);

    /**
     * 팝업별 특정 상태 주문 목록 조회 (페이징, 최신순)
     */
    Page<Order> findByPopupIdAndStatusOrderByCreatedAtDesc(UUID popupId, OrderStatus status, Pageable pageable);

    /**
     * 특정 상태이고 생성일이 특정 시점 이전인 주문들 조회 (타임아웃 처리용)
     */
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoffTime);

    /**
     * 특정 상태이고 취소 가능 시한이 지난 주문들 조회 (타임아웃 처리용)
     */
    List<Order> findByStatusAndCancelableUntilBefore(OrderStatus status, LocalDateTime cutoffTime);

    /**
     * 주문 번호 존재 여부 확인
     */
    boolean existsByOrderNo(String orderNo);

    // ================ 커스텀 쿼리 메서드들 ================

    /**
     * 취소 가능한 주문들 조회 (특정 사용자, 페이징)
     */
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId " +
           "AND o.cancelableUntil > :currentTime " +
           "AND o.status NOT IN ('CANCELLED', 'COMPLETED', 'REJECTED') " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findCancellableOrdersByCustomerId(
            @Param("customerId") Long customerId,
            @Param("currentTime") LocalDateTime currentTime,
            Pageable pageable);

    /**
     * 오래된 요청 상태 주문들 조회 (관리자용)
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'REQUESTED' " +
           "AND o.createdAt < :cutoffTime " +
           "ORDER BY o.createdAt ASC")
    Page<Order> findRequestedOrdersOlderThan(
            @Param("cutoffTime") LocalDateTime cutoffTime,
            Pageable pageable);

    // ================ Store 서비스 방식 Pagination 메서드들 ================

    /**
     * 팝업별 주문 목록 조회 (조건부 필터, Pageable 사용)
     *
     * @param popupId 팝업 ID
     * @param status 주문 상태 (선택적)
     * @param orderType 주문 타입 (선택적)
     * @param pageable 페이징 정보
     * @return 주문 목록 페이지
     */
    @Query("SELECT o FROM Order o WHERE o.popupId = :popupId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:orderType IS NULL OR o.orderType = :orderType) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersByPopupIdWithConditions(
            @Param("popupId") UUID popupId,
            @Param("status") String status,
            @Param("orderType") String orderType,
            Pageable pageable);

    /**
     * 팝업별 주문 개수 조회 (조건부 필터)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.popupId = :popupId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:orderType IS NULL OR o.orderType = :orderType)")
    long countOrdersByPopupId(
            @Param("popupId") UUID popupId,
            @Param("status") String status,
            @Param("orderType") String orderType);

    /**
     * 카테고리별 주문 목록 조회 (조건부 필터, Pageable 사용)
     *
     * @param orderType 주문 타입
     * @param status 주문 상태 (선택적)
     * @param userId 사용자 ID (선택적)
     * @param from 시작 날짜 (선택적)
     * @param to 종료 날짜 (선택적)
     * @param pageable 페이징 정보
     * @return 주문 목록 페이지
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:orderType IS NULL OR o.orderType = :orderType) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:userId IS NULL OR o.customerId = :userId) " +
           "AND (:from IS NULL OR o.createdAt >= :from) " +
           "AND (:to IS NULL OR o.createdAt <= :to) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersByCategoryWithConditions(
            @Param("orderType") ItemType orderType,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * 카테고리별 주문 개수 조회 (조건부 필터)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE " +
           "(:orderType IS NULL OR o.orderType = :orderType) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:userId IS NULL OR o.customerId = :userId) " +
           "AND (:from IS NULL OR o.createdAt >= :from) " +
           "AND (:to IS NULL OR o.createdAt <= :to)")
    long countOrdersByCategory(
            @Param("orderType") ItemType orderType,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 통합 조건 주문 목록 조회 (모든 조건 지원, Pageable 사용)
     *
     * @param popupId 팝업 ID (선택적)
     * @param orderType 주문 타입 (선택적)
     * @param status 주문 상태 (선택적)
     * @param userId 사용자 ID (선택적)
     * @param storeId 스토어 ID (선택적) - 향후 확장용
     * @param from 시작 날짜 (선택적)
     * @param to 종료 날짜 (선택적)
     * @param pageable 페이징 정보
     * @return 주문 목록 페이지
     */
    @Query("SELECT o FROM Order o WHERE " +
           "o.popupId = COALESCE(:popupId, o.popupId) " +
           "AND o.orderType = COALESCE(:orderType, o.orderType) " +
           "AND o.status = COALESCE(:status, o.status) " +
           "AND o.customerId = COALESCE(:userId, o.customerId) " +
           "AND o.createdAt >= COALESCE(:from, o.createdAt) " +
           "AND o.createdAt <= COALESCE(:to, o.createdAt) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersWithAllConditions(
            @Param("popupId") UUID popupId,
            @Param("orderType") ItemType orderType,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("storeId") UUID storeId, // 현재는 사용하지 않지만 향후 확장용
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * 통합 조건 주문 개수 조회 (모든 조건 지원)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE " +
           "o.popupId = COALESCE(:popupId, o.popupId) " +
           "AND o.orderType = COALESCE(:orderType, o.orderType) " +
           "AND o.status = COALESCE(:status, o.status) " +
           "AND o.customerId = COALESCE(:userId, o.customerId) " +
           "AND o.createdAt >= COALESCE(:from, o.createdAt) " +
           "AND o.createdAt <= COALESCE(:to, o.createdAt)")
    long countOrdersWithAllConditions(
            @Param("popupId") UUID popupId,
            @Param("orderType") ItemType orderType,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("storeId") UUID storeId, // 현재는 사용하지 않지만 향후 확장용
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ===== 새로 추가된 고급 조회 API용 메소드들 =====

    /**
     * 팝업별 주문 목록 조회 (고급 조건 필터, Pageable 사용)
     *
     * [Java 초보자를 위한 가이드]
     *
     * 이 메소드가 하는 일:
     * - 특정 팝업(popupId)의 모든 주문을 조회
     * - 주문 타입(예약/구매), 상태, 기간별 필터링 가능
     * - 페이지네이션 지원
     *
     * 참고: 이 MSA 구조에서는 주문이 매장이 아닌 팝업에 직접 연결됩니다.
     *
     * @param popupId 팝업 ID
     * @param status 주문 상태 (선택적, null이면 전체)
     * @param orderType 주문 타입 (선택적, null이면 전체)
     * @param from 시작 날짜 (선택적)
     * @param to 종료 날짜 (선택적)
     * @param pageable 페이징 정보
     * @return 주문 목록 페이지
     */
    @Query("SELECT o FROM Order o WHERE o.popupId = :popupId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:orderType IS NULL OR o.orderType = :orderType) " +
           "AND (:from IS NULL OR o.createdAt >= :from) " +
           "AND (:to IS NULL OR o.createdAt <= :to) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersByPopupIdWithAdvancedConditions(
            @Param("popupId") UUID popupId,
            @Param("status") OrderStatus status,
            @Param("orderType") ItemType orderType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

}
