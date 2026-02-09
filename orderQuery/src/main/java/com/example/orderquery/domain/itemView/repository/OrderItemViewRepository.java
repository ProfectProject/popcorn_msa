package com.example.orderquery.domain.itemView.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderItemViewId;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;

public interface OrderItemViewRepository extends JpaRepository<OrderItemView, OrderItemViewId>,
        JpaSpecificationExecutor<OrderItemView> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.checkedIn = true, v.checkinAt = :checkinAt " +
            "where v.storeId = :storeId and v.id.popupId = :popupId and v.id.orderGoodsId = :orderGoodsId")
    int markCheckedIn(@Param("storeId") UUID storeId,
                      @Param("popupId") UUID popupId,
                      @Param("orderGoodsId") UUID orderGoodsId,
                      @Param("checkinAt") LocalDateTime checkinAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.orderStatus = :orderStatus " +
            "where v.id.popupId = :popupId and v.orderId = :orderId")
    int updateOrderStatus(@Param("popupId") UUID popupId,
                          @Param("orderId") UUID orderId,
                          @Param("orderStatus") OrderStatus orderStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.paymentStatus = :paymentStatus, v.paymentApprovedAt = :approvedAt " +
            "where v.id.popupId = :popupId and v.orderId = :orderId")
    int updatePaymentStatus(@Param("popupId") UUID popupId,
                            @Param("orderId") UUID orderId,
                            @Param("paymentStatus") PaymentStatus paymentStatus,
                            @Param("approvedAt") LocalDateTime approvedAt);
}
