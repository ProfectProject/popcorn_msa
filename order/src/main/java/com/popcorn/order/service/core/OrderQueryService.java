package com.popcorn.order.service.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.order.dto.query.OrderListQuery;
import com.popcorn.order.dto.response.OrderDetailResponse;
import com.popcorn.order.dto.response.OrderListResponse;
import com.popcorn.order.dto.response.OrderSummaryResponse;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.util.PerformanceLogger;
import com.popcorn.order.entity.OrderStatusHistory;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.repository.OrderStatusHistoryRepository;
import com.popcorn.order.dto.store.PopupInfoResponse;
import com.popcorn.order.service.lookup.OrderPopupLookupService;
import com.popcorn.order.client.OrderQueryServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

/**
 * 주문 조회(Query) 서비스 - CQRS 패턴의 조회 쪽 담당
 *
 * CQRS 패턴에서는 명령(Command)과 조회(Query)를 분리해요:
 * - OrderCommandService: 데이터 변경 (생성, 수정, 삭제)
 * - OrderQueryService: 데이터 조회 (읽기 전용)
 *
 * 이렇게 분리하는 이유:
 * - 복잡한 조회 로직과 변경 로직을 분리해서 이해하기 쉬워져요
 * - 조회 성능을 최적화하기 쉬워져요
 * - 각각 독립적으로 확장할 수 있어요
 * - 보안상 조회와 변경 권한을 다르게 관리할 수 있어요
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // 모든 메서드가 읽기 전용임을 명시
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainService orderDomainService;
    private final OrderPopupLookupService orderPopupLookupService;
    private final OrderQueryServiceClient orderQueryServiceClient;

    // ================ 단일 주문 조회 ================

    /**
     * 주문 ID로 주문 상세 정보 조회
     *
     * @param orderId 조회할 주문 ID
     * @return 주문 상세 정보 (없으면 Optional.empty())
     */
    public Optional<OrderDetailResponse> findOrderById(UUID orderId) {
        return PerformanceLogger.logTimeWithReturn("주문 상세 조회", () -> {
            log.debug("주문 상세 조회 - ID: {}", orderId);

            // OrderItem들도 함께 로드하는 FETCH JOIN 쿼리 사용
            Optional<Order> orderOpt = orderRepository.findByIdWithItems(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("주문을 찾을 수 없음 - ID: {}", orderId);
                return Optional.<OrderDetailResponse>empty();
            }

            Order order = orderOpt.get();
            List<OrderStatusHistory> statusHistories = orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);

            OrderDetailResponse response = OrderDetailResponse.fromOrder(order, statusHistories);
            log.debug("주문 상세 조회 완료 - 주문번호: {}", order.getOrderNo());

            return Optional.of(response);
        });
    }

    /**
     * 주문 번호로 주문 조회
     *
     * @param orderNo 주문 번호 (O20260120-000001 형태)
     * @return 주문 상세 정보
     */
    public Optional<OrderDetailResponse> findOrderByOrderNo(String orderNo) {
        log.debug("주문 조회 - 주문번호: {}", orderNo);

        Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            log.warn("주문을 찾을 수 없음 - 주문번호: {}", orderNo);
            return Optional.empty();
        }

        return findOrderById(orderOpt.get().getId());
    }

    // ================ 주문 목록 조회 ================

    /**
     * 사용자의 주문 목록 조회 (페이징)
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 주문 요약 목록
     */
    public Page<OrderSummaryResponse> findOrdersByUserId(Long userId, Pageable pageable) {
        log.debug("사용자 주문 목록 조회 - 사용자: {}, 페이지: {}", userId, pageable.getPageNumber());

        Page<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(userId, pageable);

        return orders.map(order -> {
            // 각 주문에 대해 간단한 요약 정보만 조회
            return OrderSummaryResponse.fromOrder(order);
        });
    }

    /**
     * 팝업별 주문 목록 조회
     *
     * @param popupId 팝업 ID
     * @param pageable 페이징 정보
     * @return 주문 목록
     */
    public Page<OrderSummaryResponse> findOrdersByPopupId(UUID popupId, Pageable pageable) {
        log.debug("팝업 주문 목록 조회 - 팝업: {}", popupId);

        Page<Order> orders = orderRepository.findByPopupIdOrderByCreatedAtDesc(popupId, pageable);
        return orders.map(OrderSummaryResponse::fromOrder);
    }

    // ================ 조건부 조회 ================

    /**
     * 취소 가능한 주문들 조회
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 취소 가능한 주문 목록
     */
    public Page<OrderSummaryResponse> findCancellableOrdersByUserId(Long userId, Pageable pageable) {
        log.debug("취소 가능한 주문 조회 - 사용자: {}", userId);

        LocalDateTime now = LocalDateTime.now();
        Page<Order> orders = orderRepository.findCancellableOrdersByCustomerId(userId, now, pageable);

        return orders.map(OrderSummaryResponse::fromOrder);
    }

    // ================ 통계 및 집계 ================

    /**
     * 사용자의 총 주문 수 조회
     *
     * @param userId 사용자 ID
     * @return 총 주문 수
     */
    public long countOrdersByUserId(Long userId) {
        log.debug("사용자 총 주문 수 조회 - 사용자: {}", userId);
        return orderRepository.countByCustomerId(userId);
    }

    // ================ 비즈니스 로직 조회 ================

    /**
     * 주문이 취소 가능한지 확인
     *
     * @param orderId 주문 ID
     * @return 취소 가능 여부
     */
    public boolean isOrderCancellable(UUID orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        return orderDomainService.canCancelOrder(order);
    }

    /**
     * 주문의 현재 상태 조회
     *
     * @param orderId 주문 ID
     * @return 현재 주문 상태 (주문이 없으면 Optional.empty())
     */
    public Optional<OrderStatus> getOrderStatus(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getStatus);
    }

    /**
     * 주문 상태 변경 이력 조회
     *
     * @param orderId 주문 ID
     * @return 상태 변경 이력 목록
     */
    public List<OrderStatusHistory> getOrderStatusHistory(UUID orderId) {
        log.debug("주문 상태 이력 조회 - 주문: {}", orderId);
        return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

    // ================ 관리자용 조회 ================

    /**
     * 처리가 필요한 주문들 조회 (관리자용)
     *
     * 예: 오랫동안 REQUESTED 상태인 주문들
     *
     * @param hours 체크할 시간 (몇 시간 전부터)
     * @param pageable 페이징 정보
     * @return 처리 필요한 주문 목록
     */
    public Page<OrderSummaryResponse> findOrdersNeedingAttention(int hours, Pageable pageable) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);
        log.debug("처리 필요 주문 조회 - {} 시간 전부터", hours);

        Page<Order> orders = orderRepository.findRequestedOrdersOlderThan(cutoffTime, pageable);
        return orders.map(OrderSummaryResponse::fromOrder);
    }

    /**
     * 주문이 존재하는지 확인
     *
     * @param orderId 주문 ID
     * @return 존재 여부
     */
    public boolean existsById(UUID orderId) {
        return orderRepository.existsById(orderId);
    }

    /**
     * 주문 번호가 존재하는지 확인
     *
     * @param orderNo 주문 번호
     * @return 존재 여부
     */
    public boolean existsByOrderNo(String orderNo) {
        return orderRepository.existsByOrderNo(orderNo);
    }

    // ================ 새로운 Store 서비스 방식 조회 (Pagination) ================

    /**
     * 팝업별 주문 목록 조회 (Store 서비스 방식)
     *
     * @param query 조회 조건
     * @return Store 서비스 방식의 주문 목록 응답
     */
    public OrderListResponse findOrdersByPopupId(OrderListQuery query) {
        log.info("팝업별 주문 목록 조회 - 팝업ID: {}, 페이지: {}, 사이즈: {}",
                query.getPopupId(), query.getValidatedPage(), query.getValidatedSize());

        // 1. 페이지네이션 파라미터 검증
        int page = query.getValidatedPage();
        int size = query.getValidatedSize();

        try {
            // 2. Pageable 생성 (Spring Page는 0부터 시작하므로 -1)
            Pageable pageable = PageRequest.of(page - 1, size);

            // 3. 조건에 맞는 주문 목록 조회
            Page<Order> orderPage = orderRepository.findOrdersByPopupIdWithConditions(
                    query.getPopupId(),
                    query.getStatus(),
                    query.getOrderType(),
                    pageable
            );

            // 4. 전체 개수 (withTotal=false인 경우 -1 반환)
            long total = query.getValidatedWithTotal() ? orderPage.getTotalElements() : -1L;

            // 5. Store 서비스 방식으로 응답 생성
            OrderListResponse response = OrderListResponse.from(
                    orderPage.getContent(), page, size, total);

            log.info("팝업 주문 목록 조회 완료 - 팝업ID: {}, 조회된 주문: {}개, 전체: {}",
                    query.getPopupId(), orderPage.getContent().size(), total);

            return response;

        } catch (Exception e) {
            log.error("팝업 주문 목록 조회 실패 - 팝업ID: {}, 에러: {}", query.getPopupId(), e.getMessage(), e);
            // 에러 시 빈 응답 반환
            return OrderListResponse.from(List.of(), page, size, 0L);
        }
    }

    /**
     * 카테고리별 주문 목록 조회 (Store 서비스 방식)
     *
     * @param query 조회 조건
     * @return Store 서비스 방식의 주문 목록 응답
     */
    public OrderListResponse findOrdersByCategory(OrderListQuery query) {
        log.info("카테고리별 주문 목록 조회 - 카테고리: {}, 페이지: {}, 사이즈: {}",
                query.getOrderType(), query.getValidatedPage(), query.getValidatedSize());

        // 1. 페이지네이션 파라미터 검증
        int page = query.getValidatedPage();
        int size = query.getValidatedSize();

        try {
            // 2. ItemType 검증
            ItemType orderType = null;
            if (query.getOrderType() != null) {
                try {
                    orderType = ItemType.valueOf(query.getOrderType());
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 주문 타입: {}", query.getOrderType());
                    return OrderListResponse.from(List.of(), page, size, 0L);
                }
            }

            // 3. Pageable 생성
            Pageable pageable = PageRequest.of(page - 1, size);

            // 4. 조건에 맞는 주문 목록 조회
            Page<Order> orderPage = orderRepository.findOrdersByCategoryWithConditions(
                    orderType,
                    query.getStatus(),
                    query.getUserId(),
                    query.getFrom(),
                    query.getTo(),
                    pageable
            );

            // 5. 전체 개수 (withTotal=false인 경우 -1 반환)
            long total = query.getValidatedWithTotal() ? orderPage.getTotalElements() : -1L;

            // 6. Store 서비스 방식으로 응답 생성
            OrderListResponse response = OrderListResponse.from(
                    orderPage.getContent(), page, size, total);

            log.info("카테고리 주문 목록 조회 완료 - 카테고리: {}, 조회된 주문: {}개, 전체: {}",
                    query.getOrderType(), orderPage.getContent().size(), total);

            return response;

        } catch (Exception e) {
            log.error("카테고리 주문 목록 조회 실패 - 카테고리: {}, 에러: {}", query.getOrderType(), e.getMessage(), e);
            // 에러 시 빈 응답 반환
            return OrderListResponse.from(List.of(), page, size, 0L);
        }
    }

    /**
     * 통합 주문 목록 조회 (Store 서비스 방식)
     * 모든 검색 조건을 지원하는 범용 메서드
     *
     * @param query 조회 조건
     * @return Store 서비스 방식의 주문 목록 응답
     */
    public OrderListResponse findOrdersWithQuery(OrderListQuery query) {
        log.info("통합 주문 목록 조회 - 팝업: {}, 타입: {}, 상태: {}, 사용자: {}, 페이지: {}",
                query.getPopupId(), query.getOrderType(), query.getStatus(),
                query.getUserId(), query.getValidatedPage());

        int page = query.getValidatedPage();
        int size = query.getValidatedSize();

        try {
            // ItemType 검증
            ItemType orderType = null;
            if (query.getOrderType() != null) {
                try {
                    orderType = ItemType.valueOf(query.getOrderType());
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 주문 타입: {}", query.getOrderType());
                    return OrderListResponse.from(List.of(), page, size, 0L);
                }
            }

            // Pageable 생성
            Pageable pageable = PageRequest.of(page - 1, size);

            // 통합 조회 (모든 조건 지원)
            Page<Order> orderPage = orderRepository.findOrdersWithAllConditions(
                    query.getPopupId(),
                    orderType,
                    query.getStatus(),
                    query.getUserId(),
                    query.getStoreId(),
                    query.getFrom(),
                    query.getTo(),
                    pageable
            );

            // 전체 개수
            long total = query.getValidatedWithTotal() ? orderPage.getTotalElements() : -1L;

            return OrderListResponse.from(orderPage.getContent(), page, size, total);

        } catch (Exception e) {
            log.error("통합 주문 목록 조회 실패 - 에러: {}", e.getMessage(), e);
            return OrderListResponse.from(List.of(), page, size, 0L);
        }
    }

    // ===== 새로 추가된 고급 조회 API 메소드들 =====

    /**
     * 내 주문 타임라인 조회 (Redis 캐시 적용)
     *
     * [Java 초보자를 위한 가이드]
     *
     * 이 메소드가 하는 일:
     * 1. 특정 사용자의 모든 주문(예약+구매)을 시간순으로 조회
     * 2. 페이지네이션 적용
     * 3. 필터링 조건 적용 (주문 타입, 상태, 기간)
     *
     * 캐시 적용:
     * - 사용자별 주문 목록은 자주 조회되므로 3분간 캐시
     * - 실시간성이 중요하므로 짧은 TTL 적용
     * - 필터 조건별로 다른 캐시 엔트리 생성
     *
     * @param customerId 고객 ID (JWT에서 추출)
     * @param orderType 주문 타입 ("RESERVATION", "PURCHASE", null=전체)
     * @param status 주문 상태 ("PAID", "COMPLETED", null=전체)
     * @param from 조회 시작 시각 (null이면 제한 없음)
     * @param to 조회 종료 시각 (null이면 제한 없음)
     * @param limit 페이지 사이즈
     * @param offset 건너뛸 개수 (페이지네이션)
     * @return 내 주문 타임라인 응답
     */
    @Cacheable(value = "my-orders",
               key = "#customerId + ':' + (#orderType ?: 'ALL') + ':' + (#status ?: 'ALL') + ':' + (#offset ?: 0) + ':' + (#limit ?: 20)",
               condition = "#from == null and #to == null") // 기간 필터가 없을 때만 캐시
    public com.popcorn.order.dto.response.MyOrderTimelineResponse getMyOrderTimeline(
            Long customerId,
            String orderType,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit,
            Long offset) {

        log.info("🕐 고객 주문 타임라인 조회 - 고객: {}, 타입: {}, 상태: {}", customerId, orderType, status);

        try {
            // 1. Pageable 생성 (offset/limit을 페이지로 변환)
            int page = offset != null ? (int) (offset / limit) : 0;
            Pageable pageable = PageRequest.of(page, limit != null ? limit : 20);

            // 2. 사용자별 주문 조회 (기존 메소드 활용)
            Page<Order> orderPage = orderRepository.findOrdersWithAllConditions(
                null, // popupId
                orderType != null ? ItemType.valueOf(orderType) : null,
                status,
                customerId,
                null, // storeId
                from,
                to,
                pageable
            );

            // 3. 응답 DTO로 변환
            List<com.popcorn.order.dto.response.MyOrderTimelineResponse.ItemDto> items = orderPage.getContent().stream()
                .map(this::convertToMyOrderTimelineItem)
                .toList();

            return com.popcorn.order.dto.response.MyOrderTimelineResponse.builder()
                .items(items)
                .page(page + 1) // 사용자에게는 1부터 시작하는 페이지 번호 반환
                .size(limit != null ? limit : 20)
                .total(orderPage.getTotalElements())
                .build();

        } catch (Exception e) {
            log.error("내 주문 타임라인 조회 실패 - 고객: {}, 에러: {}", customerId, e.getMessage(), e);
            return com.popcorn.order.dto.response.MyOrderTimelineResponse.builder()
                .items(List.of())
                .page(1)
                .size(limit != null ? limit : 20)
                .total(0L)
                .build();
        }
    }

    /**
     * 매장별 주문 현황 조회
     *
     * [Java 초보자 설명]
     * 매장 운영자가 "우리 가게에 들어온 주문들"을 확인할 때 사용
     *
     * @param storeId 매장 ID
     * @param popupId 팝업 ID (특정 팝업만 보고 싶을 때)
     * @param scheduleId 스케줄 ID
     * @param orderType 주문 타입
     * @param status 주문 상태
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @param limit 페이지 사이즈
     * @param offset 건너뛸 개수
     * @return 매장 주문 현황
     */
    public com.popcorn.order.dto.response.StoreOrderReservationListResponse getStoreOrderReservations(
            UUID storeId,
            UUID popupId,
            UUID scheduleId,
            String orderType,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit,
            Long offset) {

        log.info("🏪 매장 주문 현황 조회 - 매장: {}, 팝업: {}, 타입: {}", storeId, popupId, orderType);

        try {
            // 1. Pageable 생성
            int page = offset != null ? (int) (offset / limit) : 0;
            Pageable pageable = PageRequest.of(page, limit != null ? limit : 20);

            // 2. 매장별 주문 조회
            Page<Order> orderPage;
            if (popupId != null) {
                // 특정 팝업의 주문만 조회
                orderPage = orderRepository.findOrdersByPopupIdWithConditions(
                    popupId,
                    status,  // String 타입으로 전달
                    orderType,  // String 타입으로 전달
                    pageable
                );
            } else {
                // 매장 전체 주문 조회 - 현재는 storeId 직접 조회가 불가능하므로
                // Store 서비스를 통해 해당 매장의 모든 팝업을 조회한 후 주문을 조회해야 함
                // TODO: Store 서비스에서 매장의 모든 팝업 ID 목록을 가져와서 조회하도록 구현 필요
                log.warn("매장 전체 주문 조회는 현재 구현되지 않음 - storeId: {}", storeId);
                orderPage = Page.empty(pageable);
            }

            // 3. 응답 DTO로 변환
            List<com.popcorn.order.dto.response.StoreOrderReservationListResponse.ItemDto> items = orderPage.getContent().stream()
                .map(this::convertToStoreOrderItem)
                .toList();

            return com.popcorn.order.dto.response.StoreOrderReservationListResponse.builder()
                .items(items)
                .page(page + 1)
                .size(limit != null ? limit : 20)
                .total(orderPage.getTotalElements())
                .build();

        } catch (Exception e) {
            log.error("매장 주문 현황 조회 실패 - 매장: {}, 에러: {}", storeId, e.getMessage(), e);
            return com.popcorn.order.dto.response.StoreOrderReservationListResponse.builder()
                .items(List.of())
                .page(1)
                .size(limit != null ? limit : 20)
                .total(0L)
                .build();
        }
    }

    // ===== 헬퍼 메소드들 (DTO 변환용) =====

    /**
     * Order 엔티티를 MyOrderTimelineResponse.ItemDto로 변환
     *
     * [Java 초보자 설명]
     * 이런 변환 메소드를 만드는 이유:
     * 1. 같은 변환 로직을 여러 곳에서 재사용
     * 2. 코드 중복 방지
     * 3. 변환 로직 변경 시 한 곳만 수정하면 됨
     *
     * Store 서비스 연동:
     * - 이벤트 기반으로 팝업/매장 정보 조회
     * - 서비스 장애 시 기본값을 반환 (Fallback 패턴)
     */
    private com.popcorn.order.dto.response.MyOrderTimelineResponse.ItemDto convertToMyOrderTimelineItem(Order order) {
        // 1. Store 서비스에서 팝업 정보 조회 (매장 정보 포함)
        PopupInfoResponse popupInfo = orderPopupLookupService.getPopupInfo(order.getPopupId())
                .orElseGet(() -> PopupInfoResponse.builder()
                        .popupId(order.getPopupId())
                        .title("팝업 정보를 불러올 수 없습니다")
                        .description("")
                        .storeId(null)
                        .storeInfo(PopupInfoResponse.StoreInfo.builder()
                                .name("매장 정보 없음")
                                .address1("")
                                .address2("")
                                .phoneNumber("")
                                .build())
                        .status("UNKNOWN")
                        .build());

        return com.popcorn.order.dto.response.MyOrderTimelineResponse.ItemDto.builder()
            .type(order.getOrderType().name())
            .id(order.getId())
            .orderNo(order.getOrderNo())
            .status(order.getStatus().name())
            .totalAmount(order.getTotalAmount())
            .cancelableUntil(order.getCancelableUntil())
            .createdAt(order.getCreatedAt())
            .popupId(order.getPopupId())
            .storeId(popupInfo.getStoreId())  // Store 서비스에서 조회한 실제 매장 ID
            .title(popupInfo.getSafeTitle()) // Store 서비스에서 조회한 실제 팝업 제목
            .sessionStartAt(null) // TODO: 방문 예정 시각은 현재 Order 엔티티에 없음. 별도 테이블에서 조회 필요
            .location(popupInfo.getLocationDto()) // Store 서비스에서 조회한 실제 매장 정보
            .build();
    }

    /**
     * Order 엔티티를 StoreOrderReservationListResponse.ItemDto로 변환
     */
    private com.popcorn.order.dto.response.StoreOrderReservationListResponse.ItemDto convertToStoreOrderItem(Order order) {
        return com.popcorn.order.dto.response.StoreOrderReservationListResponse.ItemDto.builder()
            .id(order.getId())
            .reservationNo(order.getOrderNo()) // 주문 번호를 예약 번호로 사용
            .status(order.getStatus().name())
            .totalAmount(order.getTotalAmount())
            .cancelableUntil(order.getCancelableUntil())
            .createdAt(order.getCreatedAt())
            .build();
    }

    /**
     * 팝업별 주문 목록 조회 (관리자용)
     *
     * @param popupId 팝업 ID
     * @param status 주문 상태 필터 (선택사항)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 주문 목록
     */
    public List<OrderListResponse.OrderItemDto> findOrdersByPopup(UUID popupId, String status, int page, int size) {
        log.info("팝업별 주문 목록 조회 - popupId: {}, status: {}, page: {}, size: {}",
                popupId, status, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Order> orderPage;

            if (status != null && !status.trim().isEmpty()) {
                // 상태 필터가 있는 경우
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                orderPage = orderRepository.findByPopupIdAndStatusOrderByCreatedAtDesc(
                        popupId, orderStatus, pageable);
            } else {
                // 전체 주문 조회
                orderPage = orderRepository.findByPopupIdOrderByCreatedAtDesc(popupId, pageable);
            }

            return orderPage.getContent().stream()
                    .map(OrderListResponse.OrderItemDto::fromEntity)
                    .toList();

        } catch (IllegalArgumentException e) {
            log.error("잘못된 주문 상태: {}", status, e);
            throw new IllegalArgumentException("유효하지 않은 주문 상태입니다: " + status);
        } catch (Exception e) {
            log.error("팝업별 주문 목록 조회 실패 - popupId: {}", popupId, e);
            throw new RuntimeException("주문 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 필터를 적용한 전체 주문 조회 (매니저용) → OrderQuery 서비스로 위임
     */
    @Transactional(readOnly = true)
    public List<OrderListResponse.OrderItemDto> findAllOrdersWithFilters(
            String orderType, String paymentMethod, String status, Long userId,
            Integer minAmount, Integer maxAmount, int page, int size,
            String sortBy, String sortDirection) {

        try {
            log.info("📋 매니저용 주문 필터 조회 - 타입: {}, 결제: {}, 상태: {}, 사용자: {} - OrderQuery 서비스로 위임",
                orderType, paymentMethod, status, userId);

            // OrderQuery 서비스에서 고급 필터링 주문 조회
            // 날짜 파라미터 변환 (year, month → startDate, endDate)
            String startDate = null;
            String endDate = null;
            if (minAmount != null && maxAmount != null) {
                // TODO: year, month를 실제 날짜 문자열로 변환하는 로직 추가
                // 현재는 null로 전달
            }

            return orderQueryServiceClient.getAllOrdersWithFilters(
                status, startDate, endDate, userId, minAmount, maxAmount,
                page, size, sortBy, sortDirection);

        } catch (Exception e) {
            log.error("필터링된 주문 조회 실패 - OrderQuery 서비스 호출 오류", e);
            throw new RuntimeException("주문 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 주문 통계 조회 → OrderQuery 서비스로 위임
     */
    @Transactional(readOnly = true)
    public com.popcorn.order.dto.response.OrderStatisticsResponse getOrderStatistics(String period) {
        try {
            log.info("📈 주문 통계 조회 - 기간: {}, OrderQuery 서비스로 위임", period);

            // period를 days로 변환 (기본값: 7일)
            int days = convertPeriodToDays(period);
            String includeTypes = "all";

            // OrderQuery 서비스에서 실제 통계 조회
            return orderQueryServiceClient.getDetailedStatistics(days, includeTypes);

        } catch (Exception e) {
            log.error("주문 통계 조회 실패 - OrderQuery 서비스 호출 오류", e);
            throw new RuntimeException("통계 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 주문 상태별 요약 조회 → OrderQuery 서비스로 위임
     */
    @Transactional(readOnly = true)
    public com.popcorn.order.dto.response.OrderStatusSummaryResponse getOrderStatusSummary() {
        try {
            log.info("📊 주문 상태별 요약 조회 - OrderQuery 서비스로 위임");

            // OrderQuery 서비스에서 실시간 상태별 요약 조회
            return orderQueryServiceClient.getRealtimeStatusSummary();

        } catch (Exception e) {
            log.error("주문 상태별 요약 조회 실패 - OrderQuery 서비스 호출 오류", e);
            throw new RuntimeException("상태별 요약 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * period 문자열을 일 수로 변환하는 헬퍼 메서드
     */
    private int convertPeriodToDays(String period) {
        if (period == null || period.isEmpty()) {
            return 7; // 기본값: 7일
        }

        switch (period.toLowerCase()) {
            case "today":
                return 1;
            case "week":
                return 7;
            case "month":
                return 30;
            case "year":
                return 365;
            case "all":
                return 365; // 전체는 1년으로 제한
            default:
                try {
                    return Integer.parseInt(period);
                } catch (NumberFormatException e) {
                    log.warn("잘못된 period 값: {}, 기본값 7일 사용", period);
                    return 7;
                }
        }
    }
}
