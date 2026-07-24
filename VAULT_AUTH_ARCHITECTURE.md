# Vault Authentication Architecture

## Overview

This project uses **3 different Vault authentication methods** for different contexts:

1. **AppRole** - Local development and CI/CD scripts
2. **GitHub OIDC JWT** - GitHub Actions workflows  
3. **Kubernetes ServiceAccount** - K8s pods (microservices)

This hybrid approach ensures:
- ✅ **No long-lived tokens** (except for initial setup)
- ✅ **Automatic token rotation** in GitHub Actions (5-min JWT expiry)
- ✅ **No secrets in code** (Vault is single source of truth)
- ✅ **Audit trails** (who accessed what, when)
- ✅ **Different permissions** per context (least privilege)

---

## 1. AppRole (Local Development)

### What is AppRole?

AppRole is Vault's answer to "how do I authenticate without a token?" It uses:
- **RoleID**: Like a username (stable, can be shared)
- **SecretID**: Like a password (rotates, kept secret)

### Setup

```bash
# First time setup
cd scripts/terraform
chmod +x approle-init.sh
./approle-init.sh

# You'll be prompted for Datadog API keys
# Output will include:
#   VAULT_ROLE_ID=abc123...
#   VAULT_SECRET_ID=xyz789...
```

### Usage in Local Development

```bash
# Set environment variables
export VAULT_ADDR="http://localhost:8200"
export VAULT_ROLE_ID="abc123..."
export VAULT_SECRET_ID="xyz789..."

# Terraform will auto-detect these and authenticate to Vault
cd terraform
terraform plan
terraform apply
```

### Terraform Configuration

In `providers.tf`, Terraform Provider detects `VAULT_ROLE_ID` and `VAULT_SECRET_ID`:

```hcl
provider "vault" {
  address = var.vault_address
  auth_login {
    path = "auth/approle/login"
    parameters = {
      role_id   = var.vault_role_id
      secret_id = var.vault_secret_id
    }
  }
}
```

### Security Considerations

- ✅ SecretID is rotated on each `terraform apply` (in vault-approle.tf)
- ⚠️ Store RoleID and SecretID in `.env` file (git-ignored)
- ⚠️ Never commit credentials to git
- 🔄 Rotate SecretID regularly: `vault write -f auth/approle/role/terraform-dev/secret-id`

---

## 2. GitHub OIDC JWT (GitHub Actions)

### What is GitHub OIDC?

GitHub Actions automatically generates short-lived JWT tokens (5-minute expiry) using OpenID Connect. These tokens can be exchanged for Vault tokens without storing any secrets in GitHub.

### How It Works

```
GitHub Action → GitHub issues JWT → Vault → Vault issues token → Terraform
   (identity)      (5 min TTL)      (validates JWT)    (30 min TTL)
```

### Setup in Vault

Terraform automatically creates this in `terraform/vault-jwt-auth.tf`:

```hcl
resource "vault_jwt_auth_backend" "github" {
  path               = "jwt"
  oidc_discovery_url = "https://token.actions.githubusercontent.com"
  oidc_client_id     = "sigstore"
}

resource "vault_jwt_auth_backend_role" "github_actions" {
  role_name       = "github-actions"
  bound_audiences = ["sigstore"]
  bound_claims = {
    repository = "your-org/spring-datadog-lab"
  }
}
```

### GitHub Actions Workflow

In `.github/workflows/terraform-apply.yml`:

```yaml
jobs:
  terraform-apply:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write  # ← Required for OIDC JWT
    
    steps:
      - name: Authenticate to Vault using GitHub OIDC
        uses: hashicorp/vault-action@v2
        with:
          url: ${{ secrets.VAULT_ADDR }}
          path: jwt
          role: github-actions
          method: jwt
```

### Why No Long-Lived Token in GitHub Secrets?

❌ Old way (NOT USED):
```
secrets.VAULT_TOKEN → Stored in GitHub forever ⚠️
```

✅ New way (USED):
```
GitHub OIDC JWT → Exchanged for token → Token expires in 30 min ✅
```

### Limitations

- 🔒 GitHub OIDC is read-only in Vault (cannot store in GitHub Secrets)
- Only works for workflows in this repository
- Cannot be used manually from laptop (only in GitHub Actions)

---

## 3. Kubernetes ServiceAccount Auth (K8s Pods)

### What is K8s Auth?

Kubernetes automatically mounts a ServiceAccount token in each pod. Vault can validate these tokens to authenticate pods.

### How It Works

```
Pod → Mount /var/run/secrets/kubernetes.io/serviceaccount/token
    → VSO uses token to authenticate to Vault
    → Vault validates token with K8s API
    → VSO retrieves secrets → Creates K8s Secret
    → Pod mounts K8s Secret → Pod reads credentials
```

### Setup

Terraform creates this in `terraform/vault-k8s-auth.tf`:

```hcl
resource "vault_kubernetes_auth_backend_config" "default" {
  kubernetes_host    = "https://kubernetes.default.svc"
  kubernetes_ca_cert = file("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
  token_reviewer_jwt = file("/var/run/secrets/kubernetes.io/serviceaccount/token")
}

resource "vault_kubernetes_auth_backend_role" "microservices" {
  role_name                     = "microservices"
  bound_service_account_names   = ["default", "auth-service", "user-profile-service", ...]
  bound_service_account_namespaces = ["spring-datadog-lab"]
  policies                      = [vault_policy.k8s_policy.name]
}
```

### K8s Configuration (VSO)

In `k8s/kustomize/overlays/dev/vault-vso.yaml`:

