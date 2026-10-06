package com.nepopay.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable page shape for the API (instead of exposing Spring's internal Page JSON). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
