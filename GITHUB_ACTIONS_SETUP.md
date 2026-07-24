# GitHub Actions CI/CD Setup Guide

Complete guide for setting up GitHub Actions workflows for **spring-datadog-lab**.

## Overview

GitHub Actions workflows automate:
- ✅ **CI**: Build, test, linting (on PR/push)
- ✅ **Terraform Plan**: Infrastructure change reviews (on PR)
- ✅ **Terraform Apply**: Auto-deploy infrastructure (main branch)
- ✅ **K8s Deploy**: Container deployment (on release tag)

## Workflows

| Workflow | Trigger | Purpose | Environment |
|----------|---------|---------|-------------|
| **CI** | PR, push to main/develop | Build, test, lint | - |
| **Terraform Plan** | PR with terraform/* changes | Plan infrastructure | - |
| **Terraform Apply** | Push to main with terraform/* | Apply infrastructure | Production |
| **Deploy K8s** | Tag (v*) or manual trigger | Deploy to K8s | dev/staging/prod |

## Setup Steps

### 1. Create GitHub Secrets

Go to **Settings → Secrets and Variables → Actions** and add:

#### Vault Integration
```
VAULT_ADDR              https://vault.example.com  (or http://localhost:8200 for dev)
```

#### Terraform Cloud
```
TF_CLOUD_TOKEN          your-terraform-cloud-api-token
```

#### Kubernetes (Optional, for K8s deployment)
```
KUBECONFIG              base64-encoded kubeconfig
SLACK_WEBHOOK           https://hooks.slack.com/services/... (optional)
```

### 2. Create GitHub Environment (Production)

Go to **Settings → Environments** and create `production` environment:

- **Environment protection rules**: Require approval from code owners
- **Secrets**: Add `VAULT_ADDR`, `VAULT_TOKEN`, `TF_CLOUD_TOKEN`

### 3. Configure Vault for GitHub OIDC (Recommended)

For production, use GitHub OIDC instead of long-lived tokens:

```bash
# In Vault, create JWT auth method for GitHub
vault auth enable jwt

vault write auth/jwt/config \
  jwks_url="https://token.actions.githubusercontent.com/.well-known/jwks" \
  bound_issuer="https://token.actions.githubusercontent.com"

vault write auth/jwt/role/github-actions \
  bound_audiences="https://github.com/your-org" \
  user_claim="actor" \
  role_type="jwt" \
  policies="terraform"
```

Then in GitHub Actions:

```yaml
- uses: actions/github-script@v7
  with:
    script: |
      const token = await core.getIDToken('https://vault.example.com');
      core.setSecret(token);
      core.exportVariable('VAULT_TOKEN', token);
```

### 4. Branch Protection Rules (Optional but Recommended)

Go to **Settings → Branches → Branch protection rules**:

For `main` branch:
- ✅ Require PR reviews
- ✅ Require status checks to pass: `build-and-test`
- ✅ Require branches to be up to date
- ✅ Dismiss stale PR approvals
- ✅ Restrict who can push: Code owners only

## Usage

### 1. CI Workflow (Automatic on PR)

```
Feature Branch → Push → GitHub Actions CI
├── Maven compile
├── Unit tests
├── Integration tests
├── Build Docker image (Spring Boot Buildpacks)
└── Publish test results
```

**Result**: Green ✅ or Red ❌ check on PR

### 2. Terraform Plan (Automatic on PR)

When you push changes to `terraform/*`:

```
Feature Branch → Push terraform/ → GitHub Actions Terraform Plan
├── Terraform fmt check
├── Terraform validate
├── Terraform plan
└── Comment plan on PR
```

**Result**: Plan preview in PR comments

### 3. Terraform Apply (Main Branch)

When you merge to `main`:

```
main → Push terraform/ → GitHub Actions Terraform Apply
├── Terraform plan
├── Requires: Production environment approval
├── Terraform apply
├── Updates Terraform Cloud state
└── Notifications (Slack, GitHub)
```

**Result**: Infrastructure updated, state synced to Terraform Cloud

### 4. K8s Deploy (Release Tags)

```
Tag: v1.0.0 → GitHub Actions Deploy
├── Build Docker images
├── Push to GHCR
├── Deploy with Helm + Kustomize
├── Verify deployment
└── Slack notification
```

**Result**: Services running in K8s

## Local Development

### Test CI Workflow Locally

```bash
# Using act (GitHub Actions local runner)
brew install act

# Run CI workflow
act -j build-and-test
```

### Trigger Terraform Plan

```bash
# Make changes to terraform/
git add terraform/
git commit -m "terraform: update monitoring"
git push origin feature/monitoring
```

**Then**: Open PR → GitHub Actions runs terraform-plan automatically

### Manual Trigger

Go to **Actions → Deploy to Kubernetes → Run workflow**:
- Select environment: dev/staging/prod
- GitHub runs deployment

## Secrets Management Best Practices

| Secret | Storage | Method | Rotation |
|--------|---------|--------|----------|
| `VAULT_ADDR` | GitHub Secrets | Plain text | Manual (change in Vault) |
| `TF_CLOUD_TOKEN` | Vault secret | Vault retrieval | Manual (generate in TF Cloud) |
| `KUBECONFIG` | GitHub Secrets | Base64 encoded | Manual (rotate kubeconfig) |

### Environment Variables Override

You can override secrets per environment:

```yaml
env:
  VAULT_ADDR: ${{ secrets.VAULT_ADDR || 'http://localhost:8200' }}
```

## Troubleshooting

### Terraform Init Fails: "No credentials"

**Cause**: VAULT_ADDR or VAULT_TOKEN not set

**Solution**:
```bash
# Check GitHub Secrets
Settings → Secrets and Variables → Actions

# Verify values are set
# If using OIDC, check JWT auth method in Vault
```

### Docker Push Fails: "Permission denied"

**Cause**: `GITHUB_TOKEN` doesn't have container registry permissions

**Solution**:
```yaml
# In workflow, add package:write scope
permissions:
  packages: write
  contents: read
```

### K8s Deployment Fails: "kubeconfig invalid"

**Cause**: KUBECONFIG secret not properly base64 encoded

**Solution**:
```bash
# Encode kubeconfig
cat ~/.kube/config | base64 -w0

# Paste into GitHub Secrets
```

### Terraform Apply Requires Approval but Won't Proceed

**Cause**: Environment protection rules blocking

**Solution**:
1. Go to **Settings → Environments → production**
2. Check "Required reviewers"
3. Reviewer must approve in GitHub workflow
4. Actions → Pending deployments → Review & approve

## Monitoring and Logs

### View Workflow Runs

Go to **Actions** tab → Select workflow → View run details

### Enable Debug Logging

Add GitHub Secret:
```
ACTIONS_STEP_DEBUG = true
```

### Slack Notifications

Update `SLACK_WEBHOOK` in:
- Settings → Secrets
- CI workflow sends notifications
- Deploy workflow sends notifications

## Cost Considerations

| Action | Cost | Monthly Budget |
|--------|------|-----------------|
| CI workflow (2 min/run) | Free (2000 min/month) | Free tier OK |
| Terraform workflow (3 min/run) | Free | Free tier OK |
| K8s deploy (5 min/run) | Free | Free tier OK |
| Storage (artifacts, logs) | Free (500 MB) | Free tier OK |

**Recommendation**: Free tier sufficient for this project

## Advanced Features

### Matrix Builds (Multi-version testing)

```yaml
strategy:
  matrix:
    java-version: [17, 21]
    spring-version: [4.0, 4.1]
```

### Conditional Deployments

```yaml
if: github.ref == 'refs/heads/main' && github.event_name == 'push'
```

### Artifact Management

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: build-artifacts
    path: target/
    retention-days: 30
```

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Terraform GitHub Actions](https://registry.terraform.io/modules/terraform-aws-modules/github-actions/aws)
- [Helm GitHub Action](https://github.com/actions/deploy)
- [Docker Login Action](https://github.com/docker/login-action)

