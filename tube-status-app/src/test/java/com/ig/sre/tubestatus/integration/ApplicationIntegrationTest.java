package com.ig.sre.tubestatus.integration;

import com.ig.sre.resilience.core.executor.ResilientExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class ApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ResilientExecutor resilientExecutor;

    @Test
    void healthEndpointIsUp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void openApiEndpointsAreExposed() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest docsRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .GET()
                .build();
        HttpResponse<String> docsResponse = client.send(docsRequest, HttpResponse.BodyHandlers.ofString());

        HttpRequest swaggerUiRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/swagger-ui/index.html"))
                .GET()
                .build();
        HttpResponse<String> swaggerUiResponse = client.send(swaggerUiRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(docsResponse.statusCode()).isEqualTo(200);
        assertThat(docsResponse.body()).contains("\"openapi\"");
        assertThat(swaggerUiResponse.statusCode()).isEqualTo(200);
        assertThat(swaggerUiResponse.body()).contains("Swagger UI");
    }
}
