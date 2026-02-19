package com.ig.sre.tubestatus.web;

import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.StatusEntry;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.service.TubeStatusService;
import com.ig.sre.tubestatus.support.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.api.trust-forward-headers=true",
                "app.api.trusted-proxy-ips=10.0.0.5"
        }
)
@ActiveProfiles("test")
class TubeStatusControllerForwardedHeaderTrustTest {

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
    void usesXForwardedForWhenProxyIsTrusted() throws Exception {
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
                                .header(AppConstants.Api.HEADER_FORWARDED_FOR, "203.0.113.10, 70.41.3.18")
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
                eq(TestConstants.FORWARDED_FOR_203_0_113_10)
        );
    }

    @Test
    void usesForwardedHeaderWhenProxyIsTrusted() throws Exception {
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
                                .header(AppConstants.Api.HEADER_FORWARDED, "for=198.51.100.7;proto=https")
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
                eq("198.51.100.7")
        );
    }

    @Test
    void keepsRemoteAddrWhenProxyIsNotTrusted() throws Exception {
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
                                    request.setRemoteAddr(TestConstants.REMOTE_ADDR_10_0_0_9_TRIMMED);
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