```yaml
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultConnection
metadata:
  name: default
spec:
  address: http://vault.spring-datadog-lab.svc.cluster.local:8200

---
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultAuth
metadata:
  name: default
spec:
  vaultConnectionRef: default
  method: kubernetes
  kubernetes:
    role: microservices
    serviceAccount: default

---
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultStaticSecret
metadata:
  name: datadog-secret
spec:
  type: kv-v2
  mount: secret
  path: datadog
  destination:
    name: datadog-k8s-secret
    create: true
```

### Pod Access to Secrets

In deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
spec:
  template:
    spec:
      containers:
      - name: auth-service
        env:
        - name: DATADOG_API_KEY
          valueFrom:
            secretKeyRef:
              name: datadog-k8s-secret
              key: api_key
```

### How Vault Verifies the Pod

1. Pod reads ServiceAccount token from `/var/run/secrets/kubernetes.io/serviceaccount/token`
2. Vault receives token + pod metadata (namespace, service account)
3. Vault calls K8s API to validate the token
4. If valid → Vault issues a token to the pod
5. Pod uses token to read secrets

### Policies

Each auth method has different policies:

**Local Dev (AppRole)**:
```hcl
path "secret/data/*" {
  capabilities = ["read", "list", "create", "update", "delete"]
}
```

**GitHub Actions (JWT)**:
```hcl
path "secret/data/datadog/*" {
  capabilities = ["read", "list"]
}
path "secret/data/terraform/*" {
  capabilities = ["read", "list"]
}
```

**K8s Pods (ServiceAccount)**:
```hcl
path "secret/data/datadog/*" {
  capabilities = ["read"]
}
path "secret/data/database/*" {
  capabilities = ["read"]
}
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Vault (Central Authority)                      │
│                                                                      │
│  ├─ secret/data/datadog                  (Datadog API keys)         │
│  ├─ secret/data/database                 (DB passwords)             │
│  ├─ secret/data/kafka                    (Kafka credentials)        │
│  │                                                                   │
│  ├─ auth/approle      (Local Dev)        ← RoleID + SecretID        │
│  ├─ auth/jwt          (GitHub Actions)   ← JWT from GitHub          │
│  └─ auth/kubernetes   (K8s Pods)         ← ServiceAccount tokens    │
└─────────────────────────────────────────────────────────────────────┘
         ↑                    ↑                          ↑
         │                    │                          │
    ┌────┴─────┐      ┌──────┴────────┐      ┌──────────┴─────┐
    │ Localhost│      │ GitHub Actions│      │   K8s Cluster   │
    │           │      │                │      │                 │
    │ ┌────────┐│      │ ┌────────────┐│      │ ┌────────────┐ │
    │ │Terraform││      │ │ terraform-│ │      │ │Microservice││ │
    │ │(AppRole)││      │ │ apply wf  │ │      │ │(ServiceAcc)│ │
    │ └────────┘│      │ │(JWT)       │ │      │ └────────────┘ │
    │           │      │ └────────────┘│      │                 │
    │ VAULT_    │      │ GitHub OIDC   │      │ K8s API        │
    │ ROLE_ID   │      │ Issuer        │      │ Validation     │
    │ VAULT_    │      └──────────────┘│      └────────────────┘
    │ SECRET_ID │                        │
    └──────────┘                        └──────────────────────┐
```

---

## Troubleshooting

### Local Dev Issues

**Problem**: `Error: Auth method approle not found`

```bash
# Solution: Run AppRole setup
./scripts/terraform/approle-init.sh
```

**Problem**: `Invalid SecretID`

```bash
# Solution: Generate new SecretID
vault write -f auth/approle/role/terraform-dev/secret-id
export VAULT_SECRET_ID="<new_secret_id>"
```

### GitHub Actions Issues

**Problem**: `Error: Invalid JWT`

```
Check:
1. secrets.VAULT_ADDR is set correctly
2. GitHub Actions workflow has: permissions: { id-token: write }
3. Vault JWT role includes this repository in bound_claims
```

**Problem**: `Error: OIDC discovery failed`

```
Vault cannot reach GitHub OIDC endpoint:
- Check outbound network access from Vault to https://token.actions.githubusercontent.com
- Check firewall rules
```

### K8s Pod Issues

**Problem**: `Error validating ServiceAccount token`

```bash
# Check K8s auth config
kubectl exec -it vault-0 -- vault auth list
kubectl exec -it vault-0 -- vault read auth/kubernetes/config

# Re-validate K8s API connectivity
vault write auth/kubernetes/config \
  kubernetes_host="https://kubernetes.default.svc" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token
```

**Problem**: Pod can't read secret

```bash
# Check VSO is running
kubectl get pods -n spring-datadog-lab | grep vault-secrets-operator

# Check VaultStaticSecret status
kubectl describe vaultstaticsecret datadog-secret -n spring-datadog-lab

# Check K8s Secret was created
kubectl get secret datadog-k8s-secret -n spring-datadog-lab
```

---

## Best Practices

1. **Never use root token in production** - Use AppRole or JWT instead
2. **Rotate SecretIDs regularly** - Terraform does this automatically
3. **Use least privilege policies** - Each role has minimal permissions needed
4. **Monitor Vault audit logs** - Track who accessed what secrets
5. **Use TLS in production** - `vault_skip_tls_verify = false`
6. **Store credentials securely** - Use OS credential managers (1Password, Vault, etc.)

---

## References

- [Vault AppRole Auth](https://www.vaultproject.io/docs/auth/approle)
- [Vault JWT/OIDC Auth](https://www.vaultproject.io/docs/auth/jwt/oidc-providers/github-actions)
- [Vault Kubernetes Auth](https://www.vaultproject.io/docs/auth/kubernetes)
- [GitHub Actions OIDC](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
