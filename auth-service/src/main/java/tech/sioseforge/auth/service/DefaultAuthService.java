package tech.sioseforge.auth.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tech.sioseforge.auth.domain.view.AuthResponseVO;
import tech.sioseforge.auth.domain.view.LoginRequestVO;
import tech.sioseforge.auth.client.UserProfileClient;
import tech.sioseforge.auth.domain.entity.OutboxEvent;
import tech.sioseforge.auth.event.UserRegisteredEvent;
import tech.sioseforge.auth.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultAuthService implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthService.class);
    private static final long TOKEN_EXPIRATION_SECONDS = 3600L;
    private final Tracer tracer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final tech.sioseforge.auth.repository.UserRepository userRepository;
    private final tech.sioseforge.auth.repository.TenantRepository tenantRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserProfileClient userProfileClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Set<String> adminUsers;
    
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Timer tokenGenerationTimer;

    public DefaultAuthService(Tracer tracer, 
                              KafkaTemplate<String, Object> kafkaTemplate,
                              tech.sioseforge.auth.repository.UserRepository userRepository,
                              tech.sioseforge.auth.repository.TenantRepository tenantRepository,
                              OutboxEventRepository outboxEventRepository,
                              UserProfileClient userProfileClient,
                              PasswordEncoder passwordEncoder,
                              JwtEncoder jwtEncoder,
                              @Value("${app.security.admin-users:}") String adminUsersCsv,
                              MeterRegistry registry) {
        this.tracer = tracer;
        this.kafkaTemplate = kafkaTemplate;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.userProfileClient = userProfileClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.adminUsers = parseAdminUsers(adminUsersCsv);
        
        this.loginSuccessCounter = Counter.builder("auth.login.success")
            .description("Successful login attempts")
            .tag("service", "auth-service")
            .register(registry);

        this.loginFailureCounter = Counter.builder("auth.login.failure")
            .description("Failed login attempts")
            .tag("service", "auth-service")
            .register(registry);

        this.tokenGenerationTimer = Timer.builder("auth.token.generation")
            .description("JWT token generation duration")
            .tag("service", "auth-service")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    @Override
    public AuthResponseVO login(LoginRequestVO request) {
        Span loginSpan = tracer.spanBuilder("UserLoginProcess").startSpan();
        try (var scope = loginSpan.makeCurrent()) {
            log.info("Processing login for user: {}", request.username());

            tech.sioseforge.auth.domain.entity.User user = verifyCredentials(request);
            Set<String> roles = resolveRoles(user.getUsername());

            Span profileSpan = tracer.spanBuilder("fetch_user_profile").startSpan();
            String token;
            try {
                var stats = userProfileClient.getProfileStats();
                token = tokenGenerationTimer.record(() -> generateToken(user.getUsername(), roles));
                profileSpan.addEvent("Fetched stats from user-profile-service: " + stats);
            } catch (Exception e) {
                log.error("Failed to fetch profile stats", e);
                token = tokenGenerationTimer.record(() -> generateToken(user.getUsername(), roles));
                profileSpan.recordException(e);
            } finally {
                profileSpan.end();
            }

            Span kafkaSpan = tracer.spanBuilder("publish_kafka_event").startSpan();
            try (var kafkaScope = kafkaSpan.makeCurrent()) {
                UserRegisteredEvent event = new UserRegisteredEvent(
                    user.getUsername(),
                    user.getUsername() + "@example.com",
                    Instant.now().toString()
                );
                // join so Micrometer Kafka observation injects traceparent while span is active
                kafkaTemplate.send("auth-events", user.getUsername(), event).join();
                kafkaSpan.addEvent("Event published to auth-events topic");
            } finally {
                kafkaSpan.end();
            }

            loginSuccessCounter.increment();
            return new AuthResponseVO(token, TOKEN_EXPIRATION_SECONDS);
        } catch (Exception ex) {
            loginFailureCounter.increment();
            throw ex;
        } finally {
            loginSpan.end();
        }
    }

    @Override
    @Transactional
    public AuthResponseVO register(tech.sioseforge.auth.domain.view.RegisterRequestVO request) {
        Span registerSpan = tracer.spanBuilder("UserRegisterProcess").startSpan();
        try (var scope = registerSpan.makeCurrent()) {
            log.info("Processing registration for user: {}", request.username());

            tech.sioseforge.auth.domain.entity.Tenant defaultTenant = tenantRepository.findByDomain("default.local")
                .orElseThrow(() -> new RuntimeException("Default tenant not found"));

            tech.sioseforge.auth.domain.entity.User user = new tech.sioseforge.auth.domain.entity.User();
            user.setUsername(request.username());
            user.setSsoId(request.ssoId());
            ensureRegistrationIsValid(request.username(), request.ssoId());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setTenant(defaultTenant);

            Span dbSpan = tracer.spanBuilder("db_query.save_user").startSpan();
            try {
                user = userRepository.save(user);
                dbSpan.addEvent("User saved successfully");
            } finally {
                dbSpan.end();
            }

            Span kafkaSpan = tracer.spanBuilder("publish_kafka_event").startSpan();
            try (var kafkaScope = kafkaSpan.makeCurrent()) {
                UserRegisteredEvent event = new UserRegisteredEvent(
                    user.getUsername(),
                    user.getUsername() + "@example.com",
                    Instant.now().toString()
                );
                kafkaTemplate.send("user-registered-topic", user.getUsername(), event).join();
                kafkaSpan.addEvent("Event published to user-registered-topic");
                persistOutboxEvent(user, event);
            } finally {
                kafkaSpan.end();
            }

            return new AuthResponseVO(generateToken(user.getUsername(), resolveRoles(user.getUsername())), TOKEN_EXPIRATION_SECONDS);
        } finally {
            registerSpan.end();
        }
    }

    private tech.sioseforge.auth.domain.entity.User verifyCredentials(LoginRequestVO request) {
        tech.sioseforge.auth.domain.entity.User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return user;
    }

    private void ensureRegistrationIsValid(String username, String ssoId) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username is already in use");
        }
        if (userRepository.findBySsoId(ssoId).isPresent()) {
            throw new IllegalArgumentException("SSO ID is already in use");
        }
    }

    private String generateToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("auth-service")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(TOKEN_EXPIRATION_SECONDS))
            .subject(username)
            .claim("roles", roles)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Set<String> resolveRoles(String username) {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        if (adminUsers.contains(username.toLowerCase())) {
            roles.add("ROLE_ADMIN");
        }
        return roles;
    }

    private Set<String> parseAdminUsers(String adminUsersCsv) {
        Set<String> users = new HashSet<>();
        if (adminUsersCsv == null || adminUsersCsv.isBlank()) {
            return users;
        }
        String[] tokens = adminUsersCsv.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                users.add(trimmed.toLowerCase());
            }
        }
        return users;
    }

    private void persistOutboxEvent(tech.sioseforge.auth.domain.entity.User user, UserRegisteredEvent event) {
        String payload = "{\"username\":\"" + escapeJson(event.username()) + "\","
            + "\"email\":\"" + escapeJson(event.email()) + "\","
            + "\"registeredAt\":\"" + escapeJson(event.timestamp().toString()) + "\"}";

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID().toString());
        outboxEvent.setAggregateType("User");
        outboxEvent.setAggregateId(String.valueOf(user.getId()));
        outboxEvent.setType("UserRegistered");
        outboxEvent.setPayload(payload);
        outboxEvent.setCreatedAt(Instant.now());
        outboxEventRepository.save(outboxEvent);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
