# Build jar images for Rancher Desktop and helm-install lab services (skaffold optional).
# Usage: pwsh -File .\deploy\scripts\k8s\build-and-deploy-apps.ps1

param(
    [string]$Namespace = "spring-datadog-lab",
    [string]$Tag = "1.0.0-SNAPSHOT",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
Set-Location $root

$modules = @(
    @{ Name = "api-gateway";            Chart = "api-gateway";            Values = "deploy/k8s/kustomize/overlays/dev/values/api-gateway.yaml" },
    @{ Name = "auth-service";           Chart = "auth-service";           Values = "deploy/k8s/kustomize/overlays/dev/values/auth-service.yaml" },
    @{ Name = "user-profile-service";   Chart = "user-profile-service";   Values = "deploy/k8s/kustomize/overlays/dev/values/user-profile-service.yaml" },
    @{ Name = "audit-log-service";      Chart = "audit-log-service";      Values = "deploy/k8s/kustomize/overlays/dev/values/audit-log-service.yaml" },
    @{ Name = "dashboard-service";      Chart = "dashboard-service";      Values = "deploy/k8s/kustomize/overlays/dev/values/dashboard-service.yaml" },
    @{ Name = "notification-service";   Chart = "notification-service";   Values = "deploy/k8s/kustomize/overlays/dev/values/notification-service.yaml" }
)

if (-not $SkipBuild) {
    Write-Host "=== mvn package ==="
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

    foreach ($m in $modules) {
        $image = "spring-datadog-lab/$($m.Name):$Tag"
        Write-Host "=== docker build $image ==="
        Copy-Item "deploy\docker\app\Dockerfile" "$($m.Name)\Dockerfile" -Force
        docker build -t $image "$($m.Name)"
        if ($LASTEXITCODE -ne 0) { throw "docker build failed for $($m.Name)" }
    }
}

Write-Host "=== helm upgrade apps ==="
foreach ($m in $modules) {
    helm upgrade --install $m.Chart deploy/k8s/helm/spring-microservice `
        -n $Namespace `
        -f $m.Values `
        --set "image.repository=spring-datadog-lab/$($m.Name)" `
        --set "image.tag=$Tag" `
        --wait --timeout 3m
    if ($LASTEXITCODE -ne 0) { throw "helm failed for $($m.Chart)" }
}

kubectl -n $Namespace get pods,svc
Write-Host "Done."
