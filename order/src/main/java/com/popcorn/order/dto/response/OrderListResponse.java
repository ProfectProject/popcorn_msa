package com.popcorn.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 목록 조회 응답 DTO (Store 서비스 방식)
 *
 * [설계 가이드]
 * Store 서비스의 PopupListResponse와 동일한 구조:
 * - items: 실제 데이터 배열
 * - page: 현재 페이지 번호
 * - size: 페이지 크기
 * - total: 전체 개수
 *
 * 이 구조의 장점:
 * - 일관된 API 응답 형식
 * - 프론트엔드에서 페이징 처리 용이
 * - 성능 최적화 (withTotal=false로 count 쿼리 생략 가능)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponse {

    /** 주문 목록 */
    private List<OrderItemDto> items;

    /** 현재 페이지 번호 */
    private int page;

    /** 페이지 크기 */
    private int size;

    /** 전체 주문 개수 (-1이면 count 미계산) */
    private long total;

    /**
     * 주문 목록 항목 DTO (내부 클래스)
     *
     * [초보자 가이드]
     * static nested class를 사용하는 이유:
     * - OrderListResponse와 밀접하게 관련된 데이터 구조
     * - 외부에서 OrderListResponse.OrderItemDto로 접근 가능
     * - 패키지 구조를 깔끔하게 유지
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        /** 주문 ID */
        private UUID orderId;

        /** 주문 번호 */
        private String orderNo;

        /** 주문 타입 */
        private ItemType orderType;

        /** 주문 상태 */
        private OrderStatus status;

        /** 팝업 ID */
        private UUID popupId;

        /** 고객 ID */
        private Long customerId;

        /** 총 주문 금액 */
        private Integer totalAmount;

        /** 취소 가능 시한 */
        private LocalDateTime cancelableUntil;

        /** 주문 생성 시간 */
        private LocalDateTime createdAt;

        /** 주문 수정 시간 */
        private LocalDateTime updatedAt;

        /**
         * Order 엔티티로부터 OrderItemDto 생성
         * @param order 주문 엔티티
         * @return 변환된 DTO
         */
        public static OrderItemDto fromEntity(Order order) {
            return OrderItemDto.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .orderType(order.getOrderType())
                    .status(order.getStatus())
                    .popupId(order.getPopupId())
                    .customerId(order.getCustomerId())
                    .totalAmount(order.getTotalAmount())
                    .cancelableUntil(order.getCancelableUntil())
                    .createdAt(order.getCreatedAt())
                    .updatedAt(order.getUpdatedAt())
                    .build();
        }
    }

    /**
     * 페이징된 Order 엔티티 목록으로부터 OrderListResponse 생성
     * @param orders 주문 엔티티 목록
     * @param page 현재 페이지
     * @param size 페이지 크기
     * @param total 전체 개수
     * @return 응답 DTO
     */
    public static OrderListResponse from(List<Order> orders, int page, int size, long total) {
        List<OrderItemDto> items = orders.stream()
                .map(OrderItemDto::fromEntity)
                .toList();

        return OrderListResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .build();
    }

    /**
     * 전체 개수 없이 OrderListResponse 생성 (성능 최적화)
     * @param orders 주문 엔티티 목록
     * @param page 현재 페이지
     * @param size 페이지 크기
     * @return 응답 DTO (total = -1)
     */
    public static OrderListResponse fromWithoutTotal(List<Order> orders, int page, int size) {
        return from(orders, page, size, -1L);
    }
}
