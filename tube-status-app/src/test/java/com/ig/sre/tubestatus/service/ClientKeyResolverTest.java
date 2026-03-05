package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.ApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientKeyResolverTest {

    @Test
    void resolveReturnsUnknownWhenRemoteAddrMissing() {
        ClientKeyResolver resolver = new ClientKeyResolver(apiProperties(false, List.of()));
        MockHttpServletRequest request = requestWithRemoteAddr("");

        String resolved = resolver.resolve(request);

        assertEquals(AppConstants.Api.CLIENT_KEY_UNKNOWN, resolved);
    }

    @Test
    void resolveUsesRemoteAddressWhenForwardHeadersAreDisabled() {
        ClientKeyResolver resolver = new ClientKeyResolver(apiProperties(false, List.of("10.0.0.5")));
        MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.5");
        request.addHeader(AppConstants.Api.HEADER_FORWARDED_FOR, "203.0.113.10");

        String resolved = resolver.resolve(request);

        assertEquals("10.0.0.5", resolved);
    }

    @Test
    void resolveUsesXForwardedForWhenProxyIsTrusted() {
        ClientKeyResolver resolver = new ClientKeyResolver(apiProperties(true, List.of("10.0.0.5")));
        MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.5");
        request.addHeader(AppConstants.Api.HEADER_FORWARDED_FOR, "203.0.113.10, 70.41.3.18");

        String resolved = resolver.resolve(request);

        assertEquals("203.0.113.10", resolved);
    }

    @Test
    void resolveUsesStandardForwardedHeaderWhenPresent() {
        ClientKeyResolver resolver = new ClientKeyResolver(apiProperties(true, List.of("10.0.0.5")));
        MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.5");
        request.addHeader(AppConstants.Api.HEADER_FORWARDED, "for=\"[2001:db8:cafe::17]:4711\"");

        String resolved = resolver.resolve(request);

        assertEquals("2001:db8:cafe::17", resolved);
    }

    @Test
    void resolveKeepsRemoteAddrWhenProxyIsNotTrusted() {
        ClientKeyResolver resolver = new ClientKeyResolver(apiProperties(true, List.of("10.0.0.5")));
        MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.9");
        request.addHeader(AppConstants.Api.HEADER_FORWARDED_FOR, "203.0.113.10");

        String resolved = resolver.resolve(request);

        assertEquals("10.0.0.9", resolved);
    }

    private ApiProperties apiProperties(boolean trustForwardHeaders, List<String> trustedProxyIps) {
        return new ApiProperties(
                AppConstants.Api.DEFAULT_BASE_PATH,
                AppConstants.Api.DEFAULT_LINE_STATUS_PATH,
                AppConstants.Api.DEFAULT_UNPLANNED_DISRUPTIONS_PATH,
                AppConstants.Api.DEFAULT_API_VERSION,
                AppConstants.Api.DEFAULT_API_VERSION_RANGE,
                AppConstants.Api.DEFAULT_API_VERSION_HEADER,
                trustForwardHeaders,
                trustedProxyIps
        );
    }

    private MockHttpServletRequest requestWithRemoteAddr(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
