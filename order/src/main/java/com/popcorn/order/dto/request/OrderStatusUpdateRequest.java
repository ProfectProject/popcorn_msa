package com.popcorn.order.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

/**
 * 주문 상태 변경 요청 DTO
 *
 * 팝업 스토어 관리자나 시스템이 주문 상태를 변경할 때 사용됩니다.
 */
@Getter
@Builder
@Schema(description = "주문 상태 변경 요청 - 스토어, 관리자, 시스템이 주문 상태를 변경할 때 사용")
public class OrderStatusUpdateRequest {

    /** 상태를 변경할 주문 ID */
    @Schema(description = "상태를 변경할 주문 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "주문 ID는 필수입니다.")
    private final UUID orderId;

    /** 주문 번호 (선택적, 검증용) */
    @Schema(description = "주문 번호 (검증용)", example = "O-20241201-001")
    private final String orderNo;

    /** 변경할 상태 */
    @Schema(description = "변경할 주문 상태", example = "ACCEPTED",
            allowableValues = {"REQUESTED", "ACCEPTED", "RESERVED", "PAYMENT_PENDING", "PAID", "COMPLETED", "CANCELLED", "REJECTED"})
    @NotNull(message = "변경할 상태는 필수입니다.")
    private final String newStatus;

    /** 백엔드 호환성을 위한 status getter */
    public String getStatus() {
        return newStatus;
    }

    /** 상태 변경 사유 */
    @Size(max = 500, message = "상태 변경 사유는 500자 이하여야 합니다.")
    private final String reason;

    /** 상태 변경 주체 (STORE, MANAGER, SYSTEM) */
    @NotNull(message = "상태 변경 주체는 필수입니다.")
    private final String updatedBy;

    /** 변경 요청자 ID (관리자 ID 또는 스토어 ID) */
    private final Long requesterId;

    /** 강제 상태 변경 여부 (관리자 권한) */
    @Builder.Default
    private final Boolean forceUpdate = false;

    /** 고객 알림 발송 여부 */
    @Builder.Default
    private final Boolean notifyCustomer = true;

    /** 추가 메모 (내부용) */
    @Size(max = 1000, message = "메모는 1000자 이하여야 합니다.")
    private final String internalNote;
}
