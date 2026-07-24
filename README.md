# Spring Datadog Lab

[![CI - Build, Test, and Lint](https://github.com/siosetech/spring-datadog-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/siosetech/spring-datadog-lab/actions/workflows/ci.yml)

A hands-on lab exploring modern observability and security practices using **Spring Boot 4.1** (Spring Framework 7). This project mirrors the domain of [quarkus-datadog-lab](../quarkus-datadog-lab) while leveraging Spring-native features.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 / Spring Framework 7.0.8 |
| Java | 25 (Virtual Threads enabled) |
| Concurrency | Virtual Threads (`spring.threads.virtual.enabled=true`) |
| REST | Spring MVC |
| REST Client | `@HttpExchange` + `RestClient` (declarative) |
| Persistence | Spring Data JPA + PostgreSQL + Flyway |
| Tracing | OpenTelemetry → OTLP → Collector → **Jaeger v2** + **Datadog** (dual-export) |
| Metrics | Micrometer + OTLP Exporter → OTel Collector → Datadog |
| Secrets | Spring Cloud Vault (2025.1.x Oakwood) |
| Resilience | Built-in `@Retryable` + `@ConcurrencyLimit` |
| Logging | Logback + logstash-logback-encoder (JSON/ECS) |
| API Docs | SpringDoc OpenAPI + Swagger UI |

## Project Structure

```
spring-datadog-lab/
├── auth-service/              # Authentication, JWT issuance, Vault integration
├── user-profile-service/      # Downstream profile service
├── audit-log-service/         # Audit events and persistence
├── notification-service/      # Kafka-driven notifications
├── dashboard-service/         # Aggregation and dashboard-facing APIs
├── api-gateway/               # Spring Cloud Gateway routes and CORS
├── k8s/                       # Helm chart + Kustomize overlays
├── terraform/                 # Datadog and infra provisioning
├── docs/                      # Technical documentation
└── .github/workflows/         # CI/CD pipelines
```

## Local Component URLs

Host ports use the **9xxx** range so this lab can run beside **FleetForge**
(FleetForge typically uses `8080–8086`, `8200`, `4317/4318`, `16686`, `5432`, `9092`).

### Spring services

| Component | URL | Notes |
|---|---|---|
| API Gateway | http://localhost:9000 | Entry point / routes |
| Auth Service | http://localhost:9180 | JWT / auth APIs |
| User Profile Service | http://localhost:9082 | Profiles |
| Audit Log Service | http://localhost:9083 | Audit APIs |
| Dashboard Service | http://localhost:9084 | Aggregation |
| Notification Service | http://localhost:9085 | Kafka-driven |

### Gateway route targets (defaults)

| Gateway path | Upstream |
|---|---|
| `/api/v1/auth/**` | http://localhost:9180 |
| `/api/v1/profiles/**` | http://localhost:9082 |
| `/api/v1/audit/**` | http://localhost:9083 |
| `/api/v1/dashboard/**` | http://localhost:9084 |

Override with `AUTH_SERVICE_URL`, `USER_PROFILE_SERVICE_URL`, `AUDIT_LOG_SERVICE_URL`, `DASHBOARD_SERVICE_URL`.

### Infra (Docker Compose host → container)

| Component | Host URL / endpoint | Container port |
|---|---|---|
| Jaeger UI | http://localhost:9668 | 16686 |
| Vault | http://localhost:9200 (`token=root`) | 8200 |
| OTel Collector OTLP HTTP | http://localhost:9318 | 4318 |
| OTel Collector OTLP gRPC | localhost:9317 | 4317 |
| Kafka (clients on host) | localhost:9192 | 9094 external |
| Kafka UI | http://localhost:9088 | 8080 |
| Postgres | localhost:9543 (`lab` / `lab`) | 5432 |
| Debezium Connect | http://localhost:9086 | 8083 |

### Tracing

```yaml
management:
  otlp:
    metrics:
      export:
        url: http://localhost:9318/v1/metrics
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:9318/v1/traces
```

Path: **Service → OTel Collector `:9318` (traces + metrics) → Jaeger (`:9668`) + Datadog**.

### Quick health checks

```bash
curl -s http://localhost:9000/actuator/health
curl -s http://localhost:9180/actuator/health
curl -s http://localhost:9082/actuator/health
curl -s http://localhost:9083/actuator/health
curl -s http://localhost:9084/actuator/health
curl -s http://localhost:9085/actuator/health
```

## Runtime Configuration (Security-first)

Set sensitive values from environment/Vault instead of hardcoding.

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC secret for auth-service JWT signing (min 32 chars) |
| `VAULT_TOKEN` / `SPRING_CLOUD_VAULT_TOKEN` | Vault token (`root` for local Compose Vault) |
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials for services using Postgres |
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | Local Docker Compose Postgres bootstrap |
| `GATEWAY_ALLOWED_ORIGIN` | Allowed CORS origin in API gateway |
| `AUTH_SERVICE_URL`, `USER_PROFILE_SERVICE_URL`, `AUDIT_LOG_SERVICE_URL`, `DASHBOARD_SERVICE_URL` | Gateway route targets |
| `SERVER_PORT` | Override a service listen port |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Trace OTLP URL (default `http://localhost:9318/v1/traces`) |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | Metrics OTLP URL (default `http://localhost:9318/v1/metrics`) |
| `OTEL_TRACES_SAMPLER`, `OTEL_TRACES_SAMPLER_ARG` | Trace sampling policy by environment |

**Vendor-agnostic tracing:** apps export OTLP only to the collector; the collector dual-exports to Jaeger v2 and Datadog. Copy `.env.example` → `.env`, set `DD_API_KEY` (and `DD_SITE=us5.datadoghq.com`), then `docker compose up`.

**Screenshot walkthrough:** [`docs/OBSERVABILITY_WALKTHROUGH.md`](docs/OBSERVABILITY_WALKTHROUGH.md)  
**Observability lessons (architecture + diagrams + annotated shots):** [`docs/OBSERVABILITY_LESSONS.md`](docs/OBSERVABILITY_LESSONS.md)

Pinned Compose images (no `:latest`):
| Image | Tag |
|---|---|
| `cr.jaegertracing.io/jaegertracing/jaeger` | `2.20.0` |
| `otel/opentelemetry-collector-contrib` | `0.100.0` |
| `hashicorp/vault` | `1.19.5` |
| `bitnamilegacy/kafka` | `3.9` |
| `provectuslabs/kafka-ui` | `v0.7.2` |
| `gcr.io/datadoghq/agent` | `7.64.3` |
| `postgres` | `15.8-alpine` |
| `debezium/connect` | `2.7.3.Final` |

K8s namespace: `spring-datadog-lab` (Jaeger + OTel Collector + Vault via Kustomize overlay `deploy/k8s/kustomize/overlays/dev`). App secrets use **Spring Cloud Vault** directly (no Vault Secrets Operator); only the OTel collector relies on a plain K8s secret (`datadog-k8s-secret`).

## Documentation

| Doc | Purpose |
|---|---|
| [`docs/OBSERVABILITY_LESSONS.md`](docs/OBSERVABILITY_LESSONS.md) | Primary lessons: architecture, diagrams, annotated Jaeger/Datadog screenshots |
| [`docs/OBSERVABILITY_WALKTHROUGH.md`](docs/OBSERVABILITY_WALKTHROUGH.md) | Screenshot checklist + deep links for a guided smoke test |
| [`docs/LOCAL_OBSERVABILITY_ROADMAP.md`](docs/LOCAL_OBSERVABILITY_ROADMAP.md) | Port map, phases, K8s notes |
| [`docs/OPENTELEMETRY_FUNDAMENTALS.md`](docs/OPENTELEMETRY_FUNDAMENTALS.md) | OTel concepts (Phase 1, certification prep) |
| [`docs/DATADOG_INTEGRATION.md`](docs/DATADOG_INTEGRATION.md) | Datadog APM deep dive (Phase 2, certification prep) |
| [`docs/TEST_SCENARIOS_AND_VALIDATION.md`](docs/TEST_SCENARIOS_AND_VALIDATION.md) | Test scenarios & validation matrix (Phase 4) |
| [`docs/SPRING_vs_QUARKUS_OTEL.md`](docs/SPRING_vs_QUARKUS_OTEL.md) | Spring vs Quarkus OTel comparison (Phase 3) |

## Building

```bash
./mvnw clean compile
```

## Debezium Outbox Demo (CDC)

This repository now includes a Debezium-based outbox demo for `auth-service` user registration events.

1. Start local infra:
   - `docker compose up -d postgres kafka debezium-connect`
2. Register the connector:
   - `pwsh -File .\deploy\scripts\debezium\register-auth-outbox-connector.ps1`
3. Trigger a registration via `auth-service`.
4. Observe emitted events on Kafka topic:
   - `outbox.auth.User`

Implementation notes:
- `auth-service` writes to `users` and `outbox_event` in the same transaction.
- Debezium captures `public.outbox_event` and publishes through the Outbox Event Router SMT.

## Implementation Plan

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the full migration roadmap.

