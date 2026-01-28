package com.popcorn.order.dto.payment;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보안 결제 URL 응답 DTO
 *
 * 주문 생성 후 클라이언트에게 전달되는 안전한 결제 URL 정보입니다.
 * JWT 토큰이 포함된 URL로 변조 방지와 만료 시간 제어가 가능합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentUrlResponse {

    /** JWT 토큰이 포함된 완전한 결제 URL */
    @Schema(description = "보안 결제 URL", example = "https://pay.popcorn.com/payment?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...")
    private String paymentUrl;

    /** JWT 토큰 (URL 파라미터에서 추출 가능) */
    @Schema(description = "JWT 결제 토큰")
    private String token;

    /** 관련 주문 ID */
    @Schema(description = "주문 ID")
    private UUID orderId;

    /** 주문 번호 */
    @Schema(description = "주문 번호", example = "ORD-20240315-001")
    private String orderNo;

    /** 결제 금액 */
    @Schema(description = "결제 금액 (원)", example = "50000")
    private Long amount;

    /** 결제 방식 */
    @Schema(description = "결제 방식", example = "CARD")
    private String paymentMethod;

    /** 토큰 생성 시간 */
    @Schema(description = "토큰 생성 시간")
    private LocalDateTime createdAt;

    /** 토큰 만료 시간 */
    @Schema(description = "토큰 만료 시간")
    private LocalDateTime expiresAt;

    /** 사용자에게 표시할 만료 시간 (분 단위) */
    @Schema(description = "만료까지 남은 시간 (분)", example = "30")
    private Integer expiresInMinutes;

    /** QR 코드 URL (모바일 결제용) */
    @Schema(description = "QR 코드 URL")
    private String qrCodeUrl;

    /** 결제 페이지에서 사용할 추가 메타데이터 */
    @Schema(description = "추가 메타데이터")
    private java.util.Map<String, Object> metadata;

    // ========================= 편의 메서드 =========================

    /**
     * 토큰이 만료되었는지 확인
     * @return 만료되었으면 true
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 토큰이 곧 만료되는지 확인 (5분 이내)
     * @return 5분 이내 만료되면 true
     */
    public boolean isExpiringSoon() {
        if (expiresAt == null) return false;
        return LocalDateTime.now().isAfter(expiresAt.minusMinutes(5));
    }

    /**
     * 남은 유효 시간 계산 (분 단위)
     * @return 남은 시간 (분), 만료된 경우 0
     */
    public long getRemainingMinutes() {
        if (expiresAt == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) return 0;

        return java.time.Duration.between(now, expiresAt).toMinutes();
    }

    /**
     * 남은 유효 시간 계산 (초 단위)
     * @return 남은 시간 (초), 만료된 경우 0
     */
    public long getRemainingSeconds() {
        if (expiresAt == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) return 0;

        return java.time.Duration.between(now, expiresAt).getSeconds();
    }

    /**
     * 결제 URL에서 토큰만 추출
     * @return JWT 토큰 문자열
     */
    public String extractTokenFromUrl() {
        if (paymentUrl == null) return token;

        // URL에서 token 파라미터 추출
        try {
            String[] parts = paymentUrl.split("token=");
            if (parts.length > 1) {
                String tokenPart = parts[1];
                int ampIndex = tokenPart.indexOf('&');
                return ampIndex > 0 ? tokenPart.substring(0, ampIndex) : tokenPart;
            }
        } catch (Exception e) {
            // 추출 실패 시 저장된 토큰 반환
        }

        return token;
    }

    /**
     * 모바일 브라우저용 간단한 URL 생성
     * @return 모바일 최적화된 결제 URL
     */
    public String getMobilePaymentUrl() {
        if (paymentUrl == null) return null;
        return paymentUrl.replace("/payment", "/mobile/payment");
    }

    /**
     * 결제 정보 요약
     * @return 결제 정보 요약 문자열
     */
    public String getSummary() {
        return String.format("주문 %s - %,d원 %s 결제 (만료: %d분 후)",
                orderNo, amount, paymentMethod, getRemainingMinutes());
    }

    /**
     * 디버깅용 토큰 정보
     * @return 토큰의 앞/뒷부분만 포함한 안전한 정보
     */
    public String getTokenPreview() {
        if (token == null || token.length() < 20) return "N/A";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }

}
