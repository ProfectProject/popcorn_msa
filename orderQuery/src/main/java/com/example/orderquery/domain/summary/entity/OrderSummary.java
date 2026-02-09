package com.example.orderquery.domain.summary.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.popcorn.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "popup_order_summary",
        indexes = {
                @Index(name = "idx_popup_summary_store", columnList = "store_id, popup_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderSummary extends BaseEntity {

    @Id
    @Column(name = "popup_id", nullable = false)
    private UUID popupId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "popup_title", length = 200)
    private String popupTitle;

    @Column(name = "popup_status", length = 30)
    private String popupStatus;

    @Column(name = "address_road", columnDefinition = "text")
    private String addressRoad;

    @Column(name = "address_detail", columnDefinition = "text")
    private String addressDetail;

    @Column(name = "reservation_open_at")
    private LocalDateTime reservationOpenAt;

    @Column(name = "owner_id")
    private Long ownerId;

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    // 예약 KPI
    @Column(name = "reservation_total_orders", nullable = false)
    private int reservationTotalOrders;

    @Column(name = "reservation_paid_orders", nullable = false)
    private int reservationPaidOrders;

    @Column(name = "reservation_cancelled_orders", nullable = false)
    private int reservationCancelledOrders;

    // 굿즈 KPI
    @Column(name = "goods_total_orders", nullable = false)
    private int goodsTotalOrders;

    @Column(name = "goods_paid_orders", nullable = false)
    private int goodsPaidOrders;

    @Column(name = "goods_cancelled_orders", nullable = false)
    private int goodsCancelledOrders;

    // 체크인 KPI
    @Column(name = "checked_in_orders", nullable = false)
    private int checkedInOrders;



    // ====== 도메인 업데이트 메서드 (summary 적용 로그 방식용) ======
    public void applyDeltas(int dReservationTotal, int dReservationPaid, int dReservationCancelled,
                            int dGoodsTotal, int dGoodsPaid, int dGoodsCancelled,
                            int dCheckedIn,
                            LocalDateTime appliedAt) {

        this.reservationTotalOrders += dReservationTotal;
        this.reservationPaidOrders += dReservationPaid;
        this.reservationCancelledOrders += dReservationCancelled;

        this.goodsTotalOrders += dGoodsTotal;
        this.goodsPaidOrders += dGoodsPaid;
        this.goodsCancelledOrders += dGoodsCancelled;

        this.checkedInOrders += dCheckedIn;

    }

    public void updatePopupInfo(String title,
                                String status,
                                String addressRoad,
                                String addressDetail,
                                LocalDateTime reservationOpenAt) {
        this.popupTitle = title;
        this.popupStatus = status;
        this.addressRoad = addressRoad;
        this.addressDetail = addressDetail;
        this.reservationOpenAt = reservationOpenAt;
    }
}
