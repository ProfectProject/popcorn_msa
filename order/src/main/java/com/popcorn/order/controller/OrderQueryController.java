package com.popcorn.order.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.popcorn.common.filter.PassportPrincipal;

import com.popcorn.common.dto.BaseResponse;
import com.popcorn.order.dto.query.OrderListQuery;
import com.popcorn.order.dto.response.OrderDetailResponse;
import com.popcorn.order.dto.response.OrderListResponse;
import com.popcorn.order.dto.response.OrderResponseCode;
import com.popcorn.order.dto.response.OrderSummaryResponse;
import com.popcorn.order.dto.payment.PaymentUrlResponse;
import com.popcorn.order.service.cache.OrderCacheService;
import com.popcorn.order.service.core.OrderQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 조회(Query) 전용 컨트롤러 - CQRS 패턴
 *
 * [Java 초보자를 위한 설명]
 *
 * CQRS란?
 * - Command Query Responsibility Segregation의 줄임말
 * - 데이터를 변경하는 작업(Command)과 조회하는 작업(Query)을 분리하는 패턴
 * - 각각의 특성에 맞춰 최적화할 수 있어서 성능과 유지보수성이 좋아짐
 *
 * 왜 분리하나요?
 * 1. 조회는 복잡한 검색 조건과 빠른 성능이 중요
 * 2. 명령은 데이터 일관성과 비즈니스 규칙 검증이 중요
 * 3. 요구사항이 다르므로 분리하면 각각 최적화 가능
 *
 * 이 컨트롤러의 역할:
 * - 주문 상세 조회
 * - 주문 목록 조회 (사용자별, 가게별)
 * - 주문 검색 및 필터링
 * - 주문 생성/수정/삭제는 OrderCommandController에서 담당
 *
 * 사용된 Spring 어노테이션:
 * - RestController: REST API를 제공하는 컨트롤러
 * - @GetMapping: HTTP GET 요청 처리 (데이터 조회용)
 * - @PathVariable: URL 경로에서 변수 추출 (예: /orders/{id})
 * - @RequestParam: 쿼리 파라미터 추출 (예: ?status=PAID)
 * - @PageableDefault: 페이징 기본 설정
 */
@RestController
@RequestMapping("/api/orders/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Query", description = "주문 조회 API (CQRS Query)")
public class OrderQueryController {

    /**
     * 의존성 주입
     * - final 키워드: 객체 생성 후 변경 불가능 (불변성)
     * - RequiredArgsConstructor: final 필드의 생성자를 Lombok이 자동 생성
     * - Spring이 OrderQueryService 구현체를 자동으로 주입해줌
     */
    private final OrderQueryService orderQueryService;
    private final OrderCacheService orderCacheService;

