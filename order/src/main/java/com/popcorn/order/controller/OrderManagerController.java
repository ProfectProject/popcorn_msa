package com.popcorn.order.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.popcorn.order.annotation.CheckAuth;

import com.popcorn.common.dto.BaseResponse;
import com.popcorn.common.dto.BaseError;
import com.popcorn.common.dto.CommonResponseCode;
import com.popcorn.order.dto.response.OrderDetailResponse;
import com.popcorn.order.dto.response.OrderListResponse;
import com.popcorn.order.dto.request.OrderStatusUpdateRequest;
import com.popcorn.order.dto.response.OrderStatusUpdateResponse;
import com.popcorn.order.dto.response.OrderStatisticsResponse;
import com.popcorn.order.dto.response.OrderStatusSummaryResponse;
import com.popcorn.order.service.core.OrderQueryService;
import com.popcorn.order.service.core.OrderCommandService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매니저/오너 전용 주문 관리 컨트롤러
 *
 * [권한 요구사항]
 * - MANAGER: 권한 범위 내 팝업의 주문 관리
 * - OWNER: 본인 스토어의 모든 주문 관리
 *
 * [주요 기능]
 * - 가게별 주문 목록 조회
 * - 주문 상태 변경 (MANAGER/OWNER만 가능)
 * - 주문 상세 정보 조회
 */
