package com.popcorn.store.domain.goods.repository;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsOrderReservationRepository extends JpaRepository<GoodsOrderReservation, UUID> {

    List<GoodsOrderReservation> findByOrderId(UUID orderId);

    List<GoodsOrderReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    List<GoodsOrderReservation> findByOrderIdAndReservationType(UUID orderId, ReservationType type);

    Optional<GoodsOrderReservation> findFirstByOrderIdAndGoodsIdAndReservationTypeOrderByCreatedAtDesc(
            UUID orderId, UUID goodsId, ReservationType type);

    Optional<GoodsOrderReservation> findFirstByOrderIdAndScheduleIdAndReservationTypeOrderByCreatedAtDesc(
            UUID orderId, UUID scheduleId, ReservationType type);

    Optional<GoodsOrderReservation> findFirstByOrderIdAndPopupIdAndGoodsIdAndReservationTypeOrderByCreatedAtDesc(
            UUID orderId, UUID popupId, UUID goodsId, ReservationType type);

    Optional<GoodsOrderReservation> findFirstByOrderIdAndPopupIdAndScheduleIdAndReservationTypeOrderByCreatedAtDesc(
            UUID orderId, UUID popupId, UUID scheduleId, ReservationType type);

}
