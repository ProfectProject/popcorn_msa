package com.popcorn.store.domain.goods.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.goods.dto.query.response.GoodsPriceResponse;
import com.popcorn.store.domain.goods.service.GoodsService;

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
}