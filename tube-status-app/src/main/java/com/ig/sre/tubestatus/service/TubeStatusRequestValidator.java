package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TubeStatusRequestValidator {

    public void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new BadRequestException(AppConstants.Messages.START_END_BOTH_REQUIRED);
        }

        if (startDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException(AppConstants.Messages.END_MUST_BE_AFTER_START);
        }
    }
}