    /**
     * 주문 상세 조회 API
     *
     * 사용 예시: GET /api/orders/v1/12345678-1234-1234-1234-123456789abc
     *
     * 실무 활용:
     * - 고객이 자신의 주문 상태를 확인할 때
     * - 관리자가 특정 주문의 세부 정보를 확인할 때
     * - 모바일 앱의 주문 상세 화면
     */
    @GetMapping("/{orderId}")
    @Operation(
        summary = "주문 상세 조회",
        description = """
            주문 ID로 특정 주문의 상세 정보를 조회합니다.

            포함 정보:
            - 주문 기본 정보 (번호, 상태, 금액, 생성시간)
            - 주문 상품 목록 (수량, 가격)
            - 결제 정보
            - 배송 정보 (구매형 주문의 경우)
            """
    )
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('SYSTEM')")
    public ResponseEntity<BaseResponse<OrderDetailResponse>> getOrder(
            @Parameter(description = "주문 ID", example = "12345678-1234-1234-1234-123456789abc")
            @PathVariable UUID orderId,
            @AuthenticationPrincipal PassportPrincipal principal) {

        // 인증 정보 디버깅
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("🔍 주문 상세 조회 요청 - 주문ID: {}", orderId);
        log.info("🔍 인증 정보: {}", auth);
        if (auth != null) {
            log.info("🔍 Principal: {}", auth.getPrincipal());
            log.info("🔍 Authorities: {}", auth.getAuthorities());
            log.info("🔍 Is Authenticated: {}", auth.isAuthenticated());
        }
        if (principal != null) {
            log.info("🔍 PassportPrincipal - userId: {}, role: {}", principal.userId(), principal.role());
        }

        try {
            // Service 계층에서 실제 조회 로직 수행
            // Controller는 HTTP 처리만, 비즈니스 로직은 Service에서
            Optional<OrderDetailResponse> orderOpt = orderQueryService.findOrderById(orderId);

            // Optional을 사용하는 이유: null 체크를 명확하고 안전하게 처리
            if (orderOpt.isEmpty()) {
                log.warn("주문을 찾을 수 없음 - 주문ID: {}", orderId);

                BaseResponse<OrderDetailResponse> notFoundResponse = BaseResponse.from(
                        OrderResponseCode.ORDER_NOT_FOUND, null);

                return ResponseEntity.status(OrderResponseCode.ORDER_NOT_FOUND.getHttpStatus())
                        .body(notFoundResponse);
            }

            OrderDetailResponse response = orderOpt.get();
            log.info("주문 조회 완료 - 주문번호: {}", response.getOrderNo());

            // 성공 응답 생성
            BaseResponse<OrderDetailResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            // 예상치 못한 에러 발생 시 처리
            log.error("주문 조회 중 오류 발생 - 주문ID: {}", orderId, e);

            BaseResponse<OrderDetailResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 결제 URL 조회 API (예약 성공 이후 제공)
     */
    @GetMapping("/{orderId}/payment-url")
    @Operation(summary = "결제 URL 조회", description = "예약 성공 이후 생성된 결제 URL을 조회합니다.")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('SYSTEM')")
    public ResponseEntity<BaseResponse<PaymentUrlResponse>> getPaymentUrl(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal PassportPrincipal principal) {

        PaymentUrlResponse paymentUrl = orderCacheService.getPaymentUrl(orderId);
        if (paymentUrl == null) {
            return ResponseEntity.status(OrderResponseCode.ORDER_NOT_FOUND.getHttpStatus())
                    .body(BaseResponse.of(
                            OrderResponseCode.ORDER_NOT_FOUND.getCode(),
                            OrderResponseCode.ORDER_NOT_FOUND.getMessage(),
                            null
                    ));
        }
        return ResponseEntity.ok(BaseResponse.from(OrderResponseCode.ORDER_RETRIEVED, paymentUrl));
    }

    /**
     * 사용자별 주문 목록 조회 API (Store 서비스 방식 pagination)
     *
     * 사용 예시: GET /api/orders/v1/users/123?page=1&size=20&withTotal=true
     *
     * 실무 활용:
     * - 마이페이지의 "내 주문 내역" 화면
     * - 무한 스크롤 구현 시 페이지별 데이터 로드
     * - 대량의 주문 데이터를 효율적으로 처리
     */
    @GetMapping("/users/{userId}")
    @Operation(
        summary = "사용자별 주문 목록 조회 (Store 서비스 방식)",
        description = """
            특정 사용자의 주문 목록을 페이지별로 조회합니다.

            페이징 파라미터 (Store 서비스와 동일):
            - page: 페이지 번호 (1부터 시작)
            - size: 한 페이지당 항목 수 (기본 20개)
            - withTotal: 전체 개수 포함 여부 (기본 true)

            응답 정보:
            - items: 실제 주문 데이터 배열
            - page: 현재 페이지 번호
            - size: 페이지 크기
            - total: 전체 주문 개수
            """
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<BaseResponse<OrderListResponse>> getOrdersByUserId(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId,

            @Parameter(description = "주문 상태 필터", example = "PAID")
            @RequestParam(required = false) String status,

            @Parameter(description = "페이지(기본 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,

            @Parameter(description = "사이즈(기본 20, 최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,

            @Parameter(description = "전체 개수 포함 여부(기본 true)", example = "true")
            @RequestParam(required = false, defaultValue = "true") Boolean withTotal) {

        log.info("사용자별 주문 목록 조회 - 사용자ID: {}, 상태: {}, 페이지: {}, 사이즈: {}",
                userId, status, page, size);

        try {
            OrderListQuery query = OrderListQuery.builder()
                    .userId(userId)
                    .status(status)
                    .page(page)
                    .size(size)
                    .withTotal(withTotal)
                    .build();

            OrderListResponse response = orderQueryService.findOrdersWithQuery(query);

            log.info("사용자 주문 목록 조회 완료 - 사용자ID: {}, 조회된 주문: {}개",
                    userId, response.getItems().size());

            BaseResponse<OrderListResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("사용자 주문 목록 조회 중 오류 발생 - 사용자ID: {}", userId, e);

            BaseResponse<OrderListResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 가게별 주문 목록 조회 API (Store 서비스 방식)
     *
     * 사용 예시: GET /api/orders/v1/stores/store-uuid?status=PAID&from=2024-01-01T00:00:00&page=1&size=20
     *
     * 실무 활용:
     * - 가게 사장님의 매출 현황 확인
     * - 특정 기간의 주문만 필터링
     * - 상태별 주문 관리 (결제대기, 완료 등)
     */
    @GetMapping("/stores/{storeId}")
    @Operation(
        summary = "가게별 주문 목록 조회 (Store 서비스 방식)",
        description = """
            특정 가게의 주문 목록을 다양한 조건으로 검색합니다.

            검색 조건:
            - status: 주문 상태 필터 (REQUESTED, PAID, COMPLETED 등)
            - orderType: 주문 타입 (RESERVATION, GOODS, MIXED)
            - from/to: 기간 검색 (ISO 8601 형식)
            - page: 페이지 번호 (1부터 시작)
            - size: 페이지 크기 (기본 20개)
            - withTotal: 전체 개수 포함 여부

            관리자 기능:
            - 실시간 주문 현황 모니터링
            - 기간별 매출 분석
            - 상태별 주문 처리 현황
            """
    )
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER')")
    public ResponseEntity<BaseResponse<OrderListResponse>> getOrdersByStoreId(
            @Parameter(description = "가게 ID")
            @PathVariable UUID storeId,

            @Parameter(description = "주문 상태 필터", example = "PAID")
            @RequestParam(required = false) String status,

            @Parameter(description = "주문 타입 필터", example = "RESERVATION")
            @RequestParam(required = false) String orderType,

            @Parameter(description = "검색 시작 날짜")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @Parameter(description = "검색 종료 날짜")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @Parameter(description = "페이지(기본 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,

            @Parameter(description = "사이즈(기본 20, 최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,

            @Parameter(description = "전체 개수 포함 여부(기본 true)", example = "true")
            @RequestParam(required = false, defaultValue = "true") Boolean withTotal) {

        log.info("가게별 주문 목록 조회 - 가게ID: {}, 상태: {}, 타입: {}, 기간: {} ~ {}, 페이지: {}",
                storeId, status, orderType, from, to, page);

        try {
            OrderListQuery query = OrderListQuery.builder()
                    .storeId(storeId)
                    .status(status)
                    .orderType(orderType)
                    .from(from)
                    .to(to)
                    .page(page)
                    .size(size)
                    .withTotal(withTotal)
                    .build();

            OrderListResponse response = orderQueryService.findOrdersWithQuery(query);

            log.info("가게 주문 목록 조회 완료 - 가게ID: {}, 조회된 주문: {}개",
                    storeId, response.getItems().size());

            BaseResponse<OrderListResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("가게 주문 목록 조회 중 오류 발생 - 가게ID: {}", storeId, e);

            BaseResponse<OrderListResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 팝업별 주문 목록 조회 API (Store 서비스 pagination 방식)
     *
     * 사용 예시: GET /api/orders/v1/popups/popup-uuid?page=1&size=20&status=PAID
     *
     * 실무 활용:
     * - 특정 팝업의 주문 현황 확인
     * - 팝업별 매출 분석
     * - 예약 현황 모니터링
     */
    @GetMapping("/popups/{popupId}")
    @Operation(
        summary = "팝업별 주문 목록 조회",
        description = """
            특정 팝업의 주문 목록을 조회합니다.

            주요 기능:
            - 팝업별 주문 필터링
            - 상태별 필터링 (REQUESTED, PAID, COMPLETED 등)
            - 주문 타입별 필터링 (RESERVATION, GOODS, MIXED)
            - 페이징 처리 (기본 20개, 최대 100개)

            사용 예시:
            - 전체 조회: /api/orders/v1/popups/{popupId}
            - 상태 필터: /api/orders/v1/popups/{popupId}?status=PAID
            - 페이징: /api/orders/v1/popups/{popupId}?page=1&size=10
            """
    )
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER')")
    public ResponseEntity<BaseResponse<OrderListResponse>> getOrdersByPopupId(
            @Parameter(description = "팝업 ID", example = "00000000-0000-0000-0000-000000000101")
            @PathVariable UUID popupId,

            @Parameter(description = "주문 상태 필터", example = "PAID")
            @RequestParam(required = false) String status,

            @Parameter(description = "주문 타입 필터", example = "RESERVATION")
            @RequestParam(required = false) String orderType,

            @Parameter(description = "페이지(기본 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,

            @Parameter(description = "사이즈(기본 20, 최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,

            @Parameter(description = "전체 개수 포함 여부(기본 true)", example = "true")
            @RequestParam(required = false, defaultValue = "true") Boolean withTotal) {

        log.info("팝업별 주문 목록 조회 - 팝업ID: {}, 상태: {}, 타입: {}, 페이지: {}, 사이즈: {}",
                popupId, status, orderType, page, size);

        try {
            OrderListQuery query = OrderListQuery.builder()
                    .popupId(popupId)
                    .status(status)
                    .orderType(orderType)
                    .page(page)
                    .size(size)
                    .withTotal(withTotal)
                    .build();

            OrderListResponse response = orderQueryService.findOrdersByPopupId(query);

            log.info("팝업 주문 목록 조회 완료 - 팝업ID: {}, 조회된 주문: {}개",
                    popupId, response.getItems().size());

            BaseResponse<OrderListResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("팝업 주문 목록 조회 중 오류 발생 - 팝업ID: {}", popupId, e);

            BaseResponse<OrderListResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 카테고리별 주문 목록 조회 API
     *
     * 사용 예시: GET /api/orders/v1/categories/RESERVATION?page=1&size=20
     *
     * 실무 활용:
     * - 예약형 vs 구매형 주문 분석
     * - 카테고리별 매출 현황
     * - 비즈니스 성과 측정
     */
    @GetMapping("/categories/{category}")
    @Operation(
        summary = "카테고리별 주문 목록 조회",
        description = """
            주문 타입별로 주문 목록을 조회합니다.

            주문 카테고리:
            - RESERVATION: 예약형 주문 (팝업 체험)
            - GOODS: 구매형 주문 (굿즈 구매)
            - MIXED: 혼합형 주문 (예약 + 굿즈)

            검색 조건:
            - category: 주문 타입 (필수)
            - status: 주문 상태 필터
            - userId: 특정 사용자 필터
            - 페이징 처리
            """
    )
    public ResponseEntity<BaseResponse<OrderListResponse>> getOrdersByCategory(
            @Parameter(description = "주문 카테고리(RESERVATION/GOODS/MIXED)", example = "RESERVATION")
            @PathVariable String category,

            @Parameter(description = "주문 상태 필터", example = "PAID")
            @RequestParam(required = false) String status,

            @Parameter(description = "사용자 ID 필터", example = "123")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "검색 시작 날짜")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @Parameter(description = "검색 종료 날짜")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @Parameter(description = "페이지(기본 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,

            @Parameter(description = "사이즈(기본 20, 최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,

            @Parameter(description = "전체 개수 포함 여부(기본 true)", example = "true")
            @RequestParam(required = false, defaultValue = "true") Boolean withTotal) {

        log.info("카테고리별 주문 목록 조회 - 카테고리: {}, 상태: {}, 사용자ID: {}, 페이지: {}, 사이즈: {}",
                category, status, userId, page, size);

        try {
            OrderListQuery query = OrderListQuery.builder()
                    .orderType(category)
                    .status(status)
                    .userId(userId)
                    .from(from)
                    .to(to)
                    .page(page)
                    .size(size)
                    .withTotal(withTotal)
                    .build();

            OrderListResponse response = orderQueryService.findOrdersByCategory(query);

            log.info("카테고리 주문 목록 조회 완료 - 카테고리: {}, 조회된 주문: {}개",
                    category, response.getItems().size());

            BaseResponse<OrderListResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("카테고리 주문 목록 조회 중 오류 발생 - 카테고리: {}", category, e);

            BaseResponse<OrderListResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    // ===== 새로 추가된 고급 조회 API들 =====

    /**
     * 내 주문 목록 조회 (타임라인 형태)
     *
     * [Java 초보자를 위한 가이드]
     *
     * 이 API가 하는 일:
     * - 로그인한 사용자의 모든 주문(예약+구매)를 시간순으로 보여줌
     * - 모바일 앱에서 "내 주문 내역" 화면에 사용
     * - 필터링 옵션: 주문 타입, 상태, 기간
     * - 페이지네이션 지원
     *
     * URL 예시:
     * GET /api/orders/v1/me?page=1&size=20
     * GET /api/orders/v1/me?orderType=RESERVATION&status=PAID
     */
    @GetMapping("/me")
    @Operation(
        summary = "내 주문 타임라인 조회",
        description = """
            로그인한 사용자의 모든 주문을 시간순으로 조회합니다.

            ## 📱 사용 용도
            - 모바일 앱 "내 주문 내역" 화면
            - 마이페이지 주문 목록
            - 고객 지원을 위한 주문 이력 확인

            ## 🔍 필터링 옵션
            - **orderType**: 주문 타입 ("RESERVATION" | "PURCHASE" | "ALL")
            - **status**: 주문 상태 ("REQUESTED" | "PAID" | "COMPLETED" | "CANCELLED")
            - **from/to**: 기간별 조회

            ## 📄 응답 데이터
            - 주문 기본 정보 (번호, 상태, 금액)
            - 팝업/매장 정보 (제목, 위치)
            - 예약 정보 (방문 예정 시각)
            - 취소 가능 여부 및 시한

            ## 🎯 Java 초보자 학습 포인트
            - JWT에서 사용자 ID 추출하는 방법
            - 페이지네이션 처리 방법
            - Optional 파라미터 처리 방법
            """
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BaseResponse<com.popcorn.order.dto.response.MyOrderTimelineResponse>> getMyOrders(
            @Parameter(description = "주문 타입", example = "ALL")
            @RequestParam(required = false, defaultValue = "ALL") String orderType,
            @Parameter(description = "주문 상태", example = "PAID")
            @RequestParam(required = false) String status,
            @Parameter(description = "조회 시작 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime from,
            @Parameter(description = "조회 종료 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime to,
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal PassportPrincipal principal) {

        try {
            // 1. JWT에서 사용자 ID 추출 (PassportPrincipal 사용)
            Long customerId;
            if (principal != null) {
                customerId = principal.userId();
            } else {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                customerId = extractUserIdFromAuthentication(authentication);
            }

            log.info("🎯 JWT에서 추출된 사용자 ID: {}", customerId);

            // 2. 파라미터 정규화
            String normalizedItemType = "ALL".equalsIgnoreCase(orderType) ? null : orderType;
            String normalizedStatus = (status == null || "ALL".equalsIgnoreCase(status)) ? null : status;

            // 3. page/size를 offset/limit으로 변환
            Integer limit = size;
            Long offset = (long) (page - 1) * size;

            // 4. 서비스 호출
            com.popcorn.order.dto.response.MyOrderTimelineResponse response = orderQueryService.getMyOrderTimeline(
                customerId, normalizedItemType, normalizedStatus, from, to, limit, offset
            );

            log.info("내 주문 타임라인 조회 완료 - 사용자: {}, 조회된 주문: {}개",
                    customerId, response.getItems().size());

            // 5. 응답 생성
            BaseResponse<com.popcorn.order.dto.response.MyOrderTimelineResponse> baseResponse =
                BaseResponse.from(OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("내 주문 타임라인 조회 실패", e);

            BaseResponse<com.popcorn.order.dto.response.MyOrderTimelineResponse> errorResponse =
                BaseResponse.from(OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    private Long extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("⚠️ 인증 정보가 없습니다. JWT 토큰이 없거나 유효하지 않습니다.");
            throw new IllegalArgumentException("인증 정보가 없습니다. JWT 토큰을 확인하세요.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof PassportPrincipal passportPrincipal) {
            return passportPrincipal.userId();
        }
        if (principal instanceof com.popcorn.common.security.PassportPrincipal passportPrincipal) {
            return passportPrincipal.getUserId();
        }

        String userIdStr = authentication.getName();
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            log.warn("⚠️ Authentication에서 사용자 ID를 찾을 수 없습니다.");
            throw new IllegalArgumentException("JWT 토큰에서 사용자 ID를 추출할 수 없습니다.");
        }

        try {
            Long userId = Long.parseLong(userIdStr);
            log.debug("🔐 JWT에서 사용자 ID 추출 완료: {} (권한: {})",
                userId, authentication.getAuthorities());
            return userId;
        } catch (NumberFormatException e) {
            log.error("💥 JWT 토큰의 사용자 ID 형식이 잘못되었습니다. 값: {}", userIdStr, e);
            throw new IllegalArgumentException("JWT 토큰의 사용자 ID 형식이 올바르지 않습니다.", e);
        }
    }

    /**
     * 매장별 주문 현황 조회
     *
     * [Java 초보자를 위한 가이드]
     *
     * 이 API가 하는 일:
     * - 매장 운영자가 자기 가게에 들어온 주문들을 확인
     * - 팝업별로 필터링 가능
     * - 주문 상태별로 관리 가능
     */
    @GetMapping("/store")
    @Operation(
        summary = "매장별 주문 현황 조회",
        description = """
            매장 운영자가 자신의 매장에 들어온 주문 현황을 조회합니다.

            ## 🏪 사용 용도
            - 매장 관리 대시보드
            - 일일 주문 현황 확인
            - 팝업별 주문 관리

            ## 🔍 필터링 옵션
            - **storeId**: 매장 ID (필수)
            - **popupId**: 특정 팝업만 조회 (선택적)
            - **orderType**: 주문 타입
            - **status**: 주문 상태
            """
    )
    public ResponseEntity<BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse>> getStoreOrders(
            @Parameter(description = "매장 ID (매장 운영자는 JWT에서 자동 추출)")
            @RequestParam(required = false) UUID storeId,
            @Parameter(description = "팝업 ID (특정 팝업만 조회시)")
            @RequestParam(required = false) UUID popupId,
            @Parameter(description = "주문 타입")
            @RequestParam(required = false) String orderType,
            @Parameter(description = "주문 상태")
            @RequestParam(required = false, defaultValue = "REQUESTED") String status,
            @Parameter(description = "조회 시작 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime from,
            @Parameter(description = "조회 종료 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime to,
            @Parameter(description = "페이지 번호")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지 크기")
            @RequestParam(required = false, defaultValue = "20") Integer size,
            org.springframework.security.core.Authentication authentication) {

        // 1. 매장 ID 처리 - storeId가 없으면 JWT에서 추출 (매장 운영자인 경우)
        UUID effectiveStoreId = storeId;
        if (effectiveStoreId == null) {
            // TODO: JWT에서 매장 ID 추출 로직 구현 필요
            // 현재는 기본 매장 ID 사용
            effectiveStoreId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            log.info("🏪 매장 ID가 제공되지 않음. 기본 매장 ID 사용: {}", effectiveStoreId);
        }

        try {

            log.info("매장 주문 현황 조회 - 매장: {}, 팝업: {}", effectiveStoreId, popupId);

            // 1. page/size를 offset/limit으로 변환
            Integer limit = size;
            Long offset = (long) (page - 1) * size;

            // 2. 서비스 호출
            com.popcorn.order.dto.response.StoreOrderReservationListResponse response =
                orderQueryService.getStoreOrderReservations(
                    effectiveStoreId, popupId, null, orderType, status, from, to, limit, offset
                );

            log.info("매장 주문 현황 조회 완료 - 매장: {}, 조회된 주문: {}개",
                    effectiveStoreId, response.getItems().size());

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> baseResponse =
                BaseResponse.from(OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("매장 주문 현황 조회 실패 - 매장: {}", effectiveStoreId, e);

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> errorResponse =
                BaseResponse.from(OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 팝업별 예약 주문 목록 조회
     *
     * [Java 초보자를 위한 가이드]
     *
     * 이 API와 구매 API의 차이점:
     * - 예약: 시간 지정해서 방문하는 주문 (팝업 체험)
     * - 구매: 상품을 사서 가져가는 주문 (굿즈 구매)
     */
    @GetMapping("/popup/{popupId}/reservations")
    @Operation(
        summary = "팝업별 예약 주문 목록 조회",
        description = """
            특정 팝업의 예약형 주문만 조회합니다.

            ## 📅 예약형 주문이란?
            - 시간을 지정해서 팝업을 체험하는 주문
            - 예: 팝콘 만들기 체험 오후 2시 예약

            ## 🎯 사용 용도
            - 팝업 운영자가 예약 현황 확인
            - 시간대별 예약 관리
            - 노쇼(No-show) 관리
            """
    )
    public ResponseEntity<BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse>> getPopupReservationOrders(
            @Parameter(description = "팝업 ID", example = "00000000-0000-0000-0000-000000000101")
            @PathVariable UUID popupId,
            @Parameter(description = "주문 상태")
            @RequestParam(required = false) String status,
            @Parameter(description = "조회 시작 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime from,
            @Parameter(description = "조회 종료 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime to,
            @Parameter(description = "페이지 번호")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지 크기")
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        try {
            log.info("팝업 예약 주문 조회 - 팝업: {}, 상태: {}", popupId, status);

            // 1. page/size를 offset/limit으로 변환
            Integer limit = size;
            Long offset = (long) (page - 1) * size;

            // 2. 서비스 호출 (orderType을 "RESERVATION"으로 고정)
            com.popcorn.order.dto.response.StoreOrderReservationListResponse response =
                orderQueryService.getStoreOrderReservations(
                    null, // storeId는 null (팝업 ID로만 조회)
                    popupId,
                    null, // scheduleId
                    "RESERVATION", // 예약형 주문만 조회
                    status,
                    from,
                    to,
                    limit,
                    offset
                );

            log.info("팝업 예약 주문 조회 완료 - 팝업: {}, 조회된 주문: {}개",
                    popupId, response.getItems().size());

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> baseResponse =
                BaseResponse.from(OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("팝업 예약 주문 조회 실패 - 팝업: {}", popupId, e);

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> errorResponse =
                BaseResponse.from(OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 팝업별 구매 주문 목록 조회
     *
     * [Java 초보자를 위한 가이드]
     *
     * 예약 API와의 차이점:
     * - 예약: 체험 시간을 예약하는 주문
     * - 구매: 굿즈를 사서 가져가는 주문
     */
    @GetMapping("/popup/{popupId}/purchases")
    @Operation(
        summary = "팝업별 구매 주문 목록 조회",
        description = """
            특정 팝업의 구매형 주문만 조회합니다.

            ## 🛒 구매형 주문이란?
            - 굿즈나 상품을 구매하는 주문
            - 예: 팝콘 굿즈, 브랜드 상품 구매
            - 배송 또는 픽업으로 받는 주문

            ## 🎯 사용 용도
            - 팝업 운영자가 상품 판매 현황 확인
            - 재고 관리
            - 매출 분석
            """
    )
    public ResponseEntity<BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse>> getPopupPurchaseOrders(
            @Parameter(description = "팝업 ID", example = "00000000-0000-0000-0000-000000000101")
            @PathVariable UUID popupId,
            @Parameter(description = "주문 상태")
            @RequestParam(required = false) String status,
            @Parameter(description = "조회 시작 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime from,
            @Parameter(description = "조회 종료 시각")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime to,
            @Parameter(description = "페이지 번호")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지 크기")
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        try {
            log.info("팝업 구매 주문 조회 - 팝업: {}, 상태: {}", popupId, status);

            // 1. page/size를 offset/limit으로 변환
            Integer limit = size;
            Long offset = (long) (page - 1) * size;

            // 2. 서비스 호출 (orderType을 "PURCHASE"으로 고정)
            com.popcorn.order.dto.response.StoreOrderReservationListResponse response =
                orderQueryService.getStoreOrderReservations(
                    null, // storeId는 null (팝업 ID로만 조회)
                    popupId,
                    null, // scheduleId
                    "PURCHASE", // 구매형 주문만 조회
                    status,
                    from,
                    to,
                    limit,
                    offset
                );

            log.info("팝업 구매 주문 조회 완료 - 팝업: {}, 조회된 주문: {}개",
                    popupId, response.getItems().size());

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> baseResponse =
                BaseResponse.from(OrderResponseCode.ORDER_LIST_RETRIEVED, response);

            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("팝업 구매 주문 조회 실패 - 팝업: {}", popupId, e);

            BaseResponse<com.popcorn.order.dto.response.StoreOrderReservationListResponse> errorResponse =
                BaseResponse.from(OrderResponseCode.DATABASE_ERROR, null);

            return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                    .body(errorResponse);
        }
    }
}
