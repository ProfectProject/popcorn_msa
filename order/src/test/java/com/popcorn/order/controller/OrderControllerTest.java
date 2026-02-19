/*package com.popcorn.order.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.common.security.JwtAuthenticationFilter;
import com.popcorn.order.dto.response.OrderCreateResponse;
import com.popcorn.order.service.OrderCommandService;
import com.popcorn.order.service.OrderQueryService;
import com.popcorn.order.service.OrderDomainService;
import com.popcorn.order.util.PaymentTokenUtil;

/**
 * MSA 구조로 업데이트된 컨트롤러 테스트
 * OrderCommandController와 OrderQueryController를 테스트합니다.
 *
 * [Java 초보자를 위한 가이드]
 *
 * 테스트가 업데이트된 이유:
 * 1. MSA 구조: 여러 마이크로서비스와 통신하는 구조로 변경
 * 2. JWT 인증: 사용자 ID를 JWT에서 추출하는 방식으로 변경
 * 3. Store 서비스 연동: 이벤트 기반 조회로 변경
 *
 * 필요한 MockBean들:
 * - OrderCommandService, OrderQueryService: 비즈니스 로직
 * - OrderDomainService: 도메인 로직
 * - PaymentTokenUtil: JWT 토큰 관련
 */
/*@WebMvcTest({OrderCommandController.class, OrderQueryController.class})
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderCommandService orderCommandService;

    @MockBean
    private OrderQueryService orderQueryService;

    @MockBean
    private OrderDomainService orderDomainService;

    @MockBean
    private PaymentTokenUtil paymentTokenUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * MSA 구조에서의 주문 생성 API 테스트
     *
     * [변경사항]
     * 1. API 경로: /api/orders → /api/orders/v1 (MSA 버전 관리)
     * 2. JWT 인증: @WithMockUser로 테스트용 사용자 인증 추가
     * 3. userId 제거: JWT에서 자동 추출하므로 요청에서 제거
     */
    /*@Test
    @WithMockUser(username = "1", roles = "USER") // 테스트용 JWT 사용자 (ID: 1, ROLE: USER)
    void 주문생성_API_테스트() throws Exception {
        // Given
        OrderCreateResponse response = OrderCreateResponse.builder()
                .orderId(UUID.randomUUID())
                .orderNo("ORD-123456")
                .build();

        when(orderCommandService.createOrder(any())).thenReturn(response);

        // MSA 구조에서는 JWT에서 사용자 ID를 추출하므로 userId를 요청에서 제거
        String requestBody = """
                {
                    "popupId": "550e8400-e29b-41d4-a716-446655440000",
                    "orderType": "RESERVATION",
                    "items": []
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/orders/v1") // MSA 버전 경로 사용
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(csrf())) // CSRF 토큰 추가 (Spring Security 테스트용)
                .andExpect(status().isOk());
    }

    /**
     * JWT 인증이 없는 경우 테스트 (401 Unauthorized 예상)
     */
    /*@Test
    void 인증없이_주문생성_실패_테스트() throws Exception {
        String requestBody = """
                {
                    "popupId": "550e8400-e29b-41d4-a716-446655440000",
                    "orderType": "RESERVATION",
                    "items": []
                }
                """;

        // JWT 인증 없이 요청
        /*mockMvc.perform(post("/api/orders/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized()); // 401 Unauthorized 예상
    }

}*/
