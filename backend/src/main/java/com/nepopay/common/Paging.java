package com.nepopay.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class Paging {

    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    /** Clamps client-supplied paging so nobody can request a million rows. */
    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE));
    }
}
