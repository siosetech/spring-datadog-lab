#!/usr/bin/env bash

NAMESPACE="spring-datadog-lab"

echo "Deploying Infrastructure (Vault, VSO, Kafka, PostgreSQL) via Kustomize..."
kubectl kustomize --enable-helm k8s/kustomize/overlays/dev | kubectl apply -f -

echo "Waiting for infrastructure to be ready..."
sleep 10

echo "Deploying Microservices via Helm..."

SERVICES=("api-gateway" "auth-service" "user-profile-service" "audit-log-service" "dashboard-service" "notification-service")

for SERVICE in "${SERVICES[@]}"
do
  echo "Deploying $SERVICE..."
  # Use the generic Helm chart and specific values for each service
  helm upgrade --install $SERVICE ./k8s/helm/spring-microservice \
    --namespace $NAMESPACE \
    -f ./k8s/kustomize/overlays/dev/values/${SERVICE}.yaml
done

echo "All services deployed successfully!"
