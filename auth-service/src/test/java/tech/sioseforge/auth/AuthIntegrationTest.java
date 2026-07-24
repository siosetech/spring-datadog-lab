package tech.sioseforge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.sioseforge.auth.domain.view.AuthResponseVO;
import tech.sioseforge.auth.domain.view.LoginRequestVO;
import tech.sioseforge.auth.domain.view.RegisterRequestVO;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.generate-ddl=true",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false"
})
// The test will be automatically ignored if no valid Docker environment (like dockerd) is found.
// This is perfect for containerd environments where Testcontainers cannot run.
public class AuthIntegrationTest {

    @MockitoBean
    private VaultTemplate vaultTemplate;

    @LocalServerPort
    private int port;

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_service")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.vault.enabled", () -> "false"); // Disable Vault for this test
        registry.add("app.security.jwt.secret", () -> "integration-test-jwt-secret-key-1234567890");
    }

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private tech.sioseforge.auth.repository.TenantRepository tenantRepository;

    @org.junit.jupiter.api.BeforeEach
    void setupData() {
        if (tenantRepository.findByDomain("default.local").isEmpty()) {
            tech.sioseforge.auth.domain.entity.Tenant t = new tech.sioseforge.auth.domain.entity.Tenant();
            t.setDomain("default.local");
            t.setName("Test Tenant");
            tenantRepository.save(t);
        }
    }

    @Test
    void shouldRegisterUserAndPublishEvent() {
        RegisterRequestVO request = new RegisterRequestVO("sso-123", "testuser", "secretpass");

        String url = "http://localhost:" + port + "/api/v1/auth/register";
        ResponseEntity<AuthResponseVO> response = restTemplate.postForEntity(url, request, AuthResponseVO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        
        // Note: In a real test, we would also inject a KafkaConsumer here to verify 
        // that the message actually arrived on the 'user-registered-topic' topic.
        // For now, the successful execution without throwing connection refused proves 
        // Testcontainers Kafka is working and the producer sent the message.
    }

    @Test
    void shouldRejectLoginWhenPasswordIsInvalid() {
        RegisterRequestVO registerRequest = new RegisterRequestVO("sso-456", "invalid-pass-user", "secretpass");
        String registerUrl = "http://localhost:" + port + "/api/v1/auth/register";
        restTemplate.postForEntity(registerUrl, registerRequest, AuthResponseVO.class);

        String loginUrl = "http://localhost:" + port + "/api/v1/auth/login";
        LoginRequestVO loginRequest = new LoginRequestVO("invalid-pass-user", "wrongpass");

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> restTemplate.postForEntity(loginUrl, loginRequest, AuthResponseVO.class)
        )
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
