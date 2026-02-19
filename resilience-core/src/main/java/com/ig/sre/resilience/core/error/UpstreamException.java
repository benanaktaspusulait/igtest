package com.ig.sre.resilience.core.error;

import java.io.Serial;

public class UpstreamException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;
    private final Integer statusCode;

    public UpstreamException(ErrorCategory category, String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.statusCode = statusCode;
    }

    public static UpstreamException clientError(int statusCode, String message) {
        return new UpstreamException(ErrorCategory.CLIENT_ERROR, message, statusCode, null);
    }

    public static UpstreamException serverError(int statusCode, String message) {
        return new UpstreamException(ErrorCategory.SERVER_ERROR, message, statusCode, null);
    }

    public static UpstreamException timeout(String message, Throwable cause) {
        return new UpstreamException(ErrorCategory.TIMEOUT, message, null, cause);
    }

    public static UpstreamException network(String message, Throwable cause) {
        return new UpstreamException(ErrorCategory.NETWORK, message, null, cause);
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

}
