package com.popcorn.order.service;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고 예약 타임아웃 관리 서비스
 *
 * [역할]
 * - 주기적으로 만료된 재고 예약을 확인하고 자동 취소 처리
 * - RESERVED 상태에서 30분 경과한 주문들을 CANCELLED로 변경
 * - 재고 예약 취소를 통해 다른 고객이 주문할 수 있도록 함
 *
 * [스케줄링]
 * - 5분마다 실행되어 만료된 예약을 확인
 * - Spring Boot의 @Scheduled 애노테이션 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationTimeoutService {

    private final OrderRepository orderRepository;
    private final OrderCommandService orderCommandService;

    /**
     * 만료된 재고 예약들을 주기적으로 취소합니다.
     *
     * [실행 주기]
     * - 매 5분마다 실행 (300,000ms = 5분)
     * - 새벽 시간대에도 실행되어 24시간 운영
     *
     * [처리 대상]
     * - RESERVED 상태인 주문 중
     * - 생성일시가 30분 이전인 주문들
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void cancelExpiredReservations() {
        try {
            log.info("만료된 재고 예약 취소 작업 시작");

            // 30분 이전 시점 계산
            LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(30);

            // RESERVED 상태이면서 30분 경과한 주문들 조회
            List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(
                    OrderStatus.RESERVED, expiredTime);

            if (expiredOrders.isEmpty()) {
                log.debug("만료된 재고 예약이 없습니다");
                return;
            }

            log.info("만료된 재고 예약 발견 - 처리 대상: {}건", expiredOrders.size());

            // 각 만료된 주문을 취소 처리
            int successCount = 0;
            int failureCount = 0;

            for (Order expiredOrder : expiredOrders) {
                try {
                    log.info("만료된 재고 예약 취소 처리 - 주문번호: {}, 생성시간: {}",
                            expiredOrder.getOrderNo(), expiredOrder.getCreatedAt());

                    // 주문 취소 (자동으로 재고 예약도 취소됨)
                    orderCommandService.cancelOrder(
                            expiredOrder.getId(),
                            "결제 타임아웃 (30분 경과)으로 인한 자동 취소"
                    );

                    successCount++;

                    log.info("만료된 재고 예약 취소 완료 - 주문번호: {}", expiredOrder.getOrderNo());

                } catch (Exception e) {
                    failureCount++;
                    log.error("만료된 재고 예약 취소 실패 - 주문번호: {}, 에러: {}",
                            expiredOrder.getOrderNo(), e.getMessage(), e);
                    // 개별 실패는 로그만 남기고 계속 진행
                }
            }

            log.info("만료된 재고 예약 취소 작업 완료 - 성공: {}건, 실패: {}건", successCount, failureCount);

        } catch (Exception e) {
            log.error("만료된 재고 예약 취소 작업 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 예약/재고 응답이 없는 주문을 타임아웃 처리합니다.
     *
     * [처리 대상]
     * - REQUESTED 상태이고
     * - cancelableUntil이 지난 주문들
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    @Transactional
    public void cancelStalledRequestedOrders() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now();
            List<Order> stalledOrders = orderRepository.findByStatusAndCancelableUntilBefore(
                    OrderStatus.REQUESTED, cutoffTime);

            if (stalledOrders.isEmpty()) {
                return;
            }

            for (Order stalledOrder : stalledOrders) {
                try {
                    log.warn("예약 응답 타임아웃 - 주문번호: {}, 생성시간: {}",
                            stalledOrder.getOrderNo(), stalledOrder.getCreatedAt());
                    orderCommandService.updateOrderStatus(
                            stalledOrder.getId(),
                            OrderStatus.CANCELLED.name(),
                            "예약 응답 타임아웃으로 자동 취소"
                    );
                } catch (Exception e) {
                    log.error("예약 응답 타임아웃 취소 실패 - 주문번호: {}, 에러: {}",
                            stalledOrder.getOrderNo(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("예약 응답 타임아웃 처리 중 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 만료 임박한 재고 예약들을 확인하고 알림을 보냅니다.
     *
     * [실행 주기]
     * - 매 10분마다 실행
     * - 결제 완료까지 5분 남은 주문들에게 알림 발송
     *
     * [알림 대상]
     * - RESERVED 상태인 주문 중
     * - 생성일시가 25분 전인 주문들 (만료 5분 전)
     */
    @Scheduled(fixedRate = 600000) // 10분마다 실행
    @Transactional(readOnly = true)
    public void notifyExpiringReservations() {
        try {
            log.debug("만료 임박 재고 예약 알림 작업 시작");

            // 25분 이전 시점 계산 (만료 5분 전)
            LocalDateTime warningTime = LocalDateTime.now().minusMinutes(25);

            // RESERVED 상태이면서 25분 경과한 주문들 조회
            List<Order> expiringOrders = orderRepository.findByStatusAndCreatedAtBefore(
                    OrderStatus.RESERVED, warningTime);

            if (expiringOrders.isEmpty()) {
                log.debug("만료 임박한 재고 예약이 없습니다");
                return;
            }

            log.info("만료 임박 재고 예약 발견 - 알림 대상: {}건", expiringOrders.size());

            for (Order expiringOrder : expiringOrders) {
                try {
                    log.info("만료 임박 재고 예약 알림 - 주문번호: {}, 생성시간: {}",
                            expiringOrder.getOrderNo(), expiringOrder.getCreatedAt());

                    // TODO: 실제 알림 발송 로직 구현
                    // - 푸시 알림: "결제 시간이 5분 남았습니다"
                    // - SMS: "주문 [주문번호]의 결제를 완료해주세요"

                    log.debug("만료 임박 알림 발송 완료 - 주문번호: {}", expiringOrder.getOrderNo());

                } catch (Exception e) {
                    log.error("만료 임박 알림 발송 실패 - 주문번호: {}, 에러: {}",
                            expiringOrder.getOrderNo(), e.getMessage(), e);
                    // 개별 실패는 로그만 남기고 계속 진행
                }
            }

            log.info("만료 임박 재고 예약 알림 작업 완료");

        } catch (Exception e) {
            log.error("만료 임박 재고 예약 알림 작업 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 수동으로 특정 주문의 재고 예약을 취소합니다.
     *
     * [사용 케이스]
     * - 관리자가 수동으로 예약을 취소해야 하는 경우
     * - 시스템 오류로 인해 특정 주문을 취소해야 하는 경우
     *
     * @param orderId 취소할 주문 ID
     * @param reason 취소 이유
     * @return 취소 성공 여부
     */
    public boolean manualCancelReservation(java.util.UUID orderId, String reason) {
        try {
            log.info("수동 재고 예약 취소 요청 - 주문ID: {}, 이유: {}", orderId, reason);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            if (!OrderStatus.RESERVED.equals(order.getStatus())) {
                log.warn("RESERVED 상태가 아닌 주문의 예약 취소 시도 - 주문ID: {}, 현재상태: {}",
                        orderId, order.getStatus());
                return false;
            }

            orderCommandService.cancelOrder(orderId, "수동 취소: " + reason);

            log.info("수동 재고 예약 취소 완료 - 주문ID: {}", orderId);
            return true;

        } catch (Exception e) {
            log.error("수동 재고 예약 취소 실패 - 주문ID: {}, 에러: {}", orderId, e.getMessage(), e);
            return false;
        }
    }
}
