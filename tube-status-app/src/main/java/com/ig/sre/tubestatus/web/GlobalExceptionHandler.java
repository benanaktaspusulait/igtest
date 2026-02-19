package com.ig.sre.tubestatus.web;

import com.ig.sre.tubestatus.api.model.ApiError;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.exception.TooManyRequestsException;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import com.ig.sre.tubestatus.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, AppConstants.ErrorCodes.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TubeLineNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(TubeLineNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, AppConstants.ErrorCodes.LINE_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(UpstreamUnavailableException ex) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                AppConstants.ErrorCodes.UPSTREAM_UNAVAILABLE,
                ex.getMessage()
        );
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(AppConstants.Api.HEADER_RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ApiError(AppConstants.ErrorCodes.TOO_MANY_REQUESTS, ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                AppConstants.ErrorCodes.BAD_REQUEST,
                AppConstants.Messages.INVALID_REQUEST_PARAMETER_PREFIX + ex.getName()
        );
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            BindException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> handleRequestBindingProblems(Exception ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                AppConstants.ErrorCodes.BAD_REQUEST,
                AppConstants.Messages.INVALID_REQUEST_PAYLOAD
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        LOGGER.error(AppConstants.Messages.UNHANDLED_EXCEPTION_LOG, ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                AppConstants.ErrorCodes.INTERNAL_ERROR,
                AppConstants.Messages.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(error, message, Instant.now()));
    }
}
