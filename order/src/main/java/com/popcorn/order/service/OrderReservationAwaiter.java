package com.popcorn.order.service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.popcorn.order.dto.payment.PaymentUrlResponse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderReservationAwaiter {

    private final ConcurrentHashMap<UUID, CompletableFuture<ReservationOutcome>> waiters = new ConcurrentHashMap<>();

    public void register(UUID orderId) {
        waiters.computeIfAbsent(orderId, id -> new CompletableFuture<>());
    }

    public void completeSuccess(UUID orderId, PaymentUrlResponse paymentUrl) {
        CompletableFuture<ReservationOutcome> future = waiters.computeIfAbsent(orderId, id -> new CompletableFuture<>());
        ReservationOutcome outcome = ReservationOutcome.success(paymentUrl);
        if (future.complete(outcome)) {
            waiters.remove(orderId, future);
        }
    }

    public void completeFailure(UUID orderId, String reason) {
        CompletableFuture<ReservationOutcome> future = waiters.computeIfAbsent(orderId, id -> new CompletableFuture<>());
        ReservationOutcome outcome = ReservationOutcome.failure(reason);
        if (future.complete(outcome)) {
            waiters.remove(orderId, future);
        }
    }

    public ReservationOutcome await(UUID orderId, Duration timeout) throws TimeoutException {
        CompletableFuture<ReservationOutcome> future = waiters.computeIfAbsent(orderId, id -> new CompletableFuture<>());

        // 즉시 체크: 이미 완료된 경우 바로 반환
        if (future.isDone()) {
            try {
                waiters.remove(orderId, future);
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("완료된 예약 결과 조회 실패", e);
            }
        }

        try {
            log.debug("⏳ 예약 응답 대기 시작 - orderId: {}, timeout: {}ms", orderId, timeout.toMillis());
            ReservationOutcome result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            waiters.remove(orderId, future);
            log.info("✅ 예약 응답 성공 - orderId: {}", orderId);
            return result;
        } catch (TimeoutException e) {
            waiters.remove(orderId, future);
            log.warn("⏱️ 예약 응답 타임아웃 - orderId: {}", orderId);
            throw e;
        } catch (Exception e) {
            waiters.remove(orderId, future);
            log.error("❌ 예약 응답 대기 중 오류 - orderId: {}, error: {}", orderId, e.getMessage());
            throw new RuntimeException("예약 응답 대기 중 오류가 발생했습니다.", e);
        }
    }

    @Getter
    public static class ReservationOutcome {
        private final boolean success;
        private final String failureReason;
        private final PaymentUrlResponse paymentUrl;

        private ReservationOutcome(boolean success, String failureReason, PaymentUrlResponse paymentUrl) {
            this.success = success;
            this.failureReason = failureReason;
            this.paymentUrl = paymentUrl;
        }

        public static ReservationOutcome success(PaymentUrlResponse paymentUrl) {
            return new ReservationOutcome(true, null, paymentUrl);
        }

        public static ReservationOutcome failure(String reason) {
            return new ReservationOutcome(false, reason, null);
        }
    }
}
