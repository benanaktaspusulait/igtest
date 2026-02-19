package com.ig.sre.tubestatus.web;

import com.ig.sre.tubestatus.api.model.ApiError;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.exception.TooManyRequestsException;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import com.ig.sre.tubestatus.exception.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequestReturns400() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(new BadRequestException("bad request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(AppConstants.ErrorCodes.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("bad request");
    }

    @Test
    void handleNotFoundReturns404() {
        ResponseEntity<ApiError> response = handler.handleNotFound(new TubeLineNotFoundException("central"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(AppConstants.ErrorCodes.LINE_NOT_FOUND);
    }

    @Test
    void handleUpstreamUnavailableReturns503() {
        ResponseEntity<ApiError> response = handler.handleUpstreamUnavailable(
                new UpstreamUnavailableException("upstream unavailable", new RuntimeException("cause"))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(AppConstants.ErrorCodes.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void handleTooManyRequestsSetsRetryAfterHeader() {
        ResponseEntity<ApiError> response = handler.handleTooManyRequests(new TooManyRequestsException(9));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(AppConstants.Api.HEADER_RETRY_AFTER)).isEqualTo("9");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(AppConstants.ErrorCodes.TOO_MANY_REQUESTS);
    }

    @Test
    void handleMethodArgumentTypeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "invalid",
                String.class,
                "lineId",
                null,
                new IllegalArgumentException("invalid type")
        );

        ResponseEntity<ApiError> response = handler.handleMethodArgumentTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo(AppConstants.Messages.INVALID_REQUEST_PARAMETER_PREFIX + "lineId");
    }

    @Test
    void handleRequestBindingProblemsReturns400() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("startDate", "String");

        ResponseEntity<ApiError> response = handler.handleRequestBindingProblems(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(AppConstants.Messages.INVALID_REQUEST_PAYLOAD);
    }

    @Test
    void handleGenericReturns500() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(AppConstants.ErrorCodes.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo(AppConstants.Messages.INTERNAL_SERVER_ERROR);
    }
}
