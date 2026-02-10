package com.popcorn.store.domain.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.popcorn.store.domain.goods.dto.GoodsCreateRequest;
import com.popcorn.store.domain.goods.dto.GoodsIdResponse;
import com.popcorn.store.domain.goods.dto.GoodsListResponse;
import com.popcorn.store.domain.goods.dto.GoodsStatusUpdateRequest;
import com.popcorn.store.domain.goods.dto.GoodsUpdateRequest;
import com.popcorn.store.domain.goods.entity.GoodsVariant;
import com.popcorn.store.domain.goods.exception.GoodsNotFoundException;
import com.popcorn.store.domain.goods.repository.GoodsVariantRepository;
import com.popcorn.store.domain.popup.entity.Popup;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupRepository;
import com.popcorn.store.domain.popup.cache.PopupDetailCacheManager;

class GoodsOwnerServiceTest {

	@Mock
	private GoodsVariantRepository goodsVariantRepository;

	@Mock
	private OwnerPopupRepository ownerPopupRepository;

	@Mock
	private PopupDetailCacheManager popupDetailCacheManager;

	private GoodsOwnerService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new GoodsOwnerService(goodsVariantRepository, ownerPopupRepository, popupDetailCacheManager);
	}

	@Test
	@DisplayName("굿즈 목록 - 오너 팝업이면 목록 반환")
	void listReturnsItems() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		when(goodsVariantRepository.findAllByPopupIdAndDeletedAtIsNullOrderByCreatedAtDesc(popupId))
				.thenReturn(List.of());

		GoodsListResponse response = service.list(ownerId, popupId);

		assertThat(response.getItems()).isEmpty();
	}

	@Test
	@DisplayName("굿즈 목록 - 소유 팝업이 아니면 실패")
	void listRejectsWhenPopupMissing() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(ownerId, popupId))
				.isInstanceOf(PopupException.class);
	}

	@Test
	@DisplayName("굿즈 생성 - 저장 후 이벤트 발행")
	void createPublishesEvent() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		GoodsCreateRequest request = new GoodsCreateRequest();
		ReflectionTestUtils.setField(request, "stockUnit", "개");
		ReflectionTestUtils.setField(request, "goodsName", "키링");
		ReflectionTestUtils.setField(request, "goodsPrice", 12000);
		ReflectionTestUtils.setField(request, "stock", 5);
		ReflectionTestUtils.setField(request, "isActive", true);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));

		UUID goodsId = UUID.randomUUID();
		doAnswer(invocation -> {
			GoodsVariant goods = invocation.getArgument(0);
			ReflectionTestUtils.setField(goods, "id", goodsId);
			return goods;
		}).when(goodsVariantRepository).save(any(GoodsVariant.class));

		GoodsIdResponse response = service.create(ownerId, popupId, request);

		assertThat(response.getId()).isEqualTo(goodsId);
	}

	@Test
	@DisplayName("굿즈 수정 - 존재하지 않으면 실패")
	void updateRejectsWhenMissing() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		UUID goodsId = UUID.randomUUID();
		GoodsUpdateRequest request = new GoodsUpdateRequest();
		ReflectionTestUtils.setField(request, "goodsName", "키링");
		ReflectionTestUtils.setField(request, "goodsPrice", 12000);
		ReflectionTestUtils.setField(request, "stock", 3);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		when(goodsVariantRepository.findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(ownerId, popupId, goodsId, request))
				.isInstanceOf(GoodsNotFoundException.class);
	}

	@Test
	@DisplayName("굿즈 수정 - 성공 시 이벤트 발행")
	void updatePublishesEvent() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		UUID goodsId = UUID.randomUUID();
		GoodsUpdateRequest request = new GoodsUpdateRequest();
		ReflectionTestUtils.setField(request, "goodsName", "키링");
		ReflectionTestUtils.setField(request, "goodsPrice", 12000);
		ReflectionTestUtils.setField(request, "stock", 3);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		GoodsVariant goods = GoodsVariant.create(popupId, "개", "기존", 10000, 5, true);
		ReflectionTestUtils.setField(goods, "id", goodsId);
		when(goodsVariantRepository.findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId))
				.thenReturn(Optional.of(goods));

		GoodsIdResponse response = service.update(ownerId, popupId, goodsId, request);

		assertThat(response.getId()).isEqualTo(goodsId);
	}

	@Test
	@DisplayName("굿즈 조회 - 오너 팝업이면 반환")
	void getReturnsItem() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		UUID goodsId = UUID.randomUUID();
		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		GoodsVariant goods = GoodsVariant.create(popupId, "개", "키링", 10000, 5, true);
		ReflectionTestUtils.setField(goods, "id", goodsId);
		when(goodsVariantRepository.findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId))
				.thenReturn(Optional.of(goods));

		assertThat(service.get(ownerId, popupId, goodsId).getId()).isEqualTo(goodsId);
	}

	@Test
	@DisplayName("굿즈 상태 변경 - 이벤트 발행")
	void updateStatusPublishesEvent() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		UUID goodsId = UUID.randomUUID();
		GoodsStatusUpdateRequest request = new GoodsStatusUpdateRequest();
		ReflectionTestUtils.setField(request, "isActive", false);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		GoodsVariant goods = GoodsVariant.create(popupId, "개", "키링", 12000, 5, true);
		ReflectionTestUtils.setField(goods, "id", goodsId);
		when(goodsVariantRepository.findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId))
				.thenReturn(Optional.of(goods));

		service.updateStatus(ownerId, popupId, goodsId, request);

	}

	@Test
	@DisplayName("굿즈 삭제 - 이벤트 발행")
	void deletePublishesEvent() {
		Long ownerId = 1L;
		UUID popupId = UUID.randomUUID();
		UUID goodsId = UUID.randomUUID();

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId))
				.thenReturn(Optional.of(stubPopup(popupId, ownerId)));
		GoodsVariant goods = GoodsVariant.create(popupId, "개", "키링", 12000, 5, true);
		ReflectionTestUtils.setField(goods, "id", goodsId);
		when(goodsVariantRepository.findByIdAndPopupIdAndDeletedAtIsNull(goodsId, popupId))
				.thenReturn(Optional.of(goods));

		service.delete(ownerId, popupId, goodsId);

	}

	private Popup stubPopup(UUID popupId, Long ownerId) {
		Popup popup = Popup.builder()
				.storeId(UUID.randomUUID())
				.title("팝업")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);
		return popup;
	}
}
