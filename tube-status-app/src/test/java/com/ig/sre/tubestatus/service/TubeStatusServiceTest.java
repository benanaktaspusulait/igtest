package com.ig.sre.tubestatus.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.StatusEntry;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.api.model.UnplannedLineDisruption;
import com.ig.sre.tubestatus.client.tfl.TflClient;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.SliProperties;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.exception.DependencySaturatedException;
import com.ig.sre.tubestatus.exception.TooManyRequestsException;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import com.ig.sre.tubestatus.exception.UpstreamUnavailableException;
import com.ig.sre.tubestatus.support.TestConstants;
import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TubeStatusServiceTest {

    @Mock
    private TflClient tflClient;

    @Mock
    private TubeStatusMapper mapper;

    private MeterRegistry meterRegistry;
    private Cache<String, LineStatusResponse> lineStatusCache;
    private Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache;
    private TubeStatusService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lineStatusCache = Caffeine.newBuilder().maximumSize(100).build();
        unplannedDisruptionCache = Caffeine.newBuilder().maximumSize(10).build();

        SliProperties sliProperties = new SliProperties();
        sliProperties.setFreshnessThreshold(Duration.ofMinutes(5));

        service = new TubeStatusService(
                tflClient,
                mapper,
                meterRegistry,
                lineStatusCache,
                unplannedDisruptionCache,
                sliProperties
        );
    }

    @Test
    void getLineStatusReturnsLiveResponseAndCachesIt() {
        TflLine line = new TflLine(TestConstants.LINE_ID_CENTRAL, TestConstants.LINE_NAME_CENTRAL, TestConstants.MODE_TUBE, List.of(), List.of());
        LineStatusResponse liveResponse = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now()
        );

        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of(line));
        when(mapper.toLineStatusResponse(eq(line), eq(false), any(Instant.class))).thenReturn(liveResponse);

        LineStatusResponse result = service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result).isEqualTo(liveResponse);
        assertThat(lineStatusCache.getIfPresent(TestConstants.LINE_STATUS_CACHE_KEY_CURRENT)).isEqualTo(liveResponse);
    }

    @Test
    void getLineStatusUsesRangeEndpointWhenDatesProvided() {
        LocalDate startDate = LocalDate.parse(TestConstants.DATE_2026_02_01);
        LocalDate endDate = LocalDate.parse(TestConstants.DATE_2026_02_02);
        TflLine line = new TflLine(TestConstants.LINE_ID_CENTRAL, TestConstants.LINE_NAME_CENTRAL, TestConstants.MODE_TUBE, List.of(), List.of());
        LineStatusResponse liveResponse = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now()
        );

        when(tflClient.getLineStatusInRange(TestConstants.LINE_ID_CENTRAL_UPPER, startDate, endDate, TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of(line));
        when(mapper.toLineStatusResponse(eq(line), eq(false), any(Instant.class))).thenReturn(liveResponse);

        LineStatusResponse result = service.getLineStatus(TestConstants.LINE_ID_CENTRAL_UPPER, startDate, endDate, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result).isEqualTo(liveResponse);
        assertThat(lineStatusCache.getIfPresent(TestConstants.LINE_STATUS_CACHE_KEY_RANGE)).isEqualTo(liveResponse);
        verify(tflClient).getLineStatusInRange(TestConstants.LINE_ID_CENTRAL_UPPER, startDate, endDate, TestConstants.CLIENT_KEY_IP_1);
    }

    @Test
    void getLineStatusReturnsStaleFromCacheOnTransientError() {
        LineStatusResponse cached = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now().minus(Duration.ofMinutes(1))
        );
        lineStatusCache.put(TestConstants.LINE_STATUS_CACHE_KEY_CURRENT, cached);

        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.timeout("timeout", new RuntimeException("timeout")));

        LineStatusResponse result = service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result.stale()).isTrue();
        assertThat(result.lineId()).isEqualTo(TestConstants.LINE_ID_CENTRAL);
    }

    @Test
    void getLineStatusMapsClient404ToLineNotFound() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_UNKNOWN, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.clientError(404, "not found"));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_UNKNOWN, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(TubeLineNotFoundException.class);
    }

    @Test
    void getLineStatusThrowsLineNotFoundWhenNoLineIsReturned() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_UNKNOWN, TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_UNKNOWN, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(TubeLineNotFoundException.class);
    }

    @Test
    void getLineStatusMapsRateLimitException() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new RateLimitExceededException("limited", 7));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Rate limit");
    }

    @Test
    void getLineStatusReturnsStaleFromCacheWhenDependencyIsSaturated() {
        LineStatusResponse cached = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now().minus(Duration.ofMinutes(1))
        );
        lineStatusCache.put(TestConstants.LINE_STATUS_CACHE_KEY_CURRENT, cached);

        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new DependencySaturatedException(AppConstants.Messages.TFL_CLIENT_SATURATED));

        LineStatusResponse result = service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result.stale()).isTrue();
        assertThat(result.lineId()).isEqualTo(TestConstants.LINE_ID_CENTRAL);
    }

    @Test
    void getLineStatusThrowsUnavailableWhenDependencyIsSaturatedAndCacheMissing() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new DependencySaturatedException(AppConstants.Messages.TFL_CLIENT_SATURATED));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining(AppConstants.Messages.LINE_STATUS_CACHE_MISS_SATURATED);
    }

    @Test
    void getUnplannedDisruptionsReturnsStaleFromCacheOnTransientError() {
        UnplannedDisruptionsResponse cached = new UnplannedDisruptionsResponse(List.of(), false, Instant.now());
        unplannedDisruptionCache.put(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY, cached);

        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.timeout("timeout", new RuntimeException("timeout")));

        UnplannedDisruptionsResponse result = service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1);

        assertThat(result.stale()).isTrue();
        assertThat(result.lines()).isEmpty();
    }

    @Test
    void getLineStatusMapsClientErrorToBadRequest() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.clientError(400, "bad request"));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(AppConstants.Messages.INVALID_LINE_STATUS_REQUEST_PREFIX);
    }

    @Test
    void getLineStatusThrowsUnavailableWhenNoCacheAndTransientFailure() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.timeout("timeout", new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining(AppConstants.Messages.LINE_STATUS_CACHE_MISS_UPSTREAM);
    }

    @Test
    void getLineStatusIncludesRetryAfterWhenCircuitIsOpenAndCacheMissing() {
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new CircuitBreakerOpenException(AppConstants.Tfl.DEPENDENCY_KEY, 12));

        assertThatThrownBy(() -> service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("Retry after 12 seconds");
    }

    @Test
    void getLineStatusReturnsStaleFromCacheWhenCircuitIsOpen() {
        LineStatusResponse cached = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Minor Delays", "signal issue", false, null, null)),
                false,
                Instant.now().minus(Duration.ofMinutes(2))
        );
        lineStatusCache.put(TestConstants.LINE_STATUS_CACHE_KEY_CURRENT, cached);
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new CircuitBreakerOpenException(AppConstants.Tfl.DEPENDENCY_KEY, 30));

        LineStatusResponse result = service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result.stale()).isTrue();
        assertThat(result.fetchedAt()).isEqualTo(cached.fetchedAt());
    }

    @Test
    void getLineStatusRecordsStaleFreshnessForOldCachedData() {
        LineStatusResponse cached = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now().minus(Duration.ofMinutes(30))
        );
        lineStatusCache.put(TestConstants.LINE_STATUS_CACHE_KEY_CURRENT, cached);
        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.network("network", new RuntimeException("network")));

        service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(counterCount(
                AppConstants.Metrics.TUBE_STATUS_FRESHNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                AppConstants.Metrics.ENDPOINT_LINE_STATUS,
                AppConstants.Metrics.TAG_RESULT,
                AppConstants.Metrics.RESULT_STALE
        )).isEqualTo(1.0d);
    }

    @Test
    void getLineStatusTreatsFutureFetchedAtAsFresh() {
        TflLine line = new TflLine(TestConstants.LINE_ID_CENTRAL, TestConstants.LINE_NAME_CENTRAL, TestConstants.MODE_TUBE, List.of(), List.of());
        LineStatusResponse response = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                TestConstants.LINE_NAME_CENTRAL,
                List.of(new StatusEntry("Good Service", null, false, null, null)),
                false,
                Instant.now().plus(Duration.ofMinutes(1))
        );

        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of(line));
        when(mapper.toLineStatusResponse(eq(line), eq(false), any(Instant.class))).thenReturn(response);

        service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(counterCount(
                AppConstants.Metrics.TUBE_STATUS_FRESHNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                AppConstants.Metrics.ENDPOINT_LINE_STATUS,
                AppConstants.Metrics.TAG_RESULT,
                AppConstants.Metrics.RESULT_FRESH
        )).isEqualTo(1.0d);
    }

    @Test
    void getLineStatusRecordsInvalidCorrectnessWhenResponseHasMissingFields() {
        TflLine line = new TflLine(TestConstants.LINE_ID_CENTRAL, TestConstants.LINE_NAME_CENTRAL, TestConstants.MODE_TUBE, List.of(), List.of());
        LineStatusResponse invalid = new LineStatusResponse(
                TestConstants.LINE_ID_CENTRAL,
                "",
                List.of(),
                false,
                null
        );

        when(tflClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of(line));
        when(mapper.toLineStatusResponse(eq(line), eq(false), any(Instant.class))).thenReturn(invalid);

        service.getLineStatus(TestConstants.LINE_ID_CENTRAL, null, null, TestConstants.CLIENT_KEY_IP_1);

        assertThat(counterCount(
                AppConstants.Metrics.TUBE_STATUS_CORRECTNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                AppConstants.Metrics.ENDPOINT_LINE_STATUS,
                AppConstants.Metrics.TAG_RESULT,
                AppConstants.Metrics.RESULT_INVALID
        )).isEqualTo(1.0d);
    }

    @Test
    void getUnplannedDisruptionsMapsClientErrorToBadRequest() {
        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.clientError(400, "bad request"));

        assertThatThrownBy(() -> service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(AppConstants.Messages.INVALID_DISRUPTION_REQUEST_PREFIX);
    }

    @Test
    void getUnplannedDisruptionsThrowsUnavailableWhenNoCacheAndTransientFailure() {
        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(UpstreamException.network("network", new RuntimeException("network")));

        assertThatThrownBy(() -> service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining(AppConstants.Messages.DISRUPTION_CACHE_MISS_UPSTREAM);
    }

    @Test
    void getUnplannedDisruptionsMapsRateLimitException() {
        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new RateLimitExceededException("limited", 9));

        assertThatThrownBy(() -> service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Rate limit");
    }

    @Test
    void getUnplannedDisruptionsReturnsStaleFromCacheWhenDependencyIsSaturated() {
        UnplannedDisruptionsResponse cached = new UnplannedDisruptionsResponse(List.of(), false, Instant.now());
        unplannedDisruptionCache.put(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY, cached);

        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new DependencySaturatedException(AppConstants.Messages.TFL_CLIENT_SATURATED));

        UnplannedDisruptionsResponse result = service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1);

        assertThat(result.stale()).isTrue();
    }

    @Test
    void getUnplannedDisruptionsThrowsUnavailableWhenDependencyIsSaturatedAndCacheMissing() {
        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new DependencySaturatedException(AppConstants.Messages.TFL_CLIENT_SATURATED));

        assertThatThrownBy(() -> service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining(AppConstants.Messages.DISRUPTION_CACHE_MISS_SATURATED);
    }

    @Test
    void getUnplannedDisruptionsReturnsStaleFromCacheWhenCircuitIsOpen() {
        UnplannedDisruptionsResponse cached = new UnplannedDisruptionsResponse(List.of(), false, Instant.now());
        unplannedDisruptionCache.put(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY, cached);

        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1))
                .thenThrow(new CircuitBreakerOpenException(AppConstants.Tfl.DEPENDENCY_KEY, 45));

        UnplannedDisruptionsResponse response = service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1);

        assertThat(response.stale()).isTrue();
        assertThat(response.fetchedAt()).isEqualTo(cached.fetchedAt());
    }

    @Test
    void getUnplannedDisruptionsRecordsInvalidCorrectnessWhenLineHasNoStatuses() {
        TflLine line = new TflLine(TestConstants.LINE_ID_NORTHERN, TestConstants.LINE_NAME_NORTHERN, TestConstants.MODE_TUBE, List.of(), List.of());
        UnplannedDisruptionsResponse invalid = new UnplannedDisruptionsResponse(
                List.of(new UnplannedLineDisruption(TestConstants.LINE_ID_NORTHERN, TestConstants.LINE_NAME_NORTHERN, List.of())),
                false,
                Instant.now()
        );

        when(tflClient.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_1)).thenReturn(List.of(line));
        when(mapper.toUnplannedDisruptionsResponse(any(), eq(false), any(Instant.class))).thenReturn(invalid);

        service.getUnplannedDisruptions(TestConstants.CLIENT_KEY_IP_1);

        assertThat(counterCount(
                AppConstants.Metrics.TUBE_STATUS_CORRECTNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                AppConstants.Metrics.TAG_RESULT,
                AppConstants.Metrics.RESULT_INVALID
        )).isEqualTo(1.0d);
    }

    private double counterCount(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
