# Terraform - Datadog Monitoring & Alerts

Datadog-based monitoring infrastructure for `spring-datadog-lab` services with **Vault integration for secrets management**.

## Overview

This Terraform configuration manages:
- **Error Rate Alerts** (>5% error rate per service)
- **Latency Monitors** (p99 latency > 1000ms)
- **Service Availability** (downtime detection)
- **Vault Access Failures** (secret management issues)
- **OpenTelemetry Backlog** (trace processing health)
- **Database Connection Pool** (exhaustion detection)
- **JVM Memory Pressure** (heap usage monitoring)

## Architecture: Terraform + Vault Integration + GitHub IaC

```
┌─────────────────────┐
│   Terraform         │
│  (IaC - Alerts)     │
└────────┬────────────┘
         │ (Vault Provider)
         ▼
┌─────────────────────────────────┐    ┌──────────────────────────────┐
│   HashiCorp Vault               │    │   GitHub Provider (Terraform)│
│   └─ secret/datadog             │    │   ├─ Secrets                 │
│       ├─ api_key                │    │   ├─ Environments            │
│       └─ app_key                │    │   └─ Branch Protection       │
└────────┬────────────────────────┘    └──────────────────────────────┘
         │                                         │
         ▼                                         ▼
┌─────────────────┐                    ┌──────────────────────┐
│    Datadog      │                    │   GitHub Repository  │
│   (Monitors)    │                    │   (CI/CD Configured) │
└─────────────────┘                    └──────────────────────┘
```

## Setup

### Prerequisites

- Terraform >= 1.5
- HashiCorp Vault running (local or remote)
- Datadog account (free tier supported)
- GitHub personal access token or GitHub App token
- Curl (for Vault initialization scripts)

### 2. Get Datadog Credentials

