# Datadog Integration Guide — Phase 2

> **Certification Series** | Phase 2: Datadog APM Associate Exam Preparation  
> **Framework**: Spring Boot 4.1 with OpenTelemetry  
> **Target**: Datadog APM Associate Certification

---

> ### Lab runtime (current)
>
> - **Host ports (9xxx range, coexists with FleetForge):** gateway `9000` · auth `9180` · profile `9082` · audit `9083` · dashboard `9084` · notification `9085` · Debezium `9086` · Kafka UI `9088` · Kafka `9192` · Vault `9200` · OTLP gRPC/HTTP `9317`/`9318` · Postgres `9543` · Jaeger UI `9668`
> - **Datadog site:** `us5.datadoghq.com` (API: `api.us5.datadoghq.com`) — not the default `datadoghq.com`
> - **Pipeline:** apps export OTLP only → `otel-collector` dual-exports to **Jaeger** + **Datadog us5** (no app embeds a Datadog SDK)
> - **Auth paths (via gateway):** `POST /api/v1/auth/register` / `POST /api/v1/auth/login`, body `{ssoId, username, password}` / `{username, password}`, header `X-Tenant-Id: acme`
> - **K8s Jaeger port-forward is often `:19668`** (Compose Jaeger is `:9668`; FleetForge is often `:16686`) — don't mix these up
>
> Full walkthrough with diagrams and annotated screenshots: [`OBSERVABILITY_LESSONS.md`](OBSERVABILITY_LESSONS.md).

---

## Table of Contents

