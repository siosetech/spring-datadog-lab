# Terraform Cloud Remote State Backend Configuration
#
# This file enables remote state storage and team management in Terraform Cloud.
# It works in conjunction with Vault for storing sensitive authentication tokens.

# Retrieve Terraform Cloud API token from Vault
# Vault path should contain: { "token": "your-terraform-cloud-api-token" }
data "vault_generic_secret" "terraform_cloud_token" {
  path = var.vault_terraform_cloud_path
}

locals {
  terraform_cloud_token = try(data.vault_generic_secret.terraform_cloud_token.data["token"], null)
  
  # Enable cloud block if both terraform_cloud_enabled and token exist
  use_terraform_cloud = var.terraform_cloud_enabled && local.terraform_cloud_token != null
}

# Validation: Warn if cloud is disabled or token is missing
output "terraform_cloud_status" {
  description = "Terraform Cloud configuration status"
  value = {
    enabled  = var.terraform_cloud_enabled
    org      = var.terraform_cloud_org
    workspace = var.terraform_cloud_workspace
    token_available = local.terraform_cloud_token != null
    ready = local.use_terraform_cloud
  }
}

# Instructions for enabling Terraform Cloud
output "terraform_cloud_setup_instructions" {
  description = "How to enable Terraform Cloud remote state"
  value = var.terraform_cloud_enabled && local.terraform_cloud_token == null ? <<-EOT
    
    ⚠️  Terraform Cloud is enabled but token not found in Vault!
    
    To enable remote state:
    
    1. Store token in Vault:
       export VAULT_ADDR="http://localhost:8200"
       export VAULT_TOKEN="root"
       vault kv put secret/terraform-cloud token="your-api-token-here"
    
    2. Update terraform.tfvars:
       terraform_cloud_enabled = true
       terraform_cloud_org = "your-org"
       terraform_cloud_workspace = "spring-datadog-lab"
    
    3. Run:
       terraform init
    
    4. When prompted, choose "migrate" to move state to Terraform Cloud
    
  EOT : ""
}