1. Go to [Datadog API Settings](https://app.datadoghq.com/organization/settings/api-keys)
2. Generate **API Key** and **App Key**
3. Store in Vault (next steps)

### 3. Get GitHub Token (for GitHub IaC management)

1. Go to [GitHub Settings → Personal Access Tokens](https://github.com/settings/tokens)
2. Generate **Fine-grained personal access token** or **Classic token** with scopes:
   - `repo` (all)
   - `admin:repo_hook` (manage webhooks)
   - `admin:org_hook` (if managing organization)
3. Copy token securely
4. Store in Vault or GitHub Secrets (via Terraform)

### 4. Initialize Vault with Datadog Secrets (Local Dev)

```bash
# Start Vault in dev mode (one terminal)
vault server -dev

# In another terminal, run setup script
bash scripts/terraform/vault-init.sh

# This will:
# 1. Prompt for Datadog API Key and App Key
# 2. Store them in Vault at secret/datadog
# 3. Output environment variables to export
```

### 5. Configure Terraform

```bash
cd terraform/

# Copy example tfvars
cp terraform.tfvars.example terraform.tfvars

# Edit with your settings (Vault + GitHub)
# - github_token: Your GitHub personal access token
# - github_owner: Your GitHub username or org
# - vault_address, vault_token, etc.
```

### 6. Initialize & Apply

```bash
terraform init

# Preview changes (GitHub + Datadog)
terraform plan

# Create resources:
# - Datadog monitors
# - GitHub secrets
# - GitHub production environment
# - Branch protection rules
terraform apply
```

## Environment Variables (CI/CD)

For GitHub Actions or other CI/CD pipelines:

```bash
export VAULT_ADDR="http://vault.example.com:8200"
export VAULT_TOKEN="your-vault-token"
export TF_VAR_vault_address="$VAULT_ADDR"
export TF_VAR_vault_token="$VAULT_TOKEN"
export TF_VAR_vault_secret_path="secret/datadog"
export TF_VAR_environment="prod"
```

## GitHub Repository Setup as IaC

Terraform manages GitHub repository configuration automatically:

### Resources Created

1. **GitHub Secrets** (Repository level)
   - `VAULT_ADDR`: Vault address
   - `VAULT_TOKEN`: Vault authentication token
   - `TF_CLOUD_TOKEN`: Terraform Cloud API token (optional)
   - `KUBECONFIG`: Kubernetes config for deployment (optional)
   - `SLACK_WEBHOOK`: Slack notifications (optional)

2. **GitHub Environments**
   - `production`: Requires manual approval before deployment
   - Separate secrets per environment (override defaults)

3. **Branch Protection** (Main branch)
   - Require 1+ pull request review
   - Require status checks: `build-and-test`, `code-quality`, `terraform-plan`
   - Enforce admins
   - Dismiss stale reviews

### Security Notes

- **GitHub Token**: Store securely, never commit
- **Production Vault**: Use separate `vault_token_prod` for production
- **OIDC (Recommended)**: Use GitHub OIDC JWT instead of long-lived tokens

---

## Remote State Management (Terraform Cloud) - State Backup Strategy

To use Terraform Cloud for remote state backup (recommended for production):

### 1. Create Terraform Cloud Account

- Go to [terraform.cloud](https://app.terraform.io)
- Create free account (free tier includes 1 state, team collaboration)
- Create organization: `your-org`

### 2. Generate Terraform Cloud API Token

1. Login to [terraform.cloud](https://app.terraform.io)
2. Go to **Settings** → **Tokens** → **Create an API Token**
3. Copy token (you won't see it again!)

### 3. Store Token in Vault (Secure)

```bash
# Using setup script (recommended)
bash scripts/terraform/terraform-cloud-init.sh

# Or manually:
export VAULT_ADDR="http://localhost:8200"
export VAULT_TOKEN="root"
vault kv put secret/terraform-cloud token="your-api-token-here"
```

### 4. Enable Terraform Cloud in terraform.tfvars

```hcl
terraform_cloud_enabled = true
terraform_cloud_org     = "your-org"
terraform_cloud_workspace = "spring-datadog-lab"
```

### 5. Uncomment cloud block in providers.tf

```hcl
terraform {
  cloud {
    organization = "your-org"
    workspaces {
      name = "spring-datadog-lab"
    }
  }
}
```

### 6. Migrate State to Terraform Cloud

```bash
cd terraform
terraform init

# When prompted:
# "Do you want to copy existing state to the new backend?"
# Answer: yes
```

### Backup Architecture

```
┌─────────────────────┐
│  terraform apply    │
└────────┬────────────┘
         │ (state + logs)
         ▼
┌─────────────────────────────────┐
│   Terraform Cloud              │
│   (Remote State + Runs)        │
│   ✅ Version control           │
│   ✅ Encryption at rest       │
│   ✅ Team access control      │
│   ✅ Run history & logs       │
└─────────────────────────────────┘
```

### CI/CD Pipeline (GitHub Actions)

```yaml
env:
  VAULT_ADDR: ${{ secrets.VAULT_ADDR }}
  VAULT_TOKEN: ${{ secrets.VAULT_TOKEN }}
  TF_TOKEN_app_terraform_io: ${{ secrets.TF_CLOUD_TOKEN }}
  TF_VAR_terraform_cloud_enabled: "true"

steps:
  - uses: hashicorp/setup-terraform@v2
  - run: terraform init
  - run: terraform plan
  - run: terraform apply
```

### Local Development with Terraform Cloud

```bash
# Setup once
bash scripts/terraform/terraform-cloud-init.sh

# Then:
cd terraform
terraform init  # Migrates state to cloud
terraform plan  # Shows changes
terraform apply # Applies and updates remote state
```

### Benefits

| Aspect | Local State | Terraform Cloud |
|--------|------------|-----------------|
| State Storage | Local disk | Remote (encrypted) |
| Backup | Manual | Automatic |
| Team Access | File sharing | Built-in |
| Version History | ❌ | ✅ |
| Access Logs | ❌ | ✅ |
| Cost | Free | Free (1 state) |
| Recommended For | Dev only | Production |

## Monitors Created

| Alert | Threshold | Priority | Action |
|-------|-----------|----------|--------|
| High Error Rate | >5% | P1 | Escalate to on-call |
| High Latency | p99 > 1000ms | P2 | Performance review |
| Service Down | 0 available | P1 | Immediate incident |
| Vault Failures | >5 errors | P1 | Check connectivity |
| OTel Backlog | >1000 spans | P2 | Review exporter |
| DB Pool Exhaustion | >80% utilization | P1 | Connection leak check |
| JVM Memory Pressure | >85% heap | P1 | Memory dump & analysis |

## File Structure

```
terraform/
├── providers.tf              # Datadog + Vault providers
├── vault.tf                  # Vault data source (secrets)
├── variables.tf              # Input variables
├── datadog.tf                # Datadog monitors/alerts
├── terraform.tfvars.example  # Configuration template
├── .gitignore                # Sensitive files excluded
└── README.md                 # This file

scripts/terraform/
└── vault-init.sh             # Local dev: Initialize Vault + store secrets
```

## Troubleshooting

### Vault Connection Error
```
Error: Failed to retrieve secret from Vault
```
**Solution:**
- Ensure Vault is running: `vault server -dev`
- Check `vault_address` in `terraform.tfvars`
- Verify `vault_token` has access to `secret/datadog`

### Secret Not Found
```
Error: api_key not found in secret/datadog
```
**Solution:**
- Run `bash scripts/terraform/vault-init.sh` to store credentials
- Verify: `vault kv list secret/` and `vault kv get secret/datadog`

### Terraform State Issues
```
Error: Error acquiring the state lock
```
**Solution:**
- Remove local `.terraform/` and `terraform.tfstate*`
- Re-run `terraform init`

## Cleanup

To destroy all monitors and Terraform state:

```bash
terraform destroy
```

## References

- [Terraform Vault Provider](https://registry.terraform.io/providers/hashicorp/vault/latest)
- [Terraform Datadog Provider](https://registry.terraform.io/providers/DataDog/datadog/latest)
- [Datadog Monitor Documentation](https://docs.datadoghq.com/monitors/)
- [Vault Documentation](https://www.vaultproject.io/docs)
