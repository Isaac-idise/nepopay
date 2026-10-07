package com.nepopay.admin;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Admin filters take plain dates (2026-10-01), read as Nigerian (Africa/Lagos) calendar days. */
final class AdminDates {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private AdminDates() {
    }

    static OffsetDateTime startOfDay(String date) {
        return parse(date).atStartOfDay(LAGOS).toOffsetDateTime();
    }

    /** The first instant AFTER that day, so a "to" date is inclusive. */
    static OffsetDateTime startOfNextDay(String date) {
        return parse(date).plusDays(1).atStartOfDay(LAGOS).toOffsetDateTime();
    }

    static OffsetDateTime startOfToday() {
        return LocalDate.now(LAGOS).atStartOfDay(LAGOS).toOffsetDateTime();
    }

    private static LocalDate parse(String date) {
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "Dates must look like 2026-10-01.");
        }
    }
}
