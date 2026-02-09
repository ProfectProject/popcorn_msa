package com.example.orderquery.domain.summary.controller;

import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderquery.domain.summary.dto.OrderSummaryDto;
import com.example.orderquery.domain.summary.service.OrderSummaryService;
import com.example.orderquery.global.security.OwnerAuthService;
import com.example.orderquery.global.security.OwnerAuthService.OwnerContext;
import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.popcorn.common.annotation.ApiLogging;

@Tag(name = "Order Query Summary", description = "운영자 팝업 주문 요약 조회")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/orderquery/v1/owner/stores/{storeId}/popups/{popupId}/orders/summary")
public class OrderSummaryController extends BaseController {

    private final OrderSummaryService orderSummaryService;
    private final OwnerAuthService ownerAuthService;

    @GetMapping()
    @Operation(summary = "팝업 KPI 요약 조회", description = "storeId + popupId 기준으로 팝업 요약 정보를 조회합니다.")
    @ApiLogging(level = ApiLogging.LogLevel.INFO, includeResponse = false, excludeParams = { "authentication" })
    public ResponseEntity<BaseResponse<OrderSummaryDto>> getSummary(
                                                                    Authentication authentication,
                                                                    @Parameter(description = "스토어 ID") @PathVariable UUID storeId,
                                                                    @Parameter(description = "팝업 ID") @PathVariable UUID popupId) {
        OwnerContext context = ownerAuthService.resolveOwner(authentication);
        OrderSummaryDto summary = orderSummaryService.getSummary(storeId, popupId);
        ownerAuthService.authorizePopupAccess(summary, context);
        return ok(summary);
    }
}
