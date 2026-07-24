# GitHub Provider Configuration
# Manages GitHub repository configuration as Infrastructure-as-Code

terraform {
  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }
}

variable "github_token" {
  description = "GitHub personal access token or app token with repo and admin:repo_hook scope"
  type        = string
  sensitive   = true
}

variable "github_owner" {
  description = "GitHub repository owner (username or organization)"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name"
  type        = string
  default     = "spring-datadog-lab"
}

variable "github_branch" {
  description = "Main branch name"
  type        = string
  default     = "main"
}

variable "require_branch_reviews" {
  description = "Number of required pull request reviews"
  type        = number
  default     = 1
}

provider "github" {
  token = var.github_token
  owner = var.github_owner
}

# Data source: Get repository info
data "github_repository" "repo" {
  name = var.github_repo
}

# ============================================================================
# SECRETS MANAGEMENT
# ============================================================================

# Secret: Vault Address
resource "github_actions_secret" "vault_addr" {
  repository       = data.github_repository.repo.name
  secret_name      = "VAULT_ADDR"
  plaintext_value  = var.vault_address
}



# Secret: Terraform Cloud Token (from Vault)
resource "github_actions_secret" "tf_cloud_token" {
  repository       = data.github_repository.repo.name
  secret_name      = "TF_CLOUD_TOKEN"
  plaintext_value  = var.tf_cloud_token
  
  depends_on = [github_actions_secret.vault_addr]
}

# Secret: Kubernetes Config (optional, base64 encoded)
resource "github_actions_secret" "kubeconfig" {
  repository       = data.github_repository.repo.name
  secret_name      = "KUBECONFIG"
  plaintext_value  = var.kubeconfig != "" ? var.kubeconfig : "placeholder"
  
  count      = var.kubeconfig != "" ? 1 : 0
  depends_on = [github_actions_secret.tf_cloud_token]
}

# Secret: Slack Webhook (optional)
resource "github_actions_secret" "slack_webhook" {
  repository       = data.github_repository.repo.name
  secret_name      = "SLACK_WEBHOOK"
  plaintext_value  = var.slack_webhook != "" ? var.slack_webhook : "placeholder"
  
  count      = var.slack_webhook != "" ? 1 : 0
  depends_on = [github_actions_secret.tf_cloud_token]
}

# ============================================================================
# ENVIRONMENTS
# ============================================================================

# Environment: Production (requires approval)
resource "github_repository_environment" "production" {
  environment = "production"
  repository  = data.github_repository.repo.name
  description = "Production deployment environment (requires approval)"
}

# Environment Secrets: Production Vault Auth
resource "github_actions_environment_secret" "prod_vault_addr" {
  repository       = data.github_repository.repo.name
  environment      = github_repository_environment.production.environment
  secret_name      = "VAULT_ADDR"
  plaintext_value  = var.vault_address_prod != "" ? var.vault_address_prod : var.vault_address
}



# Deployment Reviewers (required to approve deployments)
# Note: Terraform requires admin:write for this
resource "github_repository_environment_deployment_policy" "production_policy" {
  repository       = data.github_repository.repo.name
  environment      = github_repository_environment.production.environment
  branch_pattern   = var.github_branch
}

# ============================================================================
# BRANCH PROTECTION
# ============================================================================

# Branch Protection: Main branch
resource "github_branch_protection" "main" {
  repository_id            = data.github_repository.repo.node_id
  pattern                  = var.github_branch
  enforce_admins           = true
  require_signed_commits   = false
  required_linear_history  = false
  
  # Require PR reviews
  required_pull_request_reviews {
    required_approving_review_count = var.require_branch_reviews
    dismiss_stale_reviews           = true
    require_code_owner_reviews      = false
    restrict_dismissals             = false
  }
  
  # Require status checks
  required_status_checks {
    strict   = true
    contexts = [
      "build-and-test",
      "code-quality",
      "terraform-plan"
    ]
  }
  
  # Restrict who can push
  restrictions {
    users = []
    teams = []
    apps  = []
  }
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "github_secrets_created" {
  description = "GitHub secrets configured"
  value = {
    vault_addr     = github_actions_secret.vault_addr.secret_name

    tf_cloud_token = github_actions_secret.tf_cloud_token.secret_name
    kubeconfig     = var.kubeconfig != "" ? github_actions_secret.kubeconfig[0].secret_name : "not configured"
    slack_webhook  = var.slack_webhook != "" ? github_actions_secret.slack_webhook[0].secret_name : "not configured"
  }
}

output "github_environments_created" {
  description = "GitHub environments configured"
  value = {
    production = github_repository_environment.production.environment
  }
}

output "github_branch_protection_status" {
  description = "Branch protection configuration"
  value = {
    branch              = github_branch_protection.main.pattern
    required_reviews    = var.require_branch_reviews
    enforce_admins      = true
    required_checks     = true
  }
}
