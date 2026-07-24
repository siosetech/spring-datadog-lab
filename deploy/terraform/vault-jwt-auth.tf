# JWT Auth Method for GitHub Actions
# Uses GitHub OIDC (OpenID Connect) to authenticate without long-lived tokens
# More secure: tokens are short-lived (5 minutes) and issued on-demand

resource "vault_jwt_auth_backend" "github" {
  description        = "GitHub OIDC JWT authentication"
  path               = "jwt"
  oidc_discovery_url = "https://token.actions.githubusercontent.com"
  oidc_client_id     = "sigstore"  # Standard GitHub Actions OIDC client

  depends_on = [vault_policy.terraform_policy]
}

# Vault role for GitHub Actions CI/CD
resource "vault_jwt_auth_backend_role" "github_actions" {
  backend   = vault_jwt_auth_backend.github.path
  role_name = "github-actions"

  # Accept JWT claims from GitHub Actions for this repo
  user_claim      = "actor"
  role_type       = "jwt"
  token_ttl       = 1800  # 30 minutes
  token_max_ttl   = 3600  # 1 hour
  token_num_uses  = 0     # No use limit
  bound_audiences = ["sigstore"]  # GitHub OIDC audience

  # Bind to specific GitHub repository and workflows
  # Format: "repo:owner/repo:*" allows all workflows in the repo
  bound_claims = {
    repository = var.github_owner_full  # e.g., "siosetech/spring-datadog-lab"
  }

  # Bind to specific workflow files for extra security (optional)
  # bound_claims = {
  #   repository      = var.github_owner_full
  #   workflow_trigger = "push"  # Only authenticate on push events
  # }

  policies = [vault_policy.github_actions_policy.name]

  depends_on = [vault_jwt_auth_backend.github]
}

# Policy for GitHub Actions (more limited than local Terraform)
resource "vault_policy" "github_actions_policy" {
  name = "github-actions-policy"

  policy = <<EOH
# Read secrets for infrastructure provisioning
path "secret/data/datadog/*" {
  capabilities = ["read", "list"]
}

path "secret/data/terraform/*" {
  capabilities = ["read", "list"]
}

path "secret/data/kubernetes/*" {
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

# Outputs for GitHub Actions workflow setup
output "vault_jwt_auth_path" {
  value       = vault_jwt_auth_backend.github.path
  description = "Vault JWT auth path for GitHub Actions (use 'jwt' in workflow)"
}

output "vault_github_role_name" {
  value       = vault_jwt_auth_backend_role.github_actions.role_name
  description = "Vault role name for GitHub Actions (use in workflow)"
}
