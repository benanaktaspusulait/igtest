package com.ig.sre.tubestatus.client.tfl;

import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.error.ErrorCategory;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.SyntheticFaultProperties;
import com.ig.sre.tubestatus.config.TflProperties;
import com.ig.sre.tubestatus.exception.DependencySaturatedException;
import com.ig.sre.tubestatus.support.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class TflClientTest {

    private static final String LINE_BODY = """
            [
              {
                "id":"%s",
                "name":"%s",
                "modeName":"%s",
                "disruptions":[],
                "lineStatuses":[]
              }
            ]
            """.formatted(
            TestConstants.LINE_ID_CENTRAL,
            TestConstants.LINE_NAME_CENTRAL,
            TestConstants.MODE_TUBE
    );

    @Mock
    private ResilientExecutor resilientExecutor;

    private MockRestServiceServer mockServer;
    private TflProperties properties;
    private TflClient client;

    @BeforeEach
    void setUp() {
        properties = defaultProperties();

        lenient().when(resilientExecutor.execute(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        initializeClient(properties);
    }

    @Test
    void getLineStatusUsesCurrentEndpointAndContext() {
        mockServer.expect(requestTo(
                        TestConstants.TEST_TFL_BASE_URL
                                + "/Line/" + TestConstants.LINE_ID_CENTRAL
                                + "/Status?app_id=" + TestConstants.TEST_TFL_APP_ID
                                + "&app_key=" + TestConstants.TEST_TFL_APP_KEY
                ))
                .andRespond(withSuccess(LINE_BODY, MediaType.APPLICATION_JSON));

        List<TflLine> result = client.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(TestConstants.LINE_ID_CENTRAL);

        ArgumentCaptor<RequestContext> contextCaptor = ArgumentCaptor.forClass(RequestContext.class);
        verify(resilientExecutor).execute(any(), contextCaptor.capture());
        RequestContext context = contextCaptor.getValue();
        assertThat(context.dependencyKey()).isEqualTo(AppConstants.Tfl.DEPENDENCY_KEY);
        assertThat(context.operationName()).isEqualTo(AppConstants.Tfl.OPERATION_LINE_STATUS_CURRENT);
        assertThat(context.clientKey()).isEqualTo(TestConstants.CLIENT_KEY_IP_1);

        mockServer.verify();
    }

    @Test
    void getLineStatusInRangeUsesRangeEndpoint() {
        LocalDate startDate = LocalDate.parse(TestConstants.DATE_2026_02_01);
        LocalDate endDate = LocalDate.parse(TestConstants.DATE_2026_02_02);
        mockServer.expect(requestTo(
                        TestConstants.TEST_TFL_BASE_URL
                                + "/Line/" + TestConstants.LINE_ID_CENTRAL
                                + "/Status/" + TestConstants.DATE_2026_02_01
                                + "/to/" + TestConstants.DATE_2026_02_02
                                + "?app_id=" + TestConstants.TEST_TFL_APP_ID
                                + "&app_key=" + TestConstants.TEST_TFL_APP_KEY
                ))
                .andRespond(withSuccess(LINE_BODY, MediaType.APPLICATION_JSON));

        List<TflLine> result = client.getLineStatusInRange(
                TestConstants.LINE_ID_CENTRAL,
                startDate,
                endDate,
                TestConstants.CLIENT_KEY_IP_2
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo(TestConstants.LINE_NAME_CENTRAL);

        mockServer.verify();
    }

    @Test
    void getAllTubeStatusesReturnsEmptyListWhenBodyIsMissing() {
        properties = createProperties(" ", null, SyntheticFaultProperties.DISABLED, 200);
        initializeClient(properties);
        mockServer.expect(requestTo(TestConstants.TEST_TFL_BASE_URL + TestConstants.TEST_TFL_ALL_TUBE_STATUSES_PATH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        List<TflLine> result = client.getAllTubeStatuses(TestConstants.CLIENT_KEY_IP_3);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void mapsClientErrorResponseToUpstreamClientError() {
        mockServer.expect(requestTo(
                        TestConstants.TEST_TFL_BASE_URL
                                + "/Line/" + TestConstants.LINE_ID_CENTRAL
                                + "/Status?app_id=" + TestConstants.TEST_TFL_APP_ID
                                + "&app_key=" + TestConstants.TEST_TFL_APP_KEY
                ))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> {
                    UpstreamException upstreamException = (UpstreamException) ex;
                    assertThat(upstreamException.getCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                    assertThat(upstreamException.getStatusCode()).isEqualTo(404);
                });
    }

    @Test
    void mapsServerErrorResponseToUpstreamServerError() {
        mockServer.expect(requestTo(
                        TestConstants.TEST_TFL_BASE_URL
                                + "/Line/" + TestConstants.LINE_ID_CENTRAL
                                + "/Status?app_id=" + TestConstants.TEST_TFL_APP_ID
                                + "&app_key=" + TestConstants.TEST_TFL_APP_KEY
                ))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> assertThat(((UpstreamException) ex).getCategory()).isEqualTo(ErrorCategory.SERVER_ERROR));
    }

    @Test
    void mapsTimeoutResourceAccessExceptionToTimeoutCategory() {
        TflClient timeoutClient = createClientThrowingOnBody(
                new ResourceAccessException("timeout", new SocketTimeoutException("socket timeout"))
        );

        assertThatThrownBy(() -> timeoutClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> assertThat(((UpstreamException) ex).getCategory()).isEqualTo(ErrorCategory.TIMEOUT));
    }

    @Test
    void mapsNonTimeoutResourceAccessExceptionToNetworkCategory() {
        TflClient networkClient = createClientThrowingOnBody(
                new ResourceAccessException("network", new ConnectException("connection refused"))
        );

        assertThatThrownBy(() -> networkClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> assertThat(((UpstreamException) ex).getCategory()).isEqualTo(ErrorCategory.NETWORK));
    }

    @Test
    void mapsRestClientExceptionToNetworkCategory() {
        TflClient networkClient = createClientThrowingOnBody(new RestClientException("unexpected"));

        assertThatThrownBy(() -> networkClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> assertThat(((UpstreamException) ex).getCategory()).isEqualTo(ErrorCategory.NETWORK));
    }

    @Test
    void syntheticTimeoutFaultCanBeForcedForObservability() {
        properties = createProperties(
                TestConstants.TEST_TFL_APP_ID,
                TestConstants.TEST_TFL_APP_KEY,
                new SyntheticFaultProperties(true, 0.0d, 1.0d),
                200
        );
        client = new TflClient(RestClient.builder().baseUrl(TestConstants.TEST_TFL_BASE_URL).build(), properties, resilientExecutor);

        assertThatThrownBy(() -> client.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> assertThat(((UpstreamException) ex).getCategory()).isEqualTo(ErrorCategory.TIMEOUT));
    }

    @Test
    void syntheticServerErrorFaultCanBeForcedForObservability() {
        properties = createProperties(
                TestConstants.TEST_TFL_APP_ID,
                TestConstants.TEST_TFL_APP_KEY,
                new SyntheticFaultProperties(true, 1.0d, 0.0d),
                200
        );
        client = new TflClient(RestClient.builder().baseUrl(TestConstants.TEST_TFL_BASE_URL).build(), properties, resilientExecutor);

        assertThatThrownBy(() -> client.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1))
                .isInstanceOf(UpstreamException.class)
                .satisfies(ex -> {
                    UpstreamException upstreamException = (UpstreamException) ex;
                    assertThat(upstreamException.getCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
                    assertThat(upstreamException.getStatusCode()).isEqualTo(503);
                });
    }

    @Test
    void rejectsWhenInFlightLimitIsReached() throws Exception {
        properties = createProperties(
                TestConstants.TEST_TFL_APP_ID,
                TestConstants.TEST_TFL_APP_KEY,
                SyntheticFaultProperties.DISABLED,
                1
        );
        ResilientExecutor blockingExecutor = mock(ResilientExecutor.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> firstCallError = new AtomicReference<>();

        when(blockingExecutor.execute(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for release latch");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            @SuppressWarnings("unchecked")
            Supplier<Object> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        RestClient.Builder limitedBuilder = RestClient.builder().baseUrl(TestConstants.TEST_TFL_BASE_URL);
        MockRestServiceServer limitedServer = MockRestServiceServer.bindTo(limitedBuilder).build();
        TflClient limitedClient = new TflClient(limitedBuilder.build(), properties, blockingExecutor);

        limitedServer.expect(requestTo(
                        TestConstants.TEST_TFL_BASE_URL
                                + "/Line/" + TestConstants.LINE_ID_CENTRAL
                                + "/Status?app_id=" + TestConstants.TEST_TFL_APP_ID
                                + "&app_key=" + TestConstants.TEST_TFL_APP_KEY
                ))
                .andRespond(withSuccess(LINE_BODY, MediaType.APPLICATION_JSON));

        TflClient finalLimitedClient = limitedClient;
        Thread firstCall = new Thread(() -> {
            try {
                finalLimitedClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_1);
            } catch (Throwable throwable) {
                firstCallError.set(throwable);
            }
        });
        firstCall.start();

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> finalLimitedClient.getLineStatus(TestConstants.LINE_ID_CENTRAL, TestConstants.CLIENT_KEY_IP_2))
                .isInstanceOf(DependencySaturatedException.class)
                .hasMessageContaining("saturation");

        release.countDown();
        firstCall.join();

        assertThat(firstCallError.get()).isNull();
        limitedServer.verify();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TflClient createClientThrowingOnBody(RuntimeException exception) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        ResilientExecutor passThroughExecutor = mock(ResilientExecutor.class);

        when(passThroughExecutor.execute(any(), any())).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(exception);

        return new TflClient(restClient, properties, passThroughExecutor);
    }

    private void initializeClient(TflProperties configuredProperties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(TestConstants.TEST_TFL_BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TflClient(builder.build(), configuredProperties, resilientExecutor);
    }

    private TflProperties defaultProperties() {
        return createProperties(
                TestConstants.TEST_TFL_APP_ID,
                TestConstants.TEST_TFL_APP_KEY,
                SyntheticFaultProperties.DISABLED,
                200
        );
    }

    private TflProperties createProperties(
            String appId,
            String appKey,
            SyntheticFaultProperties syntheticFault,
            int maxInFlight
    ) {
        return new TflProperties(
                TestConstants.TEST_TFL_BASE_URL,
                TestConstants.TEST_TFL_LINE_STATUS_PATH,
                TestConstants.TEST_TFL_LINE_STATUS_RANGE_PATH,
                TestConstants.TEST_TFL_ALL_TUBE_STATUSES_PATH,
                1000,
                2000,
                maxInFlight,
                appId,
                appKey,
                syntheticFault
        );
    }
}