1. [Introduction to Datadog APM](#1-introduction-to-datadog-apm)
2. [Datadog Agent vs Agentless (OTLP)](#2-datadog-agent-vs-agentless-otlp)
3. [APM Architecture & Data Flow](#3-apm-architecture--data-flow)
4. [Service Map & Visualization](#4-service-map--visualization)
5. [Custom Metrics & Logging](#5-custom-metrics--logging)
6. [SLO/SLI Monitoring](#6-slosli-monitoring)
7. [Terraform Integration & Infrastructure as Code](#7-terraform-integration--infrastructure-as-code)
8. [Troubleshooting & Common Issues](#8-troubleshooting--common-issues)
9. [Hands-On Integration Walkthrough](#9-hands-on-integration-walkthrough)
10. [Certification Exam Tips](#10-certification-exam-tips)

---

## 1. Introduction to Datadog APM

### What is Datadog APM?

Datadog APM (Application Performance Monitoring) is a distributed tracing and performance monitoring platform that provides end-to-end visibility into your application's performance. It ingests traces, metrics, and logs from your services, correlates them together, and surfaces actionable insights through a unified observability platform.

At its core, Datadog APM collects **spans** from distributed services, assembles them into **traces**, and provides:

- Real-time service topology maps (Service Map)
- Flame graphs and execution timelines for individual traces
- P50/P75/P95/P99 latency breakdowns
- Automatic anomaly detection and alerting
- Log and metric correlation with traces

### Datadog vs Other APM Solutions

| Feature | Datadog | Jaeger | Zipkin | New Relic | Dynatrace |
|---------|---------|--------|--------|-----------|-----------|
| **Deployment** | SaaS | Self-hosted | Self-hosted | SaaS | SaaS / On-Prem |
| **OTLP Native** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Agent Required** | Optional | No | No | Yes | Yes |
| **Metrics** | ✅ Full | ❌ | ❌ | ✅ Full | ✅ Full |
| **Log Management** | ✅ Unified | ❌ | ❌ | ✅ Full | ✅ Full |
| **Unified Platform** | ✅ | ❌ | ❌ | ✅ | ✅ |
| **Terraform Provider** | ✅ Rich | Limited | Limited | ✅ | ✅ |
| **Pricing Model** | Per host / per GB | Free (OSS) | Free (OSS) | Per host | Per host |
| **Service Map** | ✅ Automatic | ✅ | Limited | ✅ | ✅ AI-Powered |
| **Vault Integration** | Via Terraform | Manual | Manual | Limited | Limited |

**Why choose Datadog for this project?**

- **Exam alignment**: Both the OpenTelemetry and Datadog APM Associate exams require hands-on understanding of OTLP-to-Datadog pipelines.
- **Unified observability**: Traces, metrics, and logs in a single pane of glass.
- **OTLP support**: Native OTLP ingestion means the Spring Boot OTel setup works without Datadog-specific SDK changes.
- **Terraform provider**: Rich IaC support aligns with the `terraform/` directory in this project.
- **Kubernetes-native**: Full Kubernetes metadata enrichment from Helm deployments.

### Key Benefits of Datadog for Observability

1. **Correlation across signals**: A single trace ID links a distributed trace, its logs, and its runtime metrics. You can navigate from a slow span → to the JVM GC metric → to the correlated log line.

2. **Automatic instrumentation**: Datadog's tracing libraries and OpenTelemetry auto-instrumentation detect HTTP, DB, Kafka, and gRPC calls without code changes.

3. **Service Catalog**: Discovers all services automatically from incoming trace data, providing ownership, SLOs, and documentation in one place.

4. **Adaptive sampling**: Datadog's backend applies intelligent retention filtering (App Analytics / Indexed Spans) so you pay only for what you need to keep.

5. **Alerting and SLOs**: Built-in SLO tracking tied to real trace data and custom metrics.

### Datadog's Role in the Observability Stack

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Application                        │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │ auth-service │  │user-profile   │  │ audit-log       │  │
│  │ (Spring Boot)│  │ (Spring Boot) │  │ (Spring Boot)   │  │
│  └──────┬───────┘  └───────┬───────┘  └────────┬────────┘  │
│         │                  │                    │           │
│         └──────────────────┴────────────────────┘           │
│                            │ OTLP (gRPC/HTTP)               │
└────────────────────────────┼────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  OTel Collector │
                    │  (k8s/otel/)    │
                    └────────┬────────┘
                             │ Datadog Exporter
                    ┌────────▼────────┐
                    │  Datadog Cloud  │
                    │                 │
                    │  ┌───────────┐  │
                    │  │ APM/Traces│  │
                    │  │ Metrics   │  │
                    │  │ Logs      │  │
                    │  │ Service   │  │
                    │  │ Map       │  │
                    │  └───────────┘  │
                    └─────────────────┘
```

The OTel Collector is the central hub: it receives OTLP signals from all microservices and forwards them to Datadog, providing protocol translation, batching, and enrichment.

---

## 2. Datadog Agent vs Agentless (OTLP)

### Datadog Agent Model

The **Datadog Agent** is a lightweight process running on each host or as a DaemonSet in Kubernetes. It provides full observability by collecting system metrics, container metrics, traces, and logs from the local environment.

#### Agent Architecture and Components

```
┌──────────────────────────────────────────────────────────┐
│                    Datadog Agent                         │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Trace Agent  │  │ Process Agent│  │ System Probe  │  │
│  │ (port 8126)  │  │              │  │               │  │
│  └──────┬───────┘  └──────────────┘  └───────────────┘  │
│         │                                                │
│  ┌──────▼───────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  DogStatsD   │  │  Log Tail    │  │ Integrations  │  │
│  │  (port 8125) │  │              │  │ (JMX, etc.)   │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
│                                                          │
└──────────────────────────────┬───────────────────────────┘
                               │ Encrypted to Datadog cloud
                               ▼
                       https://agent.datadoghq.com
```

**Key components:**

| Component | Port | Purpose |
|-----------|------|---------|
| **Trace Agent** | 8126 (TCP/HTTP) | Receives APM traces from instrumented apps |
| **DogStatsD** | 8125 (UDP) | Receives custom metrics via StatsD protocol |
| **Process Agent** | — | Collects container and process-level metadata |
| **Log collection** | — | Tails log files or Docker stdout |
| **Integrations** | — | 600+ built-in checks (JMX, PostgreSQL, Kafka, etc.) |

#### When to Use the Datadog Agent

Use the Datadog Agent when you need:
- **Host-level metrics**: CPU, memory, disk, network (not available via OTLP)
- **Container metadata enrichment**: Automatic Kubernetes pod/service metadata attachment
- **Log collection**: Tailing log files or Kubernetes pod logs without a separate log shipper
- **JMX metrics**: Collecting JVM metrics via JMX (alternative to Micrometer)
- **Live Process Monitoring**: Process-level visibility in the Datadog UI
- **Network Performance Monitoring (NPM)**: Service-level network metrics

#### DogStatsD for Metrics

DogStatsD is a StatsD-compatible metrics collection server embedded in the Datadog Agent. It listens on UDP port 8125 and accepts custom metrics from your applications.

**Metric types supported:**

| Type | Format | Use Case |
|------|--------|----------|
| **Counter** | `metric.name:value\|c[|#tag]` | Request counts, error counts |
| **Gauge** | `metric.name:value\|g[|#tag]` | Current value (queue depth, memory) |
| **Timer/Histogram** | `metric.name:value\|ms[|#tag]` | Latency, duration distributions |
| **Set** | `metric.name:value\|s[|#tag]` | Count unique values |
| **Distribution** | `metric.name:value\|d[|#tag]` | Global percentiles across hosts |

**Example: Sending DogStatsD metrics from Spring Boot**

```java
// Using micrometer-registry-statsd
@Bean
public StatsdMeterRegistry statsdMeterRegistry(StatsdConfig config) {
    return new StatsdMeterRegistry(config, Clock.SYSTEM);
}
```

```yaml
# application.yml — DogStatsD configuration
management:
  metrics:
    export:
      statsd:
        flavor: datadog
        host: ${DD_AGENT_HOST:localhost}
        port: 8125
        step: 30s
```

#### Agent Model — Pros and Cons

| ✅ Pros | ❌ Cons |
|---------|---------|
| Full host-level visibility | Requires agent deployment in every host/node |
| 600+ built-in integrations | Additional resource consumption (~256MB RAM) |
| Automatic metadata tagging | More complex Kubernetes DaemonSet management |
| Compressed and encrypted data | Coupling to Datadog's proprietary agent |
| Live container process monitoring | Version updates require rolling agent restarts |

---

### Agentless with OTLP

The **agentless approach** uses the OpenTelemetry Protocol (OTLP) to send traces, metrics, and logs directly to Datadog's intake API — no Datadog Agent required on each host.

**This is the architecture used in this project** via the OTel Collector in `k8s/otel/`.

#### Direct OTLP Export to Datadog

Datadog provides native OTLP endpoints that accept data in the standard OpenTelemetry wire format:

| Signal | Endpoint (US1) | Protocol |
|--------|---------------|----------|
| Traces | `https://trace.agent.datadoghq.com` | OTLP/HTTP or gRPC |
| Metrics | `https://api.datadoghq.com/api/intake/otlp` | OTLP/HTTP |
| Logs | `https://http-intake.logs.datadoghq.com` | OTLP/HTTP |

> **Note**: Direct OTLP → Datadog bypasses the OTel Collector. In this project we use the OTel Collector as a gateway for batching, retries, and enrichment.

#### OTLP HTTP/gRPC Endpoints

When using the OTel Collector with dual-export to Datadog and Jaeger v2 (as in `deploy/k8s/otel/collector-config.yaml` and `deploy/docker/otel/otel-collector-config.yaml`):

```yaml
# deploy/k8s/otel / deploy/docker/otel collector config (excerpt)
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317    # gRPC — lower overhead, HTTP/2 multiplexing
      http:
        endpoint: 0.0.0.0:4318    # HTTP/Protobuf — simpler firewall rules

exporters:
  datadog:
    api:
      key: ${env:DD_API_KEY}
      site: ${env:DD_SITE}        # e.g., datadoghq.com, datadoghq.eu, us3.datadoghq.com
  otlphttp/jaeger:
    endpoint: http://jaeger:4318         # K8s: http://jaeger.spring-datadog-lab.svc.cluster.local:4318
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [datadog, otlphttp/jaeger, debug]
```

**Vendor-agnostic path:** Spring services always export OTLP to the collector. The collector fans out to Jaeger UI (local learning) and Datadog (cloud APM). Swap or add backends without changing app config.

**Available Datadog sites:**

| Site | API URL | Region |
|------|---------|--------|
| `datadoghq.com` | `https://api.datadoghq.com` | US1 (default) |
| `datadoghq.eu` | `https://api.datadoghq.eu` | EU |
| `us3.datadoghq.com` | `https://api.us3.datadoghq.com` | US3 |
| `us5.datadoghq.com` | `https://api.us5.datadoghq.com` | **US5 — this lab's site** |
| `ap1.datadoghq.com` | `https://api.ap1.datadoghq.com` | AP1 |

#### Configuration in Spring Boot

This project configures OTLP export directly in `application.yml` for each service:

```yaml
# auth-service/src/main/resources/application.yml
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4318/v1/traces   # Points to OTel Collector
      protocol: http/protobuf
  traces:
    sampler: always_on
```

For Kubernetes, the endpoint points to the collector's ClusterIP service:

```yaml
otel:
  exporter:
    otlp:
      endpoint: http://otel-collector.spring-datadog-lab.svc.cluster.local:4318/v1/traces
      protocol: http/protobuf
```

The `opentelemetry-spring-boot-starter` auto-configures the OTLP exporter. Key Maven dependencies from `pom.xml`:

```xml
<!-- Auto-instrumentation starter -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>2.26.0</version>
</dependency>

<!-- OTLP exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.46.0</version>
</dependency>

<!-- Micrometer bridge for OTel metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
    <version>1.4.1</version>
</dependency>
```

#### Agentless OTLP — Pros and Cons

| ✅ Pros | ❌ Cons |
|---------|---------|
| Vendor-neutral instrumentation | No host-level metrics (CPU, disk, network) |
| Single OTel Collector per cluster | Requires OTel Collector operational knowledge |
| Portable across APM backends | Limited built-in integrations vs Agent |
| Simpler per-service footprint | Must manually configure Kubernetes metadata |
| OpenTelemetry community support | Newer feature set — some Datadog features require Agent |

---

### Hybrid Approach

The hybrid approach runs both the Datadog Agent **and** the OTel Collector, combining the best of both worlds.

```
┌──────────────────────────────────────────────┐
│              Kubernetes Cluster              │
│                                              │
│  ┌──────────────────┐  ┌──────────────────┐  │
│  │   auth-service   │  │  user-profile    │  │
│  │   OTLP → 4318    │  │  OTLP → 4318     │  │
│  └────────┬─────────┘  └────────┬─────────┘  │
│           │                     │             │
│           └──────────┬──────────┘             │
│                      ▼                        │
│           ┌──────────────────┐                │
│           │  OTel Collector  │ (Application   │
│           │  (Deployment)    │  traces/metrics)│
│           └──────────┬───────┘                │
│                      │ OTLP                   │
│  ┌──────────────┐    │                        │
│  │  DD Agent    │◄───┘                        │
│  │  (DaemonSet) │ Also receives               │
│  │  port 8126   │ host metrics                │
│  └──────┬───────┘                             │
│         │                                     │
└─────────┼───────────────────────────────────--┘
          │ To Datadog Cloud
          ▼
```

**Best practices for this project's hybrid setup:**

1. **Use OTel Collector** for application traces and custom metrics from Spring Boot services — portable and vendor-neutral.
2. **Use Datadog Agent DaemonSet** for host metrics, Kubernetes infrastructure metrics, and PostgreSQL/Kafka JMX metrics.
3. **Route OTel Collector output** through the local Datadog Agent (`localhost:4317` to Agent's OTLP receiver) to avoid managing multiple API key references.
4. **Tag unification**: Ensure `env`, `service`, and `version` tags match between Agent and OTel Collector for proper correlation in the Datadog UI.

**Recommended Datadog Agent OTLP receiver config (when using hybrid):**

```yaml
# datadog agent datadog.yaml
otlp_config:
  receiver:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"
      http:
        endpoint: "0.0.0.0:4318"
```

---

## 3. APM Architecture & Data Flow

### OpenTelemetry → OTLP Protocol → Datadog Backend

The complete data flow for this project is:

```
┌──────────────────────────────────────────────────────────────────┐
│  Spring Boot Service (e.g., auth-service)                        │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │  opentelemetry-spring-boot-starter                      │     │
│  │                                                         │     │
│  │  HTTP Request                                           │     │
│  │      │                                                  │     │
│  │      ▼                                                  │     │
│  │  [Span Created] ──── parent context extracted           │     │
│  │      │               from W3C traceparent header        │     │
│  │      ▼                                                  │     │
│  │  [Child Spans]  ──── auto: JPA query, RestClient call   │     │
│  │      │               manual: business logic spans       │     │
│  │      ▼                                                  │     │
│  │  [SpanExporter] ──── BatchSpanProcessor                 │     │
│  │      │               (buffers spans in memory)          │     │
│  │      ▼                                                  │     │
│  │  OtlpHttpSpanExporter ──── http/protobuf                │     │
│  └──────────────────────────┬──────────────────────────────┘     │
│                             │ POST /v1/traces (OTLP/HTTP)        │
└─────────────────────────────┼────────────────────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │   OTel Collector   │
                    │   (k8s/otel/)      │
                    │                   │
                    │  Receiver: otlp   │
                    │  Processor: batch │
                    │  Exporter:        │
                    │   datadog         │
                    │   debug           │
                    └─────────┬──────────┘
                              │ HTTPS to Datadog intake
                    ┌─────────▼──────────┐
                    │   Datadog Cloud    │
                    │                   │
                    │  ┌─────────────┐  │
                    │  │  Trace      │  │
                    │  │  Ingestion  │  │
                    │  │  Pipeline   │  │
                    │  └──────┬──────┘  │
                    │         │         │
                    │  ┌──────▼──────┐  │
                    │  │  Indexing   │  │  ← Retention filters applied
                    │  │  (sampled)  │  │
                    │  └──────┬──────┘  │
                    │         │         │
                    │  ┌──────▼──────┐  │
                    │  │  APM UI     │  │
                    │  │  Service Map│  │
                    │  │  Traces     │  │
                    │  └─────────────┘  │
                    └───────────────────┘
```

### Trace Ingestion and Processing

When a span arrives at Datadog:

1. **Ingestion**: Raw spans are received and de-duplicated. All spans pass through the ingestion pipeline regardless of sampling.
2. **Trace assembly**: Spans sharing the same `trace_id` are grouped into a complete trace.
3. **Retention sampling**: Datadog applies **Intelligent Sampling** or **Custom Retention Filters** to decide which traces to index for long-term storage and search.
4. **Stats computation**: 100% of ingested spans contribute to **APM Statistics** (error rates, latency distributions, throughput) — even unindexed spans count toward metrics.
5. **Service enrichment**: Service names, resource names, and tags are extracted from span attributes.

**Key sampling concepts:**

| Concept | Description |
|---------|-------------|
| **Ingested spans** | All spans received by Datadog — counted for billing |
| **Indexed spans** | Spans retained for search in the Trace Explorer |
| **APM Statistics** | Computed from 100% of ingested spans — always accurate |
| **Head-based sampling** | Sampling decision made at trace root, propagated downstream |
| **Tail-based sampling** | Decision made after trace is complete (requires Collector or Agent) |

### Service Map Construction

Datadog automatically builds the Service Map by analyzing:

1. **`service` tag** on each span — identifies the emitting service
2. **`peer.service` or outgoing span attributes** — identifies the downstream service
3. **`db.system`, `messaging.system`** — identifies external dependencies (PostgreSQL, Kafka)

For this project's services:
- `auth-service` → calls `user-profile-service` via RestClient (HTTP span)
- `auth-service` → writes to PostgreSQL (DB span)
- `auth-service` → publishes to Kafka (messaging span)
- `user-profile-service` → reads from Kafka (messaging span)

### Span Indexing and Storage

Datadog separates **ingestion** from **retention**:

```
Ingested Spans (100%)
        │
        ├──► APM Statistics (100% — no data loss for metrics)
        │
        └──► Retention Filters ──► Indexed Spans (configurable %)
                                          │
                                          └──► Trace Explorer / Search
```

**Default retention rules:**
- `Error traces`: 100% retained for 15 days
- `Rare traces` (unique service/operation combinations): 100% retained
- `High-throughput traces`: 1% sampled (configurable via retention filters)

### Data Retention and Costs

| Data Type | Default Retention | Notes |
|-----------|-------------------|-------|
| **Indexed spans** | 15 days | Searchable in Trace Explorer |
| **APM Statistics** | 15 months | Dashboards, monitors, SLOs |
| **Live tail** | Last 15 minutes | Real-time without retention cost |
| **Custom metrics** | 15 months | From Micrometer/StatsD |
| **Logs** | 3–15 days (configurable) | Log Management pricing |

**Cost optimization knobs:**

1. **Ingestion**: Reduce `otel.traces.sampler` from `always_on` to `parentbased_traceidratio` with a ratio < 1.0 for high-traffic services.
2. **Retention filters**: Keep 100% for errors, reduce sampling for healthy high-throughput endpoints.
3. **App Analytics**: Only index spans you need to search; use APM Statistics for dashboards.

---

## 4. Service Map & Visualization

### Understanding Service Dependencies

The Datadog Service Map is automatically populated from trace data. For this project, the expected topology is:

```
              ┌───────────────┐
              │  api-gateway  │
              └──────┬────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
 ┌────────────┐ ┌──────────────┐ ┌────────────────┐
 │auth-service│ │user-profile  │ │dashboard-service│
 └──────┬─────┘ └──────┬───────┘ └────────────────┘
        │              │
        ▼              ▼
  ┌──────────┐   ┌──────────┐
  │PostgreSQL│   │  Kafka   │
  └──────────┘   └──────────┘
        │
        ▼
┌───────────────┐  ┌──────────────────┐
│ audit-log     │  │ notification      │
│ service       │  │ service           │
└───────────────┘  └──────────────────┘
```

### Distributed Trace Viewing in Datadog UI

To view a trace in the Datadog UI:
1. Navigate to **APM → Traces**
2. Filter by `service:auth-service`
3. Click on any trace to open the **Trace Detail** view
4. The flame graph shows all spans in the trace across services

**Trace detail panels:**
- **Flame Graph**: Visual timeline of spans, colored by service
- **Span List**: Tabular view with latency for each span
- **Infrastructure**: Host/container metrics at the time of the trace
- **Logs**: Correlated log events (requires trace ID injection into logs)
- **Metrics**: Service-level metrics for the trace's time window

### Flame Graphs and Execution Timeline

A flame graph represents the call hierarchy and timing of a distributed trace:

```
Trace: POST /api/login (total: 245ms)
│
├── auth-service: AuthController.login()         [0ms → 245ms]  245ms
│   ├── auth-service: UserRepository.findByEmail [5ms → 22ms]    17ms  ← JPA query
│   │   └── postgresql: SELECT users WHERE email  [5ms → 19ms]   14ms  ← DB span
│   ├── auth-service: PermissionService.check()  [25ms → 40ms]   15ms
│   ├── auth-service: RestClient → user-profile  [45ms → 200ms] 155ms  ← HTTP span
│   │   └── user-profile: GET /profile/{id}      [50ms → 195ms] 145ms
│   │       ├── user-profile: Redis.get()         [52ms → 55ms]    3ms
│   │       └── user-profile: DB.findProfile()   [60ms → 190ms] 130ms  ← slow!
│   └── auth-service: Kafka.publish(login-event) [205ms → 215ms] 10ms
```

In this trace, the bottleneck is `user-profile: DB.findProfile()` at 130ms — a missing database index candidate.

### Performance Bottleneck Identification

Datadog surfaces bottlenecks through:

1. **P99 Latency on Service Map**: Each service node shows p50/p75/p99 latency. High p99 vs p50 indicates tail latency issues.
2. **Top Slowest Traces**: Trace Explorer sorted by duration shows the worst outliers.
3. **Hotspot Analysis**: Spans taking >10% of total trace time are flagged.
4. **Database calls**: `db.statement` tag on spans reveals slow queries.
5. **Downstream dependencies**: High latency in a downstream service propagates up to all callers.

### How This Project's Traces Appear in Datadog

With the configuration in this project, Datadog will display:

| Service Name | Resource Name Pattern | Span Type |
|-------------|-----------------------|-----------|
| `auth-service` | `POST /api/login` | web |
| `auth-service` | `SELECT auth_db.users` | sql |
| `auth-service` | `GET http://user-profile-service/profile/{id}` | http |
| `auth-service` | `kafka.produce login-events` | queue |
| `user-profile-service` | `GET /profile/{id}` | web |
| `user-profile-service` | `kafka.consume user-events` | queue |

**Tags applied by OpenTelemetry auto-instrumentation:**
- `http.method`, `http.url`, `http.status_code`
- `db.system: postgresql`, `db.name`, `db.statement`
- `messaging.system: kafka`, `messaging.destination`
- `net.peer.name`, `net.peer.port`

---

## 5. Custom Metrics & Logging

### Micrometer Integration with Datadog

Spring Boot Actuator uses Micrometer as the metrics facade. With `micrometer-tracing-bridge-otel`, Micrometer metrics are exported via the OTLP exporter:

```yaml
# application.yml — Actuator + metrics configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENV:local}
```

**Built-in metrics automatically exported:**

| Metric | Description |
|--------|-------------|
| `http.server.requests` | Request count/duration by path, method, status |
| `jvm.memory.used` | JVM heap and non-heap memory |
| `jvm.gc.pause` | GC pause durations |
| `system.cpu.usage` | Process CPU utilization |
| `hikaricp.connections.*` | HikariCP connection pool stats |
| `spring.kafka.listener.*` | Kafka consumer lag, records consumed |

### Sending Custom Business Metrics

```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

@Service
public class AuthService {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Timer tokenGenerationTimer;

    public AuthService(MeterRegistry registry) {
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

    public LoginResponse login(LoginRequest request) {
        try {
            LoginResponse response = tokenGenerationTimer.record(() -> doLogin(request));
            loginSuccessCounter.increment();
            return response;
        } catch (AuthException e) {
            loginFailureCounter.increment(Tags.of("reason", e.getReason()));
            throw e;
        }
    }
}
```

These metrics will appear in Datadog as:
- `auth.login.success` (count)
- `auth.login.failure` (count, tagged with `reason`)
- `auth.token.generation` (histogram with p50/p95/p99)

### JSON Structured Logging for Datadog

This project uses `logstash-logback-encoder` (version 8.0 from `pom.xml`) for structured JSON logging. Datadog's Log Management parses JSON logs automatically.

**Logback configuration** (`src/main/resources/logback-spring.xml`):

```xml
<configuration>
    <springProperty name="APP_NAME" source="spring.application.name" defaultValue="unknown"/>

    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <providers>
                <timestamp>
                    <fieldName>timestamp</fieldName>
                    <pattern>yyyy-MM-dd'T'HH:mm:ss.SSSZ</pattern>
                </timestamp>
                <logLevel/>
                <loggerName>
                    <fieldName>logger</fieldName>
                </loggerName>
                <message/>
                <threadName>
                    <fieldName>thread</fieldName>
                </threadName>
                <stackTrace>
                    <fieldName>error.stack_trace</fieldName>
                </stackTrace>
                <!-- OTel trace correlation -->
                <mdc/>
                <keyValuePairs/>
                <!-- Service metadata -->
                <pattern>
                    <pattern>
                        {
                          "service": "${APP_NAME}",
                          "dd.trace_id": "%mdc{trace_id}",
                          "dd.span_id": "%mdc{span_id}"
                        }
                    </pattern>
                </pattern>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

**Sample JSON log output:**

```json
{
  "timestamp": "2026-07-19T08:30:00.000+0000",
  "level": "INFO",
  "logger": "com.example.auth.AuthController",
  "message": "User login successful",
  "service": "auth-service",
  "thread": "virtual-1",
  "dd.trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "dd.span_id": "00f067aa0ba902b7",
  "userId": "user-123",
  "environment": "prod"
}
```

### Log Correlation with Traces

When `dd.trace_id` and `dd.span_id` are present in log entries, Datadog automatically links logs to their corresponding traces. In the Trace Detail view, the **Logs** tab shows all log entries from within that trace's time window that share the same trace ID.

**OpenTelemetry trace context injection** into MDC is handled automatically by `micrometer-tracing-bridge-otel` when using Spring Boot's logging auto-configuration. The MDC keys `trace_id` and `span_id` are populated for each log statement within an active span.

**Verify MDC injection** by adding a log statement inside a traced method:

```java
@GetMapping("/profile/{id}")
public UserProfile getProfile(@PathVariable String id) {
    log.info("Fetching user profile"); // trace_id and span_id auto-injected into MDC
    return profileService.findById(id);
}
```

### Logback + logstash-logback-encoder Setup in This Project

Maven dependency (from `pom.xml`):

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

The encoder outputs ECS-compatible JSON that Datadog's log pipelines parse out-of-the-box, extracting:
- Log level → `status`
- Logger name → `logger.name`
- Thread name → `logger.thread_name`
- Stack trace → `error.stack_trace`, `error.message`, `error.kind`

---

## 6. SLO/SLI Monitoring

### SLO (Service Level Objective) Definition

An **SLO** is a target percentage of time a service must meet a specific reliability criterion. It represents a formal commitment between the service team and its stakeholders.

**Examples for this project:**
- "99.9% of login requests complete in under 500ms over a 30-day window"
- "99.5% availability for auth-service over a 30-day window"
- "Error rate below 1% for all API endpoints over a 7-day window"

### SLI (Service Level Indicator) Metrics

An **SLI** is the actual measured value that determines whether the SLO is being met.

| SLO | SLI | Measurement |
|-----|-----|-------------|
| 99.9% availability | `http.server.requests[status!=5xx] / http.server.requests[total]` | APM stats |
| P99 < 500ms | `trace.duration p99` for `resource:POST /api/login` | APM stats |
| Error rate < 1% | `trace.http.request.errors / trace.http.request` | APM stats |

### Error Rate, Latency, Availability SLOs

**Error Rate SLO:**

```
SLO Target: 99% (error rate < 1%)
SLI: (total_requests - error_requests) / total_requests

Good events:  HTTP 2xx and 3xx responses
Bad events:   HTTP 5xx responses
Window:       Rolling 30 days
Alert threshold: 99.5% (burn rate alert at 2× error budget consumption)
```

**Latency SLO:**

```
SLO Target: 95% of requests complete under 300ms
SLI: count(duration < 300ms) / count(all_requests)

Good events:  span.duration < 300ms
Bad events:   span.duration >= 300ms
Window:       Rolling 7 days
Alert threshold: Burn rate > 14.4× over 1 hour
```

**Availability SLO:**

```
SLO Target: 99.9% (maximum 43.8 minutes downtime/month)
SLI: successful_health_checks / total_health_checks
Source: Datadog Synthetics or service_check monitor

Window: Rolling 30 days
```

### How to Define SLOs in Datadog UI

1. Navigate to **Service Management → SLOs**
2. Click **New SLO**
3. Select SLO type:
   - **Metric-based**: Uses metric queries (best for custom SLIs)
   - **Monitor-based**: Passes/fails based on monitor state
4. Configure:
   - **Numerator**: `sum:trace.http.request.hits{service:auth-service,!http.status_class:5xx}.as_count()`
   - **Denominator**: `sum:trace.http.request.hits{service:auth-service}.as_count()`
   - **Target**: 99.9%
   - **Time window**: 30 days
5. Add tags: `service:auth-service`, `env:prod`, `team:platform`
6. Enable **error budget alerts**: Alert at 2× burn rate

### Terraform SLO Provisioning

```hcl
# terraform/datadog.tf — add SLO resources

resource "datadog_service_level_objective" "auth_availability" {
  name        = "[${var.environment}] auth-service Availability SLO"
  type        = "metric"
  description = "99.9% of auth-service requests succeed (non-5xx)"

  query {
    numerator   = "sum:trace.http.request.hits{service:auth-service,!http.status_class:5xx}.as_count()"
    denominator = "sum:trace.http.request.hits{service:auth-service}.as_count()"
  }

  thresholds {
    timeframe       = "30d"
    target          = 99.9
    warning         = 99.95
  }

  tags = merge(local.common_tags, {
    slo_type = "availability"
  })
}

resource "datadog_service_level_objective" "auth_latency" {
  name        = "[${var.environment}] auth-service Latency SLO"
  type        = "metric"
  description = "95% of login requests complete under 500ms"

  # NOTE: A true count-based latency SLO (good/total spans below a threshold)
  # requires either a monitor-based SLO using a latency threshold monitor, or
  # a custom metric emitted by the application that counts requests under the
  # latency threshold. The query below uses a monitor-based availability proxy
  # as a starting point; replace with a custom latency histogram metric for
  # precise per-request latency SLO tracking.
  query {
    numerator   = "sum:trace.http.request.hits{service:auth-service,resource_name:post_/api/login,!http.status_class:5xx}.as_count()"
    denominator = "sum:trace.http.request.hits{service:auth-service,resource_name:post_/api/login}.as_count()"
  }

  thresholds {
    timeframe = "7d"
    target    = 95.0
    warning   = 97.0
  }

  tags = merge(local.common_tags, {
    slo_type = "latency"
  })
}
```

> **Latency SLO note**: Datadog metric-based SLOs measure ratios (good events / total events). For a latency SLO measuring "95% of requests under 500ms", the preferred approach is a **monitor-based SLO** backed by a threshold monitor on `trace.http.request.duration{service:auth-service}.p99 < 500`. Alternatively, emit a custom counter metric from the application that increments for each request completing under the threshold, and use that as the numerator.

**SLO burn rate alert:**

```hcl
resource "datadog_monitor" "slo_burn_rate" {
  name    = "[${var.environment}] auth-service SLO Burn Rate Alert"
  type    = "slo alert"

  query = <<-EOT
    burn_rate("${datadog_service_level_objective.auth_availability.id}").over("1h") > 14.4
  EOT

  message = <<-EOT
    🔥 SLO burn rate critically high for auth-service!
    
    Current burn rate: {{value}}× (threshold: 14.4×)
    Error budget at risk of exhaustion within 1 hour.
    
    Notify: @pagerduty @slack-oncall
  EOT

  thresholds = {
    critical = 14.4
    warning  = 7.2
  }

  tags = merge(local.common_tags, {
    alert_type = "slo"
  })
}
```

---

## 7. Terraform Integration & Infrastructure as Code

### Datadog Provider Setup

The Terraform configuration in `terraform/providers.tf` uses the official Datadog provider:

```hcl
# terraform/providers.tf
terraform {
  required_version = ">= 1.5"
  required_providers {
    datadog = {
      source  = "DataDog/datadog"
      version = "~> 3.45"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "~> 4.2"
    }
  }
}

provider "datadog" {
  api_key = data.vault_generic_secret.datadog.data["api_key"]
  app_key = data.vault_generic_secret.datadog.data["app_key"]
  api_url = var.datadog_api_url  # e.g., https://api.datadoghq.com
}
```

**Available Datadog Terraform resources:**

| Resource | Purpose |
|----------|---------|
| `datadog_monitor` | Alert monitors (metric, APM, log) |
| `datadog_service_level_objective` | SLO definitions |
| `datadog_dashboard` | Custom dashboards |
| `datadog_synthetics_test` | Synthetic monitoring |
| `datadog_logs_index` | Log retention configuration |
| `datadog_metric_tag_configuration` | Custom metric tag cardinality |
| `datadog_service_definition_yaml` | Service Catalog entries |

### Vault Integration for Secrets

Datadog credentials are retrieved from HashiCorp Vault to avoid storing them in plaintext or environment variables:

```hcl
# terraform/vault.tf
data "vault_generic_secret" "datadog" {
  path = var.vault_secret_path    # default: "secret/datadog"
}

locals {
  datadog_api_key = try(data.vault_generic_secret.datadog.data["api_key"], null)
  datadog_app_key = try(data.vault_generic_secret.datadog.data["app_key"], null)
}
```

**Setup Vault secrets for Terraform:**

```bash
# Initialize Vault (dev mode for local testing)
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=root

# Store Datadog credentials
vault kv put secret/datadog \
  api_key=your-datadog-api-key \
  app_key=your-datadog-app-key

# Verify
vault kv get secret/datadog
```

**Required Vault policies for Terraform:**

```hcl
# vault-policy.hcl
path "secret/data/datadog" {
  capabilities = ["read"]
}
path "secret/data/terraform-cloud" {
  capabilities = ["read"]
}
```

### Monitors and Notification Channels

The project defines 7 monitors in `terraform/datadog.tf`:

| Monitor | Type | Priority | Threshold |
|---------|------|----------|-----------|
| High Error Rate | `error_tracking` | P1 | >5% error rate |
| High Latency | `latency` | P2 | p99 > 1000ms |
| Service Unavailability | `service_check` | P1 | Service down |
| Vault Access Failures | `metric_alert` | P1 | >5 Vault errors |
| OTel Span Backlog | `metric_alert` | P2 | >1000 pending spans |
| DB Connection Exhaustion | `metric_alert` | P1 | >80% pool utilization |
| JVM Memory Pressure | `metric_alert` | P1 | >85% heap usage |

**Monitor message template pattern** (from `terraform/datadog.tf`):

```hcl
message = <<-EOT
  ⚠️ Error rate is high (>5%) on {{service.name}}

  {{#is_alert}}Alert triggered at {{alert_transition_date}}{{/is_alert}}
  {{#is_recovery}}Alert recovered at {{alert_transition_date}}{{/is_recovery}}

  Environment: ${var.environment}
  Service: {{service.name}}
  
  Notify: @pagerduty @slack-team
EOT
```

**Common Datadog template variables:**

| Variable | Description |
|----------|-------------|
| `{{service.name}}` | Service name from trace tag |
| `{{host.name}}` | Hostname where alert triggered |
| `{{value}}` | Current metric value |
| `{{threshold}}` | Alert threshold value |
| `{{alert_transition_date}}` | Time when alert state changed |
| `{{#is_alert}}...{{/is_alert}}` | Content shown only on alert |
| `{{#is_recovery}}...{{/is_recovery}}` | Content shown only on recovery |

### Service-Level Alerts

For multi-service alerting with per-service context, use multi-alert monitors:

```hcl
resource "datadog_monitor" "per_service_error_rate" {
  name  = "[${var.environment}] Per-Service Error Rate"
  type  = "metric alert"
  query = "avg(last_5m):sum:trace.http.request.errors{env:${var.environment}} by {service}.as_rate() / sum:trace.http.request.hits{env:${var.environment}} by {service}.as_rate() > 0.05"

  message = <<-EOT
    🚨 High error rate on {{service.name}}
    
    Current error rate: {{value}}
    Threshold: 5%
    
    Runbook: https://wiki.example.com/runbooks/high-error-rate
    
    Notify: @pagerduty
  EOT

  # Multi-alert: fires separately per service
  options {
    enable_logs_sample   = true
    groupby_simple_monitor = false
  }

  tags = local.common_tags
}
```

### State Management and Best Practices

**Remote state with Terraform Cloud** (`terraform/cloud-backend.tf`):

```hcl
# terraform/cloud-backend.tf
terraform {
  cloud {
    organization = var.terraform_cloud_org

    workspaces {
      name = var.terraform_cloud_workspace  # "spring-datadog-lab"
    }
  }
}
```

**State management best practices:**

1. **Remote state**: Use Terraform Cloud or S3 backend — never commit `terraform.tfstate` to Git.
2. **State locking**: Terraform Cloud provides automatic state locking to prevent concurrent applies.
3. **Sensitive variables**: Store `vault_token`, `github_token` in Terraform Cloud workspace variables (not `.tfvars` files).
4. **Workspace per environment**: Use separate workspaces for `dev`, `staging`, `prod`.
5. **Plan before apply**: Always run `terraform plan` and review before `terraform apply`.

**Local development workflow:**

```bash
cd terraform/

# Copy and fill in example vars
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values

# Initialize providers
terraform init

# Preview changes
terraform plan -var-file=terraform.tfvars

# Apply
terraform apply -var-file=terraform.tfvars

# Destroy test resources
terraform destroy -var-file=terraform.tfvars
```

### Examples from This Project's `terraform/`

**providers.tf** — Defines Terraform version requirements, Datadog and Vault providers.  
**vault.tf** — Reads Datadog API/App keys from Vault at `secret/datadog`.  
**datadog.tf** — Defines 7 monitors for error rate, latency, availability, Vault, OTel, DB, JVM.  
**variables.tf** — Input variables with defaults for environment, Vault address, notification channels.  
**github.tf** — GitHub repository configuration as IaC (branch protection, webhooks).  
**cloud-backend.tf** — Optional Terraform Cloud remote state backend configuration.  
**vault-approle.tf** — AppRole authentication for automated Vault access.  
**vault-jwt-auth.tf** — GitHub Actions OIDC JWT authentication to Vault.  
**vault-k8s-auth.tf** — Kubernetes service account authentication to Vault.

---

## 8. Troubleshooting & Common Issues

### Traces Not Appearing in Datadog

**Checklist:**

```
□ Is the OTel Collector running and reachable?
  → kubectl get pods -n spring-datadog-lab | grep otel
  → curl -v http://otel-collector:4318/v1/traces

□ Is DD_API_KEY set correctly in the collector?
  → kubectl get secret dd-api-key -n spring-datadog-lab
  → Check collector logs: kubectl logs deployment/otel-collector

□ Is the OTLP endpoint configured in application.yml?
  → otel.exporter.otlp.endpoint must point to collector

□ Is the service name being set?
  → spring.application.name maps to the 'service' tag
  → Check: otel.service.name property or OTEL_SERVICE_NAME env var

□ Is sampling set to always_on?
  → otel.traces.sampler: always_on (for debugging)

□ Are spans being created?
  → Enable debug exporter in collector to see spans in logs
  → Set logging level: logging.level.io.opentelemetry=DEBUG

□ Check Datadog Live Tail
  → APM → Traces → Live Tail for real-time trace ingestion status
```

**Common fix: Wrong OTLP endpoint path**

```yaml
# ❌ Wrong — missing signal path
otel.exporter.otlp.endpoint: http://otel-collector:4318

# ✅ Correct — include /v1/traces for HTTP exporter
otel.exporter.otlp.endpoint: http://otel-collector:4318/v1/traces
```

**Verify with collector debug exporter:**

```yaml
# k8s/otel/collector-config.yaml — temporary debug setup
exporters:
  debug:
    verbosity: detailed    # Logs all received spans to stdout
  datadog:
    api:
      key: ${DD_API_KEY}
      site: ${DD_SITE}
```

### Missing Metrics or Logs

**Metrics not appearing:**

```
□ Is Micrometer OTLP export configured?
  → Verify management.otlp.metrics.export.url in application.yml

□ Is the collector metrics pipeline enabled?
  → Check service.pipelines.metrics in collector-config.yaml

□ Are metrics tags correct?
  → Verify management.metrics.tags.application property

□ DogStatsD metrics (if using hybrid):
  → Verify DD_AGENT_HOST env var is set
  → Check UDP connectivity to port 8125
  → Confirm statsd flavor is set to 'datadog'
```

**Logs not correlating with traces:**

```
□ Is logstash-logback-encoder on the classpath?
  → Check pom.xml for logstash-logback-encoder dependency

□ Is trace context injected into MDC?
  → micrometer-tracing-bridge-otel must be on the classpath
  → The OTel bridge automatically populates trace_id and span_id in MDC

□ Does the JSON log contain dd.trace_id and dd.span_id fields?
  → Test locally: curl -s -H "X-Tenant-Id: acme" -X POST http://localhost:9000/api/v1/auth/login -d '{"username":"testuser","password":"testpass"}' -H "Content-Type: application/json" | jq '.'
  → Check console output for trace_id in the JSON

□ Are logs being sent to Datadog Log Management?
  → OTel logs pipeline must be enabled in collector-config.yaml
  → Or configure Datadog Agent log collection from Kubernetes pod logs
```

### Sampling and Rate Limiting

**Head-based sampling configuration:**

```yaml
# Always send all traces (development/debugging)
otel:
  traces:
    sampler: always_on

# Sample 10% of traces (production high-traffic)
otel:
  traces:
    sampler: parentbased_traceidratio
    sampler-arg: "0.1"

# Never sample (disable tracing)
otel:
  traces:
    sampler: always_off
```

**Datadog-side sampling overrides** (retention filters):
- Error traces: 100% retained regardless of head-based sampling
- Rare operations: Automatically retained at 100% if infrequent
- Custom rules: Configurable by service/resource/tag combinations

**Rate limiting in OTel Collector:**

```yaml
processors:
  filter/drop_health:
    traces:
      span:
        - 'attributes["http.route"] == "/actuator/health"'
        - 'attributes["http.route"] == "/actuator/prometheus"'
  
  probabilistic_sampler:
    hash_seed: 22
    sampling_percentage: 10    # Sample 10% of traces
```

### Cost Optimization Strategies

| Strategy | Impact | Implementation |
|----------|--------|----------------|
| **Head-based sampling** | Reduce ingested spans by 90%+ | Set `sampler: parentbased_traceidratio` with ratio 0.1 |
| **Filter health checks** | Reduce noise | OTel Collector `filter` processor |
| **Retention filters** | Reduce indexed spans | Datadog UI: APM → Setup → Retention Filters |
| **Log sampling** | Reduce log volume | Logback `SamplingFilter` or OTel `filter` processor |
| **APM Statistics only** | No indexed spans needed for dashboards | Disable indexing for specific services |
| **Tag cardinality control** | Reduce custom metric costs | Avoid high-cardinality tags (user ID, request ID) |

### Connectivity Issues

**OTel Collector → Datadog connectivity:**

```bash
# Test from collector pod
kubectl exec -it deployment/otel-collector -n spring-datadog-lab -- \
  curl -v https://trace.agent.datadoghq.com

# Check collector logs for export errors
kubectl logs deployment/otel-collector -n spring-datadog-lab | grep -i error

# Verify API key validity (this lab's site is us5, not the default datadoghq.com)
curl -v -X GET "https://api.us5.datadoghq.com/api/v1/validate" \
  -H "DD-API-KEY: ${DD_API_KEY}"
```

**Service → Collector connectivity:**

```bash
# Test OTLP HTTP endpoint
curl -v http://otel-collector:4318/v1/traces \
  -H "Content-Type: application/x-protobuf" \
  -d ""  # Empty body — expect 400, not connection refused

# Test OTLP gRPC endpoint
grpcurl -plaintext otel-collector:4317 list
```

**Firewall/Network Policy checklist:**

```
□ Kubernetes NetworkPolicy allows pods → otel-collector on ports 4317/4318
□ OTel Collector pod → outbound HTTPS (443) to *.datadoghq.com
□ Datadog Agent DaemonSet ports 8125 (UDP) and 8126 (TCP) open if using hybrid
□ Vault address reachable from Terraform runner (port 8200 or 443)
```

---

## 9. Hands-On Integration Walkthrough

### Step 1: Start Local Services with Docker Compose

```bash
# Clone and enter the project
cd spring-datadog-lab/

# Set required environment variables (or copy .env.example -> .env)
export DD_API_KEY=your-datadog-api-key
export DD_SITE=us5.datadoghq.com

# Start infrastructure (PostgreSQL, Jaeger v2, OTel Collector, Vault, Kafka)
docker compose up -d

# Verify all services are healthy
docker compose ps
```

Expected output:
```
NAME               STATUS
postgres           running
jaeger             running
otel-collector     running
vault              running
kafka              running
kafka-ui           running
```

Local UIs (host ports remapped to the **9xxx** range so the lab coexists with FleetForge):
- Jaeger: `http://localhost:9668`
- Kafka UI: `http://localhost:9088`
- Vault: `http://localhost:9200` (`token=root`)

### Step 2: Run auth-service and user-profile-service

```bash
# Start auth-service (listens on :9180)
cd auth-service/
./mvnw spring-boot:run

# In another terminal, start user-profile-service (listens on :9082)
cd user-profile-service/
./mvnw spring-boot:run
```

Or use Skaffold for Kubernetes deployment:

```bash
# Build and deploy to local Kubernetes cluster (requires k8s context)
skaffold dev
```

### Step 3: Send Traces to Local Datadog Agent

Generate test traffic through the **API gateway** (`:9000`) to create traces:

```bash
# Register + login (creates traces across api-gateway, auth-service, user-profile-service)
curl -X POST http://localhost:9000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"ssoId": "sso-testuser", "username": "testuser", "password": "testpass"}'

curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username": "testuser", "password": "testpass"}'

# Generate multiple requests to build the service map
for i in {1..20}; do
  curl -s http://localhost:9000/actuator/health
  curl -s http://localhost:9082/actuator/health
  sleep 0.5
done

# Trigger an error trace (for error rate testing)
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username": "invalid", "password": "wrong"}'
```

### Step 4: Verify Traces in Datadog UI

1. Log in to [us5.datadoghq.com](https://us5.datadoghq.com) (this lab's Datadog site — not `app.datadoghq.com`)
2. Navigate to **APM → Traces**
3. Filter by `env:local` (or no env filter)
4. You should see services: `api-gateway`, `auth-service`, `user-profile-service`, `notification-service`, `audit-log-service`
5. Click on a trace to view the flame graph
6. Navigate to **APM → Service Map** to see the service topology

**Verify trace propagation between services:**
- Find a trace for `POST /api/login`
- Expand the flame graph to see spans from both `auth-service` and `user-profile-service`
- Both services should share the same `trace_id`

### Step 5: Create Custom Dashboards

```
1. Navigate to: Dashboards → New Dashboard
2. Name: "Spring Datadog Lab - Overview"
3. Add widgets:

   Widget 1: Timeseries
   - Metric: trace.http.request.hits{service:auth-service}
   - Title: "auth-service Request Rate"

   Widget 2: Timeseries  
   - Metric: trace.http.request.errors{service:auth-service}
   - Title: "auth-service Error Rate"

   Widget 3: Query Value
   - Metric: p99:trace.http.request.duration{service:auth-service}
   - Title: "p99 Latency"

   Widget 4: Service Map
   - Filter: env:local
   - Title: "Service Topology"

   Widget 5: Log Stream
   - Query: service:auth-service status:error
   - Title: "Recent Errors"
```

### Step 6: Set Up Alerts via Terraform

```bash
cd terraform/

# Initialize Terraform
terraform init

# Create terraform.tfvars from example
cp terraform.tfvars.example terraform.tfvars

# Edit terraform.tfvars with your values:
# vault_address = "http://localhost:8200"
# vault_token = "root"  (for dev Vault)
# environment = "local"

# Preview what will be created
terraform plan -var-file=terraform.tfvars

# Create monitors in Datadog
terraform apply -var-file=terraform.tfvars
```

After `terraform apply`, navigate to **Monitors → Manage Monitors** in Datadog to see the 7 monitors created by the project.

### Step 7: Validate Log Correlation

```bash
# Send a request and capture the trace ID from logs
curl -v http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: acme" \
  -d '{"username": "testuser", "password": "testpass"}' 2>&1

# Check service logs for trace ID
docker compose logs auth-service | grep trace_id | tail -5
```

In the Datadog UI:
1. Navigate to **APM → Traces** and find the trace
2. Click the trace to open detail view
3. Click the **Logs** tab to see correlated log entries
4. The `dd.trace_id` in the log should match the trace ID

---

## 10. Certification Exam Tips

### Key Datadog APM Concepts for Exam

**Core Architecture:**
- Understand the difference between **ingested spans** (all spans) and **indexed spans** (retained for search)
- Know that APM Statistics are computed from 100% of ingested spans, not just indexed spans
- Understand head-based vs tail-based sampling and when to use each
- Know the role of the Datadog Agent vs OTel Collector in trace ingestion

**Data Model:**
- **Service**: The logical unit of monitoring (maps to `service` tag / `spring.application.name`)
- **Resource**: Operation within a service (e.g., `POST /api/login`, `SELECT users`)
- **Span**: Single unit of work with start/end time, tags, and status
- **Trace**: Collection of spans sharing the same `trace_id`
- **Root span**: First span in a trace (no parent span ID)

**Tags and Unified Service Tagging:**
- The three required tags for full Datadog correlation: `env`, `service`, `version`
- These must match between APM, Metrics, Logs, and Infrastructure
- Set via `OTEL_RESOURCE_ATTRIBUTES=deployment.environment=prod,service.version=1.0.0`

**OTLP Integration:**
- Know which OTLP signals Datadog accepts: traces, metrics, logs
- Understand gRPC (port 4317) vs HTTP (port 4318) OTLP receivers
- Know that the Datadog exporter in OTel Collector translates OTLP to Datadog format
- The `DD_SITE` variable determines the regional Datadog intake endpoint

**SLOs:**
- Metric-based SLOs use APM Statistics queries (recommended for latency/error rate)
- Monitor-based SLOs are simpler but less granular
- Error budget = (1 - SLO target) × time window duration
- Burn rate alerts fire before the error budget is fully consumed

### Common Exam Question Patterns

**Q: A service is sending traces but they don't appear in Datadog. The OTel Collector shows no errors. What's the most likely cause?**
> A: The `service.name` OTLP attribute (or `spring.application.name`) is missing or not mapped correctly. Datadog requires a `service` tag on all spans.

**Q: You want to reduce Datadog APM costs for a high-traffic service while keeping full visibility into errors. What's the best approach?**
> A: Configure head-based sampling (e.g., 10% ratio via `parentbased_traceidratio`) and set a Datadog retention filter to keep 100% of error traces. APM Statistics still capture 100% of traffic for dashboards.

**Q: What is the difference between an ingested span and an indexed span in Datadog?**
> A: Ingested spans are all spans received by Datadog (100%), used for APM Statistics. Indexed spans are a subset retained for long-term search in Trace Explorer, based on retention filter rules.

**Q: How does Datadog correlate a log entry with a specific distributed trace?**
> A: Through the `dd.trace_id` and `dd.span_id` fields in the log entry. When these fields are present in a JSON log, Datadog automatically links the log to the corresponding trace in the Trace Explorer.

**Q: In a Kubernetes deployment, what is the recommended way to inject Datadog Agent host information into your application?**
> A: Use the Downward API to inject `DD_AGENT_HOST` from the pod spec:
> ```yaml
> env:
>   - name: DD_AGENT_HOST
>     valueFrom:
>       fieldRef:
>         fieldPath: status.hostIP
> ```

**Q: What does the `parentbased_traceidratio` sampler do in OpenTelemetry?**
> A: It respects the sampling decision of the parent span (if any), and for root spans, samples based on the configured ratio (e.g., 0.1 = 10%). This ensures consistent sampling across distributed services.

**Q: Which Terraform resource creates a Datadog monitor for APM error rates?**
> A: `datadog_monitor` with type `"metric alert"` and a query using `trace.http.request.errors` and `trace.http.request.hits` metrics.

**Q: What is the purpose of DogStatsD in the Datadog ecosystem?**
> A: DogStatsD is a StatsD-compatible metrics server embedded in the Datadog Agent that receives custom metrics from applications over UDP port 8125. It supports counters, gauges, histograms, sets, and distributions with Datadog-specific tags.

### Best Practices Checklist

**Instrumentation:**
```
□ Use Unified Service Tagging: env, service, version on all spans
□ Apply semantic conventions for HTTP, DB, messaging attributes
□ Set span status to ERROR for exceptions (auto-handled by OTel starter)
□ Add custom attributes for business context (user.id, tenant.id)
□ Use baggage for cross-service correlation attributes
□ Filter health check endpoints from tracing
```

**Sampling:**
```
□ Use always_on only in development/debugging
□ Use parentbased_traceidratio (0.05–0.20) for production
□ Keep 100% error traces via Datadog retention filters
□ Filter noise (health checks, metrics endpoints) at the Collector
□ Monitor ingestion costs via Datadog Estimated Usage metrics
```

**Monitoring:**
```
□ Define SLOs for error rate, latency, and availability
□ Create burn rate alerts (2× and 14.4× burn rates)
□ Use multi-alert monitors for per-service alerting
□ Include runbook URLs in monitor messages
□ Test alerts with terraform plan/apply before production
```

**Logging:**
```
□ Use JSON structured logging (logstash-logback-encoder)
□ Inject dd.trace_id and dd.span_id into all log entries
□ Include service, env, version in log records
□ Avoid logging sensitive data (passwords, tokens, PII)
□ Configure appropriate log retention per environment
```

**IaC (Terraform):**
```
□ Store Datadog credentials in Vault, never in .tfvars files
□ Use remote state (Terraform Cloud or S3 backend)
□ Tag all Datadog resources with environment and managed_by=terraform
□ Use separate Terraform workspaces per environment
□ Run terraform plan before every apply in CI/CD
```

### Resources and Documentation Links

**Official Datadog Documentation:**
- [Datadog APM Overview](https://docs.datadoghq.com/tracing/)
- [OTLP Ingestion](https://docs.datadoghq.com/opentelemetry/otlp_ingest_in_the_agent/)
- [Trace Sampling](https://docs.datadoghq.com/tracing/trace_pipeline/ingestion_mechanisms/)
- [Service Level Objectives](https://docs.datadoghq.com/service_management/service_level_objectives/)
- [Unified Service Tagging](https://docs.datadoghq.com/getting_started/tagging/unified_service_tagging/)
- [Terraform Provider](https://registry.terraform.io/providers/DataDog/datadog/latest/docs)
- [Log Correlation](https://docs.datadoghq.com/tracing/other_telemetry/connect_logs_and_traces/)
- [DogStatsD](https://docs.datadoghq.com/developers/dogstatsd/)

**OpenTelemetry Documentation:**
- [OTLP Specification](https://opentelemetry.io/docs/specs/otlp/)
- [OTel Collector Configuration](https://opentelemetry.io/docs/collector/configuration/)
- [Datadog Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/datadogexporter)
- [Spring Boot Instrumentation](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
- [Sampling](https://opentelemetry.io/docs/concepts/sampling/)

**Certification Resources:**
- [Datadog APM Associate Certification](https://www.datadoghq.com/certification/)
- [Datadog Learning Center](https://learn.datadoghq.com/)
- [OpenTelemetry Certification](https://opentelemetry.io/certification/)

**This Project:**
- [OpenTelemetry Fundamentals Guide](./OPENTELEMETRY_FUNDAMENTALS.md) — Phase 1
- [Observability Lessons](./OBSERVABILITY_LESSONS.md) — architecture, diagrams, annotated Jaeger/Datadog screenshots
- [Observability Walkthrough](./OBSERVABILITY_WALKTHROUGH.md) — screenshot checklist + deep links
- [Local Observability Roadmap](./LOCAL_OBSERVABILITY_ROADMAP.md) — port map, phases, K8s notes
- [OTel Collector Config](../k8s/otel/collector-config.yaml)
- [Terraform Monitors](../terraform/datadog.tf)
- [Auth Service Config](../auth-service/src/main/resources/application.yml)
- [Docker Compose](../docker-compose.yml)
- [Terraform README](../terraform/README.md)

---

> **Next in Series**: Phase 3 — Spring vs Quarkus OTel Comparison (`docs/SPRING_vs_QUARKUS_OTEL.md`)
