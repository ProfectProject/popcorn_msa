package com.popcorn.store.domain.popup.controller;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleCapacity;
import com.popcorn.store.domain.popup.exception.PopupException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.common.versioning.ApiVersion;
import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.service.PopupService;
import com.popcorn.store.domain.popup.service.ScheduleInventoryApiService;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Popup", description = "팝업 관리 API")
@ApiVersion("v1")
@RequestMapping("/api/stores/v1/popups")
@RequiredArgsConstructor
public class PopupController extends BaseController {

	private final PopupService popupService;
	private final ScheduleInventoryApiService scheduleInventoryApiService;

	@Operation(
			summary = "팝업 목록 조회",
			description = """
				다양한 조건을 통해 팝업 목록을 조회할 수 있습니다.

				**주요 기능:**
				- 카테고리별 필터링 (FOOD, IDOL, EXHIBITION 등 14개 카테고리)
				- 키워드 검색 (제목, 설명 기준)
				- 지역별 필터링
				- 특정 스토어 팝업만 조회
				- 페이징 처리 (기본 20개, 최대 100개)

				**사용 예시:**
				- 전체 조회: /api/v1/popups
				- 카테고리 필터: /api/v1/popups?category=FOOD
				- 검색: /api/v1/popups?keyword=팝업&category=FOOD&page=1&size=10
				"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "팝업 목록 조회 성공",
			content = @Content(
					schema = @Schema(implementation = PopupListResponse.class),
					examples = @ExampleObject(
							name = "성공 응답 예시",
							value = """
							{
							  "code": 200,
							  "message": "요청이 성공했습니다.",
							  "data": {
							    "items": [
							      {
							        "id": "00000000-0000-0000-0000-000000000101",
							        "storeId": "f0000000-0000-0000-0000-000000000001",
							        "title": "팝업 테스트 이벤트",
							        "description": "팝업 세션 API 테스트를 위한 데모 이벤트입니다.",
							        "category": "FOOD",
							        "status": "OPEN",
							        "eventStartAt": "2025-01-15T10:00:00",
							        "eventEndAt": "2025-01-16T12:00:00"
							      }
							    ],
							    "page": 1,
							    "size": 20,
							    "total": 1
							  }
							}
							""")
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 파라미터",
			content = @Content(
					schema = @Schema(implementation = BaseResponse.class),
					examples = @ExampleObject(
							name = "파라미터 오류",
							value = """
							{
							  "code": 2100,
							  "message": "잘못된 요청입니다."
							}
							""")
			)
	)
	@GetMapping
	@Cacheable(value = "popup-list", key = "#regionId + '-' + #category + '-' + #keyword + '-' + #storeId + '-' + #page + '-' + #size",
	          unless = "#result.body.data == null", condition = "#page <= 10")
	@Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public ResponseEntity<BaseResponse<PopupListResponse>> getPopups(
			@Parameter(description = "지역 필터", example = "101")
			@org.springframework.web.bind.annotation.RequestParam(required = false) Long regionId,
			@Parameter(description = "카테고리(FOOD/IDOL/EXHIBITION/WORKSHOP/FASHION/BEAUTY/LIFESTYLE/ART/GAME/TECH/SPORTS/BOOK/PET/ETC)",
					example = "FOOD")
			@org.springframework.web.bind.annotation.RequestParam(required = false) PopupCategory category,
			@Parameter(description = "검색어", example = "팝업")
			@org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
			@Parameter(description = "가게 필터", example = "00000000-0000-0000-0000-000000000001")
			@org.springframework.web.bind.annotation.RequestParam(required = false) java.util.UUID storeId,
			@Parameter(description = "페이지(기본 1)", example = "1")
			@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "1") Integer page,
			@Parameter(description = "사이즈(기본 20, 최대 100)", example = "20")
			@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "20") Integer size,
			@Parameter(description = "전체 개수 포함 여부(기본 true)", example = "true")
			@org.springframework.web.bind.annotation.RequestParam(required = false) Boolean withTotal) {

		PopupListQuery requestQuery = PopupListQuery.builder()
				.regionId(regionId)
				.category(category)
				.keyword(keyword)
				.storeId(storeId)
				.page(page)
				.size(size)
				.withTotal(withTotal)
				.build();

		PopupListResponse response = popupService.getPopups(requestQuery);
		return ok(response);
	}

	@Operation(
			summary = "팝업 상세 조회",
			description = "팝업/상품 상세 정보를 조회합니다."
	)
	@ApiResponse(
			responseCode = "200",
			description = "팝업 상세 조회 성공",
			content = @Content(
					schema = @Schema(implementation = PopupDetailResponse.class),
					examples = @ExampleObject(value = """
							{
							  "code": 200,
							  "message": "요청이 성공했습니다.",
							  "data": {
							    "id": "00000000-0000-0000-0000-000000000101",
							    "storeId": "00000000-0000-0000-0000-000000000001",
							    "title": "Seed Popup 1",
							    "description": "예약형 팝업",
							    "category": "FOOD",
							    "status": "OPEN",
							    "eventStartAt": "2025-01-01T10:00:00",
							    "eventEndAt": "2025-01-05T18:00:00"
							  }
							}
							""")
			)
	)
	@ApiResponse(
			responseCode = "404",
			description = "팝업 정보를 찾을 수 없음",
			content = @Content(examples = @ExampleObject(value = """
					{
					  "code": 2101,
					  "message": "팝업 정보를 찾을 수 없습니다.",
					  "data": {
					    "code": 2101,
					    "message": "팝업 정보를 찾을 수 없습니다."
					  }
					}
					"""))
	)
	@GetMapping("/{popupId}")
	@Cacheable(value = "popup-detail", key = "#popupId", unless = "#result.body.data == null")
	@Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 50))
	public ResponseEntity<BaseResponse<PopupDetailResponse>> getPopupDetail(
			@Parameter(description = "상품 ID", required = true,
					example = "00000000-0000-0000-0000-000000000101")
			@PathVariable UUID popupId) {

		PopupDetailResponse response = popupService.getPopupDetail(PopupDetailQuery.of(popupId));
		return ok(response);
	}

}
