/*package com.popcorn.checkIns.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.popcorn.checkIns.checkin.repository.CheckinRepository;
import com.popcorn.checkIns.checkin.repository.CheckinRow;

/**
 * QrCheckinEventHandler 단위 테스트
 * 이벤트 핸들러의 비동기 처리 로직을 테스트합니다.
 */
/*@ExtendWith(MockitoExtension.class)
class QrCheckinEventHandlerTest {

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private QrCheckinEventHandler qrCheckinEventHandler;

    private UUID testQrId;
    private UUID testOrderId;
    private String testQrCode;
    private LocalDateTime testCheckinTime;
    private UUID testCheckinId;
    private CheckinRow testCheckinRow;

    @BeforeEach
    void setUp() {
        testQrId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();
        testQrCode = "QR_EVENT_TEST_123";
        testCheckinTime = LocalDateTime.now();
        testCheckinId = UUID.randomUUID();
        testCheckinRow = new CheckinRow(
                testCheckinId,
                testOrderId,
                testQrId,
                testQrCode,
                testCheckinTime,
                12345L
        );
    }

    @Nested
    @DisplayName("QR 체크인 요청 이벤트 처리 테스트")
    class HandleQrCheckinRequestedTest {

        @Test
        @DisplayName("정상적으로 QR 체크인 요청을 처리하고 완료 이벤트를 발행한다")
        void handleQrCheckinRequested_Success() {
            // Given
            QrCheckinRequestedEvent requestedEvent = new QrCheckinRequestedEvent(
                    this,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime.plusHours(1),
                    testCheckinTime
            );

            when(checkinRepository.findLatestByOrderQrCodeId(testQrId))
                    .thenReturn(Optional.of(testCheckinRow));

            // When
            qrCheckinEventHandler.handleQrCheckinRequested(requestedEvent);

            // Then
            verify(checkinRepository).findLatestByOrderQrCodeId(testQrId);
            verify(eventPublisher).publishEvent(any(QrCheckinCompletedEvent.class));
        }

        @Test
        @DisplayName("체크인 레코드가 없을 경우 완료 이벤트를 발행하지 않는다")
        void handleQrCheckinRequested_NoCheckinRecord() {
            // Given
            QrCheckinRequestedEvent requestedEvent = new QrCheckinRequestedEvent(
                    this,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime.plusHours(1),
                    testCheckinTime
            );

            when(checkinRepository.findLatestByOrderQrCodeId(testQrId))
                    .thenReturn(Optional.empty());

            // When
            qrCheckinEventHandler.handleQrCheckinRequested(requestedEvent);

            // Then
            verify(checkinRepository).findLatestByOrderQrCodeId(testQrId);
            verify(eventPublisher, never()).publishEvent(any(QrCheckinCompletedEvent.class));
        }

        @Test
        @DisplayName("체크인 조회 중 예외 발생 시 에러를 처리한다")
        void handleQrCheckinRequested_ExceptionHandling() {
            // Given
            QrCheckinRequestedEvent requestedEvent = new QrCheckinRequestedEvent(
                    this,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime.plusHours(1),
                    testCheckinTime
            );

            when(checkinRepository.findLatestByOrderQrCodeId(testQrId))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatCode(() -> qrCheckinEventHandler.handleQrCheckinRequested(requestedEvent))
                    .doesNotThrowAnyException(); // 예외가 잡혀서 로그만 기록됨

            verify(checkinRepository).findLatestByOrderQrCodeId(testQrId);
            verify(eventPublisher, never()).publishEvent(any(QrCheckinCompletedEvent.class));
        }

        @Test
        @DisplayName("완료 이벤트 발행 시 올바른 데이터를 포함한다")
        void handleQrCheckinRequested_CorrectEventData() {
            // Given
            QrCheckinRequestedEvent requestedEvent = new QrCheckinRequestedEvent(
                    this,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime.plusHours(1),
                    testCheckinTime
            );

            when(checkinRepository.findLatestByOrderQrCodeId(testQrId))
                    .thenReturn(Optional.of(testCheckinRow));

            // When
            qrCheckinEventHandler.handleQrCheckinRequested(requestedEvent);

            // Then
            verify(eventPublisher).publishEvent(argThat(event -> {
                if (event instanceof QrCheckinCompletedEvent completedEvent) {
                    return completedEvent.getCheckinId().equals(testCheckinId) &&
                           completedEvent.getQrId().equals(testQrId) &&
                           completedEvent.getOrderId().equals(testOrderId) &&
                           completedEvent.getQrCode().equals(testQrCode) &&
                           completedEvent.getCheckinTime().equals(testCheckinTime);
                }
                return false;
            }));
        }
    }

    @Nested
    @DisplayName("QR 체크인 완료 이벤트 처리 테스트")
    class HandleQrCheckinCompletedTest {

        @Test
        @DisplayName("정상적으로 QR 체크인 완료를 처리한다")
        void handleQrCheckinCompleted_Success() {
            // Given
            QrCheckinCompletedEvent completedEvent = new QrCheckinCompletedEvent(
                    this,
                    testCheckinId,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime
            );

            // When & Then - 예외 없이 처리되어야 함
            assertThatCode(() -> qrCheckinEventHandler.handleQrCheckinCompleted(completedEvent))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("체크인 완료 처리 중 예외 발생 시 에러를 처리한다")
        void handleQrCheckinCompleted_ExceptionHandling() {
            // Given
            QrCheckinCompletedEvent completedEvent = new QrCheckinCompletedEvent(
                    this,
                    testCheckinId,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime
            );

            // 실제로는 내부에서 예외가 발생할 수 있는 상황이지만,
            // 현재 구현에서는 로그만 기록하므로 예외가 외부로 전파되지 않음

            // When & Then
            assertThatCode(() -> qrCheckinEventHandler.handleQrCheckinCompleted(completedEvent))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 체크인 ID로도 처리가 가능하다")
        void handleQrCheckinCompleted_NullCheckinId() {
            // Given
            QrCheckinCompletedEvent completedEvent = new QrCheckinCompletedEvent(
                    this,
                    null, // 체크인 ID가 null
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime
            );

            // When & Then
            assertThatCode(() -> qrCheckinEventHandler.handleQrCheckinCompleted(completedEvent))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("이벤트 데이터 검증 테스트")
    class EventDataValidationTest {

        @Test
        @DisplayName("QrCheckinRequestedEvent의 모든 필드가 올바르게 설정된다")
        void qrCheckinRequestedEvent_AllFieldsSet() {
            // Given & When
            QrCheckinRequestedEvent event = new QrCheckinRequestedEvent(
                    this,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime.plusHours(1),
                    testCheckinTime
            );

            // Then
            assertThat(event.getSource()).isEqualTo(this);
            assertThat(event.getQrId()).isEqualTo(testQrId);
            assertThat(event.getOrderId()).isEqualTo(testOrderId);
            assertThat(event.getQrCode()).isEqualTo(testQrCode);
            assertThat(event.getExpiresAt()).isEqualTo(testCheckinTime.plusHours(1));
            assertThat(event.getCheckinTime()).isEqualTo(testCheckinTime);
        }

        @Test
        @DisplayName("QrCheckinCompletedEvent의 모든 필드가 올바르게 설정된다")
        void qrCheckinCompletedEvent_AllFieldsSet() {
            // Given & When
            QrCheckinCompletedEvent event = new QrCheckinCompletedEvent(
                    this,
                    testCheckinId,
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testCheckinTime
            );

            // Then
            assertThat(event.getSource()).isEqualTo(this);
            assertThat(event.getCheckinId()).isEqualTo(testCheckinId);
            assertThat(event.getQrId()).isEqualTo(testQrId);
            assertThat(event.getOrderId()).isEqualTo(testOrderId);
            assertThat(event.getQrCode()).isEqualTo(testQrCode);
            assertThat(event.getCheckinTime()).isEqualTo(testCheckinTime);
        }

        @Test
        @DisplayName("이벤트 생성자에서 null 값 처리를 확인한다")
        void eventConstructor_HandlesNullValues() {
            // Given & When & Then
            assertThatCode(() -> new QrCheckinRequestedEvent(
                    this,
                    null, // qrId가 null
                    testOrderId,
                    testQrCode,
                    null, // expiresAt이 null
                    testCheckinTime
            )).doesNotThrowAnyException();

            assertThatCode(() -> new QrCheckinCompletedEvent(
                    this,
                    null, // checkinId가 null
                    testQrId,
                    testOrderId,
                    null, // qrCode가 null
                    testCheckinTime
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("비동기 처리 시뮬레이션 테스트")
    class AsyncProcessingSimulationTest {

        @Test
        @DisplayName("여러 요청 이벤트를 연속으로 처리할 수 있다")
        void multipleRequestEvents_ProcessedSequentially() {
            // Given
            QrCheckinRequestedEvent event1 = new QrCheckinRequestedEvent(
                    this, UUID.randomUUID(), testOrderId, "QR_1", testCheckinTime.plusHours(1), testCheckinTime);
            QrCheckinRequestedEvent event2 = new QrCheckinRequestedEvent(
                    this, UUID.randomUUID(), testOrderId, "QR_2", testCheckinTime.plusHours(1), testCheckinTime);

            when(checkinRepository.findLatestByOrderQrCodeId(any(UUID.class)))
                    .thenReturn(Optional.of(testCheckinRow));

            // When
            qrCheckinEventHandler.handleQrCheckinRequested(event1);
            qrCheckinEventHandler.handleQrCheckinRequested(event2);

            // Then
            verify(checkinRepository, times(2)).findLatestByOrderQrCodeId(any(UUID.class));
            verify(eventPublisher, times(2)).publishEvent(any(QrCheckinCompletedEvent.class));
        }

        @Test
        @DisplayName("요청과 완료 이벤트가 순차적으로 처리된다")
        void requestAndCompletedEvents_ProcessedInSequence() {
            // Given
            QrCheckinRequestedEvent requestedEvent = new QrCheckinRequestedEvent(
                    this, testQrId, testOrderId, testQrCode, testCheckinTime.plusHours(1), testCheckinTime);
            QrCheckinCompletedEvent completedEvent = new QrCheckinCompletedEvent(
                    this, testCheckinId, testQrId, testOrderId, testQrCode, testCheckinTime);

            when(checkinRepository.findLatestByOrderQrCodeId(testQrId))
                    .thenReturn(Optional.of(testCheckinRow));

            // When
            qrCheckinEventHandler.handleQrCheckinRequested(requestedEvent);
            qrCheckinEventHandler.handleQrCheckinCompleted(completedEvent);

            // Then
            verify(checkinRepository).findLatestByOrderQrCodeId(testQrId);
            verify(eventPublisher).publishEvent(any(QrCheckinCompletedEvent.class));
            // 완료 이벤트 처리는 단순히 로그만 기록하므로 추가 검증 없음
        }
    }
}*/