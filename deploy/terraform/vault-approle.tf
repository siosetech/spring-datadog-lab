# AppRole Auth Method for Local Development
# AppRole allows authentication without long-lived tokens
# RoleID is like username, SecretID is like password

resource "vault_auth_backend" "approle" {
  type = "approle"
  path = "approle"

  depends_on = [vault_generic_secret.datadog_secret]
}

# AppRole for local development (Terraform)
resource "vault_approle_auth_backend_role" "terraform_dev" {
  backend            = vault_auth_backend.approle.path
  role_name          = "terraform-dev"
  token_num_uses     = 0  # No use limit
  token_ttl          = 3600  # 1 hour
  token_max_ttl      = 86400  # 24 hours
  secret_id_num_uses = 0  # No use limit
  secret_id_ttl      = 86400  # 24 hours

  depends_on = [vault_auth_backend.approle]
}

# Bind AppRole to policy that allows reading Datadog secrets
resource "vault_approle_auth_backend_role_policy" "terraform_dev" {
  backend   = vault_auth_backend.approle.path
  role_name = vault_approle_auth_backend_role.terraform_dev.role_name
  policies  = [vault_policy.terraform_policy.name]

  depends_on = [vault_policy.terraform_policy]
}

# Get RoleID (auto-generated, stable)
data "vault_approle_auth_backend_role_id" "terraform_dev" {
  backend   = vault_auth_backend.approle.path
  role_name = vault_approle_auth_backend_role.terraform_dev.role_name

  depends_on = [vault_approle_auth_backend_role.terraform_dev]
}

# Generate SecretID (changes on each apply)
resource "vault_approle_auth_backend_role_secret_id" "terraform_dev" {
  backend   = vault_auth_backend.approle.path
  role_name = vault_approle_auth_backend_role.terraform_dev.role_name

  depends_on = [vault_approle_auth_backend_role.terraform_dev]
}

# Output RoleID and SecretID for local dev setup
output "approle_role_id" {
  value       = data.vault_approle_auth_backend_role_id.terraform_dev.role_id
  sensitive   = false
  description = "AppRole RoleID for local development (store in secure location)"
}

output "approle_secret_id" {
  value       = vault_approle_auth_backend_role_secret_id.terraform_dev.secret_id
  sensitive   = true
  description = "AppRole SecretID for local development (store in secure location, rotate after use)"
}

# Policy for Terraform to read/write secrets
resource "vault_policy" "terraform_policy" {
  name = "terraform-policy"

  policy = <<EOH
# Read Datadog secrets
path "secret/data/datadog/*" {
  capabilities = ["read", "list"]
}

# Read Terraform Cloud secrets
path "secret/data/terraform/*" {
  capabilities = ["read", "list"]
}

# Read database secrets
path "secret/data/database/*" {
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
