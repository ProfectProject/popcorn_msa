/*package com.popcorn.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;

/**
 * 간단한 Order 서비스 테스트 (커버리지용)
 *
 * [초보자 가이드]
 * - 복잡한 로직 대신 기본 기능만 테스트
 * - Mock을 사용해서 외부 의존성 제거
 * - 80% 커버리지를 위한 기본 테스트들
 */
/*@ExtendWith(MockitoExtension.class)
@DisplayName("간단한 주문 서비스 테스트 ✅")
class SimpleOrderServiceTest {

    @Mock
    private OrderDomainService orderDomainService;

    @InjectMocks
    private OrderDomainService realDomainService = new OrderDomainService();

    @BeforeEach
    void setUp() {
        // 테스트 준비
    }

    @Test
    @DisplayName("✅ 상태 전환 규칙 테스트")
    void 상태전환_규칙테스트() {
        // Given & When & Then: 기본 상태 전환 테스트
        assertTrue(realDomainService.canChangeStatus(OrderStatus.REQUESTED, OrderStatus.ACCEPTED),
                "요청됨 → 수락됨 가능");
        assertTrue(realDomainService.canChangeStatus(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
                "수락됨 → 취소됨 가능");
        assertFalse(realDomainService.canChangeStatus(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
                "완료됨 → 취소됨 불가");
    }

    @Test
    @DisplayName("✅ 총 금액 계산 테스트")
    void 총금액_계산테스트() {
        // Given: 테스트용 주문 항목들
        OrderItem item1 = OrderItem.builder()
                .lineAmount(10000)
                .build();
        OrderItem item2 = OrderItem.builder()
                .lineAmount(20000)
                .build();

        List<OrderItem> items = List.of(item1, item2);

        // When: 총 금액 계산
        Integer totalAmount = realDomainService.calculateTotalAmount(items);

        // Then: 합계 확인
        assertEquals(30000, totalAmount, "총 금액 = 10000 + 20000");
    }

    @Test
    @DisplayName("✅ 빈 목록 총 금액은 0")
    void 빈목록_총금액_0() {
        // When & Then
        Integer totalAmount = realDomainService.calculateTotalAmount(List.of());
        assertEquals(0, totalAmount, "빈 목록의 총 금액은 0");
    }

    @Test
    @DisplayName("✅ 취소 가능 시간 계산")
    void 취소가능시간_계산() {
        // When: 예약형 취소 가능 시간 계산
        var cancelableTime = realDomainService.calculateCancelableUntil(ItemType.RESERVATION);

        // Then: 현재 시간보다 미래여야 함
        assertNotNull(cancelableTime, "취소 가능 시간 설정");
        assertTrue(cancelableTime.isAfter(java.time.LocalDateTime.now().plusMinutes(4)),
                "최소 4분 후");
    }

    @Test
    @DisplayName("✅ 결제 관련 상태 확인")
    void 결제관련_상태확인() {
        // Given & When & Then
        assertTrue(realDomainService.isPaymentRelatedStatus(OrderStatus.PAYMENT_PENDING),
                "PAYMENT_PENDING은 결제 관련");
        assertTrue(realDomainService.isPaymentRelatedStatus(OrderStatus.PAID),
                "PAID는 결제 관련");
        assertFalse(realDomainService.isPaymentRelatedStatus(OrderStatus.REQUESTED),
                "REQUESTED는 결제 관련 아님");
    }

    @Test
    @DisplayName("✅ 주문 생성 기본 테스트")
    void 주문생성_기본테스트() {
        // Given: 기본 데이터
        Long customerId = 123L;
        UUID storeId = UUID.randomUUID();
        UUID popupId = UUID.randomUUID();
        ItemType orderType = ItemType.RESERVATION;

        OrderItem orderItem = OrderItem.builder()
                .orderItemType(ItemType.RESERVATION)
                .qty(1)
                .unitPrice(15000)
                .lineAmount(15000)
                .build();

        List<OrderItem> orderItems = List.of(orderItem);

        // When: 주문 생성
        Order order = realDomainService.createOrder(customerId, popupId, orderType, orderItems);

        // Then: 기본 검증
        assertNotNull(order, "주문 생성됨");
        assertEquals(customerId, order.getCustomerId(), "고객 ID 일치");
        assertEquals(OrderStatus.REQUESTED, order.getStatus(), "초기 상태는 요청됨");
        assertEquals(15000, order.getTotalAmount(), "총 금액");
        assertNotNull(order.getOrderNo(), "주문 번호 생성됨");
        assertTrue(order.getOrderNo().startsWith("O"), "주문 번호는 O로 시작");
    }

    @Test
    @DisplayName("✅ Order 엔티티 기본 메서드들")
    void Order_엔티티_기본메서드() {
        // Given: Order 빌더로 주문 생성
        Order order = Order.builder()
                .customerId(123L)
                .popupId(UUID.randomUUID())
                .orderType(ItemType.RESERVATION)
                .status(OrderStatus.REQUESTED)
                .totalAmount(15000)
                .build();

        // Then: 기본 메서드들 테스트
        assertNotNull(order, "Order 생성됨");
        assertEquals(123L, order.getCustomerId(), "고객 ID");
        assertEquals(ItemType.RESERVATION, order.getOrderType(), "주문 타입");
        assertTrue(order.isReservationType(), "예약형 확인");
        assertFalse(order.isGoodsType(), "구매형 아님");
        assertEquals(OrderStatus.REQUESTED, order.getStatus(), "상태");
    }

    @Test
    @DisplayName("✅ OrderItem 엔티티 기본 테스트")
    void OrderItem_엔티티_기본테스트() {
        // Given: OrderItem 생성
        OrderItem orderItem = OrderItem.builder()
                .orderItemType(ItemType.RESERVATION)
                .sessionOptionId(UUID.randomUUID())
                .qty(2)
                .unitPrice(10000)
                .lineAmount(20000)
                .build();

        // Then: 기본 검증
        assertNotNull(orderItem, "OrderItem 생성됨");
        assertEquals(ItemType.RESERVATION, orderItem.getOrderType(), "타입 확인");
        assertTrue(orderItem.isReservationType(), "예약형 확인");
        assertFalse(orderItem.isGoodsType(), "굿즈형 아님");
        assertEquals(2, orderItem.getQty(), "수량");
        assertEquals(10000, orderItem.getUnitPrice(), "단가");
        assertEquals(20000, orderItem.getLineAmount(), "라인 금액");
    }

    @Test
    @DisplayName("✅ 주문 번호 생성 테스트")
    void 주문번호_생성테스트() {
        // When: 주문 번호 생성
        String orderNo1 = Order.generateOrderNo();
        String orderNo2 = Order.generateOrderNo();

        // Then: 형식 확인
        assertNotNull(orderNo1, "주문번호 생성");
        assertTrue(orderNo1.startsWith("O"), "O로 시작");
        assertTrue(orderNo1.contains("-"), "하이픈 포함");
        assertNotEquals(orderNo1, orderNo2, "유니크한 주문번호");
    }

    @Test
    @DisplayName("✅ 주문 상태 enum 값들")
    void 주문상태_enum값들() {
        // When & Then: 모든 상태 존재 확인
        OrderStatus[] statuses = OrderStatus.values();
        assertTrue(statuses.length >= 8, "최소 8개 상태");

        // 주요 상태들 확인
        assertEquals("REQUESTED", OrderStatus.REQUESTED.name(), "REQUESTED 상태");
        assertEquals("ACCEPTED", OrderStatus.ACCEPTED.name(), "ACCEPTED 상태");
        assertEquals("CANCELLED", OrderStatus.CANCELLED.name(), "CANCELLED 상태");
    }

    @Test
    @DisplayName("✅ 주문 타입 enum 값들")
    void 주문타입_enum값들() {
        // When & Then
        ItemType[] types = ItemType.values();
        assertEquals(3, types.length, "3개 타입");

        assertEquals("RESERVATION", ItemType.RESERVATION.name(), "예약형");
        assertEquals("GOODS", ItemType.GOODS.name(), "구매형");
        assertEquals("MIXED", ItemType.MIXED.name(), "혼합형");
    }

}*/