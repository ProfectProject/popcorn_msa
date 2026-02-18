package com.example.orderquery.domain.itemView.dto;


import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class PageInfoDto {
    private final int page;
    private final int currentPage;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;
}