# Spring Datadog Lab — Implementation Plan

A plan to create a new `spring-datadog-lab` project using the **Spring Boot 4.1.0** (Spring Framework 7) ecosystem, covering the same scope as the existing `quarkus-datadog-lab` project's domain and observability architecture.

> **This plan does not touch the existing Quarkus project.** It is developed as a separate Git repository. A concise commit will be made at the end of each phase.

---

## Spring Boot 4 / Spring Framework 7 — What's New?

| Feature | Description | Usage in Project |
|---|---|---|
| **Virtual Threads** | `spring.threads.virtual.enabled=true` automatically switches Tomcat, `@Async`, RestClient to virtual threads | Blocking MVC + Virtual Threads = Reactive performance, imperative code simplicity |
| **`spring-boot-starter-opentelemetry`** | Native OTel starter — No Actuator needed. Exports Trace, metrics, logs via OTLP | OTLP/gRPC export to Datadog Agent |
| **Declarative HTTP Client (`@HttpExchange`)** | Interface-based REST client, works with `RestClient` adapter | `UserProfileClient` — service-to-service communication |
| **Native API Versioning** | `@GetMapping(version = "1")` — framework-level version support | v1 versioning in Auth API endpoints |
| **Built-in `@Retryable` & `@ConcurrencyLimit`** | Core framework resilience — no extra library needed | Vault read retry, concurrent login limits |
| **JSpecify Null Safety** | `@Nullable` / `@NonNull` for compile-time null safety | In Domain/Service layers |
| **Jackson 3** | Next-generation JSON processing (default) | REST serialization |
| **Modular JARs** | Faster startup, smaller footprint | Production-ready container image |

---

## Technology Stack Comparison

| Concern | Quarkus (Current) | Spring Boot 4.1 (New) |
|---|---|---|
| Framework | Quarkus 3.37 | **Spring Boot 4.1.0** / Spring Framework 7.0.8 |
| Java | 21 | **21** (25 recommended, 17 minimum) |
| Concurrency | Mutiny (reactive) | **Virtual Threads** (imperative + non-blocking) |
| REST API | JAX-RS (`quarkus-rest`) | **Spring MVC** (`spring-boot-starter-web`) |
| REST Client | MicroProfile REST Client | **`@HttpExchange`** + `RestClient` (declarative) |
| ORM | Hibernate Reactive Panache | **Spring Data JPA** (blocking + virtual threads) |
| DB Migration | Flyway (Quarkus ext.) | **Flyway** (`flyway-core`) |
| OpenTelemetry | `quarkus-opentelemetry` | **`spring-boot-starter-opentelemetry`** (native!) |
| OTel Export | OTLP → Datadog Agent | **OTLP → Datadog Agent** (same) |
| Metrics | Micrometer Prometheus | **Micrometer OTLP** (`micrometer-registry-otlp`) |
| Vault | `quarkus-vault` | **`spring-cloud-starter-vault-config`** (2025.1.x Oakwood) |
| Resilience | — | **`@Retryable` + `@ConcurrencyLimit`** (built-in!) |
| Logging | `quarkus-logging-json` | **Logback** + `logstash-logback-encoder` |
| Health Check | SmallRye Health | **Spring Boot Actuator** |
| API Docs | SmallRye OpenAPI | **SpringDoc OpenAPI** |
| API Versioning | — (manual) | **Native** (`version = "1"` in `@GetMapping`) |
| JSON | Jackson 2 | **Jackson 3** (default) |
| Test | Quarkus JUnit5 + Mockito | **Spring Boot Test** + Mockito |
| Mapping | MapStruct (Quarkiverse) | **MapStruct** (standard) |

---

## Phases / Roadmap

### Phase 1 — Project Scaffolding & Multi-Module Setup
**Commit:** `feat: scaffold spring-datadog-lab multi-module project`

- [ ] Parent POM (Spring Boot 4.1.0, Java 21, Spring Cloud 2025.1.x)
- [ ] `auth-service` + `user-profile-service` module POMs
- [ ] Virtual Threads configuration
- [ ] `deploy/` directory skeleton
- [ ] `.gitignore`, `README.md`
- [ ] `mvn clean compile` verification

---

### Phase 2 — Domain Model & Persistence Layer
**Commit:** `feat: add domain model, JPA entities, Flyway migrations`

- [ ] JPA Entities: `Tenant`, `User`, `Dashboard`, `DashboardPermission`
- [ ] Enums: `TenantStatus`, `AccessLevel`
- [ ] Spring Data JPA Repositories
- [ ] DTO records: `LoginRequestVO`, `AuthResponseVO`, `PermissionVO`, `UserProfileVO`, `TraceCheckResponseVO`, `DatadogApiKeyRequestVO`
- [ ] `AuthMapper` (MapStruct)
- [ ] Flyway migrations
- [ ] `application.yml` PostgreSQL + Flyway config

---

### Phase 3 — REST API Layer
**Commit:** `feat: implement REST controllers, filters, exception handling`

**auth-service:**
- [ ] `AuthController` — `POST /api/v1/auth/login`, `GET /api/v1/auth/permissions/{userId}`
- [ ] `ObservabilityController` — `GET /api/v1/observability/trace-check`, `GET /api/v1/observability/trace-deep`
- [ ] `VaultAdminController` — `POST /api/v1/admin/vault/datadog-api-key`
- [ ] `GlobalExceptionHandler` (`@ControllerAdvice`)
- [ ] `TenantMdcFilter` + `TenantSpanEnricherFilter` (Spring `OncePerRequestFilter`)

