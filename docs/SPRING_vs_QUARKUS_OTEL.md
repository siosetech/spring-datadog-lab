# Spring Boot vs Quarkus for OpenTelemetry

> **Certification Series** | Phase 3: Spring Boot vs Quarkus OTel Comparison  
> **Targets**: OpenTelemetry certification prep + Datadog APM certification prep
> **Audience**: Experienced Java engineers who want framework-specific observability trade-offs, not generic marketing

---

## Table of Contents

1. [Framework Overview](#1-framework-overview)
2. [Dependency & Configuration Management](#2-dependency--configuration-management)
3. [OpenTelemetry Instrumentation Differences](#3-opentelemetry-instrumentation-differences)
4. [Concurrency & Thread Models](#4-concurrency--thread-models)
5. [Performance Characteristics](#5-performance-characteristics)
6. [Trace Context Propagation](#6-trace-context-propagation)
7. [Custom Instrumentation Patterns](#7-custom-instrumentation-patterns)
8. [Testing & Validation](#8-testing--validation)
9. [Vault Integration & Secrets Management](#9-vault-integration--secrets-management)
10. [Deployment & Observability](#10-deployment--observability)
11. [Debugging & Troubleshooting](#11-debugging--troubleshooting)
12. [Best Practices & Trade-offs](#12-best-practices--trade-offs)
13. [Practical Comparison Table](#13-practical-comparison-table)
14. [Hands-On Lab Comparison](#14-hands-on-lab-comparison)

---

## Scope and Repositories

This guide compares:

- **Spring lab**: [`spring-datadog-lab`](../README.md)
- **Quarkus lab**: [`siosetech/quarkus-datadog-lab`](https://github.com/siosetech/quarkus-datadog-lab)

Concrete file references used throughout:

- Spring root build: [`pom.xml`](../pom.xml)
- Spring auth config: [`auth-service/src/main/resources/application.yml`](../auth-service/src/main/resources/application.yml)
- Spring virtual-thread trace example: [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java)
- Spring declarative HTTP client: [`UserProfileClientConfig`](../auth-service/src/main/java/tech/sioseforge/auth/config/UserProfileClientConfig.java)
- Spring span enrichment filters: [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java), [`SpanEnricherFilter`](../user-profile-service/src/main/java/tech/sioseforge/profile/resource/filter/SpanEnricherFilter.java)
- Quarkus root build: [`quarkus-datadog-lab/pom.xml`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/pom.xml)
- Quarkus auth config: [`auth-service/src/main/resources/application.properties`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/resources/application.properties)
- Quarkus reactive auth flow: [`DefaultAuthService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultAuthService.java)
- Quarkus Vault tracing: [`DefaultVaultSecretService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultVaultSecretService.java)
- Quarkus REST client: [`UserProfileClient`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/repository/UserProfileClient.java)

---

## 1. Framework Overview

### Spring Boot 4.1

In this repository, Spring is positioned as the **imperative, enterprise-default** option:

- Spring Boot **4.1.0** / Spring Framework **7.0.x** in [`pom.xml`](../pom.xml)
- Java **25** configured at the build level
- Virtual threads enabled with `spring.threads.virtual.enabled=true`
- Strong auto-configuration model
- Familiar MVC + JPA + Actuator + Micrometer stack

This is a good fit when you want:

- Conventional thread-per-request programming
- Fast onboarding for large teams
- Broad ecosystem coverage
- Easy insertion of OTel into existing Spring estates

### Quarkus 3.x

In the companion repository, Quarkus is positioned as the **cloud-native, ahead-of-time optimized** option:

- Quarkus **3.37.2** in the root [`pom.xml`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/pom.xml)
- Java **21**
- Reactive first-class APIs via Vert.x/Mutiny
- Native image path via GraalVM/Mandrel
- Fast-jar packaging for JVM deployments

This is a good fit when you want:

- Lower startup time and smaller memory footprint
- Native-image deployment
- High concurrency with non-blocking I/O
- Reactive pipelines with explicit async boundaries

### Architectural differences

| Topic | Spring Boot 4.1 | Quarkus 3.x |
|---|---|---|
| Core runtime style | Imperative by default | Reactive-friendly, cloud-native runtime |
| Startup philosophy | Rich runtime auto-configuration | Build-time augmentation + AOT-friendly model |
| Concurrency default | Virtual-thread-friendly request handling | Event-loop + worker/reactive model |
| OTel ergonomics | Micrometer/Observation centric | Native OTel extension centric |
| Persistence style in these labs | Blocking JPA/JDBC | Hibernate Reactive + reactive PG client |
| Native image story | Possible, but not the center of this repo | First-class design goal |

### High-level mental model

```text
Spring Boot 4.1
HTTP request -> Servlet/MVC -> service -> JPA/JDBC -> OTLP export
                ^ virtual threads preserve imperative style

Quarkus 3.x
HTTP request -> JAX-RS/RESTEasy Reactive -> Uni/Mutiny chain -> reactive DB/client -> OTLP export
                ^ build-time optimized, explicit async boundaries
```

For certification prep, the key insight is this:

- **Spring** asks: "How do I preserve observability while keeping imperative code simple?"
- **Quarkus** asks: "How do I preserve observability across reactive boundaries and deployment modes?"

---

## 2. Dependency & Configuration Management

### Spring dependencies

From [`auth-service/pom.xml`](../auth-service/pom.xml):

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-actuator`
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `spring-cloud-starter-vault-config`
- `spring-boot-starter-test`
- `spring-boot-testcontainers`

Spring's observability model in this repo is a mix of:

1. Spring Boot infrastructure
2. Micrometer tracing bridge
3. Explicit `Tracer` injection for manual business spans

### Quarkus extensions

From [`quarkus-datadog-lab/auth-service/pom.xml`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/pom.xml):

- `quarkus-rest`
- `quarkus-rest-client-jackson`
- `quarkus-opentelemetry`
- `quarkus-smallrye-context-propagation`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-vault`
- `quarkus-junit5`

Quarkus packages observability as extensions rather than starters. In practice this feels more explicit and usually more predictable at build time.

### Configuration format: YAML vs properties

| Concern | Spring | Quarkus |
|---|---|---|
| Primary format in these repos | `application.yml` | `application.properties` |
| Profile syntax | `application-dev.yml` | `%dev.`, `%test.`, `%prod.` prefixes |
| OTel endpoint style | `otel.exporter.otlp.endpoint` over YAML | `quarkus.otel.exporter.otlp.endpoint` |
| Vault config style | nested YAML tree | flat namespaced properties |

Spring example from [`auth-service/application.yml`](../auth-service/src/main/resources/application.yml):

```yaml
spring:
  threads:
    virtual:
      enabled: true

otel:
  exporter:
    otlp:
      endpoint: http://localhost:4318/v1/traces
      protocol: http/protobuf
```

Quarkus example from [`quarkus-datadog-lab/auth-service/application.properties`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/resources/application.properties):

```properties
quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
quarkus.otel.exporter.otlp.protocol=grpc
quarkus.otel.logs.enabled=true
%prod.quarkus.log.console.json.enabled=true
```

### Build modes

#### Spring

The Spring repo currently uses **Spring Boot Buildpacks (Paketo)** image generation in the root [`pom.xml`](../pom.xml), not a handwritten Dockerfile:

- build JVM applications
- containerize from Maven
- use layered OCI images through Spring Boot Buildpacks

#### Quarkus

Quarkus gives you distinct deployment choices:

- **JVM / fast-jar**: default `target/quarkus-app/` layout
- **native**: compile to a native executable
- **uber-jar**: optional single-jar distribution

The current Quarkus JVM container path is documented in [`auth-service/src/main/docker/Dockerfile.jvm`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/docker/Dockerfile.jvm).

### Environment-specific settings

Spring dev profile example:

- [`auth-service/src/main/resources/application-dev.yml`](../auth-service/src/main/resources/application-dev.yml)
- imports Vault with `spring.config.import: vault://`

Quarkus profile example:

- [`quarkus-datadog-lab/auth-service/application.properties`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/resources/application.properties)
- uses `%dev`, `%test`, `%prod` overrides for logging, Vault, and OTel

**Exam takeaway:** Spring profiles are file-oriented and hierarchical; Quarkus profiles are property-oriented and compact.

---

## 3. OpenTelemetry Instrumentation Differences

### Spring Boot: Micrometer-first, manual spans where needed

Spring Boot 4.1 emphasizes Observations/Micrometer, but this repo also uses direct `Tracer` access:

- manual span creation in [`DefaultAuthService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultAuthService.java)
- manual trace checks in [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java)
- active span enrichment in [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java)

This gives you two valid Spring patterns:

1. **Auto instrumentation** for HTTP, servlet, JDBC, Kafka, etc.
2. **Manual spans** for business steps and certification exercises

### Quarkus: extension-native OTel

Quarkus exposes OTel as a built-in extension:

- `quarkus-opentelemetry`
- `Tracer` injection
- optional `@WithSpan`
- reactive-aware context propagation via SmallRye context propagation

Examples:

- [`ObservabilityResource`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/ObservabilityResource.java) uses `@WithSpan`
- [`DefaultAuthService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultAuthService.java) uses explicit child spans with `setParent(parentContext)`

### Automatic vs manual spans

| Area | Spring | Quarkus |
|---|---|---|
| HTTP server spans | automatic in normal MVC flow | automatic via Quarkus OTel extension |
| HTTP client spans | automatic when client is instrumented | automatic for supported clients |
| Business spans | `Tracer.spanBuilder(...)` | `Tracer.spanBuilder(...)` or `@WithSpan` |
| Async/reactive parenting | often hidden unless you create threads manually | often explicit in `Uni` pipelines |

### Servlet vs reactive filters

Spring uses servlet filters:

- [`TenantMdcFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantMdcFilter.java)
- [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java)

Quarkus uses JAX-RS request filters:

- [`TenantMdcFilter`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantMdcFilter.java)
- [`TenantSpanEnricher`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricher.java)

Key difference:

- **Servlet** filters are comfortable for imperative request chains.
- **Reactive/JAX-RS** filters are lighter, but you must think harder about downstream async stages.

### REST client comparison

#### Spring: `RestClient` / `@HttpExchange`

- interface: [`UserProfileClient`](../auth-service/src/main/java/tech/sioseforge/auth/client/UserProfileClient.java)
- wiring: [`UserProfileClientConfig`](../auth-service/src/main/java/tech/sioseforge/auth/config/UserProfileClientConfig.java)

```java
@HttpExchange("/api/v1/profiles")
public interface UserProfileClient {
    @GetExchange("/stats")
    Map<String, Object> getProfileStats();
}
```

`RestTemplate` still exists in the Spring ecosystem, but for Boot 4.1 and new work, `RestClient` is the better comparison point.

#### Quarkus: MicroProfile REST Client

- interface: [`UserProfileClient`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/repository/UserProfileClient.java)

```java
@RegisterRestClient(configKey = "user-profile-api")
@ClientHeaderParam(name = "X-Tenant-Id", value = "{lookupTenantId}")
public interface UserProfileClient {
    Uni<UserProfileVO> getProfile(Long userId);
}
```

The Quarkus version is more naturally aligned with reactive downstream composition because it returns `Uni<T>`.

### JPA/JDBC instrumentation

#### Spring

This repo uses blocking persistence:

- `spring-boot-starter-data-jpa`
- PostgreSQL JDBC
- Flyway

That means span generation tends to be:

- servlet/server span
- JDBC span(s)
- optional business spans

#### Quarkus

The Quarkus repo mixes:

- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-jdbc-postgresql`
- `quarkus-flyway`

That creates an important certification distinction:

- blocking JDBC instrumentation is conceptually simpler
- reactive database instrumentation has different async boundaries and timing behavior

**Exam takeaway:** If you see "missing child span" in Spring, suspect thread handoff. In Quarkus, also suspect reactive stage boundaries and explicit parent context handling.

---

## 4. Concurrency & Thread Models

### Spring: virtual threads

This repo intentionally enables virtual threads in service config:

- [`auth-service/application.yml`](../auth-service/src/main/resources/application.yml)
- [`user-profile-service/application.yml`](../user-profile-service/src/main/resources/application.yml)

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

#### What changes for tracing?

Virtual threads do **not** remove the need for context propagation. They simplify concurrency, but OTel still depends on the active `Context`.

The best demonstration is [`DefaultObservabilityService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultObservabilityService.java):

```java
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
Future<?> future = executor.submit(() -> {
    Span asyncSpan = tracer.spanBuilder("async_background_task").startSpan();
    ...
});
```

This is useful for certification prep because it highlights a subtlety:

- the parent span is active on the request thread
- a manually created virtual thread may not automatically inherit that OTel context
- the new span can become a sibling/root instead of a proper child unless context is propagated

#### Platform threads vs virtual threads

| Topic | Platform threads | Virtual threads |
|---|---|---|
| Cost per thread | relatively expensive | much cheaper |
| Programming style | imperative | imperative |
| Trace model | straightforward | straightforward if context is propagated |
| Failure mode | thread pool saturation | context loss in custom executors if you are careless |

### Quarkus: reactive/async model

Quarkus auth flow in [`DefaultAuthService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultAuthService.java) explicitly captures parent context:

```java
Context parentContext = Context.current();

return validateRequest(request, parentContext)
        .chain(() -> findUser(request, parentContext))
        .chain(user -> fetchProfile(user, parentContext));
```

That is the core reactive observability discipline:

- capture parent context once
- pass it into async stages
- create child spans with explicit parenting

### Non-blocking I/O and trace preservation

Reactive systems can preserve trace context very well, but only if instrumentation integrates with the reactive engine correctly. Quarkus helps with this through:

- Vert.x integration
- Mutiny-aware pipelines
- SmallRye context propagation

### Coroutines vs virtual threads

For Java exam prep, treat this as a conceptual comparison:

- **virtual threads**: JVM scheduling primitive, keeps imperative code readable
- **coroutines**: language/runtime abstraction, more relevant in Kotlin than in Java-first Quarkus code
- **Mutiny/Uni**: the actual async abstraction used by this Quarkus lab

### When to use what in Quarkus

- use **imperative/virtual-thread style** when code is mostly blocking and readability matters most
- use **reactive** when you need high concurrency, streaming, backpressure, or fully non-blocking clients

**Exam takeaway:** Spring reduces conceptual complexity for tracing in imperative flows; Quarkus rewards you with efficiency, but asks for more context-propagation awareness.

---

## 5. Performance Characteristics

### What usually wins?

| Metric | Likely winner | Why |
|---|---|---|
| Cold startup | Quarkus native | AOT/native executable |
| Memory footprint | Quarkus native | smaller runtime surface |
| Simplicity of blocking I/O | Spring virtual threads | imperative model without reactive rewrite |
| Peak efficiency at very high concurrency | Quarkus reactive | event-loop + non-blocking design |
| Team familiarity / change velocity | Spring | ecosystem and cognitive fit |

### Startup time comparison

- **Spring Boot JVM**: usually slower than Quarkus fast-jar/native, especially with large auto-configured application graphs
- **Quarkus fast-jar**: typically faster than classic JVM fat-jar startup
- **Quarkus native**: fastest cold-start path, especially for serverless or rapid autoscaling

### Memory footprint

- Spring JVM + JPA + Actuator + Kafka typically consumes more resident memory
- Quarkus native typically consumes the least
- Quarkus JVM fast-jar usually lands between native and Spring JVM

### Request latency and throughput

For these labs, the practical rule is:

- **Spring virtual threads** are strong for blocking DB + HTTP workloads
- **Quarkus reactive** is strong when a large part of the pipeline is already non-blocking

If your downstreams are blocking anyway, virtual threads often provide a better cost/complexity ratio than a full reactive rewrite.

### Tracing overhead

Both frameworks pay overhead for:

- span creation
- context propagation
- exporter batching
- serialization over OTLP

The operational question is not "does tracing cost anything?" but "where should I sample?"

| Scenario | Recommended strategy |
|---|---|
| dev/lab | `always_on` or very high sampling |
| pre-prod load test | parent-based + moderate probability |
| high-volume prod | tail/head sampling via collector or backend policy |

### GC impact

- Spring/JVM and Quarkus/JVM are both subject to GC pauses
- Quarkus native reduces JVM GC concerns, but changes build/deploy trade-offs
- virtual threads help concurrency, not GC
- reactive reduces blocked threads, not object allocation by itself

### Benchmark approach for this certification series

Instead of inventing numbers, use a repeatable measurement matrix:

| Dimension | Spring lab | Quarkus lab |
|---|---|---|
| Startup | `time ./mvnw -pl auth-service spring-boot:run` | `time ./mvnw -pl auth-service package` then run JVM/native artifact |
| Memory | `docker stats` or k8s metrics on Buildpacks image | `docker stats` or k8s metrics on `Dockerfile.jvm` / native image |
| Trace latency | Datadog span duration on `/api/v1/observability/*` | Datadog span duration on `/api/v1/observability/*` and login flow |
| Throughput | repeated `curl`/load-generator against auth endpoints | same endpoints, compare p95/p99 |
| GC | JVM GC logs + Datadog runtime metrics | JVM GC logs for fast-jar, near-minimal for native |

**Certification takeaway:** You are rarely asked for exact benchmark numbers; you are expected to know which model tends to win and why.

---

## 6. Trace Context Propagation

### W3C TraceContext

Both stacks ultimately revolve around the same standard:

- `traceparent`
- `tracestate`
- optional baggage

That is why both repos can export to the same Datadog backend through OTLP.

### Baggage and business context

Both repos enrich spans with tenant information, but they do it differently.

#### Spring

- MDC enrichment: [`TenantMdcFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantMdcFilter.java)
- span enrichment: [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java)

#### Quarkus

- MDC enrichment: [`TenantMdcFilter`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantMdcFilter.java)
- span enrichment: [`TenantSpanEnricher`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricher.java)
- reactive helper: [`TenantContext`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/TenantContext.java)

### HTTP client/server propagation

```text
Inbound HTTP
  -> server span
  -> extract tenant header
  -> enrich current span
  -> outbound HTTP client
  -> downstream service span continues same trace
```

Spring downstream propagation is demonstrated through:

- [`ObservabilityController#getProfileStats`](../auth-service/src/main/java/tech/sioseforge/auth/resource/ObservabilityController.java)
- [`UserProfileClientConfig`](../auth-service/src/main/java/tech/sioseforge/auth/config/UserProfileClientConfig.java)

Quarkus downstream propagation is demonstrated through:

- [`DefaultAuthService#fetchProfile`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultAuthService.java)
- [`UserProfileClient`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/repository/UserProfileClient.java)

### Async boundary preservation

| Boundary | Spring risk | Quarkus risk |
|---|---|---|
| custom executor | lost parent span if context is not wrapped | less common in app code, still possible |
| reactive stage | only if mixing Reactor/async manually | primary place to reason carefully |
| HTTP client callback | client instrumentation gaps | reactive client chain gaps |

### Reactive stream challenge

In Quarkus, the major challenge is not header propagation; it is **preserving the correct active context while the reactive chain is reshaped**.

In Spring virtual-thread code, the major challenge is **manual thread creation outside the framework-managed flow**.

---

## 7. Custom Instrumentation Patterns

### Manual span creation in Spring

From [`DefaultAuthService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultAuthService.java):

```java
Span loginSpan = tracer.spanBuilder("UserLoginProcess").startSpan();
try (var scope = loginSpan.makeCurrent()) {
    Span dbSpan = tracer.spanBuilder("db_query.verify_credentials").startSpan();
    ...
}
```

This is straightforward and readable. It is ideal for:

- certification exercises
- business-step decomposition
- controlled demos in Datadog flame graphs

### Manual span creation in Quarkus

From [`DefaultAuthService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultAuthService.java):

```java
Span span = tracer.spanBuilder("auth.fetch_profile")
        .setParent(parentContext)
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute("peer.service", "user-profile-service")
        .startSpan();
```

The extra explicitness is valuable in reactive code because it makes parent-child relationships obvious.

### Business attributes and events

Good examples:

- Spring: `tenant.id`, `user.id`, `"Token generated"`
- Quarkus: `vault.path`, `vault.operation`, `profile.full_name`, `"vault_secret_read"`

### Error handling and status

Quarkus currently shows the stronger error instrumentation example:

- [`DefaultVaultSecretService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultVaultSecretService.java)
- sets `StatusCode.ERROR`
- records exceptions
- adds `error.type` and `error.message`

Spring currently has a simpler retry/fallback mock in [`DefaultVaultSecretService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultVaultSecretService.java). For certification purposes, this is a good reminder that **manual Vault spans should be added around secret operations if you want Vault latency and failure visibility comparable to the Quarkus example**.

### Side-by-side guidance

| Need | Spring pattern | Quarkus pattern |
|---|---|---|
| business span | `Tracer.spanBuilder(...).startSpan()` | same |
| current-span enrichment | servlet filter + `Span.current()` | request filter + `Span.current()` |
| async child span | capture context before thread hop | capture `Context.current()` before `Uni` chain split |
| error semantics | `recordException`, set status | same, often more explicit in reactive flow |

---

## 8. Testing & Validation

### Spring Boot testing

Current Spring coverage:

- [`AuthIntegrationTest`](../auth-service/src/test/java/tech/sioseforge/auth/AuthIntegrationTest.java)
- `@SpringBootTest`
- Testcontainers for PostgreSQL and Kafka

This is strong for:

- verifying real infrastructure wiring
- proving the app can emit traces inside a realistic runtime
- validating imperative flows end-to-end

### Quarkus testing

Current Quarkus coverage:

- [`ObservabilityResourceTest`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/test/java/tech/sioseforge/auth/resource/ObservabilityResourceTest.java)
- [`VaultAdminResourceTest`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/test/java/tech/sioseforge/auth/resource/VaultAdminResourceTest.java)
- `@QuarkusTest`
- RestAssured
- Mockito-based injection via `@InjectMock`

This is strong for:

- fast resource-level verification
- reactive endpoint behavior
- clean mocking of integration points

### Testcontainers for Datadog/Jaeger backends

Current state of the two repos:

- **Spring repo**: Testcontainers already exists for database/Kafka
- **Quarkus repo**: README states that deeper Testcontainers coverage for PostgreSQL and Vault is planned

For future lab expansion, the most useful observability integration test pattern is:

1. start app + collector/backend container
2. send a request
3. assert the exported span tree
4. assert business attributes such as `tenant.id`, `vault.path`, `peer.service`

### Span assertion strategy

For certification prep, assert on:

- span name
- trace continuity
- parent-child relationship
- semantic attributes
- status/error mapping

### Integration-test examples to compare

| Topic | Spring | Quarkus |
|---|---|---|
| full app bootstrap | `@SpringBootTest` | `@QuarkusTest` |
| infra containers | Testcontainers already present | not yet present in repo examples |
| endpoint assertions | `RestTemplate`/HTTP | RestAssured |
| mocked Vault path | easier with Spring beans | very clean with `@InjectMock` |

---

## 9. Vault Integration & Secrets Management

### Spring Cloud Vault

Spring dev profile files:

- [`auth-service/application-dev.yml`](../auth-service/src/main/resources/application-dev.yml)
- [`user-profile-service/application-dev.yml`](../user-profile-service/src/main/resources/application-dev.yml)

They import Vault with:

```yaml
spring:
  config:
    import: vault://
```

Current admin endpoint:

- [`VaultAdminController`](../auth-service/src/main/java/tech/sioseforge/auth/resource/VaultAdminController.java)

Current service:

- [`DefaultVaultSecretService`](../auth-service/src/main/java/tech/sioseforge/auth/service/DefaultVaultSecretService.java)

This Spring implementation emphasizes retry behavior more than real Vault telemetry.

### Quarkus Vault extension

Quarkus config:

- [`auth-service/application.properties`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/resources/application.properties)

Quarkus service:

- [`DefaultVaultSecretService`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/service/impl/DefaultVaultSecretService.java)

Quarkus admin endpoint:

- [`VaultAdminResource`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/VaultAdminResource.java)

### OTel tracing of secret retrieval

Quarkus explicitly instruments Vault calls with:

- `vault.kv.read`
- `vault.kv.write`
- `vault.path`
- `vault.operation`
- `vault.engine`

Spring should follow the same span design if you want equivalent Datadog diagnostics.

### Error scenarios and retry semantics

| Scenario | Spring | Quarkus |
|---|---|---|
| transient timeout | `@Retryable` is a natural fit | usually modeled in async chain/retry policy |
| fallback secret | present in current mock service | present via recovery path in auth flow |
| trace visibility | should be manual around retry loop | already explicit in Vault span code |

### Security-related span attributes

Good attributes:

- `vault.path`
- `vault.operation`
- `error.type`
- `retry.count`
- `secret.backend`

Bad attributes:

- secret values
- API keys
- raw tokens

**Exam takeaway:** Trace the access pattern, never the secret content.

---

## 10. Deployment & Observability

### Spring containerization

This repo does **not** use a handwritten Dockerfile today. It uses **Spring Boot Buildpacks** in [`pom.xml`](../pom.xml):

- base image: `eclipse-temurin:25-jre-alpine`
- layered image build from Maven
- good cache behavior without maintaining Dockerfile logic

So the comparison point is:

- Spring repo: **Buildpacks-based layered JVM image**
- Quarkus repo: **`Dockerfile.jvm` plus optional native image path**

### Quarkus deployment modes

From [`Dockerfile.jvm`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/docker/Dockerfile.jvm):

- `target/quarkus-app/lib/`
- `target/quarkus-app/app/`
- `target/quarkus-app/quarkus/`

That layout is intentionally cache-friendly and maps well to container image layers.

### Kubernetes resource consumption

Expected pattern:

| Mode | CPU/memory tendency |
|---|---|
| Spring JVM + virtual threads | higher memory, simple ops model |
| Quarkus fast-jar | lower startup and often lower memory than Spring JVM |
| Quarkus native | lowest footprint, highest build complexity |

### Monitoring in production

Both repos are aligned around OTLP -> collector/agent -> Datadog:

- Spring collector config: [`k8s/otel/collector-config.yaml`](../k8s/otel/collector-config.yaml)
- Spring local stack: [`docker-compose.yml`](../docker-compose.yml)
- Quarkus monitor provisioning: [`deploy/terraform/main.tf`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/deploy/terraform/main.tf), [`deploy/terraform/monitors.tf`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/deploy/terraform/monitors.tf)

### Cost implications

- More spans + higher cardinality = more backend cost in both frameworks
- Quarkus native may reduce infrastructure cost
- Spring may reduce developer cost because teams move faster in familiar stacks

Certification questions often hide this trade-off inside "best solution" wording.

---

## 11. Debugging & Troubleshooting

### Spring

Spring auth-service includes:

- `spring-boot-starter-actuator` in [`auth-service/pom.xml`](../auth-service/pom.xml)
- custom observability endpoints in [`ObservabilityController`](../auth-service/src/main/java/tech/sioseforge/auth/resource/ObservabilityController.java)

Useful debugging surfaces:

- Actuator health/metrics/tracing-related runtime views
- `/api/v1/observability/trace-check`
- `/api/v1/observability/trace-deep`
- `/api/v1/observability/profile-stats`

### Quarkus

Quarkus auth-service includes:

- `quarkus-smallrye-health`
- custom observability endpoints in [`ObservabilityResource`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/ObservabilityResource.java)

Useful debugging surfaces:

- `/api/v1/observability/trace-check`
- `/api/v1/observability/trace-deep`
- `/q/health` when health is exposed

### Common propagation issues

| Issue | Spring cause | Quarkus cause |
|---|---|---|
| downstream service not in same trace | custom executor/client config | reactive context not linked correctly |
| tenant attribute missing | filter order/header missing | request filter or MDC handoff missing |
| extra root spans | manual virtual-thread task without parent context | span created outside correct `Context` |
| missing DB spans | unsupported driver/instrumentation mismatch | reactive client instrumentation expectations |

### Virtual-thread vs reactive context loss

#### Spring-specific

If `DefaultObservabilityService` emits an async span that looks detached in Datadog, suspect:

- custom virtual-thread executor
- missing parent context propagation
- manual `SpanBuilder` without parent

#### Quarkus-specific

If a `Uni` stage produces spans that do not appear as children, suspect:

- parent context captured too late
- new span started outside active reactive stage
- context lost across custom callbacks

### Diagnostic techniques

1. hit simple trace endpoint first
2. verify one service before two-service trace
3. add one business attribute (`tenant.id`) and check it in Datadog
4. compare logs and spans using the same trace ID
5. only then debug Vault, Kafka, or DB children

---

## 12. Best Practices & Trade-offs

### When to choose Spring Boot

- existing Spring-heavy organization
- blocking persistence and HTTP clients
- need fast delivery with familiar abstractions
- virtual threads give enough concurrency without reactive complexity

### When to choose Quarkus

- startup/memory matter materially
- native-image deployment is valuable
- reactive architecture is a real requirement, not a trend
- you want tighter control over build-time/runtime behavior

### Hybrid architectures

A realistic enterprise outcome is:

- Spring for operational core services with blocking integrations
- Quarkus for edge services, bursty workloads, or low-footprint deployments
- same OTel semantic model across both

That is exactly why learning both is useful for the two certifications.

### Migration considerations

| Migration axis | Main concern |
|---|---|
| Spring -> Quarkus | replace blocking assumptions, revisit instrumentation around async boundaries |
| Quarkus -> Spring | simpler flow model, but potentially higher runtime footprint |
| Both | preserve semantic conventions, span names, resource attributes, and Datadog tags |

### Certification focus areas

Know these cold:

1. OTLP is vendor-neutral; Datadog is the backend
2. span context loss happens at async boundaries, not because of the backend
3. virtual threads improve scalability but do not eliminate tracing discipline
4. reactive pipelines need explicit reasoning about parent context
5. secret values must never be added as span attributes

---

## 13. Practical Comparison Table

### Feature matrix

| Feature | Spring lab | Quarkus lab |
|---|---|---|
| Framework version | Boot 4.1 / Spring 7 | Quarkus 3.37.x |
| Java version in repo | 25 | 21 |
| Main runtime style | MVC + virtual threads | JAX-RS + Mutiny/reactive |
| OTel dependency style | Micrometer bridge + exporter | Quarkus OTel extension |
| HTTP client | `RestClient` + `@HttpExchange` | MicroProfile Rest Client |
| DB model | JPA/JDBC | Hibernate Reactive + reactive PG |
| Vault integration | Spring Cloud Vault | Quarkus Vault |
| Container build | Buildpacks | `Dockerfile.jvm` / native path |
| Integration test style | `@SpringBootTest` + Testcontainers | `@QuarkusTest` + RestAssured |

### Code examples side-by-side

#### Span enrichment from request header

**Spring**

```java
String tenantId = request.getHeader(TenantMdcFilter.TENANT_ID_HEADER);
Span.current().setAttribute("tenant.id", tenantId);
```

Source: [`TenantSpanEnricherFilter`](../auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricherFilter.java)

**Quarkus**

```java
String tenantId = requestContext.getHeaderString(TenantMdcFilter.TENANT_ID_HEADER);
Span.current().setAttribute("tenant.id", tenantId);
```

Source: [`TenantSpanEnricher`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/auth-service/src/main/java/tech/sioseforge/auth/resource/filter/TenantSpanEnricher.java)

### Performance summary

| Dimension | Spring JVM + virtual threads | Quarkus fast-jar | Quarkus native |
|---|---|---|---|
| Cold start | moderate | good | best |
| RAM | moderate/high | moderate | lowest |
| Programming simplicity | best | medium | medium |
| Reactive scale | limited by blocking stack choices | strong | strong |
| Build complexity | low | medium | highest |

---

## 14. Hands-On Lab Comparison

### Running both services locally

#### Spring lab

Use the local support stack in [`docker-compose.yml`](../docker-compose.yml) for:

- PostgreSQL
- OTel Collector
- Vault
- Kafka

Then run Spring services from this repo.

#### Quarkus lab

Use the Quarkus repo build path from [`README.md`](https://github.com/siosetech/quarkus-datadog-lab/blob/c505cc8775da2738d9001181b950105c5ac82788/README.md):

- `./mvnw clean package -DskipTests`
- build JVM container images with `src/main/docker/Dockerfile.jvm`

### Comparing trace outputs in Datadog

Recommended sequence:

1. **Spring**: call `/api/v1/observability/trace-check`
2. **Spring**: call `/api/v1/observability/profile-stats`
3. **Spring**: call `/api/v1/observability/trace-deep`
4. **Quarkus**: call `/api/v1/observability/trace-check`
5. **Quarkus**: call `/api/v1/observability/trace-deep`
6. **Quarkus**: call login flow that triggers `fetchProfile` and Vault secret read

Compare in Datadog:

- number of spans
- child-span structure
- `tenant.id`
- Vault span presence
- downstream service linkage

### Performance testing setup

Use the same test discipline for both:

```text
Warm up service
-> send steady traffic
-> collect p50/p95/p99
-> record memory and CPU
-> inspect spans for overhead
```

Do not compare:

- Spring dev mode vs Quarkus optimized build
- different sampling rates
- different collector/exporter protocols

### Resource-utilization analysis

Track:

- container RSS / heap
- CPU at idle and under load
- startup duration
- p95 latency with tracing on/off
- total span volume per request

### Final recommendation for exam prep

If you are preparing for both certifications, use these labs this way:

- use **Spring** to master imperative instrumentation, virtual-thread caveats, and Actuator-era observability thinking
- use **Quarkus** to master reactive context propagation, native/runtime trade-offs, and explicit span parenting
- use **Datadog** as the neutral proving ground for both

That combination gives you practical intuition instead of memorized terminology.

---

## Exam-Cram Summary

```text
Spring = simpler imperative tracing, virtual-thread awareness, bigger runtime
Quarkus = reactive/native efficiency, more explicit context reasoning
OpenTelemetry = common standard across both
Datadog = analysis plane where differences become visible
```

---

## Related Documents

- [Phase 1: OpenTelemetry Fundamentals](./OPENTELEMETRY_FUNDAMENTALS.md)
- [Phase 2: Datadog Integration Guide](./DATADOG_INTEGRATION.md)
- [Observability Lessons](./OBSERVABILITY_LESSONS.md) — architecture, diagrams, annotated screenshots
- [Repository README](../README.md)
- [Implementation Plan](../IMPLEMENTATION_PLAN.md)