@RestController
@RequestMapping("/api/orders/v1/manager")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Manager", description = "매니저/오너 전용 주문 관리 API")
@SecurityRequirement(name = "bearer-token")
public class OrderManagerController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    /**
     * 가게별 주문 목록 조회 (매니저/오너 전용)
     */
    @GetMapping("/popup/{popupId}/orders")
    @CheckAuth(roles = {"MANAGER", "OWNER"}, resourceType = "POPUP", resourceParam = "popupId")
    @Operation(
        summary = "가게별 주문 목록 조회",
        description = """
            특정 팝업의 주문 목록을 조회합니다. (매니저/오너 전용)

            권한 요구사항:
            - MANAGER: 권한 범위 내 팝업만 조회 가능
            - OWNER: 본인 스토어의 팝업만 조회 가능

            조회 옵션:
            - 주문 상태별 필터링
            - 날짜 범위 필터링
            - 페이징 처리
            """
    )
    public ResponseEntity<BaseResponse<List<OrderListResponse.OrderItemDto>>> getPopupOrders(
            @Parameter(description = "팝업 ID", required = true)
            @PathVariable UUID popupId,

            @Parameter(description = "주문 상태 필터 (선택사항)")
            @RequestParam(required = false) String status,

            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,

            Authentication authentication) {

        log.info("팝업 주문 목록 조회 - popupId: {}, status: {}, page: {}, size: {}",
                popupId, status, page, size);

        try {
            // 권한이 검증된 후 실제 주문 목록 조회
            List<OrderListResponse.OrderItemDto> orders = orderQueryService.findOrdersByPopup(
                    popupId, status, page, size);

            BaseResponse<List<OrderListResponse.OrderItemDto>> response = BaseResponse.success(orders);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("팝업 주문 목록 조회 실패 - popupId: {}", popupId, e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(
                    CommonResponseCode.INTERNAL_ERROR,
                    "주문 목록 조회에 실패했습니다.");
            return ResponseEntity.status(500).body((BaseResponse) errorResponse);
        }
    }

    /**
     * 주문 상태 변경 (매니저/오너 전용)
     */
    @PatchMapping("/{orderId}/status")
    @CheckAuth(roles = {"MANAGER", "OWNER"}, resourceType = "ORDER", resourceParam = "orderId")
    @Operation(
        summary = "주문 상태 변경",
        description = """
            주문의 상태를 변경합니다. (매니저/오너 전용)

            권한 요구사항:
            - MANAGER: 권한 범위 내 주문만 상태 변경 가능
            - OWNER: 본인 스토어의 주문만 상태 변경 가능

            상태 변경 규칙:
            - REQUESTED → ACCEPTED, REJECTED
            - ACCEPTED → RESERVED, PAYMENT_PENDING
            - RESERVED → PAID, CANCELLED
            - PAYMENT_PENDING → PAID, CANCELLED
            - PAID → COMPLETED, CANCELLED
            """
    )
    public ResponseEntity<BaseResponse<OrderStatusUpdateResponse>> updateOrderStatus(
            @Parameter(description = "주문 ID", required = true)
            @PathVariable UUID orderId,

            @Valid @RequestBody OrderStatusUpdateRequest request,
            Authentication authentication) {

        log.info("주문 상태 변경 요청 - orderId: {}, status: {}, reason: {}",
                orderId, request.getStatus(), request.getReason());

        try {
            // 권한이 검증된 후 실제 상태 변경 로직 호출
            orderCommandService.updateOrderStatus(orderId, request.getStatus(), request.getReason());

            OrderStatusUpdateResponse response = OrderStatusUpdateResponse.builder()
                    .orderId(orderId)
                    .status(request.getStatus())
                    .reason(request.getReason())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

            BaseResponse<OrderStatusUpdateResponse> baseResponse = BaseResponse.success(response);
            return ResponseEntity.ok(baseResponse);

        } catch (Exception e) {
            log.error("주문 상태 변경 실패 - orderId: {}", orderId, e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(CommonResponseCode.INTERNAL_ERROR, "주문 상태 변경에 실패했습니다.");
            return ResponseEntity.status(500).body((BaseResponse) errorResponse);
        }
    }

    /**
     * 📊 전체 주문 목록 조회 - 대시보드용 (매니저/오너 전용)
     */
    @GetMapping("/dashboard/orders")
    @CheckAuth(roles = {"MANAGER", "OWNER"})
    @Operation(
        summary = "📊 전체 주문 목록 조회 - 대시보드용",
        description = """
            전체 주문 목록을 조회합니다. 관리자 대시보드에서 사용됩니다.

            ✨ 주요 기능:
            - 모든 주문 내역 조회 (페이지네이션)
            - 주문 상태별 필터링
            - 날짜 범위별 필터링
            - 사용자별 필터링
            - 금액 범위별 필터링

            🔍 필터 옵션:
            - status: 주문 상태 (PENDING, CONFIRMED, COMPLETED, CANCELLED)
            - startDate: 시작 날짜 (yyyy-MM-dd)
            - endDate: 종료 날짜 (yyyy-MM-dd)
            - userId: 사용자 ID
            - minAmount: 최소 주문 금액
            - maxAmount: 최대 주문 금액

            📈 정렬 옵션:
            - createdAt: 주문일시 (기본값)
            - totalAmount: 주문금액
            - status: 주문상태
            """
    )
    public ResponseEntity<BaseResponse<List<OrderListResponse.OrderItemDto>>> getAllOrders(
            @Parameter(description = "주문 상태 필터", example = "PENDING")
            @RequestParam(required = false) String status,

            @Parameter(description = "시작 날짜 (yyyy-MM-dd)", example = "2026-01-01")
            @RequestParam(required = false) String startDate,

            @Parameter(description = "종료 날짜 (yyyy-MM-dd)", example = "2026-12-31")
            @RequestParam(required = false) String endDate,

            @Parameter(description = "사용자 ID")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "최소 주문 금액")
            @RequestParam(required = false) Integer minAmount,

            @Parameter(description = "최대 주문 금액")
            @RequestParam(required = false) Integer maxAmount,

            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "정렬 기준", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "정렬 방향", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,

            Authentication authentication) {

        log.info("📊 전체 주문 목록 조회 - status: {}, dateRange: {} ~ {}, userId: {}, amountRange: {} ~ {}, page: {}, size: {}",
                status, startDate, endDate, userId, minAmount, maxAmount, page, size);

        try {
            // 페이지 크기 제한 (최대 100개)
            int limitedSize = Math.min(size, 100);

            // 전체 주문 목록 조회 (필터링 포함)
            List<OrderListResponse.OrderItemDto> orders = orderQueryService.findAllOrdersWithFilters(
                    status, startDate, endDate, userId, minAmount, maxAmount,
                    page, limitedSize, sortBy, sortDirection);

            BaseResponse<List<OrderListResponse.OrderItemDto>> response = BaseResponse.success(orders);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("전체 주문 목록 조회 실패", e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(
                    CommonResponseCode.INTERNAL_ERROR,
                    "주문 목록 조회에 실패했습니다: " + e.getMessage());
            return ResponseEntity.status(500).body((BaseResponse) errorResponse);
        }
    }

    /**
     * 📈 주문 통계 대시보드 (매니저/오너 전용)
     */
    @GetMapping("/dashboard/statistics")
    @CheckAuth(roles = {"MANAGER", "OWNER"})
    @Operation(
        summary = "📈 주문 통계 대시보드",
        description = """
            주문 관련 통계 정보를 제공합니다. 관리자 대시보드에서 사용됩니다.

            📊 제공 통계:
            - 총 주문 수
            - 주문 상태별 카운트 및 비율
            - 총 매출 금액
            - 평균 주문 금액
            - 오늘/이번 주/이번 달 주문 수
            - 최근 7일간 일별 주문 추이
            - 인기 상품 TOP 5

            🔄 실시간 업데이트:
            - 캐시를 통한 빠른 응답 (5분 캐시)
            - 실시간 데이터 반영
            """
    )
    public ResponseEntity<BaseResponse<OrderStatisticsResponse>> getOrderStatistics(
            @Parameter(description = "통계 기간 (today, week, month, all)", example = "all")
            @RequestParam(defaultValue = "all") String period,

            Authentication authentication) {

        log.info("📈 주문 통계 조회 - period: {}", period);

        try {
            // 주문 통계 데이터 조회
            OrderStatisticsResponse statistics = orderQueryService.getOrderStatistics(period);

            BaseResponse<OrderStatisticsResponse> response = BaseResponse.success(statistics);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("주문 통계 조회 실패", e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(
                    CommonResponseCode.INTERNAL_ERROR,
                    "주문 통계 조회에 실패했습니다: " + e.getMessage());
            return ResponseEntity.status(500).body((BaseResponse) errorResponse);
        }
    }

    /**
     * 📋 주문 상태별 요약 정보 (매니저/오너 전용)
     */
    @GetMapping("/dashboard/status-summary")
    @CheckAuth(roles = {"MANAGER", "OWNER"})
    @Operation(
        summary = "📋 주문 상태별 요약 정보",
        description = """
            주문 상태별 요약 정보를 제공합니다.

            📋 제공 정보:
            - 각 상태별 주문 수
            - 상태별 비율
            - 각 상태의 최신 주문 5개

            🚀 빠른 현황 파악:
            - 처리 대기 중인 주문 수
            - 완료된 주문 수
            - 취소된 주문 수
            - 결제 대기 중인 주문 수
            """
    )
    public ResponseEntity<BaseResponse<OrderStatusSummaryResponse>> getOrderStatusSummary(
            Authentication authentication) {

        log.info("📋 주문 상태별 요약 정보 조회");

        try {
            // 주문 상태별 요약 정보 조회
            OrderStatusSummaryResponse summary = orderQueryService.getOrderStatusSummary();

            BaseResponse<OrderStatusSummaryResponse> response = BaseResponse.success(summary);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("주문 상태별 요약 조회 실패", e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(
                    CommonResponseCode.INTERNAL_ERROR,
                    "주문 상태별 요약 조회에 실패했습니다: " + e.getMessage());
            return ResponseEntity.status(500).body((BaseResponse) errorResponse);
        }
    }

    /**
     * 주문 상세 정보 조회 (매니저/오너 전용)
     */
    @GetMapping("/{orderId}")
    @CheckAuth(roles = {"MANAGER", "OWNER"}, resourceType = "ORDER", resourceParam = "orderId")
    @Operation(
        summary = "주문 상세 정보 조회",
        description = """
            특정 주문의 상세 정보를 조회합니다. (매니저/오너 전용)

            포함 정보:
            - 주문 기본 정보
            - 주문 항목 목록
            - 결제 정보
            - 배송 정보 (굿즈 주문의 경우)
            - 상태 변경 이력
            """
    )
    public ResponseEntity<BaseResponse<OrderDetailResponse>> getOrderDetail(
            @Parameter(description = "주문 ID", required = true)
            @PathVariable UUID orderId,

            Authentication authentication) {

        log.info("주문 상세 조회 - orderId: {}", orderId);

        try {
            return orderQueryService.findOrderById(orderId)
                .map(orderDetail -> ResponseEntity.ok(BaseResponse.success(orderDetail)))
                .orElseGet(() -> {
                    BaseResponse<BaseError> errorResponse = BaseResponse.error(
                        CommonResponseCode.NOT_FOUND,
                        "주문 정보를 찾을 수 없습니다."
                    );
                    return ResponseEntity.status(404).body((BaseResponse) errorResponse);
                });

        } catch (Exception e) {
            log.error("주문 상세 조회 실패 - orderId: {}", orderId, e);
            BaseResponse<BaseError> errorResponse = BaseResponse.error(CommonResponseCode.NOT_FOUND, "주문 정보를 찾을 수 없습니다.");
            return ResponseEntity.status(404).body((BaseResponse) errorResponse);
        }
    }
}
