/*package com.popcorn.checkIns.service;

import com.popcorn.checkIns.checkin.repository.CheckinRepository;
import com.popcorn.checkIns.checkin.repository.CheckinRow;
import com.popcorn.checkIns.dto.response.QrCodeResponse;
import com.popcorn.checkIns.dto.response.QrVerifyResponse;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.repository.QrCodeRepository;
import com.popcorn.checkIns.repository.QrCodeRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QrCodeService 단위 테스트")
class QrCodeServiceUnitTest {

    @Mock
    private QrCodeRepository qrCodeRepository;

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private QrCodeService qrCodeService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();
    private final String qrCodeString = "QR_CODE_12345";
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        // Mock 초기화는 @ExtendWith(MockitoExtension.class)에서 자동 처리
    }

    @Test
    @DisplayName("QR 코드 발급 성공 - PAID 상태 주문")
    void issueQrCode_shouldSuccessfullyIssueForPaidOrder() {
        // Given
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty()); // 기존 QR 없음
        doNothing().when(qrCodeRepository).insert(any());

        // When
        var response = qrCodeService.issue(orderId);

        // Then
        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        // QR 코드는 UUID로 생성되므로 정확한 값을 확인할 수 없음
        assertNotNull(response.getQrCode());
        assertNotNull(response.getExpiresAt());

        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(qrCodeRepository, times(1)).findLatestByOrderId(orderId);
        verify(qrCodeRepository, times(1)).insert(any());
    }

    @Test
    @DisplayName("QR 코드 발급 실패 - 주문 없음")
    void issueQrCode_shouldThrowExceptionWhenOrderNotFound() {
        // Given
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.empty());

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.issue(orderId));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(qrCodeRepository, never()).findLatestByOrderId(any());
        verify(qrCodeRepository, never()).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CANCELLED", "FAILED", "READY"})
    @DisplayName("QR 코드 발급 실패 - PAID가 아닌 주문 상태")
    void issueQrCode_shouldThrowExceptionForNonPaidOrders(String orderStatus) {
        // Given
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of(orderStatus));

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.issue(orderId));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(qrCodeRepository, never()).findLatestByOrderId(any());
    }

    @Test
    @DisplayName("QR 코드 발급 - 기존 유효한 QR 재사용")
    void issueQrCode_shouldReuseExistingValidQr() {
        // Given
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));

        QrCodeRow existingQr = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.plusMinutes(5), // 아직 5분 남음
            now.minusMinutes(3) // 3분 전 생성
        );
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(existingQr));

        // When
        var response = qrCodeService.issue(orderId);

        // Then
        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        assertEquals(qrCodeString, response.getQrCode());
        assertEquals(now.plusMinutes(5), response.getExpiresAt());

        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(qrCodeRepository, times(1)).findLatestByOrderId(orderId);
        verify(qrCodeRepository, never()).insert(any()); // 새로 생성하지 않음
    }

    @Test
    @DisplayName("QR 코드 발급 - 기존 만료된 QR은 새로 생성")
    void issueQrCode_shouldCreateNewQrWhenExistingIsExpired() {
        // Given
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));

        QrCodeRow expiredQr = new QrCodeRow(
            qrId,
            orderId,
            "EXPIRED_QR_CODE",
            now.minusMinutes(5), // 5분 전에 만료
            now.minusMinutes(15) // 15분 전 생성
        );
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(expiredQr));
        doNothing().when(qrCodeRepository).insert(any());

        // When
        var response = qrCodeService.issue(orderId);

        // Then
        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        // 새로운 QR 코드가 생성됨
        assertNotNull(response.getQrCode());
        assertNotEquals("EXPIRED_QR_CODE", response.getQrCode());
        assertNotNull(response.getExpiresAt());

        verify(qrCodeRepository, times(1)).insert(any());
    }

    @Test
    @DisplayName("QR 코드 조회 성공")
    void getQrCode_shouldReturnExistingQr() {
        // Given
        QrCodeRow qrCode = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.plusMinutes(8),
            now.minusMinutes(2)
        );
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(qrCode));
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));

        // When
        var response = qrCodeService.get(orderId);

        // Then
        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        assertEquals(qrCodeString, response.getQrCode());
        assertEquals(now.plusMinutes(8), response.getExpiresAt());

        verify(qrCodeRepository, times(1)).findLatestByOrderId(orderId);
        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
    }

    @Test
    @DisplayName("QR 코드 조회 실패 - QR 없음")
    void getQrCode_shouldThrowExceptionWhenQrNotFound() {
        // Given
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.get(orderId));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findLatestByOrderId(orderId);
    }

    @Test
    @DisplayName("QR 코드 조회 실패 - QR 만료")
    void getQrCode_shouldThrowExceptionWhenQrExpired() {
        // Given
        QrCodeRow expiredQr = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.minusMinutes(1), // 1분 전에 만료
            now.minusMinutes(11) // 11분 전 생성
        );
        when(qrCodeRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(expiredQr));

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.get(orderId));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findLatestByOrderId(orderId);
    }

    @Test
    @DisplayName("QR 코드 검증 성공 - 체크인 생성")
    void verifyQrCode_shouldSuccessfullyCreateCheckin() {
        // Given
        QrCodeRow validQr = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.plusMinutes(5), // 아직 유효
            now.minusMinutes(3)
        );
        when(qrCodeRepository.findLatestByQrCode(qrCodeString)).thenReturn(Optional.of(validQr));
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));
        when(checkinRepository.findLatestByOrderQrCodeId(qrId)).thenReturn(Optional.empty()); // 기존 체크인 없음

        UUID checkinId = UUID.randomUUID();
        when(checkinRepository.insert(eq(orderId), eq(qrId), isNull(), any(LocalDateTime.class)))
            .thenReturn(checkinId);

        doNothing().when(eventPublisher).publishEvent(any());

        // When
        var response = qrCodeService.verify(qrCodeString);

        // Then
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals(checkinId, response.getCheckinId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(qrCodeString, response.getQrCode());
        assertEquals(now.plusMinutes(5), response.getExpiresAt());

        verify(qrCodeRepository, times(1)).findLatestByQrCode(qrCodeString);
        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(checkinRepository, times(1)).findLatestByOrderQrCodeId(qrId);
        verify(checkinRepository, times(1)).insert(eq(orderId), eq(qrId), isNull(), any(LocalDateTime.class));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("QR 코드 검증 성공 - 기존 체크인 반환 (중복 방지)")
    void verifyQrCode_shouldReturnExistingCheckinWhenAlreadyCheckedIn() {
        // Given
        QrCodeRow validQr = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.plusMinutes(3),
            now.minusMinutes(5)
        );
        when(qrCodeRepository.findLatestByQrCode(qrCodeString)).thenReturn(Optional.of(validQr));
        when(qrCodeRepository.findOrderStatus(orderId)).thenReturn(Optional.of("PAID"));

        UUID existingCheckinId = UUID.randomUUID();
        CheckinRow existingCheckin = new CheckinRow(
            existingCheckinId,
            orderId,
            qrId,
            qrCodeString,
            now.minusMinutes(2),
            1L
        );
        when(checkinRepository.findLatestByOrderQrCodeId(qrId)).thenReturn(Optional.of(existingCheckin));

        // When
        var response = qrCodeService.verify(qrCodeString);

        // Then
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals(existingCheckinId, response.getCheckinId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(qrCodeString, response.getQrCode());

        verify(qrCodeRepository, times(1)).findLatestByQrCode(qrCodeString);
        verify(qrCodeRepository, times(1)).findOrderStatus(orderId);
        verify(checkinRepository, times(1)).findLatestByOrderQrCodeId(qrId);
        verify(checkinRepository, never()).insert(any(), any(), any(), any()); // 새로 생성하지 않음
        verify(eventPublisher, never()).publishEvent(any()); // 이벤트 발행하지 않음
    }

    @Test
    @DisplayName("QR 코드 검증 실패 - QR 없음")
    void verifyQrCode_shouldThrowExceptionWhenQrNotFound() {
        // Given
        String invalidQrCode = "INVALID_QR_CODE";
        when(qrCodeRepository.findLatestByQrCode(invalidQrCode)).thenReturn(Optional.empty());

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.verify(invalidQrCode));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findLatestByQrCode(invalidQrCode);
        verify(checkinRepository, never()).findLatestByOrderQrCodeId(any());
    }

    @Test
    @DisplayName("QR 코드 검증 실패 - QR 만료")
    void verifyQrCode_shouldThrowExceptionWhenQrExpiredDuringVerification() {
        // Given
        QrCodeRow expiredQr = new QrCodeRow(
            qrId,
            orderId,
            qrCodeString,
            now.minusMinutes(2), // 이미 만료
            now.minusMinutes(12)
        );
        when(qrCodeRepository.findLatestByQrCode(qrCodeString)).thenReturn(Optional.of(expiredQr));

        // When & Then
        QrException exception = assertThrows(QrException.class,
            () -> qrCodeService.verify(qrCodeString));

        assertNotNull(exception);
        verify(qrCodeRepository, times(1)).findLatestByQrCode(qrCodeString);
        verify(checkinRepository, never()).findLatestByOrderQrCodeId(any());
    }
}*/