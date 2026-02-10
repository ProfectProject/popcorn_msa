package com.popcorn.store.domain.goods.service;

import com.popcorn.store.domain.goods.dto.GoodsCreateRequest;
import com.popcorn.store.domain.goods.dto.GoodsIdResponse;
import com.popcorn.store.domain.goods.dto.GoodsItemResponse;
import com.popcorn.store.domain.goods.dto.GoodsListResponse;
import com.popcorn.store.domain.goods.dto.GoodsStatusResponse;
import com.popcorn.store.domain.goods.dto.GoodsStatusUpdateRequest;
import com.popcorn.store.domain.goods.dto.GoodsUpdateRequest;
import com.popcorn.store.domain.goods.entity.GoodsVariant;
import com.popcorn.store.domain.goods.exception.GoodsNotFoundException;
import com.popcorn.store.domain.goods.repository.GoodsVariantRepository;
import com.popcorn.store.domain.popup.cache.PopupDetailCacheManager;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoodsOwnerService {

    private final GoodsVariantRepository goodsVariantRepository;
    private final OwnerPopupRepository ownerPopupRepository;
    private final PopupDetailCacheManager popupDetailCacheManager;

    @Transactional(readOnly = true)
    public GoodsListResponse list(Long ownerId, UUID popupId) {
        log.info("[GOODS_OWNER_LIST] ownerId={}, popupId={}", ownerId, popupId);
        requireOwnedPopup(ownerId, popupId);
        List<GoodsItemResponse> items = goodsVariantRepository
                .findAllByPopupIdAndDeletedAtIsNullOrderByCreatedAtDesc(popupId)
                .stream()
                .map(GoodsItemResponse::fromOwner)
                .collect(Collectors.toList());
        return new GoodsListResponse(items);
    }

    @Transactional
    public GoodsIdResponse create(Long ownerId, UUID popupId, GoodsCreateRequest request) {
        log.info("[GOODS_OWNER_CREATE] ownerId={}, popupId={}, name={}", ownerId, popupId, request.getGoodsName());
        requireOwnedPopup(ownerId, popupId);
        String stockUnit = request.getStockUnit().trim();
        String goodsName = request.getGoodsName().trim();
        boolean isActive = Boolean.TRUE.equals(request.getIsActive());
        GoodsVariant goods = GoodsVariant.create(
                popupId,
                stockUnit,
                goodsName,
                request.getGoodsPrice(),
                request.getStock(),
                isActive
        );
        goodsVariantRepository.save(goods);
        // 굿즈 변경 사항이 상세 응답에 반영되도록 캐시 삭제
        popupDetailCacheManager.evictDetail(popupId);
        return new GoodsIdResponse(goods.getId());
    }

    @Transactional(readOnly = true)
    public GoodsItemResponse get(Long ownerId, UUID popupId, UUID goodsId) {
        log.info("[GOODS_OWNER_GET] ownerId={}, popupId={}, goodsId={}", ownerId, popupId, goodsId);
        requireOwnedPopup(ownerId, popupId);
        GoodsVariant goods = getGoods(popupId, goodsId);
        return GoodsItemResponse.fromOwner(goods);
    }

    @Transactional
    public GoodsIdResponse update(Long ownerId, UUID popupId, UUID goodsId, GoodsUpdateRequest request) {
        log.info("[GOODS_OWNER_UPDATE] ownerId={}, popupId={}, goodsId={}", ownerId, popupId, goodsId);
        requireOwnedPopup(ownerId, popupId);
        String goodsName = request.getGoodsName().trim();
        GoodsVariant goods = getGoods(popupId, goodsId);
        goods.update(
                goodsName,
                request.getGoodsPrice(),
                request.getStock()
        );
        // 굿즈 변경 시 상세 캐시 무효화
        popupDetailCacheManager.evictDetail(popupId);
        return new GoodsIdResponse(goods.getId());
    }

    @Transactional
    public GoodsStatusResponse updateStatus(
            Long ownerId,
            UUID popupId,
            UUID goodsId,
            GoodsStatusUpdateRequest request
    ) {
        log.info("[GOODS_OWNER_STATUS] ownerId={}, popupId={}, goodsId={}, active={}",
                ownerId, popupId, goodsId, request.getIsActive());
        requireOwnedPopup(ownerId, popupId);
        GoodsVariant goods = getGoods(popupId, goodsId);
        goods.updateStatus(request.getIsActive());
        // 굿즈 상태 변경 시 상세 캐시 무효화
        popupDetailCacheManager.evictDetail(popupId);
        return new GoodsStatusResponse(goods.getId(), goods.isActive());
    }

    @Transactional
    public void delete(Long ownerId, UUID popupId, UUID goodsId) {
        log.info("[GOODS_OWNER_DELETE] ownerId={}, popupId={}, goodsId={}", ownerId, popupId, goodsId);
        requireOwnedPopup(ownerId, popupId);
        GoodsVariant goods = getGoods(popupId, goodsId);
        goods.softDelete();
        // 굿즈 삭제 시 상세 캐시 무효화
        popupDetailCacheManager.evictDetail(popupId);
    }

    private GoodsVariant getGoods(UUID popupId, UUID goodsId) {
        return goodsVariantRepository
                .findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId)
                .orElseThrow(GoodsNotFoundException::new);
    }

    private void requireOwnedPopup(Long ownerId, UUID popupId) {
        ownerPopupRepository.findOwnedPopup(popupId, ownerId)
                .orElseThrow(PopupException::popupNotFound);
    }
}
