package com.popcorn.payment.service

import com.popcorn.payment.client.UserServiceClient
import com.popcorn.payment.client.StoreValidationClient
import com.popcorn.payment.event.domain.payment.EventLineItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

/**
 * 🧪 개선된 하이브리드 검증 서비스 테스트
 * - Kotlin 코루틴 테스트 패턴
 * - 보안 우선 원칙 검증
 * - Fail-Safe 동작 확인
 */
@ExtendWith(MockitoExtension::class)
class ImprovedHybridValidationServiceTest {

    private lateinit var userServiceClient: UserServiceClient
    private lateinit var storeValidationClient: StoreValidationClient
    private lateinit var validationService: ImprovedHybridValidationService

    private val testUserId = 1L
    private val testOrderId = UUID.randomUUID()
    private val testSessionId = UUID.randomUUID()
    private val testGoodsId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        userServiceClient = mockk()
        storeValidationClient = mockk()
        validationService = ImprovedHybridValidationService(userServiceClient, storeValidationClient)
    }

    @Test
    fun `주소 검증 성공 시 Valid 반환`() = runBlocking {
        // Given
        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns true

        // When
        val result = validationService.validateUserAddress(testUserId)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.AddressValidation.Valid::class.java)
    }

    @Test
    fun `주소 검증 실패 시 Invalid 반환`() = runBlocking {
        // Given
        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns false

        // When
        val result = validationService.validateUserAddress(testUserId)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.AddressValidation.Invalid::class.java)
    }

    @Test
    fun `주소 검증 서비스 장애 시 Error 반환 - 보안 우선 원칙`() = runBlocking {
        // Given
        coEvery { userServiceClient.hasDefaultAddress(testUserId) } throws RuntimeException("Service down")

        // When
        val result = validationService.validateUserAddress(testUserId)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.AddressValidation.Error::class.java)
        val error = result as ImprovedHybridValidationService.AddressValidation.Error
        assertThat(error.cause).hasMessageContaining("Service down")
    }

    @Test
    fun `세션 가격 검증 성공`() = runBlocking {
        // Given
        val expectedPrice = 10000
        coEvery { storeValidationClient.getSessionPrice(testSessionId) } returns expectedPrice

        // When
        val result = validationService.validateSessionPrice(testSessionId, expectedPrice)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.PriceValidation.Valid::class.java)
        val valid = result as ImprovedHybridValidationService.PriceValidation.Valid
        assertThat(valid.actualPrice).isEqualTo(expectedPrice)
    }

    @Test
    fun `세션 가격 허용 범위 내 차이 시 성공`() = runBlocking {
        // Given
        val expectedPrice = 10000
        val actualPrice = 10500  // 5% 차이 (허용 범위 내)
        coEvery { storeValidationClient.getSessionPrice(testSessionId) } returns actualPrice

        // When
        val result = validationService.validateSessionPrice(testSessionId, expectedPrice)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.PriceValidation.Valid::class.java)
    }

    @Test
    fun `세션 가격 허용 범위 초과 시 Invalid 반환`() = runBlocking {
        // Given
        val expectedPrice = 10000
        val actualPrice = 20000  // 100% 차이 (허용 범위 초과)
        coEvery { storeValidationClient.getSessionPrice(testSessionId) } returns actualPrice

        // When
        val result = validationService.validateSessionPrice(testSessionId, expectedPrice)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.PriceValidation.Invalid::class.java)
        val invalid = result as ImprovedHybridValidationService.PriceValidation.Invalid
        assertThat(invalid.expected).isEqualTo(expectedPrice)
        assertThat(invalid.actual).isEqualTo(actualPrice)
    }

    @Test
    fun `굿즈 가격 정확히 일치하지 않으면 Invalid 반환`() = runBlocking {
        // Given
        val expectedPrice = 15000
        val actualPrice = 15001  // 1원 차이도 허용하지 않음
        coEvery { storeValidationClient.getGoodsPrice(testGoodsId) } returns actualPrice

        // When
        val result = validationService.validateGoodsPrice(testGoodsId, expectedPrice)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.PriceValidation.Invalid::class.java)
    }

    @Test
    fun `가격 정보가 없으면 Error 반환 - 보안 우선 원칙`() = runBlocking {
        // Given
        coEvery { storeValidationClient.getSessionPrice(testSessionId) } returns null

        // When
        val result = validationService.validateSessionPrice(testSessionId, 10000)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.PriceValidation.Error::class.java)
    }

    @Test
    fun `기본 결제 검증 - 금액 일치 시 성공`() = runBlocking {
        // Given
        val expectedAmount = 25000
        val orderAmount = 25000
        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns true

        // When
        val result = validationService.validateBasicPayment(
            testOrderId, testUserId, expectedAmount, orderAmount, true
        )

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.ValidationResult.Success::class.java)
        val success = result as ImprovedHybridValidationService.ValidationResult.Success
        assertThat(success.totalAmount).isEqualTo(expectedAmount)
    }

    @Test
    fun `기본 결제 검증 - 금액 불일치 시 실패`() = runBlocking {
        // Given
        val expectedAmount = 25000
        val orderAmount = 30000  // 금액 불일치

        // When
        val result = validationService.validateBasicPayment(
            testOrderId, testUserId, expectedAmount, orderAmount, false
        )

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.ValidationResult.Failure::class.java)
        val failure = result as ImprovedHybridValidationService.ValidationResult.Failure
        assertThat(failure.reason).contains("Payment amount mismatch")
    }

    @Test
    fun `굿즈 주문 시 주소 필수 검증`() = runBlocking {
        // Given
        val expectedAmount = 25000
        val orderAmount = 25000
        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns false  // 주소 없음

        // When
        val result = validationService.validateBasicPayment(
            testOrderId, testUserId, expectedAmount, orderAmount, true  // hasGoods = true
        )

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.ValidationResult.Failure::class.java)
        val failure = result as ImprovedHybridValidationService.ValidationResult.Failure
        assertThat(failure.reason).contains("address validation required")
    }

    @Test
    fun `종합 검증 - 모든 조건 성공 시 Success 반환`() = runBlocking {
        // Given
        val lineItems = listOf(
            EventLineItem(
                itemType = "GOODS",
                goodsId = testGoodsId,
                unitPrice = 10000,
                qty = 2
            ),
            EventLineItem(
                itemType = "SCHEDULE",
                scheduleId = testSessionId,
                unitPrice = 5000,
                qty = 1
            )
        )
        val expectedAmount = 25000

        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns true
        coEvery { storeValidationClient.getGoodsPrice(testGoodsId) } returns 10000
        coEvery { storeValidationClient.getSessionPrice(testSessionId) } returns 5000

        // When
        val result = validationService.validatePaymentRequest(testOrderId, testUserId, lineItems, expectedAmount)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.ValidationResult.Success::class.java)
        val success = result as ImprovedHybridValidationService.ValidationResult.Success
        assertThat(success.totalAmount).isEqualTo(expectedAmount)
        assertThat(success.addressValid).isTrue()
        assertThat(success.priceValid).isTrue()
    }

    @Test
    fun `종합 검증 - 하나라도 실패 시 Failure 반환`() = runBlocking {
        // Given
        val lineItems = listOf(
            EventLineItem(
                itemType = "GOODS",
                goodsId = testGoodsId,
                unitPrice = 10000,
                qty = 2
            )
        )
        val expectedAmount = 20000

        coEvery { userServiceClient.hasDefaultAddress(testUserId) } returns true
        coEvery { storeValidationClient.getGoodsPrice(testGoodsId) } returns 15000  // 가격 불일치

        // When
        val result = validationService.validatePaymentRequest(testOrderId, testUserId, lineItems, expectedAmount)

        // Then
        assertThat(result).isInstanceOf(ImprovedHybridValidationService.ValidationResult.Failure::class.java)
    }
}
