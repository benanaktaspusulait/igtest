package com.ig.sre.resilience.spring.config;

public final class SpringAdapterConstants {

    private SpringAdapterConstants() {
        throw new IllegalStateException("Utility class");
    }

    public static final class Config {
        public static final String RESILIENCE_PREFIX = "resilience";
        public static final String MISSING_POLICY_MESSAGE_PREFIX =
                "No resilience policy configured for dependency: ";

        private Config() {
        }
    }

    public static final class Metrics {
        public static final String REQUESTS_TOTAL = "requests_total";
        public static final String LATENCY_MS = "latency_ms";
        public static final String RETRIES_TOTAL = "retries_total";
        public static final String CIRCUIT_STATE = "circuit_state";
        public static final String RATE_LIMITED_TOTAL = "rate_limited_total";
        public static final String TAG_DEPENDENCY = "dependency";
        public static final String TAG_OPERATION = "operation";
        public static final String TAG_OUTCOME = "outcome";
        public static final String TAG_CLIENT_TYPE = "clientType";
        public static final String TAG_STATE = "state";
        public static final String CLIENT_ANONYMOUS = "anonymous";
        public static final String CLIENT_IDENTIFIED = "identified";

        private Metrics() {
        }
    }

    public static final class Tracing {
        public static final String SPAN_SUFFIX_REQUEST = ".request";
        public static final String TAG_CLIENT = "client";

        private Tracing() {
        }
    }
}
