package com.popcorn.store.domain.validation.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.domain.popup.service.PopupService;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationRequest;
import com.popcorn.store.domain.validation.dto.BatchPriceValidationResponse;
import com.popcorn.store.domain.validation.dto.LineItemPriceRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 🔍 배치 가격 검증 서비스
 * Payment 서비스에서 요청하는 여러 lineItem의 가격을 한번에 검증
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BatchPriceValidationService {

    private final PopupService popupService;
    private final GoodsService goodsService;

    /**
     * 🚀 여러 lineItem의 가격을 일괄 검증
     *
     * @param request 배치 가격 검증 요청
     * @return 검증 결과
     */
    public BatchPriceValidationResponse validateBatchPrices(BatchPriceValidationRequest request) {
        log.info("💰 [Store] 배치 가격 검증 시작 - orderId: {}, lineItems: {}",
                request.getOrderId(), request.getLineItems().size());

        List<BatchPriceValidationResponse.LineItemValidationResult> itemResults = new ArrayList<>();
        int totalActualAmount = 0;
        boolean allValid = true;
        String failureReason = null;

        // 각 lineItem에 대해 가격 검증 수행
        for (LineItemPriceRequest lineItem : request.getLineItems()) {
            BatchPriceValidationResponse.LineItemValidationResult result = validateSingleItem(lineItem);
            itemResults.add(result);

            totalActualAmount += result.getActualLineAmount();

            if (!result.isValid()) {
                allValid = false;
                if (failureReason == null) {
                    failureReason = String.format("아이템 %s 검증 실패: %s",
                        result.getItemId(), result.getFailureReason());
                }
            }
        }

        // 총 금액 검증 (null-safe 처리)
        Integer expectedAmount = request.getTotalExpectedAmount();
        if (allValid && expectedAmount != null && totalActualAmount != expectedAmount.intValue()) {
            allValid = false;
            failureReason = String.format("총 금액 불일치 - 예상: %d원, 실제: %d원",
                expectedAmount, totalActualAmount);
        } else if (allValid && expectedAmount == null) {
            // expectedAmount가 null인 경우 경고 로그만 출력하고 검증 통과
            log.warn("⚠️ [Store] 총 예상 금액이 null입니다 - orderId: {}, actualAmount: {}원",
                request.getOrderId(), totalActualAmount);
        }

        BatchPriceValidationResponse response = BatchPriceValidationResponse.builder()
            .orderId(request.getOrderId())
            .isValid(allValid)
            .totalActualAmount(totalActualAmount)
            .totalExpectedAmount(request.getTotalExpectedAmount())
            .itemResults(itemResults)
            .failureReason(failureReason)
            .build();

        if (allValid) {
            log.info("✅ [Store] 배치 가격 검증 성공 - orderId: {}, 총금액: {}원",
                    request.getOrderId(), totalActualAmount);
        } else {
            log.warn("❌ [Store] 배치 가격 검증 실패 - orderId: {}, 이유: {}",
                    request.getOrderId(), failureReason);
        }

        return response;
    }

    /**
     * 단일 아이템의 가격 검증
     */
    private BatchPriceValidationResponse.LineItemValidationResult validateSingleItem(LineItemPriceRequest lineItem) {
        try {
            Integer actualPrice = null;
            String itemType = lineItem.getItemType().toUpperCase();

            // 아이템 타입에 따라 실제 가격 조회
            if ("SESSION".equals(itemType)) {
                actualPrice = popupService.getSessionPrice(lineItem.getItemId()).getPrice();
                log.debug("🎭 [Store] 세션 가격 조회 - sessionId: {}, price: {}원",
                        lineItem.getItemId(), actualPrice);
            } else if ("GOODS".equals(itemType)) {
                actualPrice = goodsService.getGoodsPrice(lineItem.getItemId()).getPrice();
                log.debug("🎁 [Store] 굿즈 가격 조회 - goodsId: {}, price: {}원",
                        lineItem.getItemId(), actualPrice);
            } else {
                return createFailedResult(lineItem, "지원하지 않는 아이템 타입: " + itemType);
            }

            // 가격 검증
            boolean priceValid = actualPrice.equals(lineItem.getExpectedPrice());
            Integer actualLineAmount = actualPrice * lineItem.getQuantity();
            Integer expectedLineAmount = lineItem.getExpectedPrice() * lineItem.getQuantity();

            return BatchPriceValidationResponse.LineItemValidationResult.builder()
                .itemId(lineItem.getItemId())
                .itemType(itemType)
                .isValid(priceValid)
                .actualPrice(actualPrice)
                .expectedPrice(lineItem.getExpectedPrice())
                .quantity(lineItem.getQuantity())
                .actualLineAmount(actualLineAmount)
                .expectedLineAmount(expectedLineAmount)
                .failureReason(priceValid ? null :
                    String.format("가격 불일치 - 예상: %d원, 실제: %d원", lineItem.getExpectedPrice(), actualPrice))
                .build();

        } catch (Exception e) {
            log.error("❌ [Store] 아이템 가격 검증 중 오류 - itemId: {}, itemType: {}, error: {}",
                    lineItem.getItemId(), lineItem.getItemType(), e.getMessage());
            return createFailedResult(lineItem, "가격 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 실패 결과 생성
     */
    private BatchPriceValidationResponse.LineItemValidationResult createFailedResult(
            LineItemPriceRequest lineItem, String failureReason) {
        return BatchPriceValidationResponse.LineItemValidationResult.builder()
            .itemId(lineItem.getItemId())
            .itemType(lineItem.getItemType())
            .isValid(false)
            .actualPrice(null)
            .expectedPrice(lineItem.getExpectedPrice())
            .quantity(lineItem.getQuantity())
            .actualLineAmount(0)
            .expectedLineAmount(lineItem.getExpectedPrice() * lineItem.getQuantity())
            .failureReason(failureReason)
            .build();
    }
}