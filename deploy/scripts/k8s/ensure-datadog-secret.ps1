# Ensures OTel collector has DD_API_KEY (Spring apps use Vault directly).
# Usage (PowerShell):
#   $env:DD_API_KEY = "..."
#   pwsh -File .\deploy\scripts\k8s\ensure-datadog-secret.ps1
# Or copy from local compose collector if key is already in that container env.

param(
    [string]$Namespace = "spring-datadog-lab",
    [string]$SecretName = "datadog-k8s-secret"
)

$ErrorActionPreference = "Stop"

$key = $env:DD_API_KEY
if (-not $key) {
    Write-Host "DD_API_KEY not set; trying docker compose otel-collector env..."
    $inspect = docker inspect spring-datadog-lab-otel-collector-1 2>$null
    if ($LASTEXITCODE -eq 0 -and $inspect) {
        $envList = ($inspect | ConvertFrom-Json)[0].Config.Env
        $entry = $envList | Where-Object { $_ -like "DD_API_KEY=*" } | Select-Object -First 1
        if ($entry) { $key = ($entry -split "=", 2)[1] }
    }
}

if (-not $key) {
    throw "Set DD_API_KEY or start local compose otel-collector with the key in env."
}

$tmp = Join-Path $env:TEMP "dd.key"
Set-Content -Path $tmp -Value $key -NoNewline -Encoding ascii
try {
    kubectl -n $Namespace create secret generic $SecretName `
        --from-file=DD_API_KEY=$tmp `
        --dry-run=client -o yaml | kubectl apply -f -
    Write-Host "Applied $Namespace/$SecretName"
} finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}
