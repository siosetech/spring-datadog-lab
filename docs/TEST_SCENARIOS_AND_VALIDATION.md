# Test Scenarios and Validation Guide for OpenTelemetry & Datadog Integration

**Phase 4 of the OpenTelemetry Associate & Datadog APM Certification Preparation Series**

Repositories covered:
- **Spring lab** → [`siosetech/spring-datadog-lab`](../README.md) (Spring Boot 4.1, Java 25, virtual threads)
- **Quarkus lab** → [`siosetech/quarkus-datadog-lab`](https://github.com/siosetech/quarkus-datadog-lab) (Quarkus 3.x, Java 21, Mutiny reactive)

---

> ### Lab runtime (current — Spring lab)
>
> - **Host ports (9xxx range, coexists with FleetForge):** gateway `9000` · auth `9180` · profile `9082` · audit `9083` · dashboard `9084` · notification `9085` · Debezium `9086` · Kafka UI `9088` · Kafka `9192` · Vault `9200` · OTLP gRPC/HTTP `9317`/`9318` · Postgres `9543` · Jaeger UI `9668`
> - **Datadog site:** `us5.datadoghq.com` (API: `api.us5.datadoghq.com`)
> - **Pipeline:** apps export OTLP only → `otel-collector` dual-exports to **Jaeger** + **Datadog us5**
> - **Auth paths (via gateway):** `POST /api/v1/auth/register` / `POST /api/v1/auth/login`, body `{ssoId, username, password}` / `{username, password}`, header `X-Tenant-Id: acme`
> - Many script/curl snippets below were written generically (`:8080`, `:16686`) for readability or are shared with the Quarkus lab — where a snippet targets **this** Spring lab directly, prefer gateway `:9000` and Jaeger `:9668`. K8s Jaeger port-forward is often `:19668`; FleetForge Jaeger is often `:16686`.
> - Full architecture + screenshots: [`OBSERVABILITY_LESSONS.md`](OBSERVABILITY_LESSONS.md) · checklist: [`OBSERVABILITY_WALKTHROUGH.md`](OBSERVABILITY_WALKTHROUGH.md)

---

## Table of Contents

1. [Introduction to Testing OpenTelemetry](#1-introduction-to-testing-opentelemetry)
2. [Unit Testing Traces in Spring Boot](#2-unit-testing-traces-in-spring-boot)
3. [Unit Testing Traces in Quarkus](#3-unit-testing-traces-in-quarkus)
4. [Integration Tests: End-to-End Traces](#4-integration-tests-end-to-end-traces)
5. [Error Scenario Tests](#5-error-scenario-tests)
6. [Performance & Load Tests](#6-performance--load-tests)
7. [Context Propagation Tests](#7-context-propagation-tests)
8. [Virtual Threads & Reactive Concurrency Tests](#8-virtual-threads--reactive-concurrency-tests)
9. [Sampling Strategy Tests](#9-sampling-strategy-tests)
10. [Datadog Integration Validation](#10-datadog-integration-validation)
11. [Testcontainers & Local Testing](#11-testcontainers--local-testing)
12. [Manual Testing Procedures](#12-manual-testing-procedures)
13. [Troubleshooting Failed Tests](#13-troubleshooting-failed-tests)
14. [CI/CD Integration for Tests](#14-cicd-integration-for-tests)
15. [Certification Exam Practice Scenarios](#15-certification-exam-practice-scenarios)
16. [Test Data & Fixtures](#16-test-data--fixtures)
17. [Metrics for Testing Success](#17-metrics-for-testing-success)

---

## 1. Introduction to Testing OpenTelemetry

### Why OTel Testing Matters

Instrumentation that is never verified will silently fail in production. The consequences are serious: missing traces break service maps, broken context propagation makes distributed debugging impossible, and incorrect span attributes cause alert false-positives. Testing OTel instrumentation is a first-class engineering concern.

Key reasons to test your instrumentation:

- **Correctness**: spans appear with expected names, attributes, and hierarchy
- **Continuity**: trace IDs flow across service boundaries without interruption
- **Error fidelity**: 5xx errors and exceptions are accurately reflected in span status
- **Cost awareness**: sampling policies work as designed, controlling ingestion volume
- **Regression prevention**: code changes do not silently break instrumentation

### Types of Tests

| Layer | What it tests | Speed | Dependencies |
|---|---|---|---|
| Unit | Single span creation, attribute setting, mock tracer | Very fast | None (mocks) |
| Integration | Full request path within one service, real OTel SDK, in-memory exporter | Fast | Spring context / Quarkus test |
| End-to-end | Distributed trace across multiple services | Slow | Docker / K8s |
| Performance | Throughput, latency overhead, memory under load with tracing | Variable | Load generator |

### Testing Pyramid for Observability

```text
          /\
         /  \
        / E2E\        <- few; high confidence; slow
       /──────\
      / Integ. \      <- moderate count; realistic SDK
     /──────────\
    /  Unit tests \   <- many; fast; mocked tracer
   /______________\
```

The pyramid applies directly to OTel: heavy coverage at the unit level (span creation, attribute logic), meaningful integration tests (in-memory exporter assertions per endpoint), and a handful of E2E scenarios that confirm cross-service trace continuity.

### Certification Exam Focus Areas

The OTel Associate exam emphasises:

- SDK concepts: `Tracer`, `Span`, `SpanContext`, `Baggage`, `Propagator`
- Instrumentation mechanics: auto vs. manual, semantic conventions
- Context propagation: W3C TraceContext, Baggage
- Sampling: `always_on`, `always_off`, `parentbased_*`, `traceidratio`
- Export pipeline: `SpanExporter`, `SpanProcessor`, `TracerProvider`

The Datadog APM exam emphasises:

- OTLP intake vs. Datadog tracer
- Service naming and tagging (`env`, `service`, `version`, `team`)
- Trace search, retention filters, and Watchdog
- APM metrics from traces (Requests, Errors, Duration)
- Service Map topology

---

## 2. Unit Testing Traces in Spring Boot

### Testing Manual Span Creation

The `DefaultObservabilityService` and `DefaultAuthService` create spans manually. The fastest way to assert span behaviour is to substitute the `OpenTelemetry` SDK with its in-memory implementation.

Add the test dependency (already available as a transitive scope in OTel SDK):

```xml
<!-- auth-service/pom.xml – test scope only -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <scope>test</scope>
</dependency>
```

Full unit test for `DefaultObservabilityService`:

```java
// auth-service/src/test/java/tech/sioseforge/auth/service/ObservabilityServiceUnitTest.java
package tech.sioseforge.auth.service;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.sioseforge.auth.domain.view.TraceCheckResponseVO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityServiceUnitTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void checkTrace_shouldCreateSpanWithEvent() {
        var service = new DefaultObservabilityService(
                otelTesting.getOpenTelemetry().getTracer("test"));

        TraceCheckResponseVO result = service.checkTrace();

        assertThat(result.status()).isEqualTo("OK");

        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("SimpleTraceCheck");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(span.getEvents())
                .anyMatch(e -> e.getName().equals("checkTrace executed"));
        assertThat(result.traceId()).isEqualTo(span.getTraceId());
    }

    @Test
    void deepTraceCheck_shouldCreateRootAndChildSpan() throws Exception {
        var service = new DefaultObservabilityService(
                otelTesting.getOpenTelemetry().getTracer("test"));

        service.deepTraceCheck();

        List<SpanData> spans = otelTesting.getSpans();
        // root span + at least one async child
        assertThat(spans.size()).isGreaterThanOrEqualTo(1);

        SpanData root = spans.stream()
                .filter(s -> s.getName().equals("DeepTraceCheck"))
                .findFirst()
                .orElseThrow();
        assertThat(root.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }
}
```

### Testing Span Attributes and Events

The login flow in `DefaultAuthService` sets `user.id` and fires several events. Test each sub-span:

```java
// auth-service/src/test/java/tech/sioseforge/auth/service/AuthServiceSpanTest.java
package tech.sioseforge.auth.service;

import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.sioseforge.auth.domain.view.LoginRequestVO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceSpanTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void login_shouldProduceExpectedSpanHierarchy() {
        var kafkaTemplate = mock(org.springframework.kafka.core.KafkaTemplate.class);
        var userRepo = mock(tech.sioseforge.auth.repository.UserRepository.class);
        var tenantRepo = mock(tech.sioseforge.auth.repository.TenantRepository.class);

        var tenant = new tech.sioseforge.auth.domain.entity.Tenant();
        tenant.setId(1L);
        tenant.setDomain("default.local");
        when(tenantRepo.findByDomain(anyString()))
                .thenReturn(java.util.Optional.of(tenant));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var service = new DefaultAuthService(
                otelTesting.getOpenTelemetry().getTracer("test"),
                kafkaTemplate, userRepo, tenantRepo);

        service.login(new LoginRequestVO("testuser", "pass", "default.local"));

        List<SpanData> spans = otelTesting.getSpans();

        // Root span
        SpanData loginSpan = spans.stream()
                .filter(s -> s.getName().equals("UserLoginProcess"))
                .findFirst().orElseThrow();

        // db child span must carry user.id attribute
        SpanData dbSpan = spans.stream()
                .filter(s -> s.getName().equals("db_query.verify_credentials"))
                .findFirst().orElseThrow();
        assertThat(dbSpan.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("user.id")))
                .isEqualTo("testuser");

        // All spans must share the same trace ID
        String traceId = loginSpan.getTraceId();
        spans.forEach(s -> assertThat(s.getTraceId()).isEqualTo(traceId));
    }
}
```

### Testing Span Status and Error Handling

```java
// auth-service/src/test/java/tech/sioseforge/auth/service/SpanErrorStatusTest.java
package tech.sioseforge.auth.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpanErrorStatusTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void span_shouldRecordExceptionAndSetErrorStatus() {
        var tracer = otelTesting.getOpenTelemetry().getTracer("test");

        assertThatThrownBy(() -> {
            Span span = tracer.spanBuilder("failingOperation").startSpan();
            try (var scope = span.makeCurrent()) {
                span.setAttribute("operation.type", "db_query");
                throw new RuntimeException("Connection refused");
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                span.end();
            }
        }).isInstanceOf(RuntimeException.class);

        List<SpanData> spans = otelTesting.getSpans();
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Connection refused");
        assertThat(span.getEvents())
                .anyMatch(e -> e.getName().equals("exception"));
    }
}
```

### Mocking Tracer with Mockito

When you cannot use the OTel testing extension (e.g., test depends on heavy Spring context wiring), mock the `Tracer` with a no-op stub:

```java
import io.opentelemetry.api.trace.*;
import static org.mockito.Mockito.*;

Tracer noopTracer = mock(Tracer.class);
SpanBuilder spanBuilder = mock(SpanBuilder.class);
Span span = mock(Span.class);
Scope scope = mock(Scope.class);

when(noopTracer.spanBuilder(anyString())).thenReturn(spanBuilder);
when(spanBuilder.startSpan()).thenReturn(span);
when(span.makeCurrent()).thenReturn(scope);
when(span.getSpanContext()).thenReturn(SpanContext.getInvalid());
when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
```

This pattern is useful for controller unit tests where you only want to verify HTTP status codes and not OTel internals.

### Quick Reference Checklist – Spring Unit Tests

- [ ] `OpenTelemetryExtension` registered via `@RegisterExtension`
- [ ] Service receives `Tracer` via constructor (makes mocking easy)
- [ ] `otelTesting.getSpans()` called after service method completes
- [ ] Span name, attributes, events, and status asserted
- [ ] Trace ID from `SpanData.getTraceId()` matches response value
- [ ] Verify parent–child relationship via `SpanData.getParentSpanId()`

---

## 3. Unit Testing Traces in Quarkus

### @QuarkusTest for OTel Testing

Quarkus provides a built-in OTel integration. Tests annotated with `@QuarkusTest` start the full Quarkus runtime and automatically configure the SDK with an in-memory exporter when the test profile is active.

```xml
<!-- quarkus-datadog-lab/auth-service/pom.xml -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// quarkus-datadog-lab/auth-service/src/test/java/tech/sioseforge/auth/trace/ObservabilityTraceTest.java
package tech.sioseforge.auth.trace;

import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ObservabilityTraceTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void traceEndpoint_shouldReturnOkAndProduceSpan() {
        given()
            .when().get("/api/v1/observability/check")
            .then()
            .statusCode(200)
            .body("status", equalTo("OK"));

        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).isNotEmpty();
        assertThat(spans).anyMatch(s -> s.getName().contains("GET /api/v1/observability/check"));
    }
}
```

### Testing Trace Injection in Quarkus

Quarkus supports `@Inject`-based `Tracer` injection. Test that the injected tracer creates valid spans:

```java
// quarkus-datadog-lab/auth-service/src/test/java/tech/sioseforge/auth/trace/TracerInjectionTest.java
package tech.sioseforge.auth.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TracerInjectionTest {

    @Inject
    Tracer tracer;

    @Test
    void injectedTracer_shouldCreateValidSpan() {
        Span span = tracer.spanBuilder("injectionTest").startSpan();
        String traceId;
        try (var scope = span.makeCurrent()) {
            traceId = span.getSpanContext().getTraceId();
        } finally {
            span.end();
        }
        assertThat(traceId).isNotBlank().hasSize(32);
    }
}
```

### OTel In-Memory Exporter for Unit Tests

Register a custom `InMemorySpanExporter` in the Quarkus test profile:

```java
// quarkus-datadog-lab/auth-service/src/test/java/tech/sioseforge/auth/trace/InMemoryExporterTest.java
package tech.sioseforge.auth.trace;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryExporterTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setup() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void inMemoryExporter_shouldCaptureFinishedSpans() {
        var tracer = tracerProvider.get("test");
        tracer.spanBuilder("vaultSecretFetch")
              .setAttribute("vault.path", "secret/auth/db")
              .startSpan()
              .end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo("vaultSecretFetch");
        assertThat(spans.get(0).getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("vault.path")))
                .isEqualTo("secret/auth/db");
    }
}
```

### Quick Reference Checklist – Quarkus Unit Tests

- [ ] `@QuarkusTest` on the class (full runtime, real OTel SDK)
- [ ] `InMemorySpanExporter` registered via `@QuarkusTestProfile` or `@RegisterExtension`
- [ ] `@Inject Tracer tracer` works without any extra config in Quarkus OTel extension
- [ ] RestAssured assertions verify business response; OTel assertions verify spans
- [ ] `@InjectMock` mocks Vault or DB deps to keep tests fast
- [ ] `exporter.reset()` in `@AfterEach` to avoid cross-test pollution

---

## 4. Integration Tests: End-to-End Traces

### Spring Boot Scenario: Single Service Trace Validation

Extend the existing `AuthIntegrationTest` to assert span output:

```java
// auth-service/src/test/java/tech/sioseforge/auth/AuthOtelIntegrationTest.java
package tech.sioseforge.auth;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.sioseforge.auth.domain.view.LoginRequestVO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.flyway.enabled=false",
    "spring.cloud.vault.enabled=false"
})
class AuthOtelIntegrationTest {

    @LocalServerPort
    int port;

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    InMemorySpanExporter spanExporter;   // registered as a Spring bean in test config

    @BeforeEach
    void reset() {
        spanExporter.reset();
    }

    @Test
    void loginEndpoint_shouldProduceFullSpanHierarchy() {
        var rt = new RestTemplate();
        var req = new LoginRequestVO("testuser", "pass", "default.local");
        rt.postForEntity("http://localhost:" + port + "/api/v1/auth/login", req, Object.class);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        // HTTP server span from Spring auto-instrumentation
        assertThat(spans).anyMatch(s ->
            s.getName().contains("POST") && s.getName().contains("/api/v1/auth/login"));

        // Manual spans from DefaultAuthService
        assertThat(spans).anyMatch(s -> s.getName().equals("UserLoginProcess"));
        assertThat(spans).anyMatch(s -> s.getName().equals("db_query.verify_credentials"));
        assertThat(spans).anyMatch(s -> s.getName().equals("generate_jwt"));
        assertThat(spans).anyMatch(s -> s.getName().equals("publish_kafka_event"));

        // All spans must share the same trace ID
        String traceId = spans.get(0).getTraceId();
        spans.forEach(s -> assertThat(s.getTraceId())
                .as("All spans must belong to same trace").isEqualTo(traceId));
    }

    @Test
    void loginEndpoint_shouldPreserveParentChildRelationship() {
        var rt = new RestTemplate();
        rt.postForEntity("http://localhost:" + port + "/api/v1/auth/login",
                new LoginRequestVO("testuser", "pass", "default.local"), Object.class);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        SpanData loginSpan = spans.stream()
                .filter(s -> s.getName().equals("UserLoginProcess"))
                .findFirst().orElseThrow();

        SpanData dbSpan = spans.stream()
                .filter(s -> s.getName().equals("db_query.verify_credentials"))
                .findFirst().orElseThrow();

        // db span's parent must be the login span
        assertThat(dbSpan.getParentSpanId())
                .isEqualTo(loginSpan.getSpanId());
    }
}
```

Register the `InMemorySpanExporter` in a `@TestConfiguration`:

```java
// auth-service/src/test/java/tech/sioseforge/auth/OtelTestConfig.java
package tech.sioseforge.auth;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class OtelTestConfig {

    @Bean
    public InMemorySpanExporter inMemorySpanExporter(
            io.opentelemetry.sdk.trace.SdkTracerProvider tracerProvider) {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        // Attach to the provider via reflection or a custom TracerProvider bean
        // depending on the spring-boot-starter-otel version
        return exporter;
    }
}
```

### Distributed Tracing Scenario: Spring → Quarkus

This test verifies trace ID propagation when `auth-service` calls `user-profile-service`:

```java
// auth-service/src/test/java/tech/sioseforge/auth/DistributedTraceIT.java
package tech.sioseforge.auth;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires both auth-service and user-profile-service running (e.g. via docker-compose).
 * Activated by: mvn verify -Pit (integration test profile)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DistributedTraceIT {

    @LocalServerPort
    int port;

    @Autowired
    io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter;

    @Test
    void profileStats_shouldPropagateTraceContextToDownstreamService() throws Exception {
        var rt = new org.springframework.web.client.RestTemplate();
        rt.getForEntity(
            "http://localhost:" + port + "/api/v1/observability/profile-stats",
            String.class);

        List<SpanData> spans = exporter.getFinishedSpanItems();

        // HTTP client span created by RestClient should be present
        assertThat(spans).anyMatch(s ->
            s.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT);

        // The outbound HTTP span should carry traceparent header automatically
        // All spans must share the same trace root
        assertThat(spans.stream().map(SpanData::getTraceId).distinct().count())
                .as("All spans must share a single trace ID")
                .isEqualTo(1L);
    }
}
```

Verify context propagation headers with curl:

```bash
#!/usr/bin/env bash
# scripts/test-context-propagation.sh
# Spring lab ports: auth-service :9180, user-profile-service :9082 (gateway :9000)

AUTH_URL="http://localhost:9180"
USER_PROFILE_URL="http://localhost:9082"

echo "=== Step 1: Login to get trace ID ==="
RESPONSE=$(curl -s -D - -X POST "${AUTH_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username":"testuser","password":"pass","domain":"default.local"}')

echo "${RESPONSE}"

echo ""
echo "=== Step 2: Verify profile-stats returns with trace context ==="
curl -s -D - "${AUTH_URL}/api/v1/observability/profile-stats" \
  -H "X-Tenant-Id: acme" | grep -i "traceparent\|tracestate\|x-datadog"
```

### Quick Reference Checklist – Integration Tests

- [ ] `InMemorySpanExporter` injected via test configuration
- [ ] `exporter.reset()` called in `@BeforeEach`
- [ ] HTTP server span name follows `"METHOD /path"` pattern
- [ ] All spans from one request share a single `traceId`
- [ ] Parent–child links verified via `SpanData.getParentSpanId()`
- [ ] Span kind checked: `SERVER` for inbound, `CLIENT` for outbound, `INTERNAL` for manual
- [ ] At least one span has `tenant.id` attribute from `TenantSpanEnricherFilter`

---

## 5. Error Scenario Tests

### HTTP Error Responses (4xx, 5xx)

When an endpoint returns 4xx or 5xx, the OTel HTTP server span **must** have `StatusCode.ERROR` and the exception must be recorded.

```java
// auth-service/src/test/java/tech/sioseforge/auth/HttpErrorSpanTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.cloud.vault.enabled=false"
})
class HttpErrorSpanTest {

    @LocalServerPort
    int port;

    @Autowired
    io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter;

    @BeforeEach
    void reset() { exporter.reset(); }

    @Test
    void nonExistentEndpoint_shouldSetSpanStatusError() {
        var rt = new RestTemplate();
        catchThrowable(() ->
            rt.getForEntity("http://localhost:" + port + "/api/v1/auth/nonexistent", String.class));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        SpanData serverSpan = spans.stream()
                .filter(s -> s.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
                .findFirst().orElseThrow();

        // HTTP 404 – server span should record ERROR status per OTel HTTP semantic conventions
        assertThat(serverSpan.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code")))
                .isGreaterThanOrEqualTo(400L);
    }

    @Test
    void badRequest_shouldRecordExceptionEvent() {
        var rt = new RestTemplate();
        catchThrowable(() ->
            rt.postForEntity("http://localhost:" + port + "/api/v1/auth/login",
                "{invalid}", String.class));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        boolean hasException = spans.stream()
                .flatMap(s -> s.getEvents().stream())
                .anyMatch(e -> e.getName().equals("exception"));
        assertThat(hasException).isTrue();
    }
}
```

### Database Errors

```java
// auth-service/src/test/java/tech/sioseforge/auth/DatabaseErrorSpanTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseErrorSpanTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void databaseConnectionFailure_shouldRecordExceptionWithErrorStatus() {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("db-test");

        Span span = tracer.spanBuilder("db.query")
                .setAttribute("db.system", "postgresql")
                .setAttribute("db.name", "auth_service")
                .setAttribute("db.statement", "SELECT * FROM users WHERE username = ?")
                .startSpan();
        try (var scope = span.makeCurrent()) {
            throw new RuntimeException("Connection refused: postgresql:5432",
                    new SQLException("08001"));
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Database unavailable");
        } finally {
            span.end();
        }

        List<SpanData> spans = otelTesting.getSpans();
        SpanData dbSpan = spans.get(0);

        assertThat(dbSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(dbSpan.getEvents())
                .anyMatch(e -> e.getName().equals("exception"));

        // Verify db.system semantic attribute is present
        assertThat(dbSpan.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("db.system")))
                .isEqualTo("postgresql");
    }
}
```

### Vault Secret Retrieval Failures

```java
// auth-service/src/test/java/tech/sioseforge/auth/VaultErrorSpanTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.sioseforge.auth.service.DefaultVaultSecretService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VaultErrorSpanTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void vaultSecretNotFound_shouldSetSpanStatusError() {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("vault-test");

        Span span = tracer.spanBuilder("vault.getSecret")
                .setAttribute("vault.path", "secret/auth/nonexistent")
                .startSpan();
        try (var scope = span.makeCurrent()) {
            throw new org.springframework.vault.VaultException("Secret not found at path");
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Vault secret not found");
        } finally {
            span.end();
        }

        List<SpanData> spans = otelTesting.getSpans();
        SpanData vaultSpan = spans.get(0);

        assertThat(vaultSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(vaultSpan.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("vault.path")))
                .isEqualTo("secret/auth/nonexistent");
        assertThat(vaultSpan.getEvents())
                .anyMatch(e -> e.getName().equals("exception"));
    }
}
```

### Service-to-Service Communication Failures

```java
// auth-service/src/test/java/tech/sioseforge/auth/DownstreamFailureSpanTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamFailureSpanTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void downstreamTimeout_shouldRecordClientSpanAsError() {
        var tracer = otelTesting.getOpenTelemetry().getTracer("http-client-test");

        var span = tracer.spanBuilder("HTTP GET")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.request.method", "GET")
                .setAttribute("server.address", "user-profile-service")
                .setAttribute("server.port", 8081L)
                .setAttribute("url.full", "http://user-profile-service:8081/api/v1/profiles/stats")
                .startSpan();
        try (var scope = span.makeCurrent()) {
            throw new RuntimeException("Request timed out", new ConnectException("Connection refused"));
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Downstream unreachable");
        } finally {
            span.end();
        }

        List<SpanData> spans = otelTesting.getSpans();
        SpanData clientSpan = spans.get(0);

        assertThat(clientSpan.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(clientSpan.getEvents())
                .anyMatch(e -> e.getName().equals("exception"));
    }
}
```

### Quick Reference Checklist – Error Scenario Tests

- [ ] HTTP 4xx → span attribute `http.response.status_code` ≥ 400
- [ ] HTTP 5xx → span status `ERROR`, exception event recorded
- [ ] `recordException(e)` called before `setStatus(ERROR, ...)`
- [ ] Database errors include `db.system`, `db.name`, `db.statement` attributes
- [ ] Vault errors include `vault.path` attribute and `exception` event
- [ ] Client-side timeout spans have `SpanKind.CLIENT` and `StatusCode.ERROR`

---

## 6. Performance & Load Tests

### Baseline Performance Without Tracing

Use the `noop` tracer (no-op) as baseline. Configure via application property:

```yaml
# application-perf-baseline.yml
management:
  tracing:
    enabled: false
otel:
  sdk:
    disabled: true
```

Run with [k6](https://k6.io/) or [Gatling](https://gatling.io/):

```bash
#!/usr/bin/env bash
# scripts/perf/baseline.sh
k6 run --vus 50 --duration 60s scripts/perf/load-test.js
```

```javascript
// scripts/perf/load-test.js
// Default BASE_URL targets the Spring lab's api-gateway (:9000); override for auth-service
// direct (:9180) or for the Quarkus lab (:8080).
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9000';

export const options = {
  vus: 50,
  duration: '60s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    username: 'perfuser',
    password: 'pass',
  }), { headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': 'acme' } });

  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(0.1);
}
```

### Performance with Sampling Ratios

| Scenario | Sampling | Expected overhead |
|---|---|---|
| Baseline | `sdk.disabled=true` | 0% |
| Always on | `OTEL_TRACES_SAMPLER=always_on` | ~3–5% CPU, ~5–10 MB heap |
| 10% ratio | `OTEL_TRACES_SAMPLER=traceidratio` `OTEL_TRACES_SAMPLER_ARG=0.1` | ~0.5% CPU |
| 50% ratio | `OTEL_TRACES_SAMPLER_ARG=0.5` | ~2% CPU |
| Parent-based | `OTEL_TRACES_SAMPLER=parentbased_always_on` | Depends on upstream |

Run each scenario and record p50, p95, p99:

```bash
#!/usr/bin/env bash
# scripts/perf/sampling-matrix.sh

SAMPLERS=("always_on" "always_off" "traceidratio")
RATIOS=("1.0" "0.5" "0.1")

for sampler in "${SAMPLERS[@]}"; do
  for ratio in "${RATIOS[@]}"; do
    echo "=== Sampler: ${sampler} Ratio: ${ratio} ==="
    OTEL_TRACES_SAMPLER="${sampler}" \
    OTEL_TRACES_SAMPLER_ARG="${ratio}" \
    k6 run --vus 50 --duration 30s \
      --env BASE_URL=http://localhost:9000 \
      --summary-export "/tmp/perf-${sampler}-${ratio}.json" \
      scripts/perf/load-test.js
  done
done
```

### Memory Profiling with Tracing Enabled

```bash
#!/usr/bin/env bash
# Monitor heap while load test runs
PID=$(pgrep -f "auth-service")
while true; do
  jcmd "${PID}" VM.native_memory summary 2>/dev/null | grep -E "Heap|total"
  sleep 5
done &

MONITOR_PID=$!
k6 run --vus 100 --duration 120s scripts/perf/load-test.js
kill "${MONITOR_PID}"
```

### Latency Percentile Comparison

```bash
#!/usr/bin/env bash
# scripts/perf/compare-percentiles.sh
# Requires jq

echo "Comparing p50/p95/p99 across sampling configs..."

for file in /tmp/perf-*.json; do
  label=$(basename "${file}" .json)
  p50=$(jq -r '.metrics.http_req_duration.values.med' "${file}")
  p95=$(jq -r '.metrics.http_req_duration.values["p(95)"]' "${file}")
  p99=$(jq -r '.metrics.http_req_duration.values["p(99)"]' "${file}")
  echo "${label}: p50=${p50}ms  p95=${p95}ms  p99=${p99}ms"
done
```

### Quick Reference Checklist – Performance Tests

- [ ] Baseline run with tracing disabled (SDK off)
- [ ] Always-on sampling run under identical load
- [ ] Compare p95 latency overhead: target < 5%
- [ ] Heap difference measured via `jcmd` or `-Xmx` analysis
- [ ] Throughput (req/s) recorded for all scenarios
- [ ] Results committed to `docs/perf/` as CSV/JSON for history

---

## 7. Context Propagation Tests

### W3C TraceContext Header Validation

The `traceparent` header follows this format:

```
traceparent: 00-<32-hex trace-id>-<16-hex span-id>-<flags>
             00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

Use curl to verify the header is forwarded downstream:

```bash
#!/usr/bin/env bash
# scripts/test-w3c-traceparent.sh
# Spring lab: auth-service :9180 (or gateway :9000), Jaeger UI :9668

TRACE_ID="4bf92f3577b34da6a3ce929d0e0e4736"
SPAN_ID="00f067aa0ba902b7"

echo "=== Inject traceparent into auth-service ==="
curl -s -D - "http://localhost:9180/api/v1/observability/profile-stats" \
  -H "traceparent: 00-${TRACE_ID}-${SPAN_ID}-01" \
  -H "X-Tenant-Id: acme" | head -30

echo ""
echo "=== Verify spans in Jaeger ==="
sleep 1
curl -s "http://localhost:9668/api/traces/${TRACE_ID}" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d['data'][0]['spans'], indent=2))" 2>/dev/null || \
  echo "Jaeger not available – check OTLP exporter logs instead"
```

Java test for `traceparent` header format:

```java
// auth-service/src/test/java/tech/sioseforge/auth/TraceContextHeaderTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextHeaderTest {

    @Test
    void w3cPropagator_shouldInjectValidTraceparentHeader() {
        var propagator = W3CTraceContextPropagator.getInstance();

        SpanContext ctx = SpanContext.create(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                TraceFlags.getSampled(),
                TraceState.getDefault());

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(
                io.opentelemetry.context.Context.root().with(
                        io.opentelemetry.api.trace.Span.wrap(ctx)),
                carrier,
                (TextMapSetter<Map<String, String>>) Map::put);

        assertThat(carrier).containsKey("traceparent");
        String tp = carrier.get("traceparent");
        assertThat(tp).matches("00-[a-f0-9]{32}-[a-f0-9]{16}-0[01]");
        assertThat(tp).contains("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
```

### Baggage Propagation

```java
// auth-service/src/test/java/tech/sioseforge/auth/BaggagePropagationTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.trace.propagation.W3CBaggagePropagator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaggagePropagationTest {

    @Test
    void baggage_shouldBePropagatedViaW3CHeader() {
        Baggage baggage = Baggage.builder()
                .put("tenant.id", "acme")
                .put("correlation.id", "req-12345")
                .build();

        Context ctx = Context.root().with(baggage);

        Map<String, String> carrier = new HashMap<>();
        W3CBaggagePropagator.getInstance().inject(ctx, carrier,
                (TextMapSetter<Map<String, String>>) Map::put);

        assertThat(carrier).containsKey("baggage");
        assertThat(carrier.get("baggage"))
                .contains("tenant.id=acme")
                .contains("correlation.id=req-12345");
    }

    @Test
    void baggage_shouldBeExtractedFromInboundHeader() {
        Map<String, String> inboundHeaders = Map.of(
                "baggage", "tenant.id=acme,correlation.id=req-99999");

        Context extractedCtx = W3CBaggagePropagator.getInstance()
                .extract(Context.root(), inboundHeaders,
                        (getter, carrier1, key) -> carrier1.get(key));

        Baggage extracted = Baggage.fromContext(extractedCtx);
        assertThat(extracted.getEntryValue("tenant.id")).isEqualTo("acme");
        assertThat(extracted.getEntryValue("correlation.id")).isEqualTo("req-99999");
    }
}
```

### MDC Integration

The `TenantMdcFilter` should enrich log MDC with trace ID and tenant ID for log–trace correlation:

```java
// auth-service/src/test/java/tech/sioseforge/auth/MdcTraceCorrelationTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceCorrelationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void span_shouldPopulateMdcWithTraceId() {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("mdc-test");
        String capturedTraceId;

        Span span = tracer.spanBuilder("mdcCheck").startSpan();
        try (var scope = span.makeCurrent()) {
            // OTel bridge populates MDC automatically when logback-otel bridge is on classpath
            // Manually simulate what the bridge does:
            MDC.put("trace_id", span.getSpanContext().getTraceId());
            MDC.put("span_id", span.getSpanContext().getSpanId());
            capturedTraceId = MDC.get("trace_id");
        } finally {
            span.end();
            MDC.clear();
        }

        assertThat(capturedTraceId).isNotBlank().hasSize(32);
    }
}
```

### Quick Reference Checklist – Context Propagation Tests

- [ ] `traceparent` header format validated: `00-<32hex>-<16hex>-<flags>`
- [ ] Injected `traceparent` trace ID matches all spans in exported data
- [ ] Baggage `tenant.id` visible in downstream service spans
- [ ] W3C `baggage` header present on outbound HTTP requests from `RestClient`
- [ ] MDC fields `trace_id` and `span_id` populated by the OTel log bridge
- [ ] `tracestate` forwarded unchanged through all hops

---

## 8. Virtual Threads & Reactive Concurrency Tests

### Spring Virtual Threads: Context Preservation

`DefaultObservabilityService.deepTraceCheck()` spawns a virtual thread. OTel's Java agent preserves context across virtual thread boundaries using `ContextStorage`.

```java
// auth-service/src/test/java/tech/sioseforge/auth/VirtualThreadContextTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadContextTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void virtualThread_shouldPreserveOtelContext() throws Exception {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("vthread-test");
        AtomicReference<String> childTraceId = new AtomicReference<>();

        Span parentSpan = tracer.spanBuilder("parentOperation").startSpan();
        try (var scope = parentSpan.makeCurrent()) {
            // Capture current context BEFORE submitting to virtual thread
            Context capturedCtx = Context.current();

            Future<?> future = Executors.newVirtualThreadPerTaskExecutor()
                    .submit(() -> capturedCtx.wrap(() -> {
                        Span childSpan = tracer.spanBuilder("childInVirtualThread").startSpan();
                        try (var childScope = childSpan.makeCurrent()) {
                            childTraceId.set(childSpan.getSpanContext().getTraceId());
                        } finally {
                            childSpan.end();
                        }
                    }));
            future.get();
        } finally {
            parentSpan.end();
        }

        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        // Both spans must share the same trace ID
        String parentTraceId = parentSpan.getSpanContext().getTraceId();
        assertThat(childTraceId.get()).isEqualTo(parentTraceId);

        SpanData child = spans.stream()
                .filter(s -> s.getName().equals("childInVirtualThread"))
                .findFirst().orElseThrow();
        assertThat(child.getParentSpanId())
                .isEqualTo(parentSpan.getSpanContext().getSpanId());
    }
}
```

### Quarkus Reactive: Context Propagation Across Async Boundaries

```java
// quarkus-datadog-lab/auth-service/src/test/java/tech/sioseforge/auth/ReactiveContextTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ReactiveContextTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Inject
    Tracer tracer;

    @Test
    void mutinyUni_shouldPreserveOtelContext() {
        Span parent = tracer.spanBuilder("reactiveParent").startSpan();
        String childTraceId;

        try (var scope = parent.makeCurrent()) {
            childTraceId = Uni.createFrom().item("data")
                    .map(data -> {
                        // Quarkus propagates OTel context on Mutiny pipelines automatically
                        return Span.current().getSpanContext().getTraceId();
                    })
                    .await().indefinitely();
        } finally {
            parent.end();
        }

        assertThat(childTraceId).isEqualTo(parent.getSpanContext().getTraceId());
    }
}
```

### Performance Comparison: Virtual Threads vs Platform Threads

```bash
#!/usr/bin/env bash
# scripts/perf/virtual-vs-platform.sh

echo "=== Platform thread test ==="
JAVA_OPTS="-Dspring.threads.virtual.enabled=false" \
k6 run --vus 100 --duration 30s \
  --summary-export /tmp/perf-platform.json \
  scripts/perf/load-test.js

echo "=== Virtual thread test ==="
JAVA_OPTS="-Dspring.threads.virtual.enabled=true" \
k6 run --vus 100 --duration 30s \
  --summary-export /tmp/perf-virtual.json \
  scripts/perf/load-test.js

echo "=== Comparison ==="
for mode in platform virtual; do
  p99=$(jq -r '.metrics.http_req_duration.values["p(99)"]' "/tmp/perf-${mode}.json")
  echo "${mode}: p99=${p99}ms"
done
```

### Quick Reference Checklist – Concurrency Tests

- [ ] Virtual thread span has correct `parentSpanId` pointing to caller span
- [ ] `Context.current().wrap(runnable)` used when submitting to executor without automatic propagation
- [ ] Quarkus Mutiny pipelines: context propagated automatically via `io.quarkus.opentelemetry` extension
- [ ] No `TraceId.getInvalid()` (all-zeros) found in child spans
- [ ] `Span.current()` returns the correct span inside async boundaries

---

## 9. Sampling Strategy Tests

### Verify Sampled vs Non-Sampled Traces

```java
// auth-service/src/test/java/tech/sioseforge/auth/SamplingStrategyTest.java
package tech.sioseforge.auth;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.opentelemetry.sdk.trace.data.LinkData;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SamplingStrategyTest {

    @Test
    void alwaysOn_shouldSampleEverySpan() {
        var decision = Sampler.alwaysOn().shouldSample(
                io.opentelemetry.context.Context.root(),
                "traceId", "spanName",
                io.opentelemetry.api.trace.SpanKind.INTERNAL,
                Attributes.empty(),
                Collections.emptyList());

        assertThat(decision.getDecision())
                .isEqualTo(SamplingResult.recordAndSample().getDecision());
    }

    @Test
    void alwaysOff_shouldDropEverySpan() {
        var decision = Sampler.alwaysOff().shouldSample(
                io.opentelemetry.context.Context.root(),
                "traceId", "spanName",
                io.opentelemetry.api.trace.SpanKind.INTERNAL,
                Attributes.empty(),
                Collections.emptyList());

        assertThat(decision.getDecision())
                .isEqualTo(SamplingResult.drop().getDecision());
    }

    @Test
    void traceIdRatio_50pct_shouldSampleApproximatelyHalf() {
        var sampler = Sampler.traceIdRatioBased(0.5);
        int sampled = 0;
        int iterations = 10_000;

        for (int i = 0; i < iterations; i++) {
            // Generate a unique random trace ID each iteration
            String traceId = String.format("%032x", (long) (Math.random() * Long.MAX_VALUE) * 2);
            var result = sampler.shouldSample(
                    io.opentelemetry.context.Context.root(),
                    traceId, "span",
                    io.opentelemetry.api.trace.SpanKind.INTERNAL,
                    Attributes.empty(),
                    Collections.emptyList());
            if (result.getDecision() == SamplingResult.recordAndSample().getDecision()) {
                sampled++;
            }
        }

        double ratio = (double) sampled / iterations;
        // Allow ±10% tolerance
        assertThat(ratio).isBetween(0.40, 0.60);
    }
}
```

### Configure Sampling via Environment Variable

```bash
# Always on (default for dev)
export OTEL_TRACES_SAMPLER=always_on

# Parent-based always on (recommended for production: respects upstream sampling decision)
export OTEL_TRACES_SAMPLER=parentbased_always_on

# 10% of traces (cost-optimised)
export OTEL_TRACES_SAMPLER=traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1
```

### Quick Reference Checklist – Sampling Tests

- [ ] `always_on` → all requests produce spans in exporter
- [ ] `always_off` → no spans produced (verify with empty `getFinishedSpanItems()`)
- [ ] `traceidratio=0.1` → ~10% of requests produce spans (statistical)
- [ ] `parentbased_always_on` → downstream service respects sampled flag from `traceparent`
- [ ] Sampled flag `01` in `traceparent` → span exported; `00` → dropped

---

## 10. Datadog Integration Validation

### Verify Traces Appear in Datadog UI

After deploying to Kubernetes with the Datadog Agent and OTLP intake:

```bash
#!/usr/bin/env bash
# scripts/validate-datadog.sh
# Spring lab uses DD_SITE=us5.datadoghq.com; traffic goes through the gateway (:9000)

DD_API_KEY="${DD_API_KEY:?Must set DD_API_KEY}"
DD_SITE="${DD_SITE:-us5.datadoghq.com}"

echo "=== Step 1: Generate some traffic ==="
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST "http://localhost:9000/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: acme" \
    -d '{"username":"testuser","password":"pass"}'
  sleep 0.5
done

echo ""
echo "=== Step 2: Query Datadog API for recent traces ==="
sleep 10
curl -s "https://api.${DD_SITE}/api/v1/query" \
  -H "DD-API-KEY: ${DD_API_KEY}" \
  -H "DD-APPLICATION-KEY: ${DD_APP_KEY}" \
  -G --data-urlencode "query=trace.http.request{service:auth-service,env:dev}" \
     --data-urlencode "from=$(date -d '-5 minutes' +%s)" \
     --data-urlencode "to=$(date +%s)" | python3 -m json.tool

echo ""
echo "=== Step 3: Verify service map is populated ==="
echo "Open: https://app.${DD_SITE}/apm/map?env=dev"
```

### Verify Custom Attributes in Datadog

Custom attributes set via `span.setAttribute("tenant.id", ...)` appear in Datadog as **span tags**. Use the Trace Search UI:

1. Navigate to **APM → Traces**
2. Add filter: `@tenant.id:acme`
3. Verify spans from both `auth-service` and `user-profile-service` appear
4. Click a trace → verify the flame graph shows the correct span hierarchy

### Verify Service Map Construction

The service map uses `peer.service` and `server.address` semantic attributes:

```bash
# Confirm peer.service is set on outbound HTTP spans
grep -r "peer.service\|server.address" \
  auth-service/src/main/java/ \
  user-profile-service/src/main/java/
```

If not set manually, Spring Boot auto-instrumentation infers service name from the URL. For accurate service maps, set explicitly:

```yaml
# auth-service/src/main/resources/application.yml
spring:
  application:
    name: auth-service
management:
  opentelemetry:
    resource-attributes:
      service.name: auth-service
      deployment.environment: dev
      service.version: "1.0.0"
```

### Quick Reference Checklist – Datadog Validation

- [ ] Traces visible in **APM → Traces** within 30 seconds of generation
- [ ] Service Map shows `auth-service → user-profile-service` edge
- [ ] Custom attribute `tenant.id` filterable in Trace Search
- [ ] Span status ERROR visible on flame graph (red spans)
- [ ] Logs correlated via `dd.trace_id` (matches OTel trace ID converted to decimal)
- [ ] APM metrics (`trace.http.request.hits`, `.errors`, `.duration`) auto-generated

---

## 11. Testcontainers & Local Testing

### Setting Up OTel Collector with Testcontainers

```java
// auth-service/src/test/java/tech/sioseforge/auth/OtelCollectorContainer.java
package tech.sioseforge.auth;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public class OtelCollectorContainer extends GenericContainer<OtelCollectorContainer> {

    public static final int OTLP_GRPC_PORT = 4317;
    public static final int OTLP_HTTP_PORT = 4318;
    public static final int HEALTH_PORT = 13133;

    public OtelCollectorContainer() {
        super("otel/opentelemetry-collector-contrib:0.110.0");
        withExposedPorts(OTLP_GRPC_PORT, OTLP_HTTP_PORT, HEALTH_PORT);
        withCopyFileToContainer(
                MountableFile.forClasspathResource("otel-collector-test.yaml"),
                "/etc/otelcol-contrib/config.yaml");
        waitingFor(Wait.forHttp("/").forPort(HEALTH_PORT).forStatusCode(200));
    }

    public String getOtlpEndpoint() {
        return "http://localhost:" + getMappedPort(OTLP_HTTP_PORT);
    }
}
```

Place the collector config in `src/test/resources/otel-collector-test.yaml`:

```yaml
# auth-service/src/test/resources/otel-collector-test.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"
      http:
        endpoint: "0.0.0.0:4318"

processors:
  batch:

exporters:
  debug:
    verbosity: detailed
  datadog:
    api:
      site: ${env:DD_SITE}
      key: ${env:DD_API_KEY}
  # Dual-export: apps stay vendor-agnostic; collector fans out to Jaeger + Datadog
  otlphttp/jaeger:
    endpoint: http://jaeger:4318
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [datadog, otlphttp/jaeger, debug]
```

### Setting Up Jaeger v2 for Local Development

```java
// auth-service/src/test/java/tech/sioseforge/auth/JaegerContainer.java
package tech.sioseforge.auth;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class JaegerContainer extends GenericContainer<JaegerContainer> {

    public static final int UI_PORT = 16686;
    public static final int OTLP_GRPC_PORT = 4317;

    public JaegerContainer() {
        // Jaeger v2 unified image (replaces all-in-one)
        super("cr.jaegertracing.io/jaegertracing/jaeger:2.20.0");
        withExposedPorts(UI_PORT, OTLP_GRPC_PORT);
        waitingFor(Wait.forHttp("/").forPort(UI_PORT).forStatusCode(200));
    }

    public String getUiUrl() {
        return "http://localhost:" + getMappedPort(UI_PORT);
    }

    public String getOtlpEndpoint() {
        return "http://localhost:" + getMappedPort(OTLP_GRPC_PORT);
    }
}
```

### Complete Docker Compose Environment

Use the root [`docker-compose.yml`](../docker-compose.yml). Apps send OTLP to the collector (container ports `4317`/`4318`, mapped to host `9317`/`9318`); the collector dual-exports to Jaeger v2 and Datadog us5. Host ports are remapped to the **9xxx** range so this stack can run beside FleetForge.

Pinned infra images (excerpt):

```yaml
# docker-compose.yml (excerpt)
services:
  jaeger:
    image: cr.jaegertracing.io/jaegertracing/jaeger:2.20.0
    ports:
      - "9668:16686"   # UI only (host:container)

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.100.0
    ports:
      - "9317:4317"
      - "9318:4318"
    depends_on:
      - jaeger

  postgres:
    image: postgres:15.8-alpine
    ports:
      - "9543:5432"

  vault:
    image: hashicorp/vault:1.19.5
    ports:
      - "9200:8200"

  kafka:
    image: bitnamilegacy/kafka:3.9
    ports:
      - "9192:9094"   # external listener for host clients

  kafka-ui:
    image: provectuslabs/kafka-ui:v0.7.2
    ports:
      - "9088:8080"
```

Start the environment:

```bash
docker compose up -d
# Jaeger UI
open http://localhost:9668
# Kafka UI
open http://localhost:9088
```

### Quick Reference Checklist – Testcontainers

- [ ] `@Testcontainers(disabledWithoutDocker = true)` on class to skip in CI without Docker
- [ ] `@ServiceConnection` used for PostgreSQL/Kafka to auto-wire datasource properties
- [ ] `OtelCollectorContainer` starts before app under test
- [ ] App `OTEL_EXPORTER_OTLP_ENDPOINT` pointed to collector container
- [ ] Jaeger UI accessible at `http://localhost:<mappedPort>`
- [ ] `container.getLogs()` queried for assertion if span data cannot be fetched via API

---

## 12. Manual Testing Procedures

### Quick Start Guide

```bash
#!/usr/bin/env bash
# scripts/manual-test.sh
# Prerequisites: Docker, Java 21+, Maven
# Spring lab host ports: gateway 9000, auth 9180, Jaeger UI 9668, OTLP HTTP 9318

set -e

echo "=== 1. Start infrastructure ==="
docker compose up -d postgres kafka jaeger otel-collector
sleep 5

echo "=== 2. Start auth-service (default port 9180) ==="
(cd auth-service && \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:9318 \
  OTEL_SERVICE_NAME=auth-service \
  OTEL_TRACES_SAMPLER=always_on \
  SPRING_CLOUD_VAULT_ENABLED=false \
  ./mvnw spring-boot:run -pl . &)
sleep 10

echo "=== 3. Validate health (direct, no gateway needed for this smoke test) ==="
curl -f http://localhost:9180/actuator/health || exit 1

echo "=== 4. Register and login ==="
curl -X POST http://localhost:9180/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"ssoId":"sso-1","username":"testuser","password":"pass"}'

TOKEN=$(curl -s -X POST http://localhost:9180/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username":"testuser","password":"pass"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: ${TOKEN}"

echo "=== 5. Generate trace traffic ==="
for i in {1..5}; do
  curl -s http://localhost:9180/api/v1/observability/check \
    -H "X-Tenant-Id: acme" | python3 -m json.tool
done

echo "=== 6. Open Jaeger UI ==="
echo "Navigate to: http://localhost:9668"
echo "Search for service: auth-service"
```

### Using curl for Trace Validation

```bash
#!/usr/bin/env bash
# scripts/curl-trace-validate.sh
# Spring lab: prefer the gateway (:9000) so requests exercise the full
# api-gateway -> auth-service path; auth-service alone is on :9180.

BASE_URL="http://localhost:9000"
TENANT="acme"

echo "=== Test 1: Simple trace check ==="
curl -s "${BASE_URL}/api/v1/observability/check" \
  -H "X-Tenant-Id: ${TENANT}" | python3 -m json.tool

echo ""
echo "=== Test 2: Deep trace with virtual threads ==="
curl -s "${BASE_URL}/api/v1/observability/deep-check" \
  -H "X-Tenant-Id: ${TENANT}" | python3 -m json.tool

echo ""
echo "=== Test 3: Inject custom traceparent (continue existing trace) ==="
CUSTOM_TRACE_ID=$(python3 -c "import os; print(os.urandom(16).hex())")
CUSTOM_SPAN_ID=$(python3 -c "import os; print(os.urandom(8).hex())")
curl -s "${BASE_URL}/api/v1/observability/check" \
  -H "traceparent: 00-${CUSTOM_TRACE_ID}-${CUSTOM_SPAN_ID}-01" \
  -H "X-Tenant-Id: ${TENANT}" | python3 -m json.tool

echo ""
echo "=== Verify trace ${CUSTOM_TRACE_ID} in Jaeger ==="
sleep 2
curl -s "http://localhost:9668/api/traces/${CUSTOM_TRACE_ID}" | \
  python3 -c "
import json, sys
data = json.load(sys.stdin)
if data.get('data'):
    spans = data['data'][0]['spans']
    print(f'Spans found: {len(spans)}')
    for s in spans:
        print(f'  {s[\"operationName\"]} [{s[\"spanID\"]}]')
else:
    print('No trace found (may take a few seconds to appear)')
"
```

### Inspecting Traces in Datadog UI

1. **APM → Traces** → filter by `service:auth-service env:dev`
2. Click a trace → **Flame Graph** view shows full span hierarchy
3. Click a span → **Span Details** panel shows all attributes
4. **Infrastructure** tab links to underlying host/container metrics
5. Use **Compare** to overlay two traces from different deployments

### Creating Custom Dashboard for Test Validation

Use the Terraform configuration in `terraform/datadog.tf` as a base. Add a test-validation widget:

```hcl
# terraform/datadog.tf (addition)
resource "datadog_dashboard" "otel_test_validation" {
  title       = "OTel Test Validation"
  layout_type = "ordered"

  widget {
    timeseries_definition {
      title = "Span Count by Service"
      request {
        q    = "sum:trace.http.request.hits{env:dev} by {service}.as_rate()"
        display_type = "bars"
      }
    }
  }

  widget {
    timeseries_definition {
      title = "Error Rate by Service"
      request {
        q    = "sum:trace.http.request.errors{env:dev} by {service}.as_rate()"
        display_type = "line"
      }
    }
  }

  widget {
    query_value_definition {
      title = "P95 Latency – auth-service"
      request {
        q          = "p95:trace.http.request.duration{service:auth-service,env:dev}"
        aggregator = "last"
      }
    }
  }
}
```

### Quick Reference Checklist – Manual Testing

- [ ] `curl /actuator/health` returns `{"status":"UP"}` before tests
- [ ] Jaeger UI shows service `auth-service` in the service dropdown
- [ ] Custom `traceparent` is visible in Jaeger as the root of the new trace
- [ ] `tenant.id` tag visible in Jaeger span tags panel
- [ ] All child spans share the same `traceID` as the root HTTP span
- [ ] Error spans (red) appear on 4xx/5xx responses

---

## 13. Troubleshooting Failed Tests

### Decision Trees

#### Traces Not Appearing in Test Assertions

```text
Traces not in getFinishedSpanItems()?
│
├─► Is InMemorySpanExporter registered as SpanProcessor?
│       NO → Add SimpleSpanProcessor.create(exporter) to SdkTracerProvider
│       YES ↓
│
├─► Is exporter.reset() called in @BeforeEach?
│       NO → Previous test spans contaminating results
│       YES ↓
│
├─► Is the Tracer obtained from the SAME OpenTelemetry instance as the exporter?
│       NO → Different SDK instances; spans go to the wrong exporter
│       YES ↓
│
└─► Is span.end() called after the operation?
        NO → SDK holds spans in-flight; they won't appear until ended
        YES → Check @RegisterExtension scope (static vs instance)
```

#### Context Not Propagating Across Service Calls

```text
Downstream service shows different traceId?
│
├─► Is the RestClient/RestTemplate using the OTel-instrumented builder?
│       NO (raw new RestTemplate()) → Context not injected
│       YES ↓
│
├─► Does the propagator include W3CTraceContextPropagator?
│       Check: OTEL_PROPAGATORS=tracecontext,baggage (default)
│       YES ↓
│
├─► Is the downstream service extracting context on inbound requests?
│       Check: OTel agent or spring-boot-starter-otel on classpath
│       YES ↓
│
└─► Network proxy or load balancer stripping headers?
        Use Wireshark/tcpdump to verify traceparent arrives at server
```

#### Span Parent–Child Relationships Broken

```text
dbSpan.getParentSpanId() is invalid (all zeros)?
│
├─► Is the parent span current when child is created?
│       tracer.spanBuilder(...).startSpan()  ← NOT automatically a child
│       Use: parentSpan.makeCurrent() BEFORE building the child
│       YES ↓
│
├─► Is makeCurrent() result (Scope) closed before child span is built?
│       scope.close() before spanBuilder call → context is gone
│       YES ↓
│
└─► Is setParent(Context.current()) called explicitly?
        Use: tracer.spanBuilder("child").setParent(Context.current()).startSpan()
```

#### Baggage Not Present in Downstream Spans

```text
Baggage missing after service hop?
│
├─► Is baggage propagator included?
│       OTEL_PROPAGATORS must contain "baggage"
│       YES ↓
│
├─► Is baggage set in the current context (not the parent span context)?
│       Baggage.current().toBuilder()... ← correct pattern
│       YES ↓
│
├─► Does the HTTP framework propagate baggage header?
│       Spring RestClient: yes (via OTel instrumentation)
│       Manual RestTemplate: must add OTel interceptor
│       YES ↓
│
└─► Is baggage value too large? (default limit: 8192 bytes)
        Split into multiple keys or reduce value size
```

#### Virtual Thread Context Loss Diagnosis

```text
Child span in virtual thread has no parent?
│
├─► Was Context.current() captured BEFORE submitting to executor?
│       Context ctx = Context.current();
│       executor.submit(() -> ctx.wrap(task));
│       YES ↓
│
├─► Was ctx.wrap() used (not just passing the task directly)?
│       Direct lambda submission loses context in some OTel versions
│       YES ↓
│
└─► OTel agent version < 2.0?
        Older agents may not support virtual thread context propagation fully.
        Upgrade to opentelemetry-javaagent >= 2.0.0
```

### Diagnostic Tools and Techniques

```bash
#!/usr/bin/env bash
# scripts/diagnose-otel.sh
# Spring lab: auth-service listens on :9180 (default), not :8080

echo "=== 1. Verify OTLP export is reaching collector ==="
curl -s http://localhost:9180/actuator/metrics/otel.exporter.otlp.success || \
  curl -s http://localhost:9180/actuator/metrics | python3 -m json.tool

echo ""
echo "=== 2. Enable OTel SDK debug logging ==="
echo "Add to application.yml:"
echo "  logging.level.io.opentelemetry: DEBUG"

echo ""
echo "=== 3. Verify SDK is not disabled ==="
curl -s http://localhost:9180/actuator/env | \
  python3 -c "import json,sys; d=json.load(sys.stdin); \
    [print(k,v) for k,v in d.get('propertySources',[{}])[0].get('properties',{}).items() \
    if 'otel' in k.lower()]" 2>/dev/null

echo ""
echo "=== 4. Trace propagation headers check ==="
curl -v "http://localhost:9180/api/v1/observability/check" \
  -H "traceparent: 00-$(python3 -c "import os; print(os.urandom(16).hex())")-$(python3 -c "import os; print(os.urandom(8).hex())")-01" \
  2>&1 | grep -i "traceparent\|tracestate\|baggage"
```

---

## 14. CI/CD Integration for Tests

### GitHub Actions Workflow for OTel Tests

```yaml
# .github/workflows/otel-tests.yml
name: OpenTelemetry Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    name: OTel Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Run unit tests
        run: ./mvnw test -pl auth-service,user-profile-service \
          -Dtest="*SpanTest,*TraceTest,*ContextTest,*SamplingTest"

      - name: Publish test results
        uses: mikepenz/action-junit-report@v4
        if: always()
        with:
          report_paths: '**/target/surefire-reports/TEST-*.xml'

  integration-tests:
    name: OTel Integration Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: auth_service
          POSTGRES_USER: dev
          POSTGRES_PASSWORD: dev
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Run integration tests
        run: ./mvnw verify -pl auth-service -Pit \
          -Dspring.cloud.vault.enabled=false \
          -Dspring.datasource.url=jdbc:postgresql://localhost:5432/auth_service
        env:
          OTEL_TRACES_SAMPLER: always_on
          OTEL_SDK_DISABLED: false

      - name: Upload integration test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: integration-test-results
          path: '**/target/failsafe-reports/'

  performance-tests:
    name: Performance Regression Check
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4

      - name: Install k6
        run: |
          curl https://github.com/grafana/k6/releases/download/v0.54.0/k6-v0.54.0-linux-amd64.tar.gz \
            -L | tar xvz --strip-components 1

      - name: Build and start service
        run: |
          ./mvnw -pl auth-service package -DskipTests -q
          java -jar auth-service/target/*.jar \
            --spring.cloud.vault.enabled=false \
            --otel.sdk.disabled=false &
          sleep 15
          curl -f http://localhost:9180/actuator/health

      - name: Run k6 load test with always_on sampling
        run: |
          OTEL_TRACES_SAMPLER=always_on \
          ./k6 run --vus 20 --duration 30s \
            --summary-export /tmp/k6-results.json \
            scripts/perf/load-test.js

      - name: Check p95 threshold
        run: |
          P95=$(python3 -c "import json; \
            d=json.load(open('/tmp/k6-results.json')); \
            print(d['metrics']['http_req_duration']['values']['p(95)'])")
          echo "p95=${P95}ms"
          python3 -c "assert float('${P95}') < 500, f'p95 {P95}ms exceeds 500ms threshold'"
```

### Test Result Reporting

```yaml
# .github/workflows/test-reporting.yml (excerpt)
- name: Generate OTel coverage report
  run: |
    echo "# OTel Instrumentation Coverage" > /tmp/otel-coverage.md
    echo "| Service | Spans Tested | Attributes Tested |" >> /tmp/otel-coverage.md
    echo "|---|---|---|" >> /tmp/otel-coverage.md
    for service in auth-service user-profile-service; do
      span_tests=$(grep -r "@Test" "${service}/src/test" --include="*.java" | \
        grep -i "span\|trace\|otel" | wc -l)
      echo "| ${service} | ${span_tests} | - |" >> /tmp/otel-coverage.md
    done
    cat /tmp/otel-coverage.md

- name: Post coverage summary
  uses: actions/github-script@v7
  if: github.event_name == 'pull_request'
  with:
    script: |
      const fs = require('fs');
      const summary = fs.readFileSync('/tmp/otel-coverage.md', 'utf8');
      github.rest.issues.createComment({
        issue_number: context.issue.number,
        owner: context.repo.owner,
        repo: context.repo.repo,
        body: summary
      });
```

### Quick Reference Checklist – CI/CD

- [ ] Unit tests run on every push (fast, no Docker)
- [ ] Integration tests gated on pull requests (require Docker)
- [ ] Performance tests run on PRs with p95 threshold assertion
- [ ] Test results published to GitHub Actions summary
- [ ] `@Testcontainers(disabledWithoutDocker = true)` prevents failures in restricted envs
- [ ] Fail-fast: OTel unit test failures block merge

---

## 15. Certification Exam Practice Scenarios

### Scenario 1: Identify a Propagation Break in a Multi-Hop Trace

**Problem:** A customer reports that Datadog shows two unrelated traces for a login request that should produce one unified trace across `auth-service → user-profile-service`.

**Investigation steps:**

1. Reproduce the issue (Spring lab: gateway `:9000`, or auth-service directly on `:9180`):
   ```bash
   curl -s http://localhost:9000/api/v1/observability/profile-stats \
     -H "X-Tenant-Id: acme"
   ```
2. Look in Jaeger (`:9668`) for two separate traces instead of one
3. Check whether the outbound HTTP client from `auth-service` injects `traceparent`:
   ```bash
   curl -v http://localhost:9000/api/v1/observability/profile-stats 2>&1 | grep traceparent
   ```

**Root cause:** `RestClient` bean created with `new RestClient()` instead of injecting the auto-configured `RestClient.Builder`, which carries the OTel `ClientHttpRequestInterceptor`.

**Fix:**
```java
// WRONG
RestClient client = RestClient.create();

// CORRECT – Spring auto-configuration adds OTel interceptor
@Configuration
public class UserProfileClientConfig {
    @Bean
    public UserProfileClient userProfileClient(RestClient.Builder builder) {
        RestClient client = builder.baseUrl(serviceUrl).build();
        // ...
    }
}
```

**Verification:** After fix, single trace with both service spans visible in Jaeger.

---

### Scenario 2: Diagnose Missing Spans from a Specific Service

**Problem:** `notification-service` spans are absent from all traces, even though the service is running and receiving Kafka events.

**Decision tree:**

```text
notification-service spans missing?
│
├─► Is OTEL_EXPORTER_OTLP_ENDPOINT set correctly in the deployment?
│       Check: kubectl describe pod notification-service | grep OTEL
│
├─► Is the service on the classpath of OTel auto-instrumentation?
│       Check: java -javaagent:otel-agent.jar -jar notification-service.jar
│
├─► Is the Kafka consumer instrumenting the span on receive?
│       Spring Kafka + OTel Java agent: auto-instrumented from agent v1.14+
│
└─► Are spans being sampled?
        Check: OTEL_TRACES_SAMPLER=always_off drops all spans
```

**Fix:** Add `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable to the Kubernetes deployment and ensure `spring-boot-starter-otel` is in `pom.xml`.

---

### Scenario 3: Optimize Sampling for a Cost Constraint

**Problem:** Datadog ingestion bill exceeds budget. You need to reduce trace volume by 80% without losing visibility on errors.

**Solution:** Use `parentbased_traceidratio` with a 20% ratio plus a custom sampler that always samples error spans:

```java
// Custom head sampler (configures SDK before OTLP export)
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

Sampler sampler = (parentContext, traceId, name, spanKind, attrs, links) -> {
    // Always sample if there is an error attribute (set upstream)
    if (Boolean.TRUE.equals(attrs.get(
            io.opentelemetry.api.common.AttributeKey.booleanKey("error")))) {
        return SamplingResult.recordAndSample();
    }
    // Otherwise apply 20% ratio
    return Sampler.traceIdRatioBased(0.20)
            .shouldSample(parentContext, traceId, name, spanKind, attrs, links);
};
```

**Verification:** After deployment, confirm Datadog ingestion drops ~80% while error traces remain at 100%.

---

### Scenario 4: Fix Context Loss in an Async Boundary

**Problem:** Spans created inside a `CompletableFuture` do not appear as children of the HTTP request span.

**Root cause:** `CompletableFuture.supplyAsync(supplier)` does not propagate OTel context automatically unless the executor is wrapped.

**Fix:**

```java
// WRONG
CompletableFuture.supplyAsync(() -> callDownstreamService());

// CORRECT – wrap runnable in current context
import io.opentelemetry.context.Context;

Context ctx = Context.current();
CompletableFuture.supplyAsync(
    ctx.wrap(() -> callDownstreamService())
);
```

**Verification:** After fix, child spans in `CompletableFuture` share the parent `traceId`.

---

### Scenario 5: Validate Error Handling in a Distributed System

**Problem:** When `user-profile-service` returns HTTP 503, the Datadog trace does not show the error on the `auth-service` client span.

**Expected behaviour per OTel semantic conventions:**
- HTTP client span status: `ERROR`
- `http.response.status_code`: `503`
- `error.type`: `"503"`

**Investigation:**

```bash
# Simulate 503 from user-profile-service (Spring lab: gateway :9000, Jaeger :9668)
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username":"testuser","password":"pass"}'
# Verify spans
curl -s "http://localhost:9668/api/services" | python3 -m json.tool
```

**Root cause:** Auto-instrumentation marks `CLIENT` spans as `ERROR` for 5xx responses. Manually wrapping `WebClientResponseException` also requires calling `span.recordException(e)`.

**Fix:**

```java
try {
    return userProfileClient.getStats();
} catch (org.springframework.web.client.HttpServerErrorException e) {
    Span.current().recordException(e);
    Span.current().setStatus(StatusCode.ERROR, "Downstream " + e.getStatusCode());
    throw e;
}
```

---

### Scenario 6: Baggage Crossing Service Boundaries

**Problem:** The `tenant.id` baggage value is available in `auth-service` but missing in `user-profile-service` span attributes.

**Root cause:** Baggage is propagated automatically as an HTTP header (`baggage: tenant.id=acme`) but the downstream service does not read it from baggage and set it as a span attribute.

**Fix in `user-profile-service`:**

```java
// SpanEnricherFilter.java (update)
String tenantId = Baggage.current().getEntryValue("tenant.id");
if (tenantId == null) {
    tenantId = request.getHeader("X-Tenant-Id");
}
if (tenantId != null) {
    Span.current().setAttribute("tenant.id", tenantId);
}
```

**Verification:** After fix, both services show `tenant.id` as a span tag in Datadog.

---

### Scenario 7: Virtual Thread Context Loss

**Problem:** Spans inside `DefaultObservabilityService.deepTraceCheck()` appear as root spans (no parent) instead of children of the outer HTTP span.

**Root cause:** The `ExecutorService` was not `ContextPropagatingDecorator`-wrapped.

**Fix:**

```java
// WRONG
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// CORRECT
import io.opentelemetry.context.ContextStorage;

private final ExecutorService executor =
    io.opentelemetry.context.propagation.ContextPropagators
        .noop()  // placeholder; actual fix is to use context.wrap() per submission
        .getTextMapPropagator();

// Alternatively, wrap task at submission time:
Context ctx = Context.current();
executor.submit(ctx.wrap(() -> {
    Span asyncSpan = tracer.spanBuilder("async_background_task")
            .setParent(ctx)
            .startSpan();
    // ...
}));
```

---

### Exam Quick-Tip Reference

| Topic | Key fact |
|---|---|
| `traceparent` flags `01` | Sampled; `00` = not sampled |
| `Scope` lifecycle | Must be closed in finally block or try-with-resources |
| `recordException` vs `setStatus` | Both required for full error visibility |
| Baggage vs attributes | Baggage crosses services; attributes stay on the span |
| `parentbased_always_on` | Defers sampling decision to upstream; do not use `always_on` in prod |
| Service map construction | Requires `peer.service` or correct `server.address` on CLIENT spans |
| OTLP HTTP vs gRPC | HTTP port 4318; gRPC port 4317 |

---

## 16. Test Data & Fixtures

### Test User and Tenant Data

```java
// auth-service/src/test/java/tech/sioseforge/auth/fixtures/TestFixtures.java
package tech.sioseforge.auth.fixtures;

import tech.sioseforge.auth.domain.entity.Tenant;
import tech.sioseforge.auth.domain.view.LoginRequestVO;
import tech.sioseforge.auth.domain.view.RegisterRequestVO;

public final class TestFixtures {

    private TestFixtures() {}

    public static Tenant defaultTenant() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setDomain("default.local");
        t.setName("Test Tenant");
        return t;
    }

    public static Tenant acmeTenant() {
        Tenant t = new Tenant();
        t.setId(2L);
        t.setDomain("acme.local");
        t.setName("ACME Corp");
        return t;
    }

    public static RegisterRequestVO registerRequest() {
        return new RegisterRequestVO("sso-test-001", "testuser", "secretpass");
    }

    public static LoginRequestVO loginRequest() {
        return new LoginRequestVO("testuser", "secretpass", "default.local");
    }

    public static LoginRequestVO loginRequest(String domain) {
        return new LoginRequestVO("testuser", "secretpass", domain);
    }
}
```

### Mock Datadog Responses

When testing the Datadog agent integration locally, use WireMock to stub OTLP intake:

```java
// auth-service/src/test/java/tech/sioseforge/auth/fixtures/DatadogAgentStub.java
package tech.sioseforge.auth.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class DatadogAgentStub {

    private final WireMockServer server;

    public DatadogAgentStub() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    public void start() {
        server.start();
        server.stubFor(post(urlEqualTo("/v1/traces"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rate_by_service\":{}}")));
        server.stubFor(post(urlEqualTo("/v1/stats"))
                .willReturn(aResponse().withStatus(200)));
    }

    public void stop() {
        server.stop();
    }

    public int getPort() {
        return server.port();
    }

    public String getUrl() {
        return "http://localhost:" + server.port();
    }
}
```

### Fixture Management

```java
// auth-service/src/test/java/tech/sioseforge/auth/fixtures/OtelFixtures.java
package tech.sioseforge.auth.fixtures;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

public final class OtelFixtures {

    private OtelFixtures() {}

    public static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    public static final String VALID_SPAN_ID  = "00f067aa0ba902b7";
    public static final String SAMPLED_TRACEPARENT =
            "00-" + VALID_TRACE_ID + "-" + VALID_SPAN_ID + "-01";

    public static SpanContext sampledContext() {
        return SpanContext.create(
                VALID_TRACE_ID,
                VALID_SPAN_ID,
                TraceFlags.getSampled(),
                TraceState.getDefault());
    }

    public static SpanContext unsampledContext() {
        return SpanContext.create(
                VALID_TRACE_ID,
                VALID_SPAN_ID,
                TraceFlags.getDefault(),   // sampled bit = 0
                TraceState.getDefault());
    }
}
```

### Cleanup Procedures

```java
// auth-service/src/test/java/tech/sioseforge/auth/OtelTestLifecycle.java
package tech.sioseforge.auth;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for integration tests that use InMemorySpanExporter.
 * Ensures a clean exporter state before each test.
 */
public abstract class OtelTestLifecycle {

    @Autowired
    protected InMemorySpanExporter spanExporter;

    @BeforeEach
    void resetExporter() {
        spanExporter.reset();
    }

    @AfterEach
    void verifyNoUnfinishedSpans() {
        // Optional: assert no leaked in-flight spans after test
        // spanExporter.getFinishedSpanItems().forEach(s -> assertThat(s.hasEnded()).isTrue());
    }
}
```

---

## 17. Metrics for Testing Success

### Trace Coverage Percentage

Define coverage as: **tested service endpoints with at least one span assertion / total instrumented endpoints**.

```bash
#!/usr/bin/env bash
# scripts/metrics/trace-coverage.sh

TOTAL_ENDPOINTS=$(grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@RequestMapping" \
  auth-service/src/main/java user-profile-service/src/main/java \
  --include="*.java" | wc -l)

TESTED_ENDPOINTS=$(grep -r "anyMatch.*POST\|anyMatch.*GET\|assertThat.*spans" \
  auth-service/src/test user-profile-service/src/test \
  --include="*.java" | wc -l)

COVERAGE=$(python3 -c "print(f'{int(${TESTED_ENDPOINTS}) / int(${TOTAL_ENDPOINTS}) * 100:.1f}%')" 2>/dev/null || echo "N/A")
echo "Trace coverage: ${TESTED_ENDPOINTS} / ${TOTAL_ENDPOINTS} endpoints (${COVERAGE})"
```

### Key Metrics Dashboard

| Metric | Target | Measurement method |
|---|---|---|
| Trace coverage | > 80% of endpoints | count of OTel test assertions / total endpoints |
| Error rate in traces | < 1% in steady state | `sum:trace.http.request.errors / hits` |
| Span duration baseline p95 | < 500 ms for login | k6 or Datadog APM metric |
| Baggage propagation success | 100% | Assert `tenant.id` present in downstream spans |
| Sampling effectiveness | ~10% in prod | `trace.http.request.hits` in Datadog |
| Context propagation success | 100% | No orphan root spans from known service calls |
| Export success rate | > 99% | `otel.exporter.otlp.success` actuator metric |

### Span Duration Baselines

Record these after each major code change:

```bash
#!/usr/bin/env bash
# scripts/metrics/record-baselines.sh

DATE=$(date +%Y-%m-%d)
OUTPUT="docs/perf/baselines-${DATE}.csv"

mkdir -p docs/perf
echo "endpoint,p50_ms,p95_ms,p99_ms,sampler" > "${OUTPUT}"

for endpoint in "auth/login" "observability/check" "observability/deep-check"; do
  RESULT=$(k6 run --vus 20 --duration 30s \
    --env ENDPOINT="${endpoint}" \
    --summary-export /tmp/k6-temp.json \
    scripts/perf/endpoint-test.js 2>/dev/null)

  P50=$(python3 -c "import json; d=json.load(open('/tmp/k6-temp.json')); print(d['metrics']['http_req_duration']['values']['med'])" 2>/dev/null || echo "0")
  P95=$(python3 -c "import json; d=json.load(open('/tmp/k6-temp.json')); print(d['metrics']['http_req_duration']['values']['p(95)'])" 2>/dev/null || echo "0")
  P99=$(python3 -c "import json; d=json.load(open('/tmp/k6-temp.json')); print(d['metrics']['http_req_duration']['values']['p(99)'])" 2>/dev/null || echo "0")

  echo "${endpoint},${P50},${P95},${P99},always_on" >> "${OUTPUT}"
done

echo "Baselines written to ${OUTPUT}"
cat "${OUTPUT}"
```

### Baggage Propagation Success Rate

```bash
#!/usr/bin/env bash
# scripts/metrics/baggage-success-rate.sh
# Spring lab: user-profile-service listens on :9082

TOTAL_REQUESTS=100
SUCCESS=0

for i in $(seq 1 ${TOTAL_REQUESTS}); do
  TENANT_IN_SPAN=$(curl -s "http://localhost:9082/api/v1/profiles/stats" \
    -H "baggage: tenant.id=testcorp" | python3 -c \
    "import json,sys; d=json.load(sys.stdin); print(d.get('tenantId',''))" 2>/dev/null)

  if [ "${TENANT_IN_SPAN}" = "testcorp" ]; then
    ((SUCCESS++))
  fi
done

RATE=$(python3 -c "print(f'{${SUCCESS} / ${TOTAL_REQUESTS} * 100:.1f}%')")
echo "Baggage propagation success rate: ${RATE} (${SUCCESS}/${TOTAL_REQUESTS})"
```

### Final Testing Checklist

Use this before pushing to production or taking a certification exam:

**Unit test layer**
- [ ] All manual span builders covered by `OpenTelemetryExtension` unit tests
- [ ] Error paths (`recordException` + `setStatus`) tested
- [ ] Baggage injection and extraction tested in isolation

**Integration test layer**
- [ ] HTTP server span name and status validated per endpoint
- [ ] Parent–child span hierarchy verified
- [ ] `tenant.id` attribute present on instrumented spans

**End-to-end layer**
- [ ] Distributed trace visible as single trace in Jaeger/Datadog
- [ ] `traceparent` header injected on outbound calls
- [ ] Virtual thread async spans linked to parent

**Performance layer**
- [ ] p95 latency overhead with `always_on` < 5% vs baseline
- [ ] Sampling ratio test confirms correct drop rate

**Datadog layer**
- [ ] Traces appear in APM within 30 seconds
- [ ] Service Map shows correct topology
- [ ] Error spans display as red in flame graph

---

## Related Documents

- [OpenTelemetry Fundamentals](./OPENTELEMETRY_FUNDAMENTALS.md)
- [Datadog Integration](./DATADOG_INTEGRATION.md)
- [Spring Boot vs Quarkus OTel Comparison](./SPRING_vs_QUARKUS_OTEL.md)
- [Observability Lessons](./OBSERVABILITY_LESSONS.md) — architecture, diagrams, annotated screenshots
- [Local Observability Roadmap](./LOCAL_OBSERVABILITY_ROADMAP.md) — port map, phases, K8s notes

## Reference Links

- [OTel Java SDK testing utilities](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/testing)
- [OTel Semantic Conventions – HTTP](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)
- [OTel Context Propagation](https://opentelemetry.io/docs/concepts/context-propagation/)
- [Testcontainers Spring Boot](https://java.testcontainers.org/frameworks/spring_boot/)
- [Datadog APM Ingestion Controls](https://docs.datadoghq.com/tracing/trace_pipeline/ingestion_controls/)
- [k6 Load Testing](https://k6.io/docs/)
- [Quarkus OTel Extension](https://quarkus.io/guides/opentelemetry)
