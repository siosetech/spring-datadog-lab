locals {
  common_tags = {
    environment = var.environment
    project     = var.project_name
    managed_by  = "terraform"
  }
}

# Monitor: High Error Rate (>5% of requests)
resource "datadog_monitor" "high_error_rate" {
  name            = "[${var.environment}] High Error Rate - spring-datadog-lab"
  type            = "error_tracking"
  query           = "error_rate{service:auth-service OR service:user-profile-service OR service:audit-log-service OR service:notification-service OR service:dashboard-service} > 0.05"
  message         = "⚠️ Error rate is high (>5%) on {{service.name}}\n\n{{#is_alert}}Alert triggered at {{alert_transition_date}}{{/is_alert}}\n\nNotify: @pagerduty"
  escalation_message = "Error rate still high after 10 minutes. Escalating."
  priority        = "P1"
  include_tags    = true

  thresholds = {
    critical = 0.05
    warning  = 0.02
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "error_rate"
  })
}

# Monitor: High Latency (p99 > 1000ms)
resource "datadog_monitor" "high_latency" {
  name    = "[${var.environment}] High Latency - spring-datadog-lab"
  type    = "latency"
  query   = "trace.duration{service:auth-service OR service:user-profile-service}.rollup(avg) > 1000"
  message = "📊 High latency detected (p99 > 1000ms) on {{service.name}}\n\nService: {{service.name}}\nEndpoint: {{resource.name}}\n\nNotify: @slack-team"

  escalation_message = "Latency degradation continues. Check resource utilization."
  priority            = "P2"
  include_tags        = true

  thresholds = {
    critical = 1000
    warning  = 500
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "latency"
  })
}

# Monitor: Service Unavailability (>30s downtime)
resource "datadog_monitor" "service_down" {
  name    = "[${var.environment}] Service Unavailability - spring-datadog-lab"
  type    = "service_check"
  query   = "\"datadog.agent.up\"{service:auth-service OR service:user-profile-service}.by(service).last(4).count_by_status()"
  message = "🚨 Service is DOWN: {{service.name}}\n\nImmediate action required!\n\nNotify: @pagerduty @slack-oncall"

  escalation_message = "Critical: Service still down. Escalating."
  timeout_h           = 1
  priority            = "P1"
  include_tags        = true

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "service_health"
  })
}

# Monitor: Vault Secret Access Failures
resource "datadog_monitor" "vault_access_failures" {
  name    = "[${var.environment}] Vault Secret Access Failures"
  type    = "metric_alert"
  query   = "avg:vault.request.count{status:error}.as_count() > 5"
  message = "⚠️ Vault secret access failures detected\n\nService may lose connectivity to secrets.\n\nNotify: @devops"

  escalation_message = "Persistent Vault access issues. Check network and credentials."
  priority            = "P1"
  include_tags        = true

  thresholds = {
    critical = 5
    warning  = 2
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "vault"
  })
}

# Monitor: OTel Span Processing Backlog
resource "datadog_monitor" "otel_backlog" {
  name    = "[${var.environment}] OpenTelemetry Trace Backlog"
  type    = "metric_alert"
  query   = "avg:otel.exporter.sent_spans.pending{} > 1000"
  message = "📤 OpenTelemetry trace backlog building up\n\nMay indicate processing lag or export issues.\n\nNotify: @platform-team"

  escalation_message = "OTel backlog not clearing. Check exporter health."
  priority            = "P2"
  include_tags        = true

  thresholds = {
    critical = 1000
    warning  = 500
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "otel"
  })
}

# Monitor: Database Connection Pool Exhaustion
resource "datadog_monitor" "db_connection_pool" {
  name    = "[${var.environment}] Database Connection Pool Exhaustion"
  type    = "metric_alert"
  query   = "avg:db.pool.max_connections{} / avg:db.pool.available_connections{} > 0.8"
  message = "⚠️ Database connection pool utilization high (>80%)\n\nRisk of connection pool exhaustion.\n\nNotify: @database-team"

  escalation_message = "Connection pool critically low. Connections may be leaked."
  priority            = "P1"
  include_tags        = true

  thresholds = {
    critical = 0.9
    warning  = 0.8
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "database"
  })
}

# Monitor: JVM Memory Pressure
resource "datadog_monitor" "jvm_memory_pressure" {
  name    = "[${var.environment}] JVM Memory Pressure"
  type    = "metric_alert"
  query   = "avg:jvm.memory.usage{} / avg:jvm.memory.max{} > 0.85"
  message = "⚠️ JVM heap memory usage critical (>85%)\n\nService: {{service.name}}\n\nRisk of OutOfMemoryError.\n\nNotify: @platform-team"

  escalation_message = "JVM memory at critical levels. Application may crash."
  priority            = "P1"
  include_tags        = true

  thresholds = {
    critical = 0.95
    warning  = 0.85
  }

  notification_preset_name = "show_all"

  tags = merge(local.common_tags, {
    alert_type = "jvm"
  })
}

# Output: Alert Summary
output "alerts_created" {
  description = "List of Datadog monitors/alerts created"
  value = {
    high_error_rate        = datadog_monitor.high_error_rate.name
    high_latency           = datadog_monitor.high_latency.name
    service_down           = datadog_monitor.service_down.name
    vault_access_failures  = datadog_monitor.vault_access_failures.name
    otel_backlog           = datadog_monitor.otel_backlog.name
    db_connection_pool     = datadog_monitor.db_connection_pool.name
    jvm_memory_pressure    = datadog_monitor.jvm_memory_pressure.name
  }
}
