package com.ig.sre.tubestatus.exception;

import com.ig.sre.tubestatus.common.AppConstants;

public class TubeLineNotFoundException extends RuntimeException {

    public TubeLineNotFoundException(String lineId) {
        super(AppConstants.Messages.TUBE_LINE_NOT_FOUND_PREFIX + lineId);
    }
}
