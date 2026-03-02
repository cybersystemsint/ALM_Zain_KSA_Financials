package com.zain.ksa.alm.financials.dto.response;


import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated API response envelope.
 * Decouples the Spring Page object from the API contract.
 */
@Value
@Builder
public class PagedResponse<T> {

    List<T> content;
    int pageNumber;
    int pageSize;
    long totalElements;
    int totalPages;
    boolean last;

    public static <T> PagedResponse<T> of(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}