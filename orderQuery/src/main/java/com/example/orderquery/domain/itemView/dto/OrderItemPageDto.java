package com.example.orderquery.domain.itemView.dto;

import java.util.List;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class OrderItemPageDto {

    private final List<OrderItemDto> orders;
    private final PageInfoDto pageInfo;
}
