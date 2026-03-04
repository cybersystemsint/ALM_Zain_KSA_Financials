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

    /**
     * Convert Spring Data Page to PagedResponse.
     *
     * @param <T> Generic type of page content
     * @param page Spring Data Page object
     * @return PagedResponse with equivalent data
     */
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

    /**
     * Convert Spring Data Page with content transformation.
     *
     * <p>Useful when you need to map page contents (e.g., entity → DTO).</p>
     *
     * @param <T> target generic type
     * @param <U> source generic type (from page)
     * @param page Spring Data Page with source type
     * @param mapper function to transform each element
     * @return PagedResponse with transformed content
     */
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