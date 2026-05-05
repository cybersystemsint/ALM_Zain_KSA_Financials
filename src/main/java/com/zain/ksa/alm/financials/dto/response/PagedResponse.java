package com.zain.ksa.alm.financials.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated API response envelope.
 *
 * <p>Decouples the Spring Page object from the API contract.</p>
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>From Spring Page: PagedResponse.of(page)</li>
 *   <li>Manual construction: PagedResponse.builder().content(...).pageNumber(...).build()</li>
 * </ul>
 *
 * <p><b>Generic type safety:</b> Supports any DTO type (AssetDepreciationDetailDTO,
 * DepreciationHistoryDTO, etc.).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {

    /** List of items on this page. */
    private List<T> content;

    /** Current page number (0-indexed). */
    private int pageNumber;

    /** Page size (records per page). */
    private int pageSize;

    /** Total number of elements across all pages. */
    private long totalElements;

    /** Total number of pages. */
    private int totalPages;

    /** Whether this is the last page. */
    private boolean last;


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

    public static <T, U> PagedResponse<T> of(Page<U> page, java.util.function.Function<U, T> mapper) {
        return PagedResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}