# Kubernetes Auth Method
# Allows K8s pods to authenticate using their ServiceAccount tokens
# Each pod mounts a token automatically, enabling secure secret retrieval

resource "vault_auth_backend" "kubernetes" {
  type        = "kubernetes"
  path        = "kubernetes"
  description = "Kubernetes ServiceAccount authentication"

  depends_on = [vault_policy.k8s_policy]
}

# Configure K8s auth to communicate with K8s API
# In production, you'd set these to your actual cluster details
resource "vault_kubernetes_auth_backend_config" "default" {
  backend            = vault_auth_backend.kubernetes.path
  kubernetes_host    = "https://kubernetes.default.svc"
  kubernetes_ca_cert = file("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
  token_reviewer_jwt = file("/var/run/secrets/kubernetes.io/serviceaccount/token")

  depends_on = [vault_auth_backend.kubernetes]
}

# Role for all microservices in spring-datadog-lab namespace
resource "vault_kubernetes_auth_backend_role" "microservices" {
  backend                       = vault_auth_backend.kubernetes.path
  role_name                     = "microservices"
  bound_service_account_names   = ["default", "auth-service", "user-profile-service", "audit-log-service", "notification-service", "api-gateway", "dashboard-service"]
  bound_service_account_namespaces = ["spring-datadog-lab"]
  token_ttl                     = 1800  # 30 minutes
  token_max_ttl                 = 3600  # 1 hour
  policies                      = [vault_policy.k8s_policy.name]

  depends_on = [vault_auth_backend.kubernetes]
}

# Role for Vault Secrets Operator (VSO) in spring-datadog-lab namespace
resource "vault_kubernetes_auth_backend_role" "vso" {
  backend                       = vault_auth_backend.kubernetes.path
  role_name                     = "vso-role"
  bound_service_account_names   = ["vault-secrets-operator"]
  bound_service_account_namespaces = ["spring-datadog-lab"]
  token_ttl                     = 1800  # 30 minutes
  token_max_ttl                 = 3600  # 1 hour
  policies                      = [vault_policy.vso_policy.name]

  depends_on = [vault_auth_backend.kubernetes]
}

# Policy for microservices (read-only access to their own secrets)
resource "vault_policy" "k8s_policy" {
  name = "k8s-microservices-policy"

  policy = <<EOH
# Read Datadog secrets
path "secret/data/datadog/*" {
  capabilities = ["read"]
}

# Read database secrets
path "secret/data/database/*" {
  capabilities = ["read"]
}

# Read Kafka secrets
path "secret/data/kafka/*" {
  capabilities = ["read"]
}

# Read service-to-service secrets
path "secret/data/services/*" {
  capabilities = ["read"]
}

# Renew own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Lookup own token
path "auth/token/lookup-self" {
  capabilities = ["read"]
}
EOH
}

# Policy for Vault Secrets Operator (read access to all secrets)
resource "vault_policy" "vso_policy" {
  name = "vso-policy"

  policy = <<EOH
# VSO needs to read all secrets to sync them into K8s
path "secret/data/*" {
  capabilities = ["read", "list"]
}

# Renew own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Lookup own token
path "auth/token/lookup-self" {
  capabilities = ["read"]
}
EOH
}

# Outputs for K8s VSO configuration
output "vault_k8s_auth_path" {
  value       = vault_auth_backend.kubernetes.path
  description = "Vault Kubernetes auth path (use in VaultAuth CRD)"
}

output "vault_microservices_role_name" {
  value       = vault_kubernetes_auth_backend_role.microservices.role_name
  description = "Vault role name for microservices (use in VaultAuth CRD)"
}

output "vault_vso_role_name" {
  value       = vault_kubernetes_auth_backend_role.vso.role_name
  description = "Vault role name for VSO (use in VaultAuth CRD)"
}