**user-profile-service:**
- [ ] `UserProfileController`
- [ ] `SpanEnricherFilter`

---

### Phase 3.1 — Enhance Architecture with Observability Services (Catch up to Phase 3)
**Commit:** `feat: scaffold and implement core layers for audit, notification, and dashboard services`

- [ ] Create POMs (`audit-log-service`, `notification-service`, `dashboard-service`)
- [ ] Create Domain Entities and Repositories (e.g., `AuditLog`, `Notification`)
- [ ] Create REST Controllers and Filters (e.g., `AuditLogController`, `NotificationController`)
- [ ] Database and Flyway configurations
- [ ] General verification with `mvn clean compile`

---

### Phase 4 — OpenTelemetry & Datadog Pipeline
**Commit:** `feat: configure OpenTelemetry with OTLP Datadog exporter`

- [ ] `spring-boot-starter-opentelemetry` configuration
- [ ] `DefaultAuthService` manual child spans
- [ ] `ObservabilityController` — `@WithSpan` + manual span builder
- [ ] Cross-service trace propagation verification

---

### Phase 5 — Micrometer Metrics
**Commit:** `feat: add custom Micrometer business metrics with OTLP export`

- [ ] `AuthMetrics`: `auth.login.total`, `auth.login.duration_ms`, `vault.read.total`
- [ ] OTLP metrics exporter configuration
- [ ] Actuator endpoints

---

### Phase 6 — HashiCorp Vault Integration
**Commit:** `feat: integrate Spring Cloud Vault with OTel-instrumented secret operations`

- [ ] `spring-cloud-starter-vault-config` configuration
- [ ] `VaultSecretService` + `DefaultVaultSecretService` (with OTel spans)
- [ ] Vault read retry with `@Retryable`
- [ ] `VaultAdminController`

---

### Phase 7 — Structured Logging
**Commit:** `feat: configure Datadog-friendly structured JSON logging`

- [ ] `logback-spring.xml` (prod: JSON/ECS, dev: human-readable)
- [ ] MDC fields: `tenant_id`, `traceId`, `spanId`
- [ ] Log-trace correlation

---

### Phase 8 — Service-to-Service Communication
**Commit:** `feat: add declarative UserProfileClient with @HttpExchange`

- [ ] `UserProfileClient` (`@HttpExchange` declarative interface)
- [ ] `RestClient` bean + `X-Tenant-Id` header propagation
- [ ] W3C `traceparent` auto-propagation
- [ ] Fallback handling

---

### Phase 9 — Resilience & Concurrency Control
**Commit:** `feat: add built-in resilience with @Retryable and @ConcurrencyLimit`

- [ ] `@EnableResilientMethods` configuration
- [ ] `@ConcurrencyLimit` login endpoint throttling
- [ ] Resilience metrics

---

### Phase 10 — Test Suite
**Commit:** `feat: add unit and integration test suite`

- [ ] Unit Tests: `DefaultAuthServiceTest`, `DefaultPermissionServiceTest`, `DefaultVaultSecretServiceTest`
- [ ] Integration Tests: `AuthControllerIT`, `ObservabilityControllerIT`
- [ ] Testcontainers (optional)

---

### Phase 11 — Containerization & K8s (Helm + Kustomize)
**Commit:** `feat: add Dockerfiles and K8s deployment with Helm+Kustomize`

- [ ] Multi-stage `Dockerfile` (For all services)
- [ ] Helm charts: Common service infrastructure (Base deployment, service)
- [ ] Kustomize Overlays: Patching Helm outputs for `dev` and `prod` environments
- [ ] Injecting Datadog tags (labels/annotations) via Kustomize
- [ ] VSO (Vault Secrets Operator) yaml files

---

### Phase 12 — Terraform IaC & Documentation
**Commit:** `feat: add Terraform monitors and project documentation`

- [ ] Terraform: `main.tf`, `monitors.tf`, `notifications.tf`
- [ ] `README.md` (comprehensive)
- [ ] `MIGRATION_NOTES.md`

---

## Commit Strategy

| Phase | Commit Message |
|---|---|
| 1 | `feat: scaffold spring-datadog-lab multi-module project` |
| 2 | `feat: add domain model, JPA entities, Flyway migrations` |
| 3 | `feat: implement REST controllers, filters, exception handling` |
| 3.1 | `feat: scaffold and implement core layers for audit, notification, and dashboard services` |
| 4 | `feat: configure OpenTelemetry with OTLP Datadog exporter` |
| 5 | `feat: add custom Micrometer business metrics with OTLP export` |
| 6 | `feat: integrate Spring Cloud Vault with OTel-instrumented secret operations` |
| 7 | `feat: configure Datadog-friendly structured JSON logging` |
| 8 | `feat: add declarative UserProfileClient with @HttpExchange` |
| 9 | `feat: add built-in resilience with @Retryable and @ConcurrencyLimit` |
| 10 | `feat: add unit and integration test suite` |
| 11 | `feat: add Dockerfiles, Helm charts, VSO and K8s manifests` |
| 12 | `feat: add Terraform monitors and project documentation` |
