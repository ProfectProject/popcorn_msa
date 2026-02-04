package com.popcorn.order.dto.command;

import java.util.List;
import java.util.UUID;

import com.popcorn.order.dto.request.OrderCreateRequest;
import com.popcorn.order.dto.request.OrderItemRequest;
import com.popcorn.order.entity.ItemType;

import lombok.Builder;
import lombok.Getter;

/**
 * 주문 생성 명령 DTO
 * - 서비스 계층으로 전달되는 입력 값
 * - 불변 객체로 사용합니다.
 *
 * Command 패턴: 요청을 객체로 캡슐화하는 패턴
 * DTO vs Command의 차이:
 * - DTO: 단순히 데이터를 전달하는 용도
 * - Command: 특정 작업을 수행하라는 명령을 나타냄
 *
 * 이 클래스는 "주문을 생성하라"는 명령과 그에 필요한 모든 정보를 담고 있습니다.
 */
@Getter
@Builder
public class CreateOrderCommand {

    /** 주문을 생성하는 사용자 ID */
    private final Long userId;

    /** 관련된 팝업 ID */
    private final UUID popupId;

    /** 주문 타입 - "RESERVATION" 또는 "PURCHASE" */
    private final String orderType;

    /** 주문 항목들 - 실제로 주문할 상품 목록 */
    private final List<OrderItemCommand> items;

    /**
     * 주문 항목 명령 (중첩 클래스)
     *
     * [초보자 가이드]
     * 중첩 클래스를 사용하는 이유:
     * - OrderItemCommand는 CreateOrderCommand와 밀접한 관련이 있음
     * - 외부에서 독립적으로 사용될 일이 거의 없음
     * - 네임스페이스를 깔끔하게 정리 가능
     */
    @Getter
    @Builder
    public static class OrderItemCommand {

        /** 주문 항목 타입 - RESERVATION 또는 GOODS */
        private final ItemType orderItemType;

        /** 세션 ID - 예약형 상품의 경우 */
        private final UUID sessionId;

        /** 옵션 ID - 세션의 추가 옵션 */
        private final UUID optionId;

        /** 굿즈 변형 ID - 구매형 상품의 경우 */
        private final UUID goodsId;

        /** 수량 */
        private final Integer qty;

        /** 단가 - 개당 가격 (원) */
        private final Integer unitPrice;

    }

    // ================ Factory Methods ================

    /**
     * 주문 생성 요청 DTO로부터 CreateOrderCommand 생성
     *
     * @param request 주문 생성 요청 DTO
     * @param userId JWT에서 추출한 사용자 ID
     * @return 변환된 주문 생성 명령
     */
    public static CreateOrderCommand fromRequest(OrderCreateRequest request, Long userId) {
        List<OrderItemCommand> itemCommands = request.getItems().stream()
                .map(CreateOrderCommand::convertOrderItemRequest)
                .toList();

        return CreateOrderCommand.builder()
                .userId(userId)
                .popupId(request.getPopupId())
                .orderType(request.getOrderType())
                .items(itemCommands)
                .build();
    }

    /**
     * 주문 항목 요청을 OrderItemCommand로 변환
     *
     * @param itemRequest 주문 항목 요청
     * @return 변환된 주문 항목 명령
     */
    private static OrderItemCommand convertOrderItemRequest(OrderItemRequest itemRequest) {
        return OrderItemCommand.builder()
                .orderItemType(ItemType.valueOf(itemRequest.getOrderItemType()))
                .sessionId(itemRequest.getSessionId())
                .goodsId(itemRequest.getGoodsId())
                .qty(itemRequest.getQty())
                .unitPrice(itemRequest.getUnitPrice())
                .build();
    }

}
