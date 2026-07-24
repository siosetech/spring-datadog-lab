$ErrorActionPreference = "Stop"

Write-Host "=========================================="
Write-Host " Phase 15: Containerd Deployment Script"
Write-Host "=========================================="
Write-Host "Starting Maven Build (spring-boot:build-image -Dspring-boot.build-image.imageName=spring-datadog-lab/${mod}:1.0.0-SNAPSHOT)..."
Write-Host "This will build OCI tarballs for all microservices without requiring a local Docker daemon."

# Run Maven build for all modules
& .\mvnw clean package spring-boot:build-image -Dspring-boot.build-image.imageName=spring-datadog-lab/${mod}:1.0.0-SNAPSHOT -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed. Aborting deployment."
    exit $LASTEXITCODE
}

Write-Host "Maven build completed successfully."
Write-Host "Loading Tarballs into Containerd (k8s.io namespace)..."

$modules = @("api-gateway", "auth-service", "user-profile-service", "audit-log-service", "dashboard-service", "notification-service")

foreach ($mod in $modules) {
    docker save spring-datadog-lab/${mod}:1.0.0-SNAPSHOT -o $mod/target/image.tar
    $tarPath = "$mod/target/image.tar"
    if (Test-Path $tarPath) {
        Write-Host "Loading image for $mod from $tarPath ..."
        & nerdctl -n k8s.io load -i $tarPath
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Failed to load image for $mod!"
        } else {
            Write-Host "Successfully loaded $mod image."
        }
    } else {
        Write-Warning "Tarball not found for $mod at $tarPath"
    }
}

Write-Host "Restarting Kubernetes Deployments to apply the new images..."
# Restart deployments to pull the fresh images from the containerd cache
& kubectl rollout restart deployment api-gateway-spring-microservice -n spring-datadog-lab
& kubectl rollout restart deployment auth-service-spring-microservice -n spring-datadog-lab
& kubectl rollout restart deployment user-profile-service-spring-microservice -n spring-datadog-lab
& kubectl rollout restart deployment audit-log-service-spring-microservice -n spring-datadog-lab
& kubectl rollout restart deployment dashboard-service-spring-microservice -n spring-datadog-lab
& kubectl rollout restart deployment notification-service-spring-microservice -n spring-datadog-lab

Write-Host "=========================================="
Write-Host " Deployment completed successfully!"
Write-Host " Use 'kubectl get pods -n spring-datadog-lab -w' to watch the pods start."
Write-Host "=========================================="

