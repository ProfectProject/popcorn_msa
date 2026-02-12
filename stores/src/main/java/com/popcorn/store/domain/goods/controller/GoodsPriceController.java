package com.popcorn.store.domain.goods.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.goods.dto.query.response.GoodsPriceResponse;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationRequest;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationResponse;
import com.popcorn.store.domain.validation.service.BatchPriceValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 굿즈 가격 조회 API 컨트롤러
 * Order 서비스의 굿즈 가격 조회 요청을 처리합니다.
 */
@RestController
@Tag(name = "Goods Price", description = "굿즈 가격 조회 API")
@RequestMapping("/api/stores/v1/goods")
@RequiredArgsConstructor
@Slf4j
public class GoodsPriceController extends BaseController {

    private final GoodsService goodsService;
    private final BatchPriceValidationService batchPriceValidationService;

    @Operation(
        summary = "굿즈 가격 조회",
        description = "굿즈 ID로 상품의 가격 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "가격 조회 성공",
            content = @Content(schema = @Schema(implementation = GoodsPriceResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "굿즈를 찾을 수 없음"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류"
        )
    })
    @GetMapping("/{goodsId}/price")
    public ResponseEntity<BaseResponse<GoodsPriceResponse>> getGoodsPrice(
        @Parameter(description = "굿즈 ID", required = true, example = "00000000-0000-0000-0000-000000000301")
        @PathVariable UUID goodsId
    ) {
        log.info("🎁 [Store] 굿즈 가격 조회 요청 - goodsId: {}", goodsId);

        GoodsPriceResponse response = goodsService.getGoodsPrice(goodsId);

        log.info("✅ [Store] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, response.getPrice());
        return ok(response);
    }

    /**
     * 🔍 배치 가격 검증 API
     * Payment 서비스에서 호출하는 진짜 배치 가격 검증
     */
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
    @PostMapping("/validation/batch-price")
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

        return ok(response);
    }

    /**
     * 🩺 배치 가격 검증 헬스체크
     */
    @PostMapping("/validation/health")
    public ResponseEntity<BaseResponse<String>> validationHealthCheck() {
        log.debug("💚 [Store] 배치 가격 검증 API 헬스체크");
        return ok("배치 가격 검증 API가 정상적으로 동작중입니다.");
    }
}