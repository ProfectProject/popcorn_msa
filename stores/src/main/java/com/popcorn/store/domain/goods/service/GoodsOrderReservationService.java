package com.popcorn.store.domain.goods.service;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.repository.GoodsOrderReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoodsOrderReservationService {

    private final GoodsOrderReservationRepository reservationRepository;

    @Transactional
    public GoodsOrderReservation createReservation(UUID orderId, String orderNo, UUID popupId,
                                                   UUID goodsId, UUID scheduleId,
                                                   int quantity, ReservationType reservationType) {
        GoodsOrderReservation reservation = GoodsOrderReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .goodsId(goodsId)
                .scheduleId(scheduleId)
                .quantity(quantity)
                .reservationType(reservationType)
                .status(ReservationStatus.HELD)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return reservationRepository.save(reservation);
    }

    @Transactional
    public GoodsOrderReservation createGoodsReservation(UUID orderId, String orderNo, UUID popupId,
                                                        UUID goodsId, int quantity) {
        return createReservation(orderId, orderNo, popupId, goodsId, null,
                quantity, ReservationType.GOODS);
    }

    @Transactional
    public GoodsOrderReservation createScheduleReservation(UUID orderId, String orderNo, UUID popupId,
                                                          UUID scheduleId, int quantity) {
        return createReservation(orderId, orderNo, popupId, null, scheduleId,
                quantity, ReservationType.SCHEDULE);
    }

    @Transactional(readOnly = true)
    public List<GoodsOrderReservation> findByOrderId(UUID orderId) {
        return reservationRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<GoodsOrderReservation> findByOrderIdAndType(UUID orderId, ReservationType reservationType) {
        return reservationRepository.findByOrderIdAndReservationType(orderId, reservationType);
    }

    @Transactional(readOnly = true)
    public Optional<GoodsOrderReservation> findLatestGoodsReservation(UUID orderId, UUID goodsId) {
        if (orderId == null || goodsId == null) {
            return Optional.empty();
        }
        return reservationRepository.findFirstByOrderIdAndGoodsIdAndReservationTypeOrderByCreatedAtDesc(
                orderId, goodsId, ReservationType.GOODS);
    }

    @Transactional(readOnly = true)
    public Optional<GoodsOrderReservation> findLatestScheduleReservation(UUID orderId, UUID scheduleId) {
        if (orderId == null || scheduleId == null) {
            return Optional.empty();
        }
        return reservationRepository.findFirstByOrderIdAndScheduleIdAndReservationTypeOrderByCreatedAtDesc(
                orderId, scheduleId, ReservationType.SCHEDULE);
    }

    @Transactional(readOnly = true)
    public Optional<GoodsOrderReservation> findExistingGoodsReservation(UUID orderId, UUID popupId, UUID goodsId) {
        if (orderId == null || popupId == null || goodsId == null) {
            return Optional.empty();
        }
        return reservationRepository.findFirstByOrderIdAndPopupIdAndGoodsIdAndReservationTypeOrderByCreatedAtDesc(
                orderId, popupId, goodsId, ReservationType.GOODS);
    }

    @Transactional(readOnly = true)
    public Optional<GoodsOrderReservation> findExistingScheduleReservation(UUID orderId, UUID popupId, UUID scheduleId) {
        if (orderId == null || popupId == null || scheduleId == null) {
            return Optional.empty();
        }
        return reservationRepository.findFirstByOrderIdAndPopupIdAndScheduleIdAndReservationTypeOrderByCreatedAtDesc(
                orderId, popupId, scheduleId, ReservationType.SCHEDULE);
    }

    @Transactional
    public void updateStatus(GoodsOrderReservation reservation, ReservationStatus status, String failureReason) {
        reservation.setStatus(status);
        reservation.setFailureReason(failureReason);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }
}
