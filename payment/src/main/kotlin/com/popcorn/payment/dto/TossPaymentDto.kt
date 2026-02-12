package com.popcorn.payment.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 토스페이먼츠 결제 승인 요청 DTO
 */
data class TossPaymentConfirmRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: Int
)

/**
 * 토스페이먼츠 결제 승인 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TossPaymentConfirmResponse(
    val paymentKey: String,
    val orderId: String,
    val totalAmount: Int,
    val status: String,
    val method: String,
    val requestedAt: String? = null,
    val approvedAt: String? = null,

    // 추가 필드들 (토스페이먼츠 API 전체 응답 구조)
    val mId: String? = null,
    val version: String? = null,
    val currency: String? = null,
    val balanceAmount: Int? = null,
    val suppliedAmount: Int? = null,
    val vat: Int? = null,
    val taxFreeAmount: Int? = null,
    val taxExemptionAmount: Int? = null,
    val cancels: List<CancelDetail>? = null,
    val isPartialCancelable: Boolean? = null,
    val card: CardDetail? = null,
    val virtualAccount: VirtualAccountDetail? = null,
    val transfer: TransferDetail? = null,
    val mobilePhone: MobilePhoneDetail? = null,
    val giftCertificate: GiftCertificateDetail? = null,
    val easyPay: EasyPayDetail? = null,
    val cashReceipt: CashReceiptDetail? = null,
    val discount: DiscountDetail? = null,
    val failure: FailureDetail? = null
)

/**
 * 토스페이먼츠 결제 취소 요청 DTO
 */
data class TossPaymentCancelRequest(
    val cancelReason: String,
    val cancelAmount: Int? = null,  // null이면 전액 취소
    val refundReceiveAccount: RefundReceiveAccount? = null
)

/**
 * 토스페이먼츠 결제 취소 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TossPaymentCancelResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Int,
    val balanceAmount: Int,
    val suppliedAmount: Int,
    val vat: Int,
    val taxFreeAmount: Int,
    val cancels: List<CancelDetail>,
    val method: String,
    val approvedAt: String? = null,
    val requestedAt: String? = null
)

/**
 * 환불 계좌 정보
 */
data class RefundReceiveAccount(
    val bank: String,
    val accountNumber: String,
    val holderName: String
)

/**
 * 결제 취소 상세 정보
 */
data class CancelDetail(
    val cancelAmount: Int,
    val cancelReason: String,
    val taxFreeAmount: Int,
    val taxExemptionAmount: Int? = null,
    val refundableAmount: Int,
    val easyPayDiscountAmount: Int? = null,
    val canceledAt: String? = null,
    val transactionKey: String? = null,
    val receiptKey: String? = null
)

/**
 * 카드 결제 상세 정보
 */
data class CardDetail(
    val amount: Int,
    val issuerCode: String,
    val acquirerCode: String? = null,
    val number: String,
    val installmentPlanMonths: Int,
    val approveNo: String,
    val useCardPoint: Boolean,
    val cardType: String,
    val ownerType: String,
    val acquireStatus: String,
    val isInterestFree: Boolean,
    val interestPayer: String? = null
)

/**
 * 가상계좌 결제 상세 정보
 */
data class VirtualAccountDetail(
    val accountType: String,
    val accountNumber: String,
    val bankCode: String,
    val customerName: String,
    val dueDate: String,
    val refundStatus: String,
    val expired: Boolean,
    val settlementStatus: String
)

/**
 * 계좌이체 상세 정보
 */
data class TransferDetail(
    val bankCode: String,
    val settlementStatus: String
)

/**
 * 휴대폰 결제 상세 정보
 */
data class MobilePhoneDetail(
    val customerMobilePhone: String,
    val settlementStatus: String,
    val receiptUrl: String? = null
)

/**
 * 상품권 결제 상세 정보
 */
data class GiftCertificateDetail(
    val approveNo: String,
    val settlementStatus: String
)

/**
 * 간편결제 상세 정보
 */
data class EasyPayDetail(
    val provider: String,
    val amount: Int,
    val discountAmount: Int
)

/**
 * 현금영수증 상세 정보
 */
data class CashReceiptDetail(
    val type: String,
    val receiptKey: String,
    val issueNumber: String,
    val receiptUrl: String,
    val amount: Int,
    val taxFreeAmount: Int
)

/**
 * 할인 상세 정보
 */
data class DiscountDetail(
    val amount: Int
)

/**
 * 실패 상세 정보
 */
data class FailureDetail(
    val code: String,
    val message: String
)

/**
 * 토스 결제 승인 결과 DTO (내부 서비스용)
 */
data class TossPaymentConfirmResult(
    val paymentId: java.util.UUID,
    val paymentStatus: String,
    val orderStatus: String,
    val orderId: java.util.UUID,
    val orderNo: String,
    val amount: Int,
    val approvedAt: java.time.LocalDateTime
)

/**
 * 토스 결제 취소 결과 DTO (내부 서비스용)
 */
data class TossPaymentCancelResult(
    val paymentId: java.util.UUID,
    val orderId: java.util.UUID,
    val cancelAmount: Int,
    val status: String,
    val cancelReason: String
)

/**
 * 토스 결제 생성 결과 DTO (내부 서비스용)
 */
data class PaymentCreateResult(
    val paymentUrl: String,
    val orderId: String,
    val amount: Int,
    val expiresAt: java.time.LocalDateTime
)