/*package com.popcorn.checkIns.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QrCodeRow 단위 테스트")
class QrCodeRowUnitTest {

    private final UUID qrId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final String qrCode = "TEST_QR_CODE_12345";
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    @DisplayName("QR 코드 만료 검증 - 만료되지 않음")
    void isExpired_shouldReturnFalseForValidQr() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(5), // 5분 후 만료
            now.minusMinutes(3)  // 3분 전 생성
        );

        // When
        boolean isExpired = qrCodeRow.isExpired(now);

        // Then
        assertFalse(isExpired, "현재 시간보다 나중에 만료되는 QR 코드는 유효해야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 이미 만료됨")
    void isExpired_shouldReturnTrueForExpiredQr() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.minusMinutes(2), // 2분 전에 만료
            now.minusMinutes(12) // 12분 전 생성 (10분 TTL)
        );

        // When
        boolean isExpired = qrCodeRow.isExpired(now);

        // Then
        assertTrue(isExpired, "현재 시간보다 이전에 만료된 QR 코드는 만료되어야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 정확히 만료 시점")
    void isExpired_shouldReturnTrueAtExactExpiryTime() {
        // Given
        LocalDateTime expiryTime = now.withNano(0); // 나노초 제거하여 정확한 비교
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiryTime, // 정확히 현재 시간에 만료
            expiryTime.minusMinutes(10)
        );

        // When
        boolean isExpired = qrCodeRow.isExpired(expiryTime);

        // Then
        assertTrue(isExpired, "만료 시간과 동일한 시점에서는 만료된 것으로 처리되어야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 1초 전")
    void isExpired_shouldReturnFalseOneSecondBeforeExpiry() {
        // Given
        LocalDateTime expiryTime = now.plusSeconds(1); // 1초 후 만료
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiryTime,
            now.minusMinutes(10)
        );

        // When
        boolean isExpired = qrCodeRow.isExpired(now);

        // Then
        assertFalse(isExpired, "만료 1초 전에는 아직 유효해야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 1초 후")
    void isExpired_shouldReturnTrueOneSecondAfterExpiry() {
        // Given
        LocalDateTime expiryTime = now.minusSeconds(1); // 1초 전에 만료
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiryTime,
            now.minusMinutes(10)
        );

        // When
        boolean isExpired = qrCodeRow.isExpired(now);

        // Then
        assertTrue(isExpired, "만료 1초 후에는 만료된 것으로 처리되어야 함");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 30, 60, 120, 1440}) // 분 단위
    @DisplayName("QR 코드 만료 검증 - 다양한 만료 시간")
    void isExpired_shouldHandleVariousExpiryTimes(int minutesAfterNow) {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(minutesAfterNow), // minutesAfterNow 분 후 만료
            now.minusMinutes(5)
        );

        // When
        boolean isExpiredNow = qrCodeRow.isExpired(now);
        boolean isExpiredAfterExpiry = qrCodeRow.isExpired(now.plusMinutes(minutesAfterNow + 1));

        // Then
        assertFalse(isExpiredNow, String.format("%d분 후 만료되는 QR 코드는 현재 유효해야 함", minutesAfterNow));
        assertTrue(isExpiredAfterExpiry, String.format("%d분 후 만료된 QR 코드는 그 이후 시점에서 만료되어야 함", minutesAfterNow));
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 미래 시점 검증")
    void isExpired_shouldHandleFutureValidationTime() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(10), // 10분 후 만료
            now.minusMinutes(5)
        );

        // When
        boolean isExpiredInFuture = qrCodeRow.isExpired(now.plusMinutes(15)); // 15분 후 시점에서 검증

        // Then
        assertTrue(isExpiredInFuture, "미래 시점에서 검증할 때 만료 시간을 지났으면 만료로 처리되어야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - 과거 시점 검증")
    void isExpired_shouldHandlePastValidationTime() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(10), // 10분 후 만료
            now.minusMinutes(5)
        );

        // When
        boolean isExpiredInPast = qrCodeRow.isExpired(now.minusMinutes(1)); // 1분 전 시점에서 검증

        // Then
        assertFalse(isExpiredInPast, "과거 시점에서 검증할 때 아직 만료 시간이 지나지 않았으면 유효해야 함");
    }

    @Test
    @DisplayName("QR 코드 생성 시간과 만료 시간 검증")
    void qrCodeRow_shouldHaveValidCreatedAndExpiryTimes() {
        // Given
        LocalDateTime createdAt = now.minusMinutes(5);
        LocalDateTime expiresAt = now.plusMinutes(5);

        // When
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiresAt,
            createdAt
        );

        // Then
        assertEquals(qrId, qrCodeRow.qrId());
        assertEquals(orderId, qrCodeRow.orderId());
        assertEquals(qrCode, qrCodeRow.qrCode());
        assertEquals(expiresAt, qrCodeRow.expiresAt());
        assertEquals(createdAt, qrCodeRow.createdAt());

        assertTrue(qrCodeRow.expiresAt().isAfter(qrCodeRow.createdAt()),
            "만료 시간은 생성 시간보다 나중이어야 함");
    }

    @Test
    @DisplayName("QR 코드 표준 TTL 10분 검증")
    void qrCodeRow_shouldHaveStandardTtlOf10Minutes() {
        // Given
        LocalDateTime createdAt = now.minusMinutes(3);
        LocalDateTime expiresAt = createdAt.plusMinutes(10); // 생성 시점으로부터 10분 후

        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiresAt,
            createdAt
        );

        // When
        boolean isValidBeforeExpiry = qrCodeRow.isExpired(now); // 생성 후 3분 시점
        boolean isExpiredAfterTtl = qrCodeRow.isExpired(createdAt.plusMinutes(10)); // 생성 후 정확히 10분

        // Then
        assertFalse(isValidBeforeExpiry, "10분 TTL 내에는 유효해야 함");
        assertTrue(isExpiredAfterTtl, "10분 TTL 이후에는 만료되어야 함");
    }

    @Test
    @DisplayName("QR 코드 Record 불변성 검증")
    void qrCodeRow_shouldBeImmutable() {
        // Given
        LocalDateTime createdAt = now.minusMinutes(5);
        LocalDateTime expiresAt = now.plusMinutes(5);

        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiresAt,
            createdAt
        );

        // When & Then - Record는 불변이므로 setter 메서드가 없어야 함
        assertNotNull(qrCodeRow.qrId());
        assertNotNull(qrCodeRow.orderId());
        assertNotNull(qrCodeRow.qrCode());
        assertNotNull(qrCodeRow.expiresAt());
        assertNotNull(qrCodeRow.createdAt());

        // equals와 hashCode가 올바르게 구현되어 있는지 검증
        QrCodeRow identicalQrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            expiresAt,
            createdAt
        );

        assertEquals(qrCodeRow, identicalQrCodeRow, "동일한 값을 가진 QrCodeRow는 equals이어야 함");
        assertEquals(qrCodeRow.hashCode(), identicalQrCodeRow.hashCode(),
            "동일한 값을 가진 QrCodeRow는 같은 hashCode를 가져야 함");
    }

    @Test
    @DisplayName("QR 코드 toString 출력 검증")
    void qrCodeRow_shouldHaveValidToString() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(10),
            now.minusMinutes(5)
        );

        // When
        String toString = qrCodeRow.toString();

        // Then
        assertNotNull(toString, "toString()은 null이 아니어야 함");
        assertTrue(toString.contains(qrId.toString()), "toString()에 qrId가 포함되어야 함");
        assertTrue(toString.contains(orderId.toString()), "toString()에 orderId가 포함되어야 함");
        assertTrue(toString.contains(qrCode), "toString()에 qrCode가 포함되어야 함");
        assertTrue(toString.contains("QrCodeRow"), "toString()에 클래스명이 포함되어야 함");
    }

    @Test
    @DisplayName("QR 코드 만료 검증 - null 검증 시간")
    void isExpired_shouldHandleNullValidationTime() {
        // Given
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            now.plusMinutes(10),
            now.minusMinutes(5)
        );

        // When & Then
        assertThrows(NullPointerException.class,
            () -> qrCodeRow.isExpired(null),
            "null 검증 시간에 대해 NullPointerException이 발생해야 함");
    }

    @Test
    @DisplayName("QR 코드 극한 경계값 테스트 - 나노초 차이")
    void isExpired_shouldHandleNanosecondPrecision() {
        // Given
        LocalDateTime exactExpiryTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0, 0);
        QrCodeRow qrCodeRow = new QrCodeRow(
            qrId,
            orderId,
            qrCode,
            exactExpiryTime,
            exactExpiryTime.minusMinutes(10)
        );

        LocalDateTime beforeExpiry = exactExpiryTime.minusNanos(1); // 1나노초 전
        LocalDateTime afterExpiry = exactExpiryTime.plusNanos(1);  // 1나노초 후

        // When
        boolean isExpiredBefore = qrCodeRow.isExpired(beforeExpiry);
        boolean isExpiredExact = qrCodeRow.isExpired(exactExpiryTime);
        boolean isExpiredAfter = qrCodeRow.isExpired(afterExpiry);

        // Then
        assertFalse(isExpiredBefore, "1나노초 전에는 유효해야 함");
        assertTrue(isExpiredExact, "정확한 만료 시간에는 만료되어야 함");
        assertTrue(isExpiredAfter, "1나노초 후에는 만료되어야 함");
    }
}*/