package com.ig.sre.tubestatus.client.tfl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.SyntheticFaultProperties;
import com.ig.sre.tubestatus.config.TflProperties;
import com.ig.sre.tubestatus.exception.DependencySaturatedException;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.resilience.core.error.UpstreamException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class TflClient {

    private static final ParameterizedTypeReference<List<TflLine>> LINE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final TflProperties properties;
    private final ResilientExecutor resilientExecutor;
    private final Semaphore inFlightSemaphore;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-managed dependencies are injected and retained for client behavior."
    )
    public TflClient(RestClient tflRestClient, TflProperties properties, ResilientExecutor resilientExecutor) {
        this.restClient = tflRestClient;
        this.properties = properties;
        this.resilientExecutor = resilientExecutor;
        this.inFlightSemaphore = new Semaphore(properties.maxInFlight(), true);
    }

    public List<TflLine> getLineStatus(String lineId, String clientKey) {
        return executeWithResilience(
                AppConstants.Tfl.OPERATION_LINE_STATUS_CURRENT,
                clientKey,
                () -> doGet(uriBuilder -> buildLineStatusUri(uriBuilder, lineId))
        );
    }

    public List<TflLine> getLineStatusInRange(
            String lineId,
            LocalDate startDate,
            LocalDate endDate,
            String clientKey
    ) {
        return executeWithResilience(
                AppConstants.Tfl.OPERATION_LINE_STATUS_RANGE,
                clientKey,
                () -> doGet(uriBuilder -> buildLineStatusRangeUri(uriBuilder, lineId, startDate, endDate))
        );
    }

    public List<TflLine> getAllTubeStatuses(String clientKey) {
        return executeWithResilience(
                AppConstants.Tfl.OPERATION_ALL_TUBE_STATUSES,
                clientKey,
                () -> doGet(this::buildAllTubeStatusesUri)
        );
    }

    private <T> T executeWithResilience(String operation, String clientKey, Supplier<T> action) {
        if (!inFlightSemaphore.tryAcquire()) {
            throw new DependencySaturatedException(AppConstants.Messages.TFL_CLIENT_SATURATED);
        }
        RequestContext context = new RequestContext(AppConstants.Tfl.DEPENDENCY_KEY, operation, clientKey);
        try {
            return resilientExecutor.execute(() -> {
                maybeInjectSyntheticTransientFault();
                return action.get();
            }, context);
        } finally {
            inFlightSemaphore.release();
        }
    }

    private void maybeInjectSyntheticTransientFault() {
        SyntheticFaultProperties faultProperties = properties.syntheticFault();
        if (!faultProperties.enabled()) {
            return;
        }

        double timeoutRate = sanitizeRate(faultProperties.timeoutRate());
        double serverErrorRate = sanitizeRate(faultProperties.serverErrorRate());
        double combinedRate = Math.min(1.0d, timeoutRate + serverErrorRate);
        double sample = ThreadLocalRandom.current().nextDouble();

        if (sample < timeoutRate) {
            throw UpstreamException.timeout(AppConstants.Messages.TFL_SYNTHETIC_TIMEOUT, null);
        }
        if (sample < combinedRate) {
            throw UpstreamException.serverError(503, AppConstants.Messages.TFL_SYNTHETIC_SERVER_ERROR);
        }
    }

    private double sanitizeRate(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private List<TflLine> doGet(Function<UriBuilder, URI> uriFunction) {
        try {
            List<TflLine> body = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw UpstreamException.clientError(
                                response.getStatusCode().value(),
                                AppConstants.Messages.TFL_CLIENT_ERROR_PREFIX + response.getStatusCode().value()
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw UpstreamException.serverError(
                                response.getStatusCode().value(),
                                AppConstants.Messages.TFL_SERVER_ERROR_PREFIX + response.getStatusCode().value()
                        );
                    })
                    .body(LINE_LIST_TYPE);

            return body == null ? List.of() : body;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw UpstreamException.timeout(AppConstants.Messages.TFL_TIMEOUT_OR_NETWORK, ex);
            }
            throw UpstreamException.network(AppConstants.Messages.TFL_TIMEOUT_OR_NETWORK, ex);
        } catch (RestClientException ex) {
            throw UpstreamException.network(AppConstants.Messages.TFL_UNEXPECTED_ERROR, ex);
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private URI buildLineStatusUri(UriBuilder uriBuilder, String lineId) {
        UriBuilder builder = uriBuilder.path(properties.lineStatusPath());
        addAuthParams(builder);
        return builder.build(lineId);
    }

    private URI buildLineStatusRangeUri(UriBuilder uriBuilder, String lineId, LocalDate startDate, LocalDate endDate) {
        UriBuilder builder = uriBuilder.path(properties.lineStatusRangePath());
        addAuthParams(builder);
        return builder.build(lineId, startDate, endDate);
    }

    private URI buildAllTubeStatusesUri(UriBuilder uriBuilder) {
        UriBuilder builder = uriBuilder.path(properties.allTubeStatusesPath());
        addAuthParams(builder);
        return builder.build();
    }

    private void addAuthParams(UriBuilder builder) {
        if (StringUtils.hasText(properties.appId())) {
            builder.queryParam(AppConstants.Tfl.APP_ID_QUERY_PARAM, properties.appId());
        }
        if (StringUtils.hasText(properties.appKey())) {
            builder.queryParam(AppConstants.Tfl.APP_KEY_QUERY_PARAM, properties.appKey());
        }
    }
}
