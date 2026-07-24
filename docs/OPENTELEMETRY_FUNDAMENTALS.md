# OpenTelemetry Fundamentals for Spring Datadog Lab

This guide is designed for **OpenTelemetry Associate Certification** preparation, using concrete examples from this repository:

- `auth-service` (entry service, manual spans, Vault endpoints)
- `user-profile-service` (downstream HTTP service)
- Spring Boot 4.1 + Java 25 virtual threads
- OTLP export path to Datadog

---

## 1. Introduction

### What is OpenTelemetry?
OpenTelemetry (OTel) is the CNCF observability standard for generating, collecting, and exporting telemetry data: **traces, metrics, and logs**. It provides vendor-neutral APIs, SDKs, semantic conventions, and protocols (OTLP).

### Why OTel for observability?
- Standardized instrumentation across frameworks/languages
- Portability between backends (Datadog, Prometheus, Jaeger, etc.)
- Shared data model and semantic conventions
- Better long-term maintainability than vendor-specific instrumentation

### OTel vs traditional APM (Datadog, New Relic, etc.)
- **OTel** = open standard + instrumentation model + protocol
- **APM vendors** = analytics, storage, alerting, UX, correlation features
- In practice: use OTel to emit telemetry, then send to Datadog for operations and analysis.

### Three pillars: Traces, Metrics, Logs
- **Traces**: request flow and latency across services
- **Metrics**: time-series signals (throughput, errors, resource usage)
- **Logs**: detailed event context and diagnostics

OTel enables correlation across these signals via shared resource attributes and trace context.

---

## 2. Core Concepts

### Traces & Spans
A **trace** is a tree/DAG of **spans** representing a distributed operation.

```text
Trace (trace_id=abc...)
└── auth-service: HTTP GET /api/v1/observability/profile-stats
    ├── UserLoginProcess (manual)
    │   ├── db_query.verify_credentials
    │   └── publish_kafka_event
    └── HTTP client call -> user-profile-service /api/v1/profiles/stats
```

- Span lifecycle: `start -> set attributes/events/status -> end`
- Span attributes: searchable key/value metadata (`tenant.id`, `http.method`, etc.)
- Span events: timestamped annotations (`"Token generated"`)
- Span status: `UNSET`, `OK`, `ERROR` with error details

