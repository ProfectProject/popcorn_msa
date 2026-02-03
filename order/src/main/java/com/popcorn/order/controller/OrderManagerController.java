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
