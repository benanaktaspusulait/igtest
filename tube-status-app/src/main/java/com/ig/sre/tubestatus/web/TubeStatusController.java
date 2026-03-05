package com.ig.sre.tubestatus.web;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.service.ClientKeyResolver;
import com.ig.sre.tubestatus.service.TubeStatusRequestValidator;
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

@RestController
@RequestMapping(AppConstants.Api.PLACEHOLDER_BASE_PATH)
public class TubeStatusController {

    private final TubeStatusService tubeStatusService;
    private final TubeStatusRequestValidator requestValidator;
    private final ClientKeyResolver clientKeyResolver;

    public TubeStatusController(
            TubeStatusService tubeStatusService,
            TubeStatusRequestValidator requestValidator,
            ClientKeyResolver clientKeyResolver
    ) {
        this.tubeStatusService = tubeStatusService;
        this.requestValidator = requestValidator;
        this.clientKeyResolver = clientKeyResolver;
    }

    @GetMapping(path = AppConstants.Api.PLACEHOLDER_LINE_STATUS_PATH, version = AppConstants.Api.DEFAULT_API_VERSION)
    public ResponseEntity<LineStatusResponse> getLineStatus(
            @PathVariable("lineId") String lineId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request
    ) {
        requestValidator.validateDateRange(startDate, endDate);

        String clientKey = clientKeyResolver.resolve(request);
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
        String clientKey = clientKeyResolver.resolve(request);
        UnplannedDisruptionsResponse response = tubeStatusService.getUnplannedDisruptions(clientKey);
        return ResponseEntity.ok()
                .header(
                        AppConstants.Api.HEADER_DATA_SOURCE,
                        response.stale() ? AppConstants.Api.DATA_SOURCE_STALE : AppConstants.Api.DATA_SOURCE_LIVE
                )
                .body(response);
    }
}
