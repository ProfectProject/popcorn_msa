package com.popcorn.store.domain.validation.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationRequest;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationResponse;
import com.popcorn.store.domain.validation.service.BatchPriceValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 🔍 배치 가격 검증 API 컨트롤러
 * Payment 서비스에서 호출하는 여러 lineItem의 가격을 한번에 검증
 */
@RestController
@Tag(name = "Batch Price Validation", description = "배치 가격 검증 API")
@RequestMapping("/api/stores/v1/validation")
@RequiredArgsConstructor
@Slf4j
public class BatchPriceValidationController extends BaseController {

    private final BatchPriceValidationService batchPriceValidationService;

    @Operation(
        summary = "배치 가격 검증",
        description = "여러 lineItem(세션, 굿즈)의 가격을 한번에 검증합니다. Payment 서비스에서 mixed 주문의 가격 검증에 사용됩니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "검증 완료 (성공/실패 여부는 response.isValid로 확인)",
            content = @Content(schema = @Schema(implementation = BatchPriceValidationResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 파라미터"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류"
        )
    })
    @PostMapping("/batch-price")
    public ResponseEntity<BaseResponse<BatchPriceValidationResponse>> validateBatchPrices(
        @RequestBody BatchPriceValidationRequest request
    ) {
        log.info("🚀 [Store] 배치 가격 검증 API 호출 - orderId: {}, lineItems: {}개",
                request.getOrderId(), request.getLineItems().size());

        BatchPriceValidationResponse response = batchPriceValidationService.validateBatchPrices(request);

        if (response.isValid()) {
            log.info("✅ [Store] 배치 가격 검증 성공 - orderId: {}, 총금액: {}원",
                    response.getOrderId(), response.getTotalActualAmount());
        } else {
            log.warn("❌ [Store] 배치 가격 검증 실패 - orderId: {}, 이유: {}",
                    response.getOrderId(), response.getFailureReason());
        }

        // 🔍 JSON 응답 로깅
        try {
            BaseResponse<BatchPriceValidationResponse> finalResponse = ok(response);
            String responseJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(finalResponse);
            log.info("🔍 [Store] 실제 JSON 응답 - orderId: {}, response: {}",
                    response.getOrderId(), responseJson);
            return finalResponse;
        } catch (Exception e) {
            log.error("❌ [Store] JSON 직렬화 실패: {}", e.getMessage());
            return ok(response);
        }
    }

    @Operation(
        summary = "배치 가격 검증 (헬스체크)",
        description = "배치 가격 검증 API의 헬스체크를 위한 간단한 엔드포인트"
    )
    @PostMapping("/batch-health")
    public ResponseEntity<BaseResponse<String>> healthCheck() {
        log.debug("💚 [Store] 배치 가격 검증 API 헬스체크");
        return ok("배치 가격 검증 API가 정상적으로 동작중입니다.");
    }
}