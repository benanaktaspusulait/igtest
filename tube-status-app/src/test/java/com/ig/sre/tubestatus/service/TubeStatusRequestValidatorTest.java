package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TubeStatusRequestValidatorTest {

    private final TubeStatusRequestValidator validator = new TubeStatusRequestValidator();

    @Test
    void validateDateRangeAcceptsMissingDates() {
        assertDoesNotThrow(() -> validator.validateDateRange(null, null));
    }

    @Test
    void validateDateRangeAcceptsValidRange() {
        LocalDate startDate = LocalDate.of(2026, 2, 20);
        LocalDate endDate = LocalDate.of(2026, 2, 21);

        assertDoesNotThrow(() -> validator.validateDateRange(startDate, endDate));
    }

    @Test
    void validateDateRangeRejectsSingleBoundary() {
        LocalDate startDate = LocalDate.of(2026, 2, 20);

        assertThrows(BadRequestException.class, () -> validator.validateDateRange(startDate, null));
        assertThrows(BadRequestException.class, () -> validator.validateDateRange(null, startDate));
    }

    @Test
    void validateDateRangeRejectsEndBeforeStart() {
        LocalDate startDate = LocalDate.of(2026, 2, 21);
        LocalDate endDate = LocalDate.of(2026, 2, 20);

        assertThrows(BadRequestException.class, () -> validator.validateDateRange(startDate, endDate));
    }
}
