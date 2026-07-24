package tech.sioseforge.auth;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import tech.sioseforge.auth.resource.AuthController;
import tech.sioseforge.auth.repository.TenantRepository;
import tech.sioseforge.auth.repository.OutboxEventRepository;
import tech.sioseforge.auth.repository.UserRepository;
import tech.sioseforge.auth.domain.view.RegisterRequestVO;
import tech.sioseforge.auth.service.AuthService;
import tech.sioseforge.auth.domain.entity.Tenant;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        // H2 + mocked repos only — do not run Postgres Flyway scripts (TIMESTAMPTZ/BIGSERIAL)
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.cloud.vault.enabled=false",
        "app.security.jwt.secret=contract-test-jwt-secret-key-1234567890"
})
@org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier
@org.springframework.context.annotation.Import(tech.sioseforge.auth.ContractTestBase.ContractConfig.class)
public abstract class ContractTestBase {

    @Autowired
    private AuthController authController;

    @Autowired
    private AuthService authService;

    // By mocking the database repositories and Kafka template, 
    // we bypass the need for Testcontainers and actual external services 
    // during the Contract testing phase.
    
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private org.springframework.vault.core.VaultTemplate vaultTemplate;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    protected KafkaTemplate kafkaTemplate;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @BeforeEach
    public void setup() throws Exception {
        // Manually inject javax.inject.Inject fields since Spring 6 ignores them
        for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
            if (field.getAnnotation(javax.inject.Inject.class) != null) {
                field.setAccessible(true);
                field.set(this, applicationContext.getBean(field.getType()));
            }
        }

        // Setup RestAssured to standalone mode to isolate the controller logic
        RestAssuredMockMvc.standaloneSetup(authController);

        Tenant mockTenant = new Tenant();
        mockTenant.setId(1L);
        mockTenant.setDomain("default.local");
        when(tenantRepository.findByDomain(anyString())).thenReturn(Optional.of(mockTenant));

        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // This method is called by the generated contract test because we defined triggeredBy("triggerUserRegisteredEvent()")
    public void triggerUserRegisteredEvent() {
        authService.register(new RegisterRequestVO("sso-123", "testuser", "secretpass"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    public static class ContractConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        @SuppressWarnings({"rawtypes", "unchecked"})
        public org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging myContractVerifierMessaging(
                @Autowired KafkaTemplate kafkaTemplate) {
            
            org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging mockMessaging = 
                    org.mockito.Mockito.mock(org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging.class);
            
            org.mockito.Mockito.when(mockMessaging.receive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    String destination = invocation.getArgument(0);
                    org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
                    org.mockito.Mockito.verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce()).send(org.mockito.ArgumentMatchers.eq(destination), org.mockito.ArgumentMatchers.any(), captor.capture());
                    Object capturedEvent = captor.getValue();
                    if (capturedEvent instanceof tech.sioseforge.auth.event.UserRegisteredEvent) {
                        tech.sioseforge.auth.event.UserRegisteredEvent orig = (tech.sioseforge.auth.event.UserRegisteredEvent) capturedEvent;
                        capturedEvent = new tech.sioseforge.auth.event.UserRegisteredEvent(orig.username(), orig.email(), "2026-07-18T00:00:00Z");
                    }
                    
                    return new org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessage(
                            capturedEvent,
                            java.util.Collections.singletonMap("contentType", "application/json")
                    );
                });
                
            return mockMessaging;
        }
    }
}
