package com.popcorn.order.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.popcorn.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 주문 엔티티 (JPA)
 * BaseEntity를 상속받아 표준화된 감사 필드를 포함합니다.
 *
 * 매핑 테이블: p_orders
 *
 * 주요 기능:
 * - 예약형/구매형 주문 통합 관리
 * - 주문 상태 변화와 취소 정책 관리
 * - 주문 번호 자동 생성
 *
 * [초보자 가이드]
 * 이 클래스는 주문 정보를 데이터베이스에 저장하는 엔티티입니다.
 * @Entity: 이 클래스가 데이터베이스 테이블과 매핑됨을 의미
 * @Table: 실제 데이터베이스 테이블 이름 지정
 * @Column: 각 필드가 어떤 데이터베이스 컬럼과 매핑되는지 지정
 */
@Entity
@Table(name = "p_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class Order extends BaseEntity {

    // ========================= 기본 필드 =========================

    /** 주문 ID (Primary Key) - 데이터베이스에서 자동 생성되는 고유 식별자 */
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "order_id")
    private UUID id;

    /** 주문 번호 (고유 식별자) - 사용자에게 보여지는 주문 번호 */
    @Column(name = "order_no")
    private String orderNo;

    /** 고객 ID - 주문한 고객의 식별자 */
    @Column(name = "user_id")
    private Long customerId;

    /** 팝업 ID - 주문이 속한 팝업의 식별자 */
    @Column(name = "popup_id")
    private UUID popupId;

    /** 주문 타입 - 예약형/구매형/혼합형 구분 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::orders.itemtype")
    @Column(name = "order_type", nullable = false, columnDefinition = "orders.itemtype")
    private ItemType orderType;

    /** 주문 상태 - 현재 주문이 어떤 단계에 있는지 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::orders.orderstatus")
    @Column(name = "status", nullable = false, columnDefinition = "orders.orderstatus")
    private OrderStatus status;

    /** 취소 가능 시간 - 이 시간 이후로는 주문 취소 불가 */
    @Column(name = "cancelable_until")
    private LocalDateTime cancelableUntil;

    /** 총 주문 금액 (원 단위) */
    @Column(name = "total_price")
    private Integer totalAmount;

    /** 결제 완료 시간 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 주문 확정 시간 */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 주문 취소 시간 */
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** 취소 사유 */
    @Column(name = "cancel_reason")
    private String cancelReason;

    // TODO: 주소 정보는 별도 테이블로 관리하거나 향후 스키마 확장 필요
    // 현재 p_orders 테이블에는 주소 필드가 없음

    /** 주문 항목 목록 - 이 주문에 포함된 상품들의 리스트 */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    // BaseEntity에서 상속받는 필드들:
    // - createdAt: 생성 시간
    // - updatedAt: 수정 시간

    // ========================= 편의 메서드 =========================

    /**
     * 주문 번호 생성
     * 형식: O + YYYYMMDD + 6자리 시퀀스
     * 예: O20251230-000001
     *
     * [초보자 가이드]
     * static 메서드: 객체를 생성하지 않고도 호출할 수 있는 메서드
     * Order.generateOrderNo()로 바로 호출 가능
     */
    public static String generateOrderNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "O" + dateStr + "-" + uniqueId;
    }

    /**
     * 예약형 주문인지 확인
     * @return true면 예약 주문, false면 구매 주문
     */
    public boolean isReservationType() {
        return ItemType.RESERVATION.equals(orderType);
    }

    /**
     * 구매형 주문인지 확인
     * @return true면 구매 주문, false면 예약 주문
     */
    public boolean isGoodsType() {
        return ItemType.GOODS.equals(orderType);
    }

    /**
     * 혼합형 주문인지 확인
     * @return true면 혼합 주문 (예약 + 굿즈)
     */
    public boolean isMixedType() {
        return ItemType.MIXED.equals(orderType);
    }

    /**
     * 현재 시점에서 취소 가능한지 확인
     * @return 취소 가능하면 true, 불가능하면 false
     *
     * [초보자 가이드]
     * 현재 시간이 취소 가능 시간보다 이전이면 취소 가능
     */
    public boolean isCancelable() {
        return cancelableUntil != null && LocalDateTime.now().isBefore(cancelableUntil);
    }

    /**
     * 총 주문 항목 수량 계산
     * @return 전체 상품의 수량 합계
     *
     * [초보자 가이드]
     * Stream API를 사용해서 각 주문 항목의 수량을 모두 더함
     * mapToInt(): 각 객체에서 정수값을 추출
     * sum(): 모든 값을 더함
     */
    public int getTotalQuantity() {
        return orderItems.stream()
                .mapToInt(OrderItem::getQty)
                .sum();
    }

    /**
     * 주문 항목 추가
     * @param orderItem 추가할 주문 항목
     *
     * [초보자 가이드]
     * null 체크를 통해 안전하게 항목을 추가
     */
    public void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            return;
        }
        orderItems.add(orderItem);
    }

    /**
     * 주문 항목 목록 추가
     * @param items 추가할 주문 항목들의 리스트
     */
    public void addOrderItems(List<OrderItem> items) {
        if (items == null) {
            return;
        }
        items.forEach(this::addOrderItem);  // forEach + 메서드 레퍼런스 사용
    }

    /**
     * 주문 상태 변경 이력 생성
     * @param reason 변경 사유
     * @return 주문 상태 이력 객체
     */
    public OrderStatusHistory toHistory(String reason) {
        return OrderStatusHistory.builder()
                .orderId(this.id)
                .fromStatus(this.status)  // 현재 상태를 fromStatus로 (변경 전 상태)
                .toStatus(this.status)    // 현재 상태를 toStatus로 (변경 후 상태)
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 주문 상태 변경 이력 생성 (이전 상태 명시)
     * @param fromStatus 변경 전 상태
     * @param reason 변경 사유
     * @return 주문 상태 이력 객체
     */
    public OrderStatusHistory toHistory(OrderStatus fromStatus, String reason) {
        return OrderStatusHistory.builder()
                .orderId(this.id)
                .fromStatus(fromStatus)   // 변경 전 상태
                .toStatus(this.status)    // 현재 상태 (변경 후 상태)
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 주문 상태 업데이트
     * @param newStatus 새로운 주문 상태
     */
    public void updateStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("주문 상태는 null이 될 수 없습니다.");
        }

        OrderStatus oldStatus = this.status;
        this.status = newStatus;
        setUpdatedAt(LocalDateTime.now());

        // 상태별 특별 처리
        if (newStatus == OrderStatus.PAID) {
            this.paidAt = LocalDateTime.now();
        } else if (newStatus == OrderStatus.COMPLETED) {
            this.confirmedAt = LocalDateTime.now();
        } else if (newStatus == OrderStatus.CANCELLED) {
            this.canceledAt = LocalDateTime.now();
        }

        // 로그 출력
        log.info("주문 상태 변경: {} -> {} (주문ID: {})", oldStatus, newStatus, this.id);
    }

    /**
     * 주문 취소 사유 설정
     * @param reason 취소 사유
     */
    public void setCancellationReason(String reason) {
        this.cancelReason = reason;
        setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 주문 상태 조회 (OrderStatus enum 반환)
     * @return 현재 주문 상태
     */
    public OrderStatus getOrderStatus() {
        return this.status;
    }

    /**
     * 결제 완료 처리
     * 주문 상태를 PAID로 변경하고 결제 완료 시간을 기록
     */
    public void markAsPaid() {
        updateStatus(OrderStatus.PAID);
    }

    /**
     * 주문 확정 처리
     * 주문 상태를 COMPLETED로 변경하고 확정 시간을 기록
     */
    public void markAsConfirmed() {
        updateStatus(OrderStatus.COMPLETED);
    }

    /**
     * 주문 취소 처리
     * @param reason 취소 사유
     */
    public void markAsCancelled(String reason) {
        updateStatus(OrderStatus.CANCELLED);
        setCancellationReason(reason);
    }

    /**
     * 주문이 결제 완료 상태인지 확인
     * @return 결제 완료 여부
     */
    public boolean isPaid() {
        return this.status == OrderStatus.PAID;
    }

    /**
     * 주문이 확정 상태인지 확인
     * @return 확정 여부
     */
    public boolean isConfirmed() {
        return this.status == OrderStatus.COMPLETED;
    }

    /**
     * 주문이 취소 상태인지 확인
     * @return 취소 여부
     */
    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }

}
