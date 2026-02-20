package com.example.orderquery.domain.itemView.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.example.orderquery.domain.itemView.dto.OrderItemQuery;
import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;

import jakarta.persistence.criteria.Predicate;

public final class OrderItemViewSpecifications {

    private OrderItemViewSpecifications() {
    }

    public static Specification<OrderItemView> byStorePopupAndFilters(UUID storeId,
                                                                      UUID popupId,
                                                                      OrderItemQuery query,
                                                                      OrderStatus orderStatus,
                                                                      PaymentStatus paymentStatus) {
        return (root, cq, cb) -> {
            Predicate predicate = cb.equal(root.get("storeId"), storeId);
            predicate = cb.and(predicate, cb.equal(root.get("id").get("popupId"), popupId));

            if (orderStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("orderStatus"), orderStatus));
            }
            if (paymentStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (query.getItemType() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("itemType"), query.getItemType()));
            }
            if (query.getCheckedIn() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("checkedIn"), query.getCheckedIn()));
            }
            if (query.getFrom() != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("orderedAt"), query.getFrom()));
            }
            if (query.getTo() != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("orderedAt"), query.getTo()));
            }

            return predicate;
        };
    }

    // === 📊 대시보드 필터링 메서드들 ===

    /**
     * 주문 상태로 필터링
     */
    public static Specification<OrderItemView> hasStatus(OrderStatus status) {
        return (root, query, builder) ->
            status != null ? builder.equal(root.get("orderStatus"), status) : null;
    }

    /**
     * 생성일 범위로 필터링
     */
    public static Specification<OrderItemView> createdAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, builder) -> {
            if (startDate != null && endDate != null) {
                return builder.between(root.get("createdAt"), startDate, endDate);
            } else if (startDate != null) {
                return builder.greaterThanOrEqualTo(root.get("createdAt"), startDate);
            } else if (endDate != null) {
                return builder.lessThanOrEqualTo(root.get("createdAt"), endDate);
            }
            return null;
        };
    }

    /**
     * 사용자 ID로 필터링
     */
    public static Specification<OrderItemView> hasUserId(Long userId) {
        return (root, query, builder) ->
            userId != null ? builder.equal(root.get("userId"), userId) : null;
    }

    /**
     * 🏪 스토어 ID로 필터링
     */
    public static Specification<OrderItemView> hasStoreId(UUID storeId) {
        return (root, query, builder) ->
            storeId != null ? builder.equal(root.get("storeId"), storeId) : null;
    }

    /**
     * 총 금액 범위로 필터링
     */
    public static Specification<OrderItemView> totalAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, builder) -> {
            if (minAmount != null && maxAmount != null) {
                return builder.between(root.get("linePrice"), minAmount.intValue(), maxAmount.intValue());
            } else if (minAmount != null) {
                return builder.greaterThanOrEqualTo(root.get("linePrice"), minAmount.intValue());
            } else if (maxAmount != null) {
                return builder.lessThanOrEqualTo(root.get("linePrice"), maxAmount.intValue());
            }
            return null;
        };
    }

    /**
     * 팝업 ID로 필터링
     */
    public static Specification<OrderItemView> hasPopupId(String popupId) {
        return (root, query, builder) ->
            popupId != null ? builder.equal(root.get("id").get("popupId"), UUID.fromString(popupId)) : null;
    }

    /**
     * 키워드로 검색 (상품명, 주문번호 등)
     */
    public static Specification<OrderItemView> searchByKeyword(String keyword, String searchScope) {
        return (root, query, builder) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return null;
            }

            String pattern = "%" + keyword.trim().toLowerCase() + "%";

            switch (searchScope != null ? searchScope.toLowerCase() : "all") {
                case "goods":
                    return builder.like(builder.lower(root.get("goodsName")), pattern);
                case "order":
                    return builder.like(builder.lower(root.get("orderNo")), pattern);
                case "user":
                    return builder.like(builder.lower(root.get("userId").as(String.class)), pattern);
                default:
                    return builder.or(
                        builder.like(builder.lower(root.get("goodsName")), pattern),
                        builder.like(builder.lower(root.get("orderNo")), pattern)
                    );
            }
        };
    }
}
