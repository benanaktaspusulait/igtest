package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.StatusEntry;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.api.model.UnplannedLineDisruption;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.client.tfl.model.TflDisruption;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.client.tfl.model.TflLineStatus;
import com.ig.sre.tubestatus.client.tfl.model.TflValidityPeriod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TubeStatusMapper {

    private static final Pattern PLANNED_WORD_PATTERN = Pattern.compile("\\bplanned\\b");
    private static final Pattern UNPLANNED_WORD_PATTERN = Pattern.compile("\\bunplanned\\b");

    public LineStatusResponse toLineStatusResponse(TflLine line, boolean stale, Instant fetchedAt) {
        List<StatusEntry> statuses = mapStatuses(line.lineStatuses(), false);
        return new LineStatusResponse(line.id(), line.name(), statuses, stale, fetchedAt);
    }

    public UnplannedDisruptionsResponse toUnplannedDisruptionsResponse(List<TflLine> lines, boolean stale, Instant fetchedAt) {
        List<UnplannedLineDisruption> affectedLines = lines.stream()
                .map(line -> new UnplannedLineDisruption(line.id(), line.name(), mapStatuses(line.lineStatuses(), true)))
                .filter(line -> !line.statuses().isEmpty())
                .toList();

        return new UnplannedDisruptionsResponse(affectedLines, stale, fetchedAt);
    }

    private List<StatusEntry> mapStatuses(List<TflLineStatus> statuses, boolean onlyUnplanned) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }

        return statuses.stream()
                .filter(status -> !onlyUnplanned || isUnplannedStatus(status))
                .map(this::toStatusEntry)
                .toList();
    }

    private StatusEntry toStatusEntry(TflLineStatus status) {
        TflValidityPeriod firstPeriod = firstPeriod(status.validityPeriods());

        return new StatusEntry(
                status.statusSeverityDescription(),
                status.reason(),
                isPlannedStatus(status),
                firstPeriod == null ? null : firstPeriod.fromDate(),
                firstPeriod == null ? null : firstPeriod.toDate()
        );
    }

    private TflValidityPeriod firstPeriod(List<TflValidityPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            return null;
        }
        return periods.getFirst();
    }

    private boolean isUnplannedStatus(TflLineStatus status) {
        return !isGoodService(status) && !isPlannedStatus(status);
    }

    private boolean isGoodService(TflLineStatus status) {
        return AppConstants.MapperValues.GOOD_SERVICE.equalsIgnoreCase(status.statusSeverityDescription());
    }

    private boolean isPlannedStatus(TflLineStatus status) {
        TflDisruption disruption = status.disruption();
        if (disruption != null) {
            if (Boolean.TRUE.equals(disruption.isPlanned())) {
                return true;
            }
            if (containsPlannedKeyword(disruption.category()) || containsPlannedKeyword(disruption.categoryDescription())) {
                return true;
            }
        }

        return containsPlannedKeyword(status.reason());
    }

    private boolean containsPlannedKeyword(String value) {
        if (value == null) {
            return false;
        }

        String lowered = value.toLowerCase(Locale.ROOT);
        return (PLANNED_WORD_PATTERN.matcher(lowered).find()
                && !UNPLANNED_WORD_PATTERN.matcher(lowered).find())
                || lowered.contains(AppConstants.MapperValues.KEYWORD_ENGINEERING_WORK)
                || lowered.contains(AppConstants.MapperValues.KEYWORD_ENGINEERING_WORKS)
                || lowered.contains(AppConstants.MapperValues.KEYWORD_SCHEDULED_CLOSURE)
                || lowered.contains(AppConstants.MapperValues.KEYWORD_MAINTENANCE);
    }
}
