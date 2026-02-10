package com.popcorn.store.domain.goods.controller;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.goods.dto.GoodsListResponse;
import com.popcorn.store.domain.goods.dto.GoodsStockResponse;
import com.popcorn.store.domain.goods.exception.GoodsException;
import com.popcorn.store.domain.goods.service.GoodsInventoryApiService;
import com.popcorn.store.domain.goods.service.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Goods", description = "굿즈 관리 API")
@RequestMapping("/api/stores/v1/popups/{popupId}/goods")
public class GoodsQueryController extends BaseController {
    private final GoodsService goodsService;
    private final GoodsInventoryApiService inventoryApiService;

    @GetMapping
    @Operation(summary = "팝업 굿즈 목록 조회", description = "팝업에 등록된 활성 굿즈 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "굿즈 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = GoodsListResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": 200,
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "items": [
                                          {
                                            "goodsName": "팝콘 키링",
                                            "goodsPrice": 12000,
                                            "stock": 100
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            )
    })
    public ResponseEntity<BaseResponse<GoodsListResponse>> list(
            @Parameter(description = "팝업 ID", required = true)
            @PathVariable UUID popupId
    ) {
        return ok(goodsService.listForUser(popupId));
    }

}
