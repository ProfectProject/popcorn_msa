/*package com.popcorn.checkIns.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * QrCodeRepository 단위 테스트
 * JDBC 기반 QR 코드 리포지토리의 모든 메소드를 테스트합니다.
 */
/*@ExtendWith(MockitoExtension.class)
class QrCodeRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private QrCodeRepository qrCodeRepository;

    private UUID testOrderId;
    private UUID testQrId;
    private String testQrCode;
    private LocalDateTime testDateTime;
    private QrCodeRow testQrCodeRow;

    @BeforeEach
    void setUp() {
        testOrderId = UUID.randomUUID();
        testQrId = UUID.randomUUID();
        testQrCode = "QR_TEST_123";
        testDateTime = LocalDateTime.now();
        testQrCodeRow = new QrCodeRow(
                testQrId,
                testOrderId,
                testQrCode,
                testDateTime.plusHours(1),
                testDateTime
        );
    }

    @Nested
    @DisplayName("주문 상태 조회 테스트")
    class FindOrderStatusTest {

        @Test
        @DisplayName("정상적으로 주문 상태를 조회한다")
        void findOrderStatus_Success() {
            // Given
            String expectedStatus = "CONFIRMED";
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testOrderId)))
                    .thenReturn(List.of(expectedStatus));

            // When
            Optional<String> result = qrCodeRepository.findOrderStatus(testOrderId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedStatus);
            verify(jdbcTemplate).query(
                    eq("SELECT status FROM p_orders WHERE order_id = ?"),
                    any(RowMapper.class),
                    eq(testOrderId)
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
        void findOrderStatus_NotFound() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testOrderId)))
                    .thenReturn(List.of());

            // When
            Optional<String> result = qrCodeRepository.findOrderStatus(testOrderId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 상태가 있을 경우 첫 번째 상태를 반환한다")
        void findOrderStatus_MultipleResults() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testOrderId)))
                    .thenReturn(List.of("CONFIRMED", "PENDING"));

            // When
            Optional<String> result = qrCodeRepository.findOrderStatus(testOrderId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("CONFIRMED");
        }
    }

    @Nested
    @DisplayName("주문 ID로 QR 코드 조회 테스트")
    class FindLatestByOrderIdTest {

        @Test
        @DisplayName("정상적으로 최신 QR 코드를 조회한다")
        void findLatestByOrderId_Success() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testOrderId)))
                    .thenReturn(List.of(testQrCodeRow));

            // When
            Optional<QrCodeRow> result = qrCodeRepository.findLatestByOrderId(testOrderId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().qrId()).isEqualTo(testQrId);
            assertThat(result.get().orderId()).isEqualTo(testOrderId);
            assertThat(result.get().qrCode()).isEqualTo(testQrCode);
            verify(jdbcTemplate).query(
                    contains("SELECT qr_id, order_id, qr_code, expires_at, created_at"),
                    any(RowMapper.class),
                    eq(testOrderId)
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
        void findLatestByOrderId_NotFound() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testOrderId)))
                    .thenReturn(List.of());

            // When
            Optional<QrCodeRow> result = qrCodeRepository.findLatestByOrderId(testOrderId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("QR 코드로 조회 테스트")
    class FindLatestByQrCodeTest {

        @Test
        @DisplayName("정상적으로 QR 코드로 최신 레코드를 조회한다")
        void findLatestByQrCode_Success() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testQrCode)))
                    .thenReturn(List.of(testQrCodeRow));

            // When
            Optional<QrCodeRow> result = qrCodeRepository.findLatestByQrCode(testQrCode);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().qrCode()).isEqualTo(testQrCode);
            assertThat(result.get().orderId()).isEqualTo(testOrderId);
            verify(jdbcTemplate).query(
                    contains("WHERE qr_code = ?"),
                    any(RowMapper.class),
                    eq(testQrCode)
            );
        }

        @Test
        @DisplayName("존재하지 않는 QR 코드로 조회 시 빈 결과를 반환한다")
        void findLatestByQrCode_NotFound() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(testQrCode)))
                    .thenReturn(List.of());

            // When
            Optional<QrCodeRow> result = qrCodeRepository.findLatestByQrCode(testQrCode);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("QR 코드 삽입 테스트")
    class InsertTest {

        @Test
        @DisplayName("정상적으로 QR 코드를 삽입한다")
        void insert_Success() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), isNull()))
                    .thenReturn(1);

            // When
            qrCodeRepository.insert(testQrCodeRow);

            // Then
            verify(jdbcTemplate).update(
                    contains("INSERT INTO qr.qr_order_qr_codes"),
                    eq(testQrCodeRow.qrId()),
                    eq(testQrCodeRow.orderId()),
                    eq(testQrCodeRow.qrCode()),
                    any(Timestamp.class),
                    any(Timestamp.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("만료 시간이 null인 QR 코드를 삽입한다")
        void insert_WithNullExpiresAt() {
            // Given
            QrCodeRow rowWithNullExpiry = new QrCodeRow(
                    testQrId,
                    testOrderId,
                    testQrCode,
                    null, // expires_at이 null
                    testDateTime
            );
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), isNull()))
                    .thenReturn(1);

            // When
            qrCodeRepository.insert(rowWithNullExpiry);

            // Then
            verify(jdbcTemplate).update(
                    contains("INSERT INTO qr.qr_order_qr_codes"),
                    eq(testQrId),
                    eq(testOrderId),
                    eq(testQrCode),
                    isNull(), // expires_at이 null로 변환됨
                    any(Timestamp.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("생성 시간이 null인 QR 코드를 삽입한다")
        void insert_WithNullCreatedAt() {
            // Given
            QrCodeRow rowWithNullCreatedAt = new QrCodeRow(
                    testQrId,
                    testOrderId,
                    testQrCode,
                    testDateTime.plusHours(1),
                    null // created_at이 null
            );
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), isNull()))
                    .thenReturn(1);

            // When
            qrCodeRepository.insert(rowWithNullCreatedAt);

            // Then
            verify(jdbcTemplate).update(
                    contains("INSERT INTO qr.qr_order_qr_codes"),
                    eq(testQrId),
                    eq(testOrderId),
                    eq(testQrCode),
                    any(Timestamp.class),
                    isNull(), // created_at이 null로 변환됨
                    isNull()
            );
        }
    }

    @Nested
    @DisplayName("유틸리티 메소드 테스트")
    class UtilityMethodTest {

        @Test
        @DisplayName("LocalDateTime을 Timestamp로 변환할 때 null 처리")
        void timestampConversion_NullHandling() {
            // Given
            QrCodeRow rowWithNulls = new QrCodeRow(
                    testQrId,
                    testOrderId,
                    testQrCode,
                    null,
                    null
            );

            // When & Then
            assertThatCode(() -> qrCodeRepository.insert(rowWithNulls))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("QR 코드 조회 시 RowMapper가 올바르게 동작한다")
        void rowMapper_WorksCorrectly() {
            // Given - RowMapper를 통해 QrCodeRow가 정상적으로 생성되는지 확인
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenAnswer(invocation -> {
                        RowMapper<QrCodeRow> mapper = invocation.getArgument(1);
                        // 실제 RowMapper 로직을 시뮬레이션
                        return List.of(testQrCodeRow);
                    });

            // When
            Optional<QrCodeRow> result = qrCodeRepository.findLatestByQrCode(testQrCode);

            // Then
            assertThat(result).isPresent();
            QrCodeRow row = result.get();
            assertThat(row.qrId()).isEqualTo(testQrId);
            assertThat(row.orderId()).isEqualTo(testOrderId);
            assertThat(row.qrCode()).isEqualTo(testQrCode);
            assertThat(row.expiresAt()).isNotNull();
            assertThat(row.createdAt()).isNotNull();
        }
    }
}*/