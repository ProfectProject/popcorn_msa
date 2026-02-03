package com.popcorn.order.service.util;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import com.popcorn.order.dto.payment.PaymentUrlResponse;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OrderReservationAwaiter {

    private static final Logger log = LoggerFactory.getLogger(OrderReservationAwaiter.class);

    private static final long DEFAULT_WAITER_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long PRUNE_EVERY_MS = TimeUnit.SECONDS.toMillis(30);

    private final ConcurrentHashMap<UUID, WaiterEntry> waiters = new ConcurrentHashMap<>();
    private final AtomicLong lastPrunedAt = new AtomicLong(0L);

    @Value("${order.reservation.awaiter-ttl-ms:600000}")
    private long waiterTtlMs;

    public void register(UUID orderId) {
        pruneIfNeeded();
        waiters.computeIfAbsent(orderId, id -> new WaiterEntry(new CompletableFuture<>(), System.currentTimeMillis()));
    }

    public void completeSuccess(UUID orderId, PaymentUrlResponse paymentUrl) {
        pruneIfNeeded();
        WaiterEntry entry = waiters.computeIfAbsent(orderId,
                id -> new WaiterEntry(new CompletableFuture<>(), System.currentTimeMillis()));
        CompletableFuture<ReservationOutcome> future = entry.future;
        ReservationOutcome outcome = ReservationOutcome.success(paymentUrl);
        if (future.complete(outcome)) {
            waiters.remove(orderId, entry);
        }
    }

    public void completeFailure(UUID orderId, String reason) {
        pruneIfNeeded();
        WaiterEntry entry = waiters.computeIfAbsent(orderId,
                id -> new WaiterEntry(new CompletableFuture<>(), System.currentTimeMillis()));
        CompletableFuture<ReservationOutcome> future = entry.future;
        ReservationOutcome outcome = ReservationOutcome.failure(reason);
        if (future.complete(outcome)) {
            waiters.remove(orderId, entry);
        }
    }

    public ReservationOutcome await(UUID orderId, Duration timeout) throws TimeoutException {
        pruneIfNeeded();
        WaiterEntry entry = waiters.computeIfAbsent(orderId,
                id -> new WaiterEntry(new CompletableFuture<>(), System.currentTimeMillis()));
        CompletableFuture<ReservationOutcome> future = entry.future;

        // 즉시 체크: 이미 완료된 경우 바로 반환
        if (future.isDone()) {
            try {
                waiters.remove(orderId, entry);
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("완료된 예약 결과 조회 실패", e);
            }
        }

        try {
            log.debug("⏳ 예약 응답 대기 시작 - orderId: {}, timeout: {}ms", orderId, timeout.toMillis());
            ReservationOutcome result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            waiters.remove(orderId, entry);
            log.info("✅ 예약 응답 성공 - orderId: {}", orderId);
            return result;
        } catch (TimeoutException e) {
            waiters.remove(orderId, entry);
            log.warn("⏱️ 예약 응답 타임아웃 - orderId: {}", orderId);
            throw e;
        } catch (Exception e) {
            waiters.remove(orderId, entry);
            log.error("❌ 예약 응답 대기 중 오류 - orderId: {}, error: {}", orderId, e.getMessage());
            throw new RuntimeException("예약 응답 대기 중 오류가 발생했습니다.", e);
        }
    }

    private void pruneIfNeeded() {
        long now = System.currentTimeMillis();
        long last = lastPrunedAt.get();
        if (now - last < PRUNE_EVERY_MS) {
            return;
        }
        if (!lastPrunedAt.compareAndSet(last, now)) {
            return;
        }
        long cutoff = now - waiterTtlMs;
        waiters.entrySet().removeIf(entry -> entry.getValue().createdAt < cutoff);
    }

    private static final class WaiterEntry {
        private final CompletableFuture<ReservationOutcome> future;
        private final long createdAt;

        private WaiterEntry(CompletableFuture<ReservationOutcome> future, long createdAt) {
            this.future = future;
            this.createdAt = createdAt;
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
