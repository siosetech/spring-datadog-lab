variable "github_token" {
  description = "GitHub personal access token (repo + admin:repo_hook scopes)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "github_owner" {
  description = "GitHub repository owner (username or organization)"
  type        = string
  default     = ""
}

variable "github_owner_full" {
  description = "Full GitHub repository path (owner/repo) - used for JWT claims"
  type        = string
  default     = ""
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

variable "kubeconfig" {
  description = "Base64-encoded kubeconfig for K8s deployment"
  type        = string
  sensitive   = true
  default     = ""
}

variable "slack_webhook" {
  description = "Slack webhook URL for notifications"
  type        = string
  sensitive   = true
  default     = ""
}

variable "vault_address_prod" {
  description = "Vault address for production environment (override default)"
  type        = string
  default     = ""
}

variable "vault_token_prod" {
  description = "Vault token for production environment (override default)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "vault_address" {
  description = "HashiCorp Vault address (e.g., http://localhost:8200 or https://vault.example.com)"
  type        = string
  default     = "http://localhost:8200"
}

variable "vault_token" {
  description = "Vault authentication token"
  type        = string
  sensitive   = true
}

variable "vault_skip_tls_verify" {
  description = "Skip TLS verification for Vault (dev only)"
  type        = bool
  default     = true # Set to false in production
}

variable "vault_secret_path" {
  description = "Path to Datadog secrets in Vault (e.g., secret/datadog)"
  type        = string
  default     = "secret/datadog"
}

variable "vault_terraform_cloud_path" {
  description = "Path to Terraform Cloud credentials in Vault (e.g., secret/terraform-cloud)"
  type        = string
  default     = "secret/terraform-cloud"
}

variable "terraform_cloud_enabled" {
  description = "Enable Terraform Cloud remote state backend"
  type        = bool
  default     = false
}

variable "terraform_cloud_org" {
  description = "Terraform Cloud organization name"
  type        = string
  default     = "your-org"
}

variable "terraform_cloud_workspace" {
  description = "Terraform Cloud workspace name"
  type        = string
  default     = "spring-datadog-lab"
}

variable "datadog_api_url" {
  description = "Datadog API URL (e.g., https://api.datadoghq.com or https://api.datadoghq.eu)"
  type        = string
  default     = "https://api.datadoghq.com"
}

variable "notification_channels" {
  description = "Email addresses or webhook URLs for alert notifications"
  type        = list(string)
  default     = []
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "k8s_namespace" {
  description = "Kubernetes namespace for microservices"
  type        = string
  default     = "spring-datadog-lab"
}

variable "vault_jwt_audience" {
  description = "GitHub OIDC JWT audience (standard: sigstore)"
  type        = string
  default     = "sigstore"
}


variable "project_name" {
  description = "Project name for resource tagging"
  type        = string
  default     = "spring-datadog-lab"
}
