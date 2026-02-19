/*package com.popcorn.order.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.popcorn.order.dto.request.OrderCreateRequest;
import com.popcorn.order.dto.request.OrderItemRequest;

/**
 * CreateOrderRequest 테스트 (MSA 구조로 업데이트됨)
 *
 * [Java 초보자를 위한 가이드]
 *
 * MSA 구조 변경사항:
 * 1. userId 제거: JWT에서 자동 추출하므로 요청에 포함하지 않음
 * 2. isPurchaseType() → isGoodsType()로 메소드명 변경
 * 3. "PURCHASE" → "GOODS"로 주문 타입 변경
 */
/*class CreateOrderRequestTest {

    @Test
    void 주문요청_생성_테스트() {
        // Given (준비) - 테스트에 필요한 데이터 만들기
        // MSA 구조에서는 userId를 JWT에서 추출하므로 요청에서 제거
        UUID popupId = UUID.randomUUID();
        String orderType = "RESERVATION";
        List<OrderItemRequest> items = List.of(
            OrderItemRequest.builder()
                .orderItemType("RESERVATION")
                .sessionId(UUID.randomUUID())
                .qty(1)
                .build()
        );

        // When (실행) - 실제 테스트할 코드 실행
        OrderCreateRequest request = OrderCreateRequest.builder()
                .popupId(popupId)
                .orderType(orderType)
                .items(items)
                .build();

        // Then (검증) - 결과가 올바른지 확인
        // userId는 JWT에서 추출하므로 테스트하지 않음
        assertEquals(popupId, request.getPopupId());
        assertEquals(orderType, request.getOrderType());
        assertNotNull(request.getItems());
        assertEquals(1, request.getItems().size());
    }

    @Test
    void 예약타입_확인_테스트() {
        // Given
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderType("RESERVATION")
                .build();

        // When & Then
        assertTrue(request.isReservationType());
        assertFalse(request.isGoodsType()); // isPurchaseType() → isGoodsType()로 변경
    }

    @Test
    void 구매타입_확인_테스트() {
        // Given
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderType("GOODS") // "PURCHASE" → "GOODS"로 변경
                .build();

        // When & Then
        assertTrue(request.isGoodsType()); // isPurchaseType() → isGoodsType()로 변경
        assertFalse(request.isReservationType());
    }

    @Test
    void 주문아이템_포함_테스트() {
        // Given
        OrderItemRequest item = OrderItemRequest.builder()
                .orderItemType("RESERVATION")
                .sessionId(UUID.randomUUID())
                .qty(2)
                .build();

        // When
        OrderCreateRequest request = OrderCreateRequest.builder()
                .items(List.of(item))
                .build();

        // Then
        assertNotNull(request.getItems());
        assertEquals(1, request.getItems().size());
        assertEquals("RESERVATION", request.getItems().get(0).getOrderType());
    }

    @Test
    void 총수량_계산_테스트() {
        // Given
        OrderItemRequest item1 = OrderItemRequest.builder().qty(3).build();
        OrderItemRequest item2 = OrderItemRequest.builder().qty(5).build();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .items(List.of(item1, item2))
                .build();

        // When & Then
        assertEquals(8, request.getTotalQuantity());
    }

}
*/