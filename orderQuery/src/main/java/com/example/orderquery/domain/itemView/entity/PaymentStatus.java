package com.example.orderquery.domain.itemView.entity;

public enum PaymentStatus {
	PENDING,   // 결제 진행 중 (레거시/호환)
	READY,
	PAID,
	FAILED,
	CANCELLED
}
