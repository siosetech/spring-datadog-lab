# Create lab databases/roles expected by Spring apps.
# Usage: pwsh -File .\deploy\scripts\k8s\bootstrap-postgres.ps1

param([string]$Namespace = "spring-datadog-lab")

$ErrorActionPreference = "Stop"
$sqlFile = Join-Path $PSScriptRoot "bootstrap-postgres.sql"

$pod = "postgresql-0"
kubectl -n $Namespace cp $sqlFile "${pod}:/tmp/bootstrap-postgres.sql"
kubectl -n $Namespace exec $pod -- bash -c 'PGPASSWORD=$(cat /opt/bitnami/postgresql/secrets/postgres-password) psql -U postgres -h 127.0.0.1 -d postgres -v ON_ERROR_STOP=1 -f /tmp/bootstrap-postgres.sql'
Write-Host "Postgres bootstrap complete"
