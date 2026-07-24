#!/usr/bin/env bash
# Lab Vault bootstrap: KV secrets for Spring Cloud Vault (token=root in Vault -dev).
# Apps read secret/<service> and secret/datadog via spring-cloud-starter-vault-config.
# OTel collector is not Spring — use deploy/scripts/k8s/ensure-datadog-secret.ps1 for DD_API_KEY.
# Kubernetes auth / VSO helpers below are optional leftovers (lab path does not require VSO).

NAMESPACE="spring-datadog-lab"
VAULT_POD="vault-0"
VAULT_ROLE="microservices"
VAULT_POLICY="microservices-policy"

configure_kubernetes_auth_optional() {
    echo "OPTIONAL: Kubernetes auth for future SA-based login (lab uses Spring Vault + root token)..."

    kubectl exec $VAULT_POD -n $NAMESPACE -- vault auth list -format=json 2>/dev/null | grep -q '"kubernetes/"' \
        || kubectl exec $VAULT_POD -n $NAMESPACE -- vault auth enable kubernetes

    kubectl exec $VAULT_POD -n $NAMESPACE -- sh -c 'vault write auth/kubernetes/config \
        kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443"'

    kubectl exec $VAULT_POD -n $NAMESPACE -- sh -c "cat <<EOF | vault policy write ${VAULT_POLICY} -
path \"secret/data/datadog\" {
  capabilities = [\"read\"]
}
path \"secret/data/*\" {
  capabilities = [\"read\"]
}
EOF"

    kubectl exec $VAULT_POD -n $NAMESPACE -- sh -c "vault write auth/kubernetes/role/${VAULT_ROLE} \
        bound_service_account_names=default \
        bound_service_account_namespaces='${NAMESPACE}' \
        policies=${VAULT_POLICY} \
        audience=vault \
        ttl=24h"
}

echo "Waiting for Vault pod to start..."
kubectl wait --for=condition=Ready pod/$VAULT_POD -n $NAMESPACE --timeout=300s

echo "Checking if Vault is initialized..."
INIT_STATUS=$(kubectl exec $VAULT_POD -n $NAMESPACE -- vault status -format=json | grep -o '"initialized": *true')

if [ -z "$INIT_STATUS" ]; then
    echo "Initializing Vault..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault operator init -key-shares=1 -key-threshold=1 -format=json > cluster-keys.json

    UNSEAL_KEY=$(grep -o '"unseal_keys_b64": *\["[^"]*"' cluster-keys.json | cut -d '"' -f 4)
    ROOT_TOKEN=$(grep -o '"root_token": *"[^"]*"' cluster-keys.json | cut -d '"' -f 4)

    echo "Unsealing Vault..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault operator unseal $UNSEAL_KEY

    echo "Login with root token..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault login $ROOT_TOKEN

    echo "Enabling Audit Log to stdout..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault audit enable file file_path=/dev/stdout

    echo "Enabling KV-v2 Secrets Engine..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault secrets enable -path=secret kv-v2

    echo "Writing Datadog API Key to Vault..."
    # You should replace THIS_IS_YOUR_DATADOG_KEY with your actual Datadog key before running or pass it as an arg.
    DD_API_KEY=${1:-"THIS_IS_YOUR_DATADOG_KEY"}
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/datadog DD_API_KEY=$DD_API_KEY

    echo "Writing DB Credentials to Vault for Microservices..."
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/auth-service DB_USERNAME=auth DB_PASSWORD=auth
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/user-profile-service DB_USERNAME=user_profile DB_PASSWORD=user_profile
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/audit-log-service DB_USERNAME=audit DB_PASSWORD=audit
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/notification-service DB_USERNAME=notification DB_PASSWORD=notification
    kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/dashboard-service DB_USERNAME=dashboard DB_PASSWORD=dashboard

    configure_kubernetes_auth_optional

    echo "Vault initialization and configuration complete!"
    echo "SAVE THESE KEYS SAFELY:"
    cat cluster-keys.json
else
    echo "Vault is already initialized."
    if [ -n "${1:-}" ]; then
        echo "Updating Datadog API key in Vault..."
        kubectl exec $VAULT_POD -n $NAMESPACE -- vault kv put secret/datadog DD_API_KEY="$1"
    fi
    # Lab default: Spring Cloud Vault with token; skip K8s auth unless you need it.
fi
