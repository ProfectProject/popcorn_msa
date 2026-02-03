package com.popcorn.store.domain.popup.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.popcorn.common.controller.BaseController;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.popup.dto.query.response.SessionPriceResponse;
import com.popcorn.store.domain.popup.service.PopupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 세션 가격 조회 API 컨트롤러
 * Order 서비스의 가격 조회 요청을 처리합니다.
 */
@RestController
@Tag(name = "Session Price", description = "세션 가격 조회 API")
@RequestMapping("/api/stores/v1/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionPriceController extends BaseController {

    private final PopupService popupService;

    @Operation(
        summary = "세션 가격 조회",
        description = "세션 ID로 팝업 스케줄의 가격 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "가격 조회 성공",
            content = @Content(schema = @Schema(implementation = SessionPriceResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "세션을 찾을 수 없음"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류"
        )
    })
    @GetMapping("/{sessionId}/price")
    public ResponseEntity<BaseResponse<SessionPriceResponse>> getSessionPrice(
        @Parameter(description = "세션 ID", required = true, example = "00000000-0000-0000-0000-000000000201")
        @PathVariable UUID sessionId
    ) {
        log.info("💰 [Store] 세션 가격 조회 요청 - sessionId: {}", sessionId);

        SessionPriceResponse response = popupService.getSessionPrice(sessionId);

        log.info("✅ [Store] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, response.getPrice());
        return ok(response);
    }
}