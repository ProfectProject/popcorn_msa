package com.example.orderquery.domain.itemView.controller;

import com.example.orderquery.domain.itemView.dto.OrderItemPageDto;
import com.example.orderquery.domain.itemView.dto.OrderItemQuery;
import com.example.orderquery.domain.itemView.service.OrderItemViewService;
import com.example.orderquery.domain.summary.dto.OrderSummaryDto;
import com.example.orderquery.domain.summary.service.OrderSummaryService;
import com.example.orderquery.global.security.OwnerAuthService;
import com.example.orderquery.global.security.OwnerAuthService.OwnerContext;
import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.common.annotation.ApiLogging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Order Query Items", description = "운영자 팝업 주문 항목 조회")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/orderquery/v1/owner/stores/{storeId}/popups/{popupId}/orders/items")
public class OrderItemViewController extends BaseController {

    private final OrderItemViewService orderItemViewService;
    private final OwnerAuthService ownerAuthService;
    private final OrderSummaryService orderSummaryService;

    @GetMapping()
    @Operation(summary = "팝업 주문 항목 조회", description = "storeId + popupId 기준으로 라인 아이템을 조회합니다.")
    @ApiLogging(level = ApiLogging.LogLevel.INFO, includeResponse = false, excludeParams = { "authentication" })
    public ResponseEntity<BaseResponse<OrderItemPageDto>> getItems(
                                                                   Authentication authentication,
                                                                   @Parameter(description = "스토어 ID") @PathVariable UUID storeId,
                                                                   @Parameter(description = "팝업 ID") @PathVariable UUID popupId,
                                                                   @ParameterObject @Valid @ModelAttribute OrderItemQuery query) {
        OwnerContext context = ownerAuthService.resolveOwner(authentication);
        OrderSummaryDto summary = orderSummaryService.getSummary(storeId, popupId);
        ownerAuthService.authorizePopupAccess(summary, context);
        return ok(orderItemViewService.getItems(storeId, popupId, query));
    }
}
