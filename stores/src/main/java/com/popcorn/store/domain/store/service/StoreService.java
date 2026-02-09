package com.popcorn.store.domain.store.service;

import com.popcorn.store.domain.store.dto.CreateStoreRequest;
import com.popcorn.store.domain.store.dto.StoreCreatedDto;
import com.popcorn.store.domain.store.dto.StoreDetailDto;
import com.popcorn.store.domain.store.dto.StoreListDto;
import com.popcorn.store.domain.store.dto.UpdateStoreRequest;
import com.popcorn.store.domain.store.dto.StoreUpdatedDto;
import com.popcorn.store.domain.store.dto.UpdateStoreStatusRequest;
import com.popcorn.store.domain.store.dto.StoreDeletedDto;
import com.popcorn.store.domain.store.dto.StoreStatusUpdatedDto;
import com.popcorn.store.domain.store.entity.Store;
import com.popcorn.store.domain.store.entity.StorePublishStatus;
import com.popcorn.store.domain.store.exception.StoreException;
import com.popcorn.store.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final int MAX_STORES_PER_OWNER = 10;
    private static final int MAX_STORE_NAME_LENGTH = 100;
    private static final int MIN_STORE_NAME_LENGTH = 1;
    private static final String INVALID_CHARS = "<>\"'&;";

    private final StoreRepository storeRepository;

    @Transactional
    public StoreCreatedDto createStore(Long ownerId, CreateStoreRequest request) {
        log.info("[STORE_CREATE] ownerId={}, name={}", ownerId, request.getName());
        
        // 저장 전 입력 검증 및 불변식 확인.
        String trimmedName = validateAndTrimName(request.getName());
        validateOwnerId(ownerId);
        checkDuplicateName(trimmedName);
        checkStoreLimit(ownerId);
        
        Store savedStore = storeRepository.save(createStoreEntity(ownerId, trimmedName));
        
        log.info("[STORE_CREATED] storeId={}", savedStore.getId());
        return mapToDto(savedStore);
    }

    @Transactional(readOnly = true)
    public List<StoreListDto> getStoresByOwnerId(Long ownerId) {
        log.info("[STORES_GET] ownerId={}", ownerId);

        validateOwnerId(ownerId);

        List<Store> stores = storeRepository.findAllByOwnerIdAndDeletedAtIsNull(ownerId);

        if (stores == null) {
            stores = List.of();
        }
        
        log.info("[STORES_FOUND] count={}", stores.size());
        return stores.stream()
                .filter(store -> store != null && !store.isDeleted())
                .map(this::mapToListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreDetailDto getStoreDetail(Long userId, UUID storeId) {
        log.info("[STORE_DETAIL_GET] userId={}, storeId={}", userId, storeId);

        validateUserId(userId);
        validateStoreId(storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> StoreException.storeNotFound(storeId));

        if (!store.isOwner(userId)) {
            throw StoreException.accessDenied(userId, storeId);
        }

        log.info("[STORE_DETAIL_FOUND] storeId={}", store.getId());
        return mapToDetailDto(store);
    }

    @Transactional
    public StoreUpdatedDto updateStore(UUID storeId, UpdateStoreRequest request, Long userId) {
        log.info("[STORE_UPDATE] storeId={}, userId={}, name={}", storeId, userId, request.getName());
        
        validateOwnerId(userId);
        String trimmedName = validateAndTrimName(request.getName());
        
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> StoreException.storeNotFound(storeId));
        
        if (!store.isOwner(userId)) {
            throw StoreException.accessDenied(userId, storeId);
        }
        
        if (store.isDeleted()) {
            throw StoreException.storeAlreadyDeleted(storeId);
        }
        
        storeRepository.findByName(trimmedName)
                .filter(existingStore -> !existingStore.getId().equals(storeId))
                .filter(existingStore -> !existingStore.isDeleted())
                .ifPresent(existingStore -> {
                    throw StoreException.duplicateStoreName(trimmedName);
                });
        
        store.updateName(trimmedName);
        store.setUpdatedBy(userId);
        
        Store updatedStore = storeRepository.save(store);

        log.info("[STORE_UPDATED] storeId={}", updatedStore.getId());
        return mapToUpdatedDto(updatedStore);
    }
    
    @Transactional
    public StoreDeletedDto deleteStore(UUID storeId, Long userId) {
        log.info("[STORE_DELETE] storeId={}, userId={}", storeId, userId);
        
        validateOwnerId(userId);
        
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> StoreException.storeNotFound(storeId));
        
        if (!store.isOwner(userId)) {
            throw StoreException.accessDenied(userId, storeId);
        }
        
        if (store.isDeleted()) {
            throw StoreException.storeAlreadyDeleted(storeId);
        }
        
        store.delete(userId);
        Store deletedStore = storeRepository.save(store);

        log.info("[STORE_DELETED] storeId={}", storeId);
        return mapToDeletedDto(deletedStore);
    }
    
    @Transactional
    public StoreStatusUpdatedDto updateStoreStatus(UUID storeId, UpdateStoreStatusRequest request, Long userId) {
        log.info("[STORE_STATUS_UPDATE] storeId={}, userId={}, status={}", storeId, userId, request.getPublishStatus());
        
        validateOwnerId(userId);
        
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> StoreException.storeNotFound(storeId));
        
        if (!store.isOwner(userId)) {
            throw StoreException.accessDenied(userId, storeId);
        }
        
        if (store.isDeleted()) {
            throw StoreException.storeAlreadyDeleted(storeId);
        }
        
        store.updatePublishStatus(request.getPublishStatus());
        store.setUpdatedBy(userId);
        
        Store updatedStore = storeRepository.save(store);

        log.info("[STORE_STATUS_UPDATED] storeId={}, status={}", updatedStore.getId(), updatedStore.getPublishStatus());
        return mapToStatusUpdatedDto(updatedStore);
    }

    private String validateAndTrimName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw StoreException.emptyName();
        }
        
        String trimmed = name.trim();
        if (trimmed.length() < MIN_STORE_NAME_LENGTH || trimmed.length() > MAX_STORE_NAME_LENGTH) {
            throw StoreException.invalidNameLength(trimmed.length(), MIN_STORE_NAME_LENGTH, MAX_STORE_NAME_LENGTH);
        }
        
        if (trimmed.chars().anyMatch(c -> INVALID_CHARS.indexOf(c) >= 0)) {
            throw StoreException.invalidNameFormat(trimmed);
        }
        
        return trimmed;
    }

    private void validateOwnerId(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw StoreException.ownerNotFound();
        }
    }
    
    private void validateUserId(Long userId) {
        if (userId == null) {
            throw StoreException.userIdRequired();
        }
        if (userId <= 0) {
            throw StoreException.ownerNotFound();
        }
    }
    
    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw StoreException.storeIdRequired();
        }
    }

    private void checkDuplicateName(String name) {
        storeRepository.findByName(name)
                .filter(store -> !store.isDeleted())
                .ifPresent(store -> {
                    throw StoreException.duplicateStoreName(name);
                });
    }

    private void findStoreByIdAndCheckOwnership(UUID storeId, Long ownerId) {
        storeRepository.findById(storeId)
                .filter(store -> store.isOwner(ownerId))
                .orElseThrow(() -> StoreException.accessDenied(ownerId, storeId));
    }

    private void checkStoreLimit(Long ownerId) {
        long storeCount = storeRepository.countByOwnerId(ownerId);
        if (storeCount >= MAX_STORES_PER_OWNER) {
            throw StoreException.storeCreationLimitExceeded(ownerId, MAX_STORES_PER_OWNER);
        }
    }

    private Store createStoreEntity(Long ownerId, String name) {
        return Store.builder()
                .name(name)
                .ownerId(ownerId)
                .publishStatus(StorePublishStatus.DRAFT)
                .createdBy(ownerId)
                .updatedBy(ownerId)
                .build();
    }

    private StoreCreatedDto mapToDto(Store store) {
        return StoreCreatedDto.builder()
                .id(store.getId())
                .name(store.getName())
                .ownerId(store.getOwnerId())
                .publishStatus(store.getPublishStatus())
                .createdAt(store.getCreatedAt())
                .createdBy(store.getCreatedBy())
                .build();
    }

    private StoreListDto mapToListDto(Store store) {
        return StoreListDto.builder()
                .id(store.getId())
                .name(store.getName())
                .publishStatus(store.getPublishStatus())
                .createdAt(store.getCreatedAt())
                .build();
    }

    private StoreDetailDto mapToDetailDto(Store store) {
        return StoreDetailDto.builder()
                .id(store.getId())
                .name(store.getName())
                .ownerId(store.getOwnerId())
                .ownerName(null)
                .publishStatus(store.getPublishStatus())
                .createdAt(store.getCreatedAt() != null ? store.getCreatedAt() : java.time.LocalDateTime.now())
                .updatedAt(store.getUpdatedAt())
                .build();
    }
    
    private StoreUpdatedDto mapToUpdatedDto(Store store) {
        return StoreUpdatedDto.builder()
                .id(store.getId())
                .name(store.getName())
                .publishStatus(store.getPublishStatus())
                .updatedAt(store.getUpdatedAt())
                .updatedBy(store.getUpdatedBy())
                .build();
    }
    
    private StoreStatusUpdatedDto mapToStatusUpdatedDto(Store store) {
        return StoreStatusUpdatedDto.builder()
                .id(store.getId())
                .publishStatus(store.getPublishStatus())
                .updatedAt(store.getUpdatedAt())
                .updatedBy(store.getUpdatedBy())
                .build();
    }
    
    private StoreDeletedDto mapToDeletedDto(Store store) {
        return StoreDeletedDto.builder()
                .id(store.getId())
                .name(store.getName())
                .deletedAt(store.getDeletedAt())
                .deletedBy(store.getDeletedBy())
                .build();
    }
}
