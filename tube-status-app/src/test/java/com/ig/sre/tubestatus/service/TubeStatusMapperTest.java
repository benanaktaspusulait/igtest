package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.client.tfl.model.TflDisruption;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.client.tfl.model.TflLineStatus;
import com.ig.sre.tubestatus.client.tfl.model.TflValidityPeriod;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.support.TestConstants;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TubeStatusMapperTest {

    private final TubeStatusMapper mapper = new TubeStatusMapper();

    @Test
    void lineStatusResponseMarksPlannedEntries() {
        TflLine line = new TflLine(
                TestConstants.LINE_ID_NORTHERN,
                TestConstants.LINE_NAME_NORTHERN,
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(
                                9,
                                TestConstants.STATUS_MINOR_DELAYS,
                                TestConstants.REASON_PLANNED_ENGINEERING_WEEKEND,
                                new TflDisruption(
                                        TestConstants.DISRUPTION_PLANNED_WORK,
                                        TestConstants.DISRUPTION_PLANNED_WORK,
                                        TestConstants.DISRUPTION_PLANNED_MAINTENANCE,
                                        true
                                ),
                                List.of(
                                        new TflValidityPeriod(
                                                OffsetDateTime.parse(TestConstants.VALIDITY_FROM),
                                                OffsetDateTime.parse(TestConstants.VALIDITY_TO),
                                                true
                                        )
                                )
                        )
                )
        );

        LineStatusResponse response = mapper.toLineStatusResponse(
                line,
                false,
                Instant.parse(TestConstants.FETCHED_AT)
        );

        assertThat(response.statuses()).hasSize(1);
        assertThat(response.statuses().getFirst().planned()).isTrue();
        assertThat(response.stale()).isFalse();
    }

    @Test
    void lineStatusResponseHandlesNullLineStatuses() {
        TflLine line = new TflLine(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                TestConstants.MODE_TUBE,
                List.of(),
                null
        );

        LineStatusResponse response = mapper.toLineStatusResponse(line, false, Instant.now());

        assertThat(response.statuses()).isEmpty();
    }

    @Test
    void unplannedDisruptionsExcludesPlannedAndGoodService() {
        TflLine line = new TflLine(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(10, AppConstants.MapperValues.GOOD_SERVICE, null, null, List.of()),
                        new TflLineStatus(
                                9,
                                TestConstants.STATUS_MINOR_DELAYS,
                                TestConstants.REASON_PLANNED_ENGINEERING,
                                new TflDisruption(
                                        TestConstants.DISRUPTION_PLANNED_WORK,
                                        TestConstants.DISRUPTION_PLANNED_WORK,
                                        TestConstants.DISRUPTION_PLANNED_WORKS,
                                        true
                                ),
                                List.of()
                        ),
                        new TflLineStatus(
                                6,
                                TestConstants.STATUS_SEVERE_DELAYS,
                                TestConstants.REASON_SIGNAL_FAILURE_LIVERPOOL_STREET,
                                new TflDisruption(
                                        TestConstants.DISRUPTION_REAL_TIME,
                                        TestConstants.DISRUPTION_REAL_TIME,
                                        TestConstants.DISRUPTION_SIGNAL_FAILURE,
                                        false
                                ),
                                List.of()
                        )
                )
        );

        UnplannedDisruptionsResponse response = mapper.toUnplannedDisruptionsResponse(List.of(line), false, Instant.now());

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().statuses()).hasSize(1);
        assertThat(response.lines().getFirst().statuses().getFirst().status())
                .isEqualTo(TestConstants.STATUS_SEVERE_DELAYS);
        assertThat(response.lines().getFirst().statuses().getFirst().planned()).isFalse();
    }

    @Test
    void unplannedDisruptionsTreatsScheduledClosureAsPlanned() {
        TflLine line = new TflLine(
                "jubilee",
                "Jubilee",
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(
                                9,
                                TestConstants.STATUS_MINOR_DELAYS,
                                "Scheduled closure this weekend",
                                null,
                                List.of()
                        )
                )
        );

        UnplannedDisruptionsResponse response = mapper.toUnplannedDisruptionsResponse(List.of(line), false, Instant.now());

        assertThat(response.lines()).isEmpty();
    }

    @Test
    void unplannedDisruptionsTreatsDisruptionCategoryPlannedAsPlanned() {
        TflLine line = new TflLine(
                "district",
                "District",
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(
                                6,
                                TestConstants.STATUS_SEVERE_DELAYS,
                                "Service update",
                                new TflDisruption(
                                        "Planned Works",
                                        "Planned Works",
                                        "Routine works",
                                        false
                                ),
                                List.of()
                        )
                )
        );

        UnplannedDisruptionsResponse response = mapper.toUnplannedDisruptionsResponse(List.of(line), false, Instant.now());

        assertThat(response.lines()).isEmpty();
    }

    @Test
    void unplannedDisruptionsKeepsStatusWhenReasonIsNullAndNoDisruptionMetadata() {
        TflLine line = new TflLine(
                "victoria",
                "Victoria",
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(
                                6,
                                TestConstants.STATUS_SEVERE_DELAYS,
                                null,
                                null,
                                List.of()
                        )
                )
        );

        UnplannedDisruptionsResponse response = mapper.toUnplannedDisruptionsResponse(List.of(line), false, Instant.now());

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().statuses()).hasSize(1);
    }

    @Test
    void unplannedKeywordDoesNotGetClassifiedAsPlanned() {
        TflLine line = new TflLine(
                "victoria",
                "Victoria",
                TestConstants.MODE_TUBE,
                List.of(),
                List.of(
                        new TflLineStatus(
                                6,
                                TestConstants.STATUS_SEVERE_DELAYS,
                                "Unplanned signal failure near station",
                                null,
                                List.of()
                        )
                )
        );

        UnplannedDisruptionsResponse response = mapper.toUnplannedDisruptionsResponse(List.of(line), false, Instant.now());

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().statuses()).hasSize(1);
        assertThat(response.lines().getFirst().statuses().getFirst().planned()).isFalse();
    }
}
