package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.ApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ClientKeyResolver {

    private final boolean trustForwardHeaders;
    private final Set<String> trustedProxyIps;

    public ClientKeyResolver(ApiProperties apiProperties) {
        this.trustForwardHeaders = apiProperties.isTrustForwardHeaders();
        this.trustedProxyIps = Set.copyOf(apiProperties.getTrustedProxyIps());
    }

    public String resolve(HttpServletRequest request) {
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
