package com.ig.sre.tubestatus.api.model;

import java.time.Instant;

public record ApiError(String error, String message, Instant timestamp) {
}
