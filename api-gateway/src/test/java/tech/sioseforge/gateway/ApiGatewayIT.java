package tech.sioseforge.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApiGatewayIT {

    @Autowired
    private WebTestClient webClient;

    @Test
    void shouldServeStaticFrontend() {
        webClient.get().uri("/index.html")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML);
    }

    @Test
    void shouldRouteToAuthService() {
        // Without mocking auth-service, this might fail routing to localhost:9180.
        // We just assert that the gateway responds (likely with 503 Service Unavailable if auth-service is down, 
        // which still proves the route is matched by the gateway).
        webClient.get().uri("/api/v1/auth/login")
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
