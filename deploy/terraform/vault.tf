# Retrieve Datadog API credentials from Vault
data "vault_generic_secret" "datadog" {
  path = var.vault_secret_path

  depends_on = [
    # Ensure Vault provider is authenticated before reading secrets
  ]
}

# Output Vault secret path for reference
output "vault_secret_path" {
  description = "Vault path where Datadog secrets are stored"
  value       = var.vault_secret_path
  sensitive   = false
}

# Validate that required fields exist in Vault secret
locals {
  datadog_api_key = try(data.vault_generic_secret.datadog.data["api_key"], null)
  datadog_app_key = try(data.vault_generic_secret.datadog.data["app_key"], null)

  # Validate secrets are available
  _check_vault_datadog_api_key = local.datadog_api_key != null ? true : file("ERROR: api_key not found in ${var.vault_secret_path} - ensure Vault secret exists")
  _check_vault_datadog_app_key = local.datadog_app_key != null ? true : file("ERROR: app_key not found in ${var.vault_secret_path} - ensure Vault secret exists")
}
