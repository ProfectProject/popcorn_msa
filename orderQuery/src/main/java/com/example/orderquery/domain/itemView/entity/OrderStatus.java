package com.example.orderquery.domain.itemView.entity;



public enum OrderStatus {

	PENDING,         // 주문 대기 (레거시)
	REQUESTED,       // 주문 요청됨
	ACCEPTED,        // 주문 수락됨
	CONFIRMED,       // 주문 확정됨 (레거시/호환)
	PROCESSING,      // 처리 중 (레거시/호환)
	REJECTED,        // 주문 거절됨
	RESERVED,        // 예약 확정됨
	PAYMENT_PENDING, // 결제 대기
	PENDING_PAYMENT, // 결제 대기 (레거시 명칭)
	PAID,            // 결제 완료됨
	COMPLETED,       // 완료
	CANCELLED,       // 취소됨
	REFUND_PENDING,  // 환불 대기 (레거시/호환)
	REFUNDED         // 환불 완료 (레거시/호환)

}
