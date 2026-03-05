package com.ig.sre.tubestatus.common;

public final class AppConstants {

    private AppConstants() {
        throw new IllegalStateException("Utility class");
    }

    public static final class PropertyPrefixes {
        public static final String TFL = "tfl";
        public static final String APP_API = "app.api";
        public static final String CACHE = "cache";
        public static final String SLI = "sli";

        private PropertyPrefixes() {
        }
    }

    public static final class CacheNames {
        public static final String LINE_STATUS_CACHE_BEAN = "lineStatusCache";
        public static final String UNPLANNED_DISRUPTION_CACHE_BEAN = "unplannedDisruptionCache";
        public static final String UNPLANNED_DISRUPTION_CACHE_KEY = "tube_unplanned_disruptions";
        public static final String CURRENT_KEY_PART = "current";

        private CacheNames() {
        }
    }

    public static final class Tfl {
        public static final String DEPENDENCY_KEY = "tfl";
        public static final String APP_ID_QUERY_PARAM = "app_id";
        public static final String APP_KEY_QUERY_PARAM = "app_key";
        public static final String OPERATION_LINE_STATUS_CURRENT = "line_status_current";
        public static final String OPERATION_LINE_STATUS_RANGE = "line_status_range";
        public static final String OPERATION_ALL_TUBE_STATUSES = "all_tube_statuses";

        private Tfl() {
        }
    }

    public static final class Api {
        public static final String DEFAULT_API_VERSION = "1.0";
        public static final String DEFAULT_API_VERSION_RANGE = "1.0";
        public static final String DEFAULT_API_VERSION_HEADER = "API-Version";
        public static final String PLACEHOLDER_BASE_PATH = "${app.api.base-path}";
        public static final String PLACEHOLDER_LINE_STATUS_PATH = "${app.api.line-status-path}";
        public static final String PLACEHOLDER_UNPLANNED_DISRUPTIONS_PATH = "${app.api.unplanned-disruptions-path}";
        public static final String HEADER_DATA_SOURCE = "X-Data-Source";
        public static final String HEADER_RETRY_AFTER = "Retry-After";
        public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
        public static final String HEADER_FORWARDED = "Forwarded";
        public static final String CLIENT_KEY_UNKNOWN = "unknown";
        public static final String DATA_SOURCE_LIVE = "LIVE";
        public static final String DATA_SOURCE_STALE = "STALE_CACHE";

        private Api() {
        }
    }

    public static final class OpenApi {
        public static final String TITLE = "Tube Status Service API";
        public static final String DESCRIPTION = "Resilient TfL Tube status endpoints with fallback and SRE metrics.";
        public static final String VERSION = "v1";

        private OpenApi() {
        }
    }

    public static final class Metrics {
        public static final String TUBE_STATUS_REQUESTS_TOTAL = "tube_status_requests_total";
        public static final String TUBE_STATUS_REQUEST_LATENCY = "tube_status_request_latency";
        public static final String TUBE_STATUS_FRESHNESS_TOTAL = "tube_status_freshness_total";
        public static final String TUBE_STATUS_CORRECTNESS_TOTAL = "tube_status_correctness_total";
        public static final String TAG_OUTCOME = "outcome";
        public static final String TAG_ENDPOINT = "endpoint";
        public static final String TAG_RESULT = "result";
        public static final String OUTCOME_CLIENT_ERROR = "client_error";
        public static final String OUTCOME_RATE_LIMITED = "rate_limited";
        public static final String OUTCOME_LIVE = "live";
        public static final String OUTCOME_STALE = "stale";
        public static final String OUTCOME_UNAVAILABLE = "unavailable";
        public static final String RESULT_FRESH = "fresh";
        public static final String RESULT_STALE = "stale";
        public static final String RESULT_VALID = "valid";
        public static final String RESULT_INVALID = "invalid";
        public static final String ENDPOINT_LINE_STATUS = "line_status";
        public static final String ENDPOINT_UNPLANNED_DISRUPTIONS = "unplanned_disruptions";
        public static final String REQUEST_LATENCY_DESCRIPTION = "Latency of incoming requests";

        private Metrics() {
        }
    }

    public static final class ErrorCodes {
        public static final String BAD_REQUEST = "bad_request";
        public static final String LINE_NOT_FOUND = "line_not_found";
        public static final String UPSTREAM_UNAVAILABLE = "upstream_unavailable";
        public static final String INTERNAL_ERROR = "internal_error";
        public static final String TOO_MANY_REQUESTS = "too_many_requests";

        private ErrorCodes() {
        }
    }

    public static final class Messages {
        public static final String START_END_BOTH_REQUIRED = "startDate and endDate must be provided together";
        public static final String END_MUST_BE_AFTER_START = "endDate must be on or after startDate";
        public static final String INVALID_REQUEST_PARAMETER_PREFIX = "Invalid request parameter: ";
        public static final String INVALID_REQUEST_PAYLOAD = "Invalid request payload";
        public static final String TUBE_LINE_NOT_FOUND_PREFIX = "Tube line not found: ";
        public static final String INVALID_LINE_STATUS_REQUEST_PREFIX = "Invalid request for line status: ";
        public static final String INVALID_DISRUPTION_REQUEST_PREFIX = "Invalid request for disruptions: ";
        public static final String LINE_STATUS_CACHE_MISS_UPSTREAM = "TfL API is unavailable and no cached line status is available";
        public static final String DISRUPTION_CACHE_MISS_UPSTREAM = "TfL API is unavailable and no cached disruption snapshot is available";
        public static final String LINE_STATUS_CACHE_MISS_SATURATED =
                "TfL dependency is saturated and no cached line status is available";
        public static final String DISRUPTION_CACHE_MISS_SATURATED =
                "TfL dependency is saturated and no cached disruption snapshot is available";
        public static final String RETRY_AFTER_SECONDS_SUFFIX_TEMPLATE = " Retry after %d seconds.";
        public static final String INTERNAL_SERVER_ERROR = "Unexpected internal server error";
        public static final String TFL_CLIENT_ERROR_PREFIX = "TfL API client error: ";
        public static final String TFL_SERVER_ERROR_PREFIX = "TfL API server error: ";
        public static final String TFL_TIMEOUT_OR_NETWORK = "TfL API timeout or network failure";
        public static final String TFL_UNEXPECTED_ERROR = "Unexpected error calling TfL API";
        public static final String TFL_CLIENT_SATURATED = "TfL dependency saturation: too many in-flight requests";
        public static final String TFL_SYNTHETIC_TIMEOUT = "Synthetic timeout injected for observability";
        public static final String TFL_SYNTHETIC_SERVER_ERROR = "Synthetic upstream 503 injected for observability";
        public static final String UNHANDLED_EXCEPTION_LOG = "Unhandled exception";

        private Messages() {
        }
    }

    public static final class MapperValues {
        public static final String GOOD_SERVICE = "Good Service";
        public static final String KEYWORD_ENGINEERING_WORK = "engineering work";
        public static final String KEYWORD_ENGINEERING_WORKS = "engineering works";
        public static final String KEYWORD_SCHEDULED_CLOSURE = "scheduled closure";
        public static final String KEYWORD_MAINTENANCE = "maintenance";

        private MapperValues() {
        }
    }

    public static final class Formats {
        public static final String LINE_STATUS_CACHE_KEY_FORMAT = "%s|%s|%s";

        private Formats() {
        }
    }

}
