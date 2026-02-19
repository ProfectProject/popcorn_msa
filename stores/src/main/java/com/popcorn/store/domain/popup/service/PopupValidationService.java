package com.popcorn.store.domain.popup.service;

import org.springframework.stereotype.Service;

import com.popcorn.store.domain.popup.dto.PopupResponseCode;
import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.PopupScheduleListQuery;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.exception.PopupException;

@Service
public class PopupValidationService {

	private static final int DEFAULT_PAGE = 1;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 200;
	public PopupListQuery normalizeListQuery(PopupListQuery query) {
		if (query == null) {
			return PopupListQuery.builder()
					.page(DEFAULT_PAGE)
					.size(DEFAULT_SIZE)
					.withTotal(true)
					.build();
		}

		Integer page = query.getPage();
		Integer size = query.getSize();
		Long regionId = query.getRegionId();
		PopupCategory category = query.getCategory();
		Boolean withTotal = query.getWithTotal();

		// 페이지 검증: null은 기본값, 1 미만은 오류
		int normalizedPage;
		if (page == null) {
			normalizedPage = DEFAULT_PAGE;
		} else if (page < 1) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		} else {
			normalizedPage = page;
		}

		// 사이즈 검증: null은 기본값, 1 미만이나 100 초과는 오류
		int normalizedSize;
		if (size == null) {
			normalizedSize = DEFAULT_SIZE;
		} else if (size < 1 || size > MAX_SIZE) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		} else {
			normalizedSize = size;
		}

		boolean normalizedWithTotal = withTotal == null || withTotal;

		if (regionId != null && regionId <= 0) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		}
		validateCategory(category);

		return PopupListQuery.builder()
				.regionId(regionId)
				.category(category)
				.keyword(query.getKeyword())
				.storeId(query.getStoreId())
				.page(normalizedPage)
				.size(normalizedSize)
				.withTotal(normalizedWithTotal)
				.build();
	}

	public void validateDetailQuery(PopupDetailQuery query) {
		if (query == null || query.getPopupId() == null) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		}
	}

	public PopupScheduleListQuery normalizeSessionQuery(PopupScheduleListQuery query) {
		if (query == null || query.getPopupId() == null) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		}
		if (query.getFrom() != null && query.getTo() != null
				&& query.getFrom().isAfter(query.getTo())) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		}
		return query;
	}

	private void validateCategory(PopupCategory category) {
		if (category == null) {
			return;
		}
		try {
			PopupCategory.valueOf(category.name());
		} catch (IllegalArgumentException ex) {
			throw new PopupException(PopupResponseCode.INVALID_REQUEST);
		}
	}
}
