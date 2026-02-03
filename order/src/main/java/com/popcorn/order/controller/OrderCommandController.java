package com.popcorn.order.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.popcorn.common.filter.PassportPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import com.popcorn.common.dto.BaseResponse;
import com.popcorn.common.cache.IdempotencyService;
import com.popcorn.order.dto.command.CreateOrderCommand;
import com.popcorn.order.dto.request.OrderCreateRequest;
import com.popcorn.order.dto.response.OrderCreateResponse;
import com.popcorn.order.dto.response.OrderResponseCode;
import com.popcorn.order.service.core.OrderCommandService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 명령(Command) 전용 컨트롤러 - CQRS 패턴
 *
 * [Java 초보자를 위한 가이드]
 *
 * CQRS란? (Command Query Responsibility Segregation)
 * - Command: 데이터를 변경하는 작업 (생성, 수정, 삭제)
 * - Query: 데이터를 조회하는 작업 (읽기)
 * - 이 둘을 분리해서 각각 최적화하는 아키텍처 패턴
 *
 * 왜 분리하나요?
 * 1. 명령은 데이터 일관성과 비즈니스 규칙 검증이 중요
 * 2. 조회는 빠른 성능과 복잡한 검색 조건이 중요
 * 3. 각각의 특성에 맞춰 최적화할 수 있어서 성능과 유지보수성이 좋아짐
 *
 * 이 컨트롤러가 담당하는 일:
 * - 주문 생성 (POST /api/orders/v1)
 * - 주문 상태 변경 (PATCH /api/orders/v1/{id}/status)
 * - 주문 취소 (PATCH /api/orders/v1/{id}/cancel)
 * - 주문 조회는 OrderQueryController에서 담당 (CQRS 분리)
 *
 * 사용된 Spring 어노테이션 설명:
 * - RestController: 이 클래스가 REST API를 제공한다는 표시
 * - RequestMapping: 모든 API가 "/api/orders/v1"로 시작한다는 설정
 * - RequiredArgsConstructor: final 필드의 생성자를 Lombok이 자동 생성
 * - Slf4j: log 객체를 자동으로 만들어줌 (로그 출력용)
 * - Tag: Swagger UI에서 API를 그룹으로 묶어서 보여줌
 * - Validated: 입력값 검증을 활성화
 * - PostMapping: HTTP POST 요청 처리 (데이터 생성용)
 * - PatchMapping: HTTP PATCH 요청 처리 (데이터 부분 수정용)
 */
@RestController
@RequestMapping("/api/orders/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Command", description = "주문 생성/수정/삭제 API (CQRS Command)")
@SecurityRequirement(name = "bearer-token")
@Validated
public class OrderCommandController {

    private static final String UNKNOWN_IP = "UNKNOWN_IP";

    /**
     * 의존성 주입
     * - final 키워드: 객체 생성 후 변경 불가능 (불변성)
     * - RequiredArgsConstructor: final 필드의 생성자를 Lombok이 자동 생성
     * - Spring이 OrderCommandService 구현체를 자동으로 주입해줌
     *
     * CQRS 원칙에 따라 Command Controller는 Command Service만 사용
     * 조회는 OrderQueryController + OrderQueryService에서 담당
     */
    private final OrderCommandService orderCommandService;

