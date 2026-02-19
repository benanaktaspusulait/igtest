package com.ig.sre.tubestatus.web;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.ApiProperties;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.service.TubeStatusService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping(AppConstants.Api.PLACEHOLDER_BASE_PATH)
public class TubeStatusController {

    private final TubeStatusService tubeStatusService;
    private final boolean trustForwardHeaders;
    private final Set<String> trustedProxyIps;

    public TubeStatusController(TubeStatusService tubeStatusService, ApiProperties apiProperties) {
        this.tubeStatusService = tubeStatusService;
        this.trustForwardHeaders = apiProperties.isTrustForwardHeaders();
        this.trustedProxyIps = Set.copyOf(apiProperties.getTrustedProxyIps());
    }

    @GetMapping(path = AppConstants.Api.PLACEHOLDER_LINE_STATUS_PATH, version = AppConstants.Api.DEFAULT_API_VERSION)
    public ResponseEntity<LineStatusResponse> getLineStatus(
            @PathVariable("lineId") String lineId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request
    ) {
        validateDateRange(startDate, endDate);

        String clientKey = resolveClientKey(request);
        LineStatusResponse response = tubeStatusService.getLineStatus(lineId, startDate, endDate, clientKey);
        return ResponseEntity.ok()
                .header(
                        AppConstants.Api.HEADER_DATA_SOURCE,
                        response.stale() ? AppConstants.Api.DATA_SOURCE_STALE : AppConstants.Api.DATA_SOURCE_LIVE
                )
                .body(response);
    }

    @GetMapping(path = AppConstants.Api.PLACEHOLDER_UNPLANNED_DISRUPTIONS_PATH, version = AppConstants.Api.DEFAULT_API_VERSION)
    public ResponseEntity<UnplannedDisruptionsResponse> getUnplannedDisruptions(HttpServletRequest request) {
        String clientKey = resolveClientKey(request);
        UnplannedDisruptionsResponse response = tubeStatusService.getUnplannedDisruptions(clientKey);
        return ResponseEntity.ok()
                .header(
                        AppConstants.Api.HEADER_DATA_SOURCE,
                        response.stale() ? AppConstants.Api.DATA_SOURCE_STALE : AppConstants.Api.DATA_SOURCE_LIVE
                )
                .body(response);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new BadRequestException(AppConstants.Messages.START_END_BOTH_REQUIRED);
        }

        if (startDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException(AppConstants.Messages.END_MUST_BE_AFTER_START);
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String remoteAddr = normalizeClientToken(request.getRemoteAddr());
        if (remoteAddr == null) {
            return AppConstants.Api.CLIENT_KEY_UNKNOWN;
        }

        if (trustForwardHeaders && trustedProxyIps.contains(remoteAddr)) {
            String forwardedClient = extractForwardedClientIp(request);
            if (forwardedClient != null) {
                return forwardedClient;
            }
        }
        return remoteAddr;
    }

    private String extractForwardedClientIp(HttpServletRequest request) {
        String standardizedForwarded = parseStandardizedForwardedHeader(
                request.getHeader(AppConstants.Api.HEADER_FORWARDED)
        );
        if (standardizedForwarded != null) {
            return standardizedForwarded;
        }

        String forwardedFor = request.getHeader(AppConstants.Api.HEADER_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String firstToken = forwardedFor.split(",", 2)[0];
        return normalizeClientToken(firstToken);
    }

    private String parseStandardizedForwardedHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        String[] elements = headerValue.split(",");
        for (String element : elements) {
            String[] pairs = element.split(";");
            for (String pair : pairs) {
                String candidate = pair.trim();
                if (candidate.regionMatches(true, 0, "for=", 0, 4)) {
                    String parsed = normalizeClientToken(candidate.substring(4));
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeClientToken(String token) {
        if (token == null) {
            return null;
        }

        String value = token.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }

        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else {
            int lastColon = value.lastIndexOf(':');
            if (lastColon > 0 && value.indexOf(':') == lastColon && isDigits(value.substring(lastColon + 1))) {
                value = value.substring(0, lastColon);
            }
        }

        return value.isBlank() ? null : value;
    }

    private boolean isDigits(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
