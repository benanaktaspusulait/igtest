package com.ig.sre.tubestatus.web;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.StatusEntry;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.api.model.UnplannedLineDisruption;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.service.TubeStatusService;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.tubestatus.support.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TubeStatusControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private TubeStatusService tubeStatusService;

    @MockitoBean
    private ResilientExecutor resilientExecutor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .build();
    }

    @Test
    void getLineStatusReturnsLiveResponseAndHeader() throws Exception {
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T10_00_00Z)
        );

        when(tubeStatusService.getLineStatus(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get(TestConstants.API_PATH_LINE_STATUS_CENTRAL).header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION))
                .andExpect(status().isOk())
                .andExpect(header().string(AppConstants.Api.HEADER_DATA_SOURCE, AppConstants.Api.DATA_SOURCE_LIVE))
                .andExpect(jsonPath("$.lineId").value(TestConstants.LINE_ID_CENTRAL))
                .andExpect(jsonPath("$.lineName").value(TestConstants.LINE_NAME_CENTRAL));
    }

    @Test
    void getLineStatusReturnsStaleHeaderWhenResponseIsStale() throws Exception {
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Minor Delays", null, false, null, null)),
                true,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T10_00_00Z)
        );

        when(tubeStatusService.getLineStatus(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get(TestConstants.API_PATH_LINE_STATUS_CENTRAL).header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION))
                .andExpect(status().isOk())
                .andExpect(header().string(AppConstants.Api.HEADER_DATA_SOURCE, AppConstants.Api.DATA_SOURCE_STALE));
    }

    @Test
    void getLineStatusWithInvalidDateReturnsBadRequest() throws Exception {
                mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .param("startDate", TestConstants.DATE_INVALID_2026_02_20)
                                .param("endDate", TestConstants.DATE_2026_02_21)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(AppConstants.ErrorCodes.BAD_REQUEST));
    }

    @Test
    void getLineStatusWithOnlyStartDateReturnsBadRequest() throws Exception {
                mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .param("startDate", TestConstants.DATE_2026_02_20)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(AppConstants.ErrorCodes.BAD_REQUEST));
    }

    @Test
    void getLineStatusWithEndDateBeforeStartDateReturnsBadRequest() throws Exception {
                mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .param("startDate", TestConstants.DATE_2026_02_21)
                                .param("endDate", TestConstants.DATE_2026_02_20)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(AppConstants.ErrorCodes.BAD_REQUEST));
    }

    @Test
    void getUnplannedDisruptionsReturnsStaleHeaderWhenResponseIsStale() throws Exception {
        UnplannedDisruptionsResponse response = new UnplannedDisruptionsResponse(
                List.of(
                        new UnplannedLineDisruption(
                                TestConstants.LINE_ID_NORTHERN,
                                TestConstants.LINE_NAME_NORTHERN,
                                List.of(
                                        new StatusEntry(
                                                "Severe Delays",
                                                "Signal failure",
                                                false,
                                                OffsetDateTime.parse(TestConstants.TIMESTAMP_2026_02_19T09_00_00Z),
                                                OffsetDateTime.parse(TestConstants.TIMESTAMP_2026_02_19T11_00_00Z)
                                        )
                                )
                        )
                ),
                true,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T09_30_00Z)
        );

        when(tubeStatusService.getUnplannedDisruptions(any())).thenReturn(response);

        mockMvc.perform(get(TestConstants.API_PATH_UNPLANNED_DISRUPTIONS).header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION))
                .andExpect(status().isOk())
                .andExpect(header().string(AppConstants.Api.HEADER_DATA_SOURCE, AppConstants.Api.DATA_SOURCE_STALE))
                .andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    void getUnplannedDisruptionsReturnsLiveHeaderWhenResponseIsLive() throws Exception {
        UnplannedDisruptionsResponse response = new UnplannedDisruptionsResponse(List.of(), false, Instant.now());
        when(tubeStatusService.getUnplannedDisruptions(any())).thenReturn(response);

        mockMvc.perform(get(TestConstants.API_PATH_UNPLANNED_DISRUPTIONS).header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION))
                .andExpect(status().isOk())
                .andExpect(header().string(AppConstants.Api.HEADER_DATA_SOURCE, AppConstants.Api.DATA_SOURCE_LIVE))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void getLineStatusUsesRemoteAddrEvenWhenForwardedForHeaderExists() throws Exception {
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T10_00_00Z)
        );

        when(tubeStatusService.getLineStatus(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                                .header(AppConstants.Api.HEADER_FORWARDED_FOR, TestConstants.FORWARDED_FOR_203_0_113_10)
                                .with(request -> {
                                    request.setRemoteAddr(TestConstants.REMOTE_ADDR_10_0_0_5);
                                    return request;
                                })
                )
                .andExpect(status().isOk());

        verify(tubeStatusService).getLineStatus(
                eq(TestConstants.LINE_ID_CENTRAL),
                isNull(),
                isNull(),
                eq(TestConstants.REMOTE_ADDR_10_0_0_5)
        );
    }

    @Test
    void getLineStatusUsesUnknownClientKeyWhenRemoteAddrMissing() throws Exception {
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T10_00_00Z)
        );
        when(tubeStatusService.getLineStatus(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                                .with(request -> {
                                    request.setRemoteAddr("");
                                    return request;
                                })
                )
                .andExpect(status().isOk());

        verify(tubeStatusService).getLineStatus(
                eq(TestConstants.LINE_ID_CENTRAL),
                isNull(),
                isNull(),
                eq(AppConstants.Api.CLIENT_KEY_UNKNOWN)
        );
    }

    @Test
    void getLineStatusTrimsRemoteAddressBeforePassingClientKey() throws Exception {
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.parse(TestConstants.TIMESTAMP_2026_02_19T10_00_00Z)
        );
        when(tubeStatusService.getLineStatus(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(
                        get(TestConstants.API_PATH_LINE_STATUS_CENTRAL)
                                .header(AppConstants.Api.DEFAULT_API_VERSION_HEADER, TestConstants.API_VERSION)
                                .with(request -> {
                                    request.setRemoteAddr(TestConstants.REMOTE_ADDR_10_0_0_9_PADDED);
                                    return request;
                                })
                )
                .andExpect(status().isOk());

        verify(tubeStatusService).getLineStatus(
                eq(TestConstants.LINE_ID_CENTRAL),
                isNull(),
                isNull(),
                eq(TestConstants.REMOTE_ADDR_10_0_0_9_TRIMMED)
        );
    }
}