    /**
     * 주문 생성 API - CQRS Command 작업
     *
     * 사용 예시: POST /api/orders/v1
     *
     * [Java 초보자를 위한 설명]
     * CQRS Command 패턴의 핵심 작업입니다.
     * - 새로운 주문 데이터를 생성하여 시스템 상태를 변경합니다
     * - 데이터 검증, 비즈니스 규칙 확인, 트랜잭션 처리를 담당합니다
     * - 성공하면 생성된 주문의 ID와 번호를 반환합니다
     *
     * 실무 활용:
     * - 사용자가 팝업스토어에서 상품 주문할 때
     * - 예약형(체험) 또는 구매형(굿즈) 주문 모두 처리
     * - 결제 시스템과 연동되어 결제 프로세스 시작
     */
    @PostMapping
    @Operation(
        summary = "주문 생성",
        description = """
            새로운 주문을 생성합니다. (CQRS Command 작업)

            처리 과정:
            1. 요청 데이터 검증 (필수 필드, 수량, 타입 등)
            2. 비즈니스 규칙 확인 (재고, 예약 가능 여부 등)
            3. 주문 데이터 생성 및 저장
            4. 결제 시스템 연동 준비

            주문 타입:
            - RESERVATION: 예약형 주문 (팝업 체험, 시간 슬롯 기반)
            - GOODS: 구매형 주문 (굿즈 구매, 배송 필요)
            - MIXED: 혼합형 주문 (예약 + 굿즈 함께 주문)
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "주문 생성 요청 데이터",
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = OrderCreateRequest.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "1. 예약형 주문",
                        summary = "팝업 체험 예약 (2명, 10시-18시 세션)",
                        description = "팝업스토어 체험 예약만 하는 경우 (실제 테스트 데이터)",
                        value = """
                            {
                              "orderType": "RESERVATION",
                              "popupId": "00000000-0000-0000-0000-000000000101",
                              "reservationId": "00000000-0000-0000-0000-000000000201",
                              "paymentMethod": "CARD",
                              "items": [
                                {
                                  "orderItemType": "RESERVATION",
                                  "qty": 2,
                                  "unitPrice": 15000,
                                  "sessionId": "00000000-0000-0000-0000-000000000201"
                                }
                              ]
                            }
                            """
                    ),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "2. 굿즈 주문",
                        summary = "Sample Goods 구매 (3개)",
                        description = "팝업스토어 굿즈만 구매하는 경우 (실제 테스트 데이터)",
                        value = """
                            {
                              "orderType": "GOODS",
                              "popupId": "00000000-0000-0000-0000-000000000101",
                              "paymentMethod": "CARD",
                              "items": [
                                {
                                  "orderItemType": "GOODS",
                                  "qty": 3,
                                  "unitPrice": 5000,
                                  "goodsId": "00000000-0000-0000-0000-000000000301"
                                }
                              ]
                            }
                            """
                    ),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "3. 혼합형 주문",
                        summary = "예약 + 굿즈 함께 주문 (체험 1명 + Sample Goods 2개)",
                        description = "팝업 체험 예약과 굿즈를 함께 주문하는 경우 (실제 테스트 데이터)",
                        value = """
                            {
                              "orderType": "MIXED",
                              "popupId": "00000000-0000-0000-0000-000000000101",
                              "reservationId": "00000000-0000-0000-0000-000000000201",
                              "paymentMethod": "CARD",
                              "items": [
                                {
                                  "orderItemType": "RESERVATION",
                                  "qty": 1,
                                  "unitPrice": 15000,
                                  "sessionId": "00000000-0000-0000-0000-000000000201"
                                },
                                {
                                  "orderItemType": "GOODS",
                                  "qty": 2,
                                  "unitPrice": 5000,
                                  "goodsId": "00000000-0000-0000-0000-000000000301"
                                }
                              ]
                            }
                            """
                    )
                }
            )
        )
    )
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('SYSTEM')")
    public ResponseEntity<BaseResponse<OrderCreateResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal PassportPrincipal principal,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        // 📡 요청 정보 상세 로깅
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        log.info("🚀 [REQ-{}] 주문 생성 요청 시작", requestId);
        log.info("🌐 [REQ-{}] 클라이언트 정보 - IP: {}, User-Agent: {}", requestId, clientIp, userAgent);
        log.info("🔐 [REQ-{}] 인증 정보 - 인증됨: {}, 사용자: {}",
                requestId, authentication != null, authentication != null ? authentication.getName() : "익명");

        // JWT에서 사용자 ID 추출 (보안상 요청 본문이 아닌 토큰에서 추출)
        Long userId = principal != null ? principal.userId() : extractUserIdFromAuthentication(authentication);

        log.info("👤 [REQ-{}] 사용자 ID 추출 완료: {}", requestId, userId);
        log.info("📄 [REQ-{}] 요청 데이터 - 팝업ID: {}, 주문타입: {}, 아이템 수: {}",
                requestId, request.getPopupId(), request.getOrderType(), request.getItems().size());

        // 요청 본문 상세 로깅 (민감 정보 제외)
        log.debug("📝 [REQ-{}] 요청 본문: {}", requestId, request);

        long startTime = System.currentTimeMillis();

        try {
            validateOrderRequest(request);
            log.info("🔄 [REQ-{}] 1단계: Request → Command 변환 시작", requestId);
            // 1. Request를 Command 객체로 변환 (JWT에서 추출한 userId 포함)
            // Command 패턴: 요청을 객체로 캡슐화하여 처리
            CreateOrderCommand command = CreateOrderCommand.fromRequest(request, userId);
            log.info("✅ [REQ-{}] Command 객체 생성 완료", requestId);

            log.info("🔄 [REQ-{}] 2단계: 주문 생성 서비스 호출 시작", requestId);
            // 2. CQRS Command 서비스 호출
            // 실제 비즈니스 로직은 Service 계층에서 처리
            OrderCreateResponse response = orderCommandService.createOrder(command);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("🎉 [REQ-{}] 주문 생성 성공! 주문번호: {}, 주문ID: {}, 처리시간: {}ms",
                    requestId, response.getOrderNo(), response.getOrderId(), processingTime);

            log.info("🔄 [REQ-{}] 3단계: 성공 응답 생성", requestId);
            // 3. 성공 응답 생성
            BaseResponse<OrderCreateResponse> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_CREATED, response);

            log.info("📤 [REQ-{}] 응답 전송 - 상태: {}, 크기: {} bytes",
                    requestId, OrderResponseCode.ORDER_CREATED.getHttpStatus(),
                    baseResponse.toString().length());

            return ResponseEntity.status(OrderResponseCode.ORDER_CREATED.getHttpStatus())
                    .body(baseResponse);

        } catch (IdempotencyService.IdempotencyException e) {
            long processingTime = System.currentTimeMillis() - startTime;

            if (e.getCause() != null) {
                log.warn("❌ [REQ-{}] 주문 생성 실패(멱등성 래핑) - {}ms: {}",
                        requestId, processingTime, e.getCause().getMessage());
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e.getCause());
            }

            log.warn("⏳ [REQ-{}] 주문 생성 중복 요청 - {}ms: {}",
                    requestId, processingTime, e.getMessage());

            BaseResponse<OrderCreateResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, null);

            return ResponseEntity.status(OrderResponseCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.getHttpStatus())
                    .body(errorResponse);

        } catch (IllegalArgumentException e) {
            // 요청 데이터가 잘못된 경우 (검증 실패)
            long processingTime = System.currentTimeMillis() - startTime;

            log.error("❌ [REQ-{}] 주문 생성 실패 - 잘못된 요청 ({}ms): {}",
                    requestId, processingTime, e.getMessage());
            log.debug("❌ [REQ-{}] 상세 오류 스택:", requestId, e);

            BaseResponse<OrderCreateResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.INVALID_ORDER_REQUEST, null);

            log.info("📤 [REQ-{}] 잘못된 요청 응답 전송 - 상태: {}",
                    requestId, OrderResponseCode.INVALID_ORDER_REQUEST.getHttpStatus());

            return ResponseEntity.status(OrderResponseCode.INVALID_ORDER_REQUEST.getHttpStatus())
                    .body(errorResponse);
        } catch (com.popcorn.order.exception.OrderReservationFailedException e) {
            long processingTime = System.currentTimeMillis() - startTime;

            log.warn("⏳ [REQ-{}] 예약 실패로 주문 생성 실패 ({}ms): {}",
                    requestId, processingTime, e.getMessage());

            BaseResponse<OrderCreateResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_RESERVATION_FAILED, null);

            return ResponseEntity.status(OrderResponseCode.ORDER_RESERVATION_FAILED.getHttpStatus())
                    .body(errorResponse);

        } catch (com.popcorn.order.exception.OrderReservationTimeoutException e) {
            long processingTime = System.currentTimeMillis() - startTime;

            log.warn("⏳ [REQ-{}] 예약 타임아웃으로 주문 생성 실패 ({}ms): {}",
                    requestId, processingTime, e.getMessage());

            BaseResponse<OrderCreateResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_RESERVATION_TIMEOUT, null);

            return ResponseEntity.status(OrderResponseCode.ORDER_RESERVATION_TIMEOUT.getHttpStatus())
                    .body(errorResponse);

        } catch (Exception e) {
            // 예상치 못한 서버 오류
            long processingTime = System.currentTimeMillis() - startTime;

            log.error("💥 [REQ-{}] 주문 생성 실패 - 서버 내부 오류 ({}ms)", requestId, processingTime, e);

            BaseResponse<OrderCreateResponse> errorResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_CREATION_FAILED, null);

            log.info("📤 [REQ-{}] 서버 오류 응답 전송 - 상태: {}",
                    requestId, OrderResponseCode.ORDER_CREATION_FAILED.getHttpStatus());

            return ResponseEntity.status(OrderResponseCode.ORDER_CREATION_FAILED.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 클라이언트 IP 주소 추출 (프록시 고려)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "UNKNOWN_IP".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "UNKNOWN_IP".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "UNKNOWN_IP".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private void validateOrderRequest(OrderCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 요청이 비어있습니다.");
        }

        if (request.isReservationType() && !request.isValidReservationRequest()) {
            throw new IllegalArgumentException("예약형 주문 요청이 올바르지 않습니다.");
        }

        if (request.isGoodsType() && !request.isValidGoodsRequest()) {
            throw new IllegalArgumentException("굿즈 주문 요청이 올바르지 않습니다.");
        }

        if (request.isMixedType() && !request.isValidMixedRequest()) {
            throw new IllegalArgumentException("혼합 주문 요청이 올바르지 않습니다.");
        }

        if (!request.isReservationType() && !request.isGoodsType() && !request.isMixedType()) {
            throw new IllegalArgumentException("주문 타입이 올바르지 않습니다.");
        }

        if (request.requiresShippingAddress()) {
            log.debug("배송지 필요 주문 - 기본 배송지 확인은 서비스에서 처리합니다.");
        }
    }


    /**
     * 주문 상태 변경 API - CQRS Command 작업
     *
     * 사용 예시: PATCH /api/orders/v1/12345678-1234-1234-1234-123456789abc/status?status=PAID&reason=결제완료
     *
     * [Java 초보자를 위한 설명]
     * 기존 주문의 상태를 변경하는 Command 작업입니다.
     * - 주문 처리 과정에서 상태를 단계별로 업데이트합니다
     * - 상태 변경 이력을 기록하여 추적 가능합니다
     * - 잘못된 상태 변경은 비즈니스 규칙에 따라 차단됩니다
     *
     * 실무 활용:
     * - 관리자가 주문 처리 상태를 업데이트할 때
     * - 결제 시스템에서 결제 완료 시 자동 상태 변경
     * - 배송 시스템에서 배송 상태 업데이트
     *
     * 주문 상태 흐름:
     * REQUESTED → PAID → PROCESSING → SHIPPED → DELIVERED → COMPLETED
     */
    @PatchMapping("/{orderId}/status")
    @Operation(
        summary = "주문 상태 변경",
        description = """
            주문의 상태를 변경합니다. (CQRS Command 작업)

            주문 상태 종류:
            - REQUESTED: 주문 요청됨 (초기 상태)
            - PAID: 결제 완료
            - PROCESSING: 처리 중
            - SHIPPED: 배송 시작 (구매형 주문)
            - DELIVERED: 배송 완료
            - COMPLETED: 주문 완료
            - CANCELLED: 주문 취소

            비즈니스 규칙:
            - 이미 완료/취소된 주문은 상태 변경 불가
            - 특정 상태에서만 다음 상태로 변경 가능
            - 모든 상태 변경은 이력으로 기록됨
            """
    )
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('SYSTEM')")
    public ResponseEntity<BaseResponse<Void>> updateOrderStatus(
            @Parameter(description = "주문 ID", example = "12345678-1234-1234-1234-123456789abc")
            @PathVariable UUID orderId,

            @Parameter(description = "변경할 상태", example = "PAID")
            @RequestParam String status,

            @Parameter(description = "변경 사유", example = "결제 완료 확인됨")
            @RequestParam(required = false, defaultValue = "관리자 변경") String reason) {

        log.info("주문 상태 변경 요청 - 주문ID: {}, 기존상태→새상태: {}, 사유: {}",
                orderId, status, reason);

        try {
            // CQRS Command 서비스 호출
            // 실제 상태 변경 로직과 비즈니스 규칙 검증은 Service에서 수행
            orderCommandService.updateOrderStatus(orderId, status, reason);

            log.info("주문 상태 변경 완료 - 주문ID: {}, 새상태: {}", orderId, status);

            BaseResponse<Void> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_STATUS_UPDATED, null);

            return ResponseEntity.ok(baseResponse);

        } catch (RuntimeException e) {
            // 비즈니스 로직 오류 처리
            log.error("주문 상태 변경 실패 - 주문ID: {}, 상태: {}, 오류: {}",
                    orderId, status, e.getMessage());

            // 오류 메시지에 따른 적절한 응답 코드 선택
            OrderResponseCode errorCode;
            if (e.getMessage().contains("찾을 수 없")) {
                errorCode = OrderResponseCode.ORDER_NOT_FOUND;
            } else if (e.getMessage().contains("변경이 불가능")) {
                errorCode = OrderResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED;
            } else {
                errorCode = OrderResponseCode.ORDER_STATUS_UPDATE_FAILED;
            }

            BaseResponse<Void> errorResponse = BaseResponse.from(errorCode, null);

            return ResponseEntity.status(errorCode.getHttpStatus())
                    .body(errorResponse);

        } catch (Exception e) {
            // 예상치 못한 서버 오류
            log.error("주문 상태 변경 실패 - 주문ID: {}", orderId, e);

            BaseResponse<Void> errorResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_STATUS_UPDATE_FAILED, null);

            return ResponseEntity.status(OrderResponseCode.ORDER_STATUS_UPDATE_FAILED.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * 주문 취소 API - CQRS Command 작업
     *
     * 사용 예시: PATCH /api/orders/v1/12345678-1234-1234-1234-123456789abc/cancel?reason=고객변심
     *
     * [Java 초보자를 위한 설명]
     * 기존 주문을 취소하는 Command 작업입니다.
     * - 주문 상태를 CANCELLED로 변경합니다
     * - 결제가 완료된 경우 환불 프로세스를 시작합니다
     * - 재고가 있는 상품의 경우 재고를 복원합니다
     * - 취소 사유를 기록하여 고객 서비스에 활용합니다
     *
     * 실무 활용:
     * - 고객이 주문을 직접 취소할 때
     * - 관리자가 문제가 있는 주문을 취소할 때
     * - 결제 실패 시 자동으로 주문 취소
     * - 재고 부족으로 인한 주문 취소
     *
     * 취소 가능 조건:
     * - 배송 시작 전까지만 취소 가능
     * - 이미 완료된 주문은 취소 불가
     * - 예약형 주문은 체험 시작 전까지만 취소 가능
     */
    @PatchMapping("/{orderId}/cancel")
    @Operation(
        summary = "주문 취소",
        description = """
            주문을 취소합니다. (CQRS Command 작업)

            취소 처리 과정:
            1. 주문 취소 가능 여부 확인
            2. 주문 상태를 CANCELLED로 변경
            3. 결제 취소/환불 프로세스 시작
            4. 재고 복원 (재고 관리 대상 상품)
            5. 취소 알림 발송

            취소 가능한 상태:
            - REQUESTED: 주문 요청 상태
            - PAID: 결제 완료 상태 (배송 전)

            취소 불가능한 상태:
            - PROCESSING: 이미 처리 시작됨
            - SHIPPED: 배송 시작됨
            - DELIVERED: 배송 완료됨
            - COMPLETED: 주문 완료됨
            - CANCELLED: 이미 취소됨
            """
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BaseResponse<Void>> cancelOrder(
            @Parameter(description = "주문 ID", example = "12345678-1234-1234-1234-123456789abc")
            @PathVariable UUID orderId,

            @Parameter(description = "취소 사유", example = "고객 변심")
            @RequestParam(required = false, defaultValue = "고객 요청") String reason) {

        log.info("주문 취소 요청 - 주문ID: {}, 취소사유: {}", orderId, reason);

        try {
            // CQRS Command 서비스 호출
            // 실제 취소 로직 (상태변경, 환불처리, 재고복원)은 Service에서 수행
            orderCommandService.cancelOrder(orderId, reason);

            log.info("주문 취소 완료 - 주문ID: {}, 사유: {}", orderId, reason);

            BaseResponse<Void> baseResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_CANCELLED, null);

            return ResponseEntity.ok(baseResponse);

        } catch (RuntimeException e) {
            // 비즈니스 로직 오류 처리
            log.error("주문 취소 실패 - 주문ID: {}, 사유: {}, 오류: {}",
                    orderId, reason, e.getMessage());

            // 오류 메시지에 따른 적절한 응답 코드 선택
            OrderResponseCode errorCode;
            if (e.getMessage().contains("찾을 수 없")) {
                errorCode = OrderResponseCode.ORDER_NOT_FOUND;
            } else if (e.getMessage().contains("취소할 수 없")) {
                errorCode = OrderResponseCode.ORDER_NOT_CANCELLABLE;
            } else {
                errorCode = OrderResponseCode.ORDER_STATUS_UPDATE_FAILED;
            }

            BaseResponse<Void> errorResponse = BaseResponse.from(errorCode, null);

            return ResponseEntity.status(errorCode.getHttpStatus())
                    .body(errorResponse);

        } catch (Exception e) {
            // 예상치 못한 서버 오류
            log.error("주문 취소 실패 - 주문ID: {}", orderId, e);

            BaseResponse<Void> errorResponse = BaseResponse.from(
                    OrderResponseCode.ORDER_STATUS_UPDATE_FAILED, null);

            return ResponseEntity.status(OrderResponseCode.ORDER_STATUS_UPDATE_FAILED.getHttpStatus())
                    .body(errorResponse);
        }
    }

    /**
     * JWT 토큰에서 사용자 ID 추출
     *
     * @param authentication Spring Security Authentication 객체 (JwtAuthenticationFilter에서 생성)
     * @return 사용자 ID
     */
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

}
