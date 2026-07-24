# Local Observability Roadmap

FleetForge ile yan yana çalışan **spring-datadog-lab** local stack.
Host portları **9xxx**; Datadog site **us5.datadoghq.com**.

## Port map (host)

| Component | Host | Notes |
|---|---|---|
| api-gateway | 9000 | Entry |
| auth-service | **9180** | 9080 = NahimicService (Windows) |
| user-profile | 9082 | |
| audit-log | 9083 | |
| dashboard | 9084 | |
| notification | 9085 | |
| Debezium | 9086 | Compose |
| Kafka UI | 9088 | Compose |
| Kafka (host clients) | 9192 | EXTERNAL listener |
| Vault | 9200 | Compose (`token=root`) |
| OTLP gRPC / HTTP | 9317 / **9318** | Apps → collector |
| Postgres | 9543 | |
| Jaeger UI | 9668 | |

Compose ≠ apps: `docker compose` only infra. Spring apps: `mvnw -pl <module> spring-boot:run`.

## Datadog local

```powershell
copy .env.example .env   # fill DD_API_KEY
docker compose up -d --force-recreate otel-collector datadog
```

## Phase A — bootstrap (done)

- [x] 9xxx ports, auth **9180**, OTel dual-export, postgresql `42.7.12`
- [x] Metrics OTLP → `:9318`, Gateway Oakwood webflux routes
- [x] E2E register/login + APM waterfall

## Phase B — hardening (done)

### B1. Postgres init
- [x] `deploy/docker/postgres/init/01-auth-service.sh` (first volume init)
- [x] `deploy/scripts/postgres/bootstrap-auth-db.ps1` (existing volume)
- [x] Compose mounts init dir → `/docker-entrypoint-initdb.d`

### B2. Metrics `:9318`
- [x] Confirmed in app logs: `OtlpMeterRegistry ... http://localhost:9318/v1/metrics`

### B3. Kafka consumer traces
- [x] Boot 4: `spring-kafka` → **`spring-boot-starter-kafka`** (all Kafka modules)
- [x] JSON type-headers off + `UserRegisteredEvent.timestamp` as `String`
- [x] Jaeger ops: `consume_auth_event`, `handle_user_registered_event`, `auth-events process`

### B4. `.env.example`
- [x] `DD_API_KEY`, `DD_SITE=us5.datadoghq.com`, `POSTGRES_*`, Vault token

## Phase C — optional next

- [x] Kafka **trace context propagation** (producer → consumer same traceId)
  - Removed custom `KafkaTemplate` (blocked Micrometer observation)
  - `send(...).join()` under active span; consumer spans use `Context.current()` parent
  - Verified: login `traceId` == notification `consume_auth_event` `traceId`
- [x] Refresh long docs still showing `:8080`
- [x] K8s `spring-datadog-lab` redeploy
  - Prefer **Spring Cloud Vault** for app secrets (no VSO)
  - OTel collector only: `deploy/scripts/k8s/ensure-datadog-secret.ps1`
  - Strimzi **0.51** CRDs required on K8s 1.32+; entity-operator needs `v1` APIs
  - Apps: `deploy/scripts/k8s/build-and-deploy-apps.ps1` (+ postgres bootstrap scripts)
  - Smoke: `kubectl -n spring-datadog-lab port-forward svc/api-gateway 19001:9000` → register/login 200
  - Boot 4 Flyway: use `spring-boot-starter-flyway` (not raw `flyway-core`); separate DBs per Flyway app
  - Jaeger OK (services + login waterfall + Kafka same-trace)
  - OTel→Datadog us5: collector `Sending host metadata` / no 401 on exporter; **visual** APM + Metrics screenshots → [OBSERVABILITY_WALKTHROUGH.md](OBSERVABILITY_WALKTHROUGH.md)
  - Lessons doc (architecture, diagrams, annotated shots): [OBSERVABILITY_LESSONS.md](OBSERVABILITY_LESSONS.md)


## Verify quick

```powershell
curl -s http://localhost:9000/actuator/health
curl -s http://localhost:9180/actuator/health
curl -s http://localhost:9668/api/services
# After login: Jaeger service=notification-service → consume_auth_event
```
