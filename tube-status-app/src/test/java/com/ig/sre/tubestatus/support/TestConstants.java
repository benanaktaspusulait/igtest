package com.ig.sre.tubestatus.support;

public final class TestConstants {

    private TestConstants() {
    }

    public static final String API_VERSION = "1.0";
    public static final String API_PATH_LINE_STATUS_CENTRAL = "/api/v1/tube/central/status";
    public static final String API_PATH_UNPLANNED_DISRUPTIONS = "/api/v1/tube/disruptions/unplanned";
    public static final String TEST_TFL_BASE_URL = "https://api.tfl.test";
    public static final String TEST_TFL_LINE_STATUS_PATH = "/Line/{lineId}/Status";
    public static final String TEST_TFL_LINE_STATUS_RANGE_PATH = "/Line/{lineId}/Status/{startDate}/to/{endDate}";
    public static final String TEST_TFL_ALL_TUBE_STATUSES_PATH = "/Line/Mode/tube/Status";
    public static final String TEST_TFL_APP_ID = "app-1";
    public static final String TEST_TFL_APP_KEY = "key-1";

    public static final String CLIENT_KEY_IP_1 = "ip-1";
    public static final String CLIENT_KEY_IP_2 = "ip-2";
    public static final String CLIENT_KEY_IP_3 = "ip-3";
    public static final String REMOTE_ADDR_10_0_0_5 = "10.0.0.5";
    public static final String REMOTE_ADDR_10_0_0_9_TRIMMED = "10.0.0.9";
    public static final String REMOTE_ADDR_10_0_0_9_PADDED = " 10.0.0.9 ";
    public static final String FORWARDED_FOR_203_0_113_10 = "203.0.113.10";

    public static final String DATE_2026_02_01 = "2026-02-01";
    public static final String DATE_2026_02_02 = "2026-02-02";
    public static final String DATE_2026_02_20 = "2026-02-20";
    public static final String DATE_2026_02_21 = "2026-02-21";
    public static final String DATE_INVALID_2026_02_20 = "2026/02/20";
    public static final String LINE_STATUS_CACHE_KEY_CURRENT = "central|current|current";
    public static final String LINE_STATUS_CACHE_KEY_RANGE = "central|2026-02-01|2026-02-02";

    public static final String TIMESTAMP_2026_02_19T09_00_00Z = "2026-02-19T09:00:00Z";
    public static final String TIMESTAMP_2026_02_19T09_30_00Z = "2026-02-19T09:30:00Z";
    public static final String TIMESTAMP_2026_02_19T10_00_00Z = "2026-02-19T10:00:00Z";
    public static final String TIMESTAMP_2026_02_19T11_00_00Z = "2026-02-19T11:00:00Z";

    public static final String LINE_ID_NORTHERN = "northern";
    public static final String LINE_NAME_NORTHERN = "Northern";
    public static final String LINE_ID_UNKNOWN = "unknown";
    public static final String LINE_ID_CENTRAL_UPPER = "CENTRAL";
    public static final String MODE_TUBE = "tube";
    public static final String STATUS_MINOR_DELAYS = "Minor Delays";
    public static final String STATUS_SEVERE_DELAYS = "Severe Delays";
    public static final String REASON_PLANNED_ENGINEERING_WEEKEND = "Planned engineering works this weekend";
    public static final String REASON_PLANNED_ENGINEERING = "Planned engineering works";
    public static final String REASON_SIGNAL_FAILURE_LIVERPOOL_STREET = "Signal failure at Liverpool Street";
    public static final String DISRUPTION_PLANNED_WORK = "PlannedWork";
    public static final String DISRUPTION_PLANNED_MAINTENANCE = "Planned maintenance";
    public static final String DISRUPTION_PLANNED_WORKS = "Planned works";
    public static final String DISRUPTION_REAL_TIME = "RealTime";
    public static final String DISRUPTION_SIGNAL_FAILURE = "Signal failure";
    public static final String VALIDITY_FROM = TIMESTAMP_2026_02_19T10_00_00Z;
    public static final String VALIDITY_TO = "2026-02-19T16:00:00Z";
    public static final String FETCHED_AT = "2026-02-19T10:30:00Z";
    public static final String LINE_ID_CENTRAL = "central";
    public static final String LINE_NAME_CENTRAL = "Central";
}
