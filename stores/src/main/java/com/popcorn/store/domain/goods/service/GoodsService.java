package com.popcorn.store.domain.goods.service;

import com.popcorn.store.domain.goods.dto.GoodsItemResponse;
import com.popcorn.store.domain.goods.dto.GoodsListResponse;
import com.popcorn.store.domain.goods.dto.GoodsStockResponse;
import com.popcorn.store.domain.goods.dto.query.response.GoodsPriceResponse;
import com.popcorn.store.domain.goods.entity.GoodsVariant;
import com.popcorn.store.domain.goods.exception.GoodsException;
import com.popcorn.store.domain.goods.repository.GoodsReservationRepository;
import com.popcorn.store.domain.goods.repository.GoodsVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoodsService {


    private final GoodsVariantRepository goodsVariantRepository;
    private final GoodsReservationRepository goodsReservationRepository;

    @Transactional(readOnly = true)
    public GoodsListResponse listForUser(UUID popupId) {
        List<GoodsItemResponse> items = goodsVariantRepository
                .findAllByPopupIdAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(popupId)
                .stream()
                .map(GoodsItemResponse::fromUser)
                .collect(Collectors.toList());
        return new GoodsListResponse(items);
    }

    @Transactional
    public GoodsStockResponse reservationGoods(UUID popupId, UUID goodsId, int quantity){
        log.info("[GOODS_RESERVE] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
        if (quantity <= 0) {
            throw GoodsException.invalidQuantity();
        }

        GoodsStockResponse response = goodsReservationRepository.reserveStock(goodsId, quantity);
        if (response == null) {
            log.warn("[GOODS_RESERVE_FAILED] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
            throw GoodsException.insufficientStock();
        }
        return response;
    }

    @Transactional(readOnly = true)
    public UUID resolvePopupId(UUID goodsId) {
        GoodsVariant goodsVariant = goodsVariantRepository.findById(goodsId)
                .filter(variant -> variant.getDeletedAt() == null)
                .orElseThrow(GoodsException::goodsNotFound);
        return goodsVariant.getPopupId();
    }

    @Transactional
    public GoodsStockResponse cancelReservationGoods(UUID popupId, UUID goodsId, int quantity) {
        log.info("[GOODS_RESERVE_CANCEL] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
        if (quantity <= 0) {
            throw GoodsException.invalidQuantity();
        }

        GoodsStockResponse response = goodsReservationRepository.cancelStock(goodsId, quantity);
        if (response == null) {
            log.warn("[GOODS_RESERVE_CANCEL_FAILED] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
            throw GoodsException.insufficientStock();
        }
        return response;
    }

    @Transactional
    public GoodsStockResponse failReservationGoods(UUID popupId, UUID goodsId, int quantity) {
        log.info("[GOODS_RESERVE_FAIL] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
        if (quantity <= 0) {
            throw GoodsException.invalidQuantity();
        }

        GoodsStockResponse response = goodsReservationRepository.failStock(goodsId, quantity);
        if (response == null) {
            log.warn("[GOODS_RESERVE_FAIL_FAILED] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
            throw GoodsException.insufficientStock();
        }
        return response;
    }

    @Transactional
    public GoodsStockResponse completeReservationGoods(UUID popupId, UUID goodsId, int quantity) {
        log.info("[GOODS_RESERVE_COMPLETE] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
        if (quantity <= 0) {
            throw GoodsException.invalidQuantity();
        }

        GoodsStockResponse response = goodsReservationRepository.completeStock(goodsId, quantity);
        if (response == null) {
            log.warn("[GOODS_RESERVE_COMPLETE_FAILED] popupId={}, goodsId={}, quantity={}", popupId, goodsId, quantity);
            throw GoodsException.insufficientStock();
        }
        log.info(
            "[GOODS_RESERVE_COMPLETE_OK] popupId={}, goodsId={}, qty={} -> stock={}, reservationStock={}",
            popupId,
            goodsId,
            quantity,
            response.getStock(),
            response.getReservationStock()
        );
        return response;
    }

    @Transactional(readOnly = true)
    public int calculateAvailableStock(UUID goodsId) {
        GoodsVariant goodsVariant = goodsVariantRepository.findById(goodsId)
                .filter(variant -> variant.getDeletedAt() == null)
                .orElseThrow(GoodsException::goodsNotFound);

        int stock = goodsVariant.getStock();
        int reservationStock = goodsVariant.getReservationStock();
        return Math.max(0, stock - reservationStock);
    }

    /**
     * 굿즈 가격 조회
     * Order 서비스의 굿즈 가격 조회 요청을 처리합니다.
     */
    @Transactional(readOnly = true)
    public GoodsPriceResponse getGoodsPrice(UUID goodsId) {
        // 굿즈 정보 조회
        GoodsVariant goods = goodsVariantRepository.findById(goodsId)
                .filter(variant -> variant.getDeletedAt() == null)
                .orElseThrow(GoodsException::goodsNotFound);

        // 활성 상태 확인
        if (!goods.isActive()) {
            throw GoodsException.goodsNotFound();
        }

        // 사용 가능한 재고 계산
        Integer availableStock = goods.getStock();

        // 응답 생성
        return GoodsPriceResponse.builder()
                .goodsId(goodsId)
                .productName(goods.getGoodsName())
                .price(goods.getGoodsPrice())
                .originalPrice(goods.getGoodsPrice()) // 할인 기능이 없으므로 동일
                .discountRate(0) // 기본 할인율 0%
                .stockQuantity(availableStock)
                .status(calculateGoodsStatus(availableStock, goods.isActive()))
                .currency("KRW")
                .build();
    }

    /**
     * 굿즈 상태 계산 헬퍼 메서드
     */
    private String calculateGoodsStatus(Integer availableStock, boolean isActive) {
        if (!isActive) {
            return "INACTIVE";
        }
        if (availableStock == null || availableStock <= 0) {
            return "OUT_OF_STOCK";
        }
        return "AVAILABLE";
    }

}
