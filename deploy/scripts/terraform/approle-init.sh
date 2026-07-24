#!/bin/bash
# Initialize AppRole credentials for local development Terraform
# AppRole provides secure authentication without long-lived root tokens

set -e

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"

echo "🔐 Terraform AppRole Setup"
echo "=============================="
echo ""

# Check Vault connectivity
echo "📍 Checking Vault at: $VAULT_ADDR"
if ! curl -s "$VAULT_ADDR/v1/sys/health" > /dev/null 2>&1; then
    echo "❌ Cannot connect to Vault at $VAULT_ADDR"
    echo "   Make sure Vault is running:"
    echo "   vault server -dev"
    exit 1
fi
echo "✅ Vault is accessible"
echo ""

# First, ensure Datadog secrets exist
echo "📍 Checking for Datadog secrets in Vault..."
SECRETS_EXIST=$(curl -s -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/secret/data/datadog" 2>/dev/null | grep -c "api_key" || true)

if [ "$SECRETS_EXIST" -eq 0 ]; then
    echo "⚠️  Datadog secrets not found in Vault. Creating them now..."
    read -p "Enter Datadog API Key: " DATADOG_API_KEY
    read -sp "Enter Datadog App Key: " DATADOG_APP_KEY
    echo ""
    
    curl -s -X POST \
      -H "X-Vault-Token: $VAULT_TOKEN" \
      -d "{\"data\": {\"api_key\": \"$DATADOG_API_KEY\", \"app_key\": \"$DATADOG_APP_KEY\"}}" \
      "$VAULT_ADDR/v1/secret/data/datadog" > /dev/null
    
    echo "✅ Datadog secrets stored in Vault"
else
    echo "✅ Datadog secrets already exist in Vault"
fi
echo ""

# Run Terraform to create AppRole
echo "🔧 Running Terraform to set up AppRole auth method..."
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

export VAULT_ADDR
export VAULT_TOKEN
export TF_VAR_vault_address="$VAULT_ADDR"
export TF_VAR_vault_token="$VAULT_TOKEN"

cd terraform

# Initialize and apply AppRole setup
echo "📍 Terraform init..."
terraform init -upgrade

echo "📍 Terraform apply (AppRole setup)..."
terraform apply -target=vault_auth_backend.approle \
                 -target=vault_approle_auth_backend_role.terraform_dev \
                 -target=vault_approle_auth_backend_role_policy.terraform_dev \
                 -target=data.vault_approle_auth_backend_role_id.terraform_dev \
                 -target=vault_approle_auth_backend_role_secret_id.terraform_dev \
                 -target=vault_policy.terraform_policy \
                 -auto-approve

echo ""
echo "✅ AppRole configured in Vault"
echo ""

# Output credentials
ROLE_ID=$(terraform output -raw approle_role_id 2>/dev/null || echo "ERROR")
SECRET_ID=$(terraform output -raw approle_secret_id 2>/dev/null || echo "ERROR")

if [ "$ROLE_ID" = "ERROR" ] || [ "$SECRET_ID" = "ERROR" ]; then
    echo "❌ Failed to retrieve AppRole credentials from Terraform output"
    echo "   Try running: cd terraform && terraform output"
    exit 1
fi

echo "🔐 AppRole Credentials"
echo "====================="
echo ""
echo "RoleID (stable, can be checked into git if needed):"
echo "  $ROLE_ID"
echo ""
echo "SecretID (rotate after use, keep secure!):"
echo "  $SECRET_ID"
echo ""
echo "📝 Save these in your environment or ~/.hcl/vault:"
echo ""
echo "   export VAULT_ROLE_ID='$ROLE_ID'"
echo "   export VAULT_SECRET_ID='$SECRET_ID'"
echo ""
echo "   OR in .env:"
echo "   VAULT_ROLE_ID=$ROLE_ID"
echo "   VAULT_SECRET_ID=$SECRET_ID"
echo ""
echo "🚀 To use AppRole for Terraform authentication:"
echo "   export VAULT_ADDR='$VAULT_ADDR'"
echo "   export VAULT_ROLE_ID='$ROLE_ID'"
echo "   export VAULT_SECRET_ID='$SECRET_ID'"
echo ""
echo "   Then run:"
echo "   cd terraform"
echo "   terraform plan"
echo "   terraform apply"
echo ""
