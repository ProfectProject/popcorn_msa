package com.popcorn.common.dto

data class BaseResponse<T>(
    val data: T? = null,
    val message: String = "",
    val status: String = com.popcorn.payment.constants.EventConstants.EventStatus.SUCCESS
)
