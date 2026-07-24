param(
    [string]$ConnectUrl = "http://localhost:8086",
    [string]$ConfigPath = ".\scripts\debezium\auth-outbox-connector.json"
)

$resolvedPath = Resolve-Path $ConfigPath
$rawJson = Get-Content $resolvedPath -Raw

$postgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "myuser" }
$postgresPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "mypassword" }
$postgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "datadog_lab_db" }

$connectorJson = $rawJson `
    -replace "\$\{POSTGRES_USER\}", $postgresUser `
    -replace "\$\{POSTGRES_PASSWORD\}", $postgresPassword `
    -replace "\$\{POSTGRES_DB\}", $postgresDb

Invoke-RestMethod `
    -Method Put `
    -Uri "$ConnectUrl/connectors/auth-outbox-connector/config" `
    -ContentType "application/json" `
    -Body $connectorJson
