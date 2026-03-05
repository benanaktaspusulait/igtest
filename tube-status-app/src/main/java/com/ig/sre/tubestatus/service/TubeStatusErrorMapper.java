package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import com.ig.sre.resilience.core.error.UpstreamException;
import org.springframework.stereotype.Component;

@Component
public class TubeStatusErrorMapper {

    public RuntimeException toLineStatusClientError(UpstreamException upstreamException, String lineId) {
        if (upstreamException.getStatusCode() != null && upstreamException.getStatusCode() == 404) {
            return new TubeLineNotFoundException(lineId);
        }
        return new BadRequestException(AppConstants.Messages.INVALID_LINE_STATUS_REQUEST_PREFIX + upstreamException.getMessage());
    }

    public RuntimeException toDisruptionsClientError(UpstreamException upstreamException) {
        return new BadRequestException(AppConstants.Messages.INVALID_DISRUPTION_REQUEST_PREFIX + upstreamException.getMessage());
    }
}
