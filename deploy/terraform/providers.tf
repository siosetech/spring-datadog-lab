terraform {
  required_version = ">= 1.5"
  required_providers {
    datadog = {
      source  = "DataDog/datadog"
      version = "~> 3.45"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "~> 4.2"
    }
  }

  # Remote state management using Terraform Cloud
  # Uncomment and configure to enable. Requires TF_TOKEN_app_terraform_io environment variable
  # or terraform_cloud_token stored in Vault (see cloud-backend.tf)
  #
  # cloud {
  #   organization = "your-org"
  #   
  #   workspaces {
  #     name = "spring-datadog-lab"
  #   }
  # }
}

# Retrieve Terraform Cloud token from Vault (optional, for automated auth)
data "vault_generic_secret" "terraform_cloud" {
  path = var.vault_terraform_cloud_path
  
  # Only fetch if terraform_cloud_enabled is true
  depends_on = []
}

provider "vault" {
  address         = var.vault_address
  token           = var.vault_token
  skip_tls_verify = var.vault_skip_tls_verify
}

provider "datadog" {
  api_key = data.vault_generic_secret.datadog.data["api_key"]
  app_key = data.vault_generic_secret.datadog.data["app_key"]
  api_url = var.datadog_api_url
}