Project examples:
- Manual span creation in [`DefaultAuthService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultAuthService.java)
- Manual spans + virtual-thread scenario in [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java)

### Context Propagation
Context carries active trace/span and baggage across boundaries.

- **TraceContext**: `traceparent`, `tracestate`
- **Baggage**: user-defined cross-service key/value context
- Propagation is typically automatic for instrumented HTTP clients/servers.

```text
Client -> HTTP headers -> Server
traceparent: 00-<trace-id>-<parent-span-id>-01
baggage: tenant.id=acme,correlation.id=...
```

In this lab:
- `auth-service` calls `user-profile-service` via declarative client
- tenant-related attributes are enriched via filters:
  - [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java)
  - [`SpanEnricherFilter`](../user-profile-service/src/main/java/tech/sioseforge/profile/resource/filter/SpanEnricherFilter.java)

### Instrumentation
1. **Automatic instrumentation** (framework/library hooks)
   - HTTP server/client, JDBC, servlet, etc.
2. **Manual instrumentation**
   - Business spans, domain attributes, custom events
3. **Instrumentation libraries**
   - Language/framework-specific bridges and SDK integrations

Use guidance:
- Start with auto instrumentation for breadth.
- Add manual spans only where domain value is high.
- Avoid over-instrumentation noise.

### Exporters & Collectors
- **OTLP (OpenTelemetry Protocol)** is the default interoperable export format.
- Exporters send telemetry directly to backend or collector.
- Collectors provide batching, retries, filtering, routing, enrichment.

In this project:
- OTLP HTTP/protobuf endpoint is configured in service `application.yml` files.
- Example: [`auth-service/application.yml`](../auth-service/src/main/resources/application.yml)

```yaml
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4318/v1/traces
      protocol: http/protobuf
  traces:
    sampler: always_on
```

**This lab's host mapping:** the OTel Collector's container port `4318` is published on host port **`9318`** (and gRPC `4317` on **`9317`**) so it can run beside FleetForge. Locally, apps/tools outside Docker should target `http://localhost:9318/v1/traces` — the `4318` port above is the *container-internal* address used inside `docker-compose.yml` / Kubernetes service DNS. The collector then dual-exports every trace to **Jaeger** (local) and **Datadog us5** (SaaS); see [`DATADOG_INTEGRATION.md`](DATADOG_INTEGRATION.md) and [`OBSERVABILITY_LESSONS.md`](OBSERVABILITY_LESSONS.md) for the full pipeline.

OTLP vs other formats:
- OTLP is OTel-native and rich in semantic fidelity.
- Legacy/vendor formats may lose context or require translation.

---

## 3. Semantic Conventions

Semantic conventions standardize attribute names so data is consistent and queryable.

- HTTP: `http.request.method`, `url.path`, `http.response.status_code`
- DB: `db.system`, `db.operation.name`, query metadata
- gRPC: `rpc.system=grpc`, service/method naming
- Messaging: `messaging.system`, destination, operation

How Spring implements conventions here:
- Spring MVC server spans and RestClient spans follow OTel HTTP semantic conventions automatically.
- JPA/JDBC instrumentation emits DB semantic attributes when DB calls occur.
- Manual spans in `DefaultAuthService` can be enriched with domain attributes (`user.id`, tenant attributes).

---

## 4. Implementation in Spring Boot

### Spring Boot auto-configuration
Core observability dependencies are present in service modules:
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `spring-boot-starter-actuator`

See:
- [`auth-service/pom.xml`](../auth-service/pom.xml)
- [`user-profile-service/pom.xml`](../user-profile-service/pom.xml)

### Actuator integration
Actuator provides operational endpoints and metric exposure integration points for observability workflows.

### RestClient instrumentation
`@HttpExchange` + `RestClient` declarative client:
- [`UserProfileClient`](../auth-service/src/main/java/tech/sioseforge/auth/client/UserProfileClient.java)
- [`UserProfileClientConfig`](../auth-service/src/main/java/tech/sioseforge/auth/config/UserProfileClientConfig.java)

This supports service-to-service tracing with propagated context.

### Database/JPA instrumentation
`auth-service` uses JPA + PostgreSQL + Flyway:
- [`auth-service/pom.xml`](../auth-service/pom.xml)
- [`auth-service/src/main/resources/application.yml`](../auth-service/src/main/resources/application.yml)

### Custom spans and attributes
Manual span pattern used in `DefaultAuthService`:

```java
Span loginSpan = tracer.spanBuilder("UserLoginProcess").startSpan();
try (var scope = loginSpan.makeCurrent()) {
    Span dbSpan = tracer.spanBuilder("db_query.verify_credentials").startSpan();
    try {
        dbSpan.setAttribute("user.id", request.username());
        dbSpan.addEvent("Credentials verified successfully");
    } finally {
        dbSpan.end();
    }
} finally {
    loginSpan.end();
}
```

### Error handling with spans
On exceptions, record errors and set status:

```java
try {
    // business logic
} catch (Exception ex) {
    Span.current().recordException(ex);
    Span.current().setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, ex.getMessage());
    throw ex;
}
```

---

## 5. Practical Examples from This Project

### auth-service example traces
- Endpoints:
  - [`ObservabilityController`](../auth-service/src/main/java/tech/sioseforge/auth/resource/ObservabilityController.java)
  - `/api/v1/observability/trace-check`
  - `/api/v1/observability/trace-deep`
  - `/api/v1/observability/profile-stats`
- Service logic:
  - [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java)
  - [`DefaultAuthService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultAuthService.java)

### user-profile-service communication
- HTTP client from auth-service:
  - [`UserProfileClient`](../auth-service/src/main/java/tech/sioseforge/auth/client/UserProfileClient.java)
- Downstream endpoint:
  - [`ProfileController`](../user-profile-service/src/main/java/tech/sioseforge/profile/controller/ProfileController.java)

### Vault integration tracing
- Vault admin endpoint:
  - [`VaultAdminController`](../auth-service/src/main/java/tech/sioseforge/auth/resource/VaultAdminController.java)
- Service with retry semantics:
  - [`DefaultVaultSecretService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultVaultSecretService.java)

Tip: Wrap Vault read/write in manual spans and add attributes (`vault.path`, `retry.count`, result status) for clear diagnosis.

### Virtual threads impact on tracing
- Virtual thread executor usage in:
  - [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java)
- `spring.threads.virtual.enabled=true` in service configs enables virtual-thread model.

Key exam point: virtual threads change scheduling model, but trace context still depends on correct context propagation across async boundaries.

---

## 6. Distributed Tracing Deep Dive

### Service-to-service communication

```text
[Client]
   |
   v
auth-service (/profile-stats) --RestClient--> user-profile-service (/stats)
   |                                              |
   +-- parent span ------------------------------>+-- child/server span
```

### Trace context propagation between services
- Parent span created in `auth-service`
- RestClient propagates context headers
- `user-profile-service` continues same trace with new child span

### Correlation IDs and baggage
- Keep business correlation in baggage (small, stable fields)
- Keep high-cardinality data out of metric tags
- Tenant header (`X-Tenant-Id`) is used for span enrichment in this lab

### Sampling strategies
- `always_on`: great for labs and debugging
- `traceidratio`: production cost/performance balance
- parent-based sampling: preserve upstream sampling decision

### Trace sampling in Spring
Current lab defaults:

```yaml
otel:
  traces:
    sampler: always_on
```

For production exam scenarios, discuss why ratio/parent-based strategies are preferred.

---

## 7. Exam Preparation

### Key concepts checklist
- [ ] Explain trace/span model and parent-child relationships
- [ ] Identify automatic vs manual instrumentation tradeoffs
- [ ] Understand W3C TraceContext (`traceparent`, `tracestate`)
- [ ] Explain baggage use cases and risks
- [ ] Describe OTLP, exporters, and collector roles
- [ ] Apply semantic conventions for HTTP/DB/messaging
- [ ] Reason about sampling impact and data volume
- [ ] Diagnose propagation failures and missing spans

### Common exam question patterns
1. **Propagation break**: Which hop dropped context?
2. **Wrong attributes**: Which semantic key should be used?
3. **Sampling behavior**: Why does trace appear partially?
4. **Collector/exporter architecture**: Where should batching/retry happen?
5. **Manual instrumentation**: Where to add business spans safely?

### Practice scenarios
1. Trigger `/api/v1/observability/profile-stats` and validate one distributed trace across both services.
2. Send `X-Tenant-Id` header and verify `tenant.id` on spans.
3. Trigger Vault read retry path and inspect error/fallback behavior.
4. Compare `trace-check` vs `trace-deep` to understand async/virtual-thread behavior.

### Best practices summary
- Prefer auto instrumentation first; add manual spans surgically.
- Use semantic conventions consistently.
- Set span status and record exceptions for real errors.
- Keep attributes low-cardinality and meaningful.
- Use collectors in production for reliability and control.

---

## 8. Troubleshooting & Common Issues

### Traces not appearing in Datadog
Checklist:
1. Verify OTLP endpoint/protocol config in `application.yml`
2. Confirm backend/agent is reachable from service runtime
3. Check sampling setting (`always_on` for debugging)
4. Confirm service name/resource attributes are present
5. Inspect exporter/connection logs

### Context propagation failures
Symptoms: broken trace trees, new root spans in downstream services.

Checks:
- Ensure instrumented client/server stack is used (RestClient/Spring MVC)
- Validate no custom thread handoff loses context
- Validate proxies/gateways preserve `traceparent` headers

### Memory overhead with tracing
- Large attribute values and high-cardinality tags increase memory and backend cost.
- Unbounded baggage can become expensive.
- Keep span/event payloads concise.

### Performance impact of instrumentation
- Always-on sampling in high throughput systems can be expensive.
- Manual spans inside hot loops add overhead.
- Prefer collector batching and tuned sampling strategies for production.

---

## Official OpenTelemetry References

- OTel Docs Home: <https://opentelemetry.io/docs/>
- OTel Specification: <https://opentelemetry.io/docs/specs/otel/>
- Context Propagation: <https://opentelemetry.io/docs/concepts/context-propagation/>
- Traces: <https://opentelemetry.io/docs/concepts/signals/traces/>
- Semantic Conventions: <https://opentelemetry.io/docs/specs/semconv/>
- OTLP Protocol: <https://opentelemetry.io/docs/specs/otlp/>
- Java Instrumentation: <https://opentelemetry.io/docs/languages/java/>

## Project Cross-References

- Root overview: [`README.md`](../README.md)
- Architecture notes: [`VAULT_AUTH_ARCHITECTURE.md`](../VAULT_AUTH_ARCHITECTURE.md)
- Migration roadmap: [`IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md)
- Observability lessons (architecture, diagrams, annotated screenshots): [`OBSERVABILITY_LESSONS.md`](OBSERVABILITY_LESSONS.md)
- Screenshot walkthrough / checklist: [`OBSERVABILITY_WALKTHROUGH.md`](OBSERVABILITY_WALKTHROUGH.md)
- Local port map, phases, K8s notes: [`LOCAL_OBSERVABILITY_ROADMAP.md`](LOCAL_OBSERVABILITY_ROADMAP.md)
- Datadog APM deep dive (Phase 2): [`DATADOG_INTEGRATION.md`](DATADOG_INTEGRATION.md)
