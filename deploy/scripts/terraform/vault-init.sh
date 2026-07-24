#!/bin/bash
# Initialize Vault and store Datadog credentials for Terraform

set -e

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"
VAULT_SECRET_PATH="secret/datadog"

echo "🔐 Terraform + Vault Integration Setup"
echo "========================================"
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

# Prompt for Datadog credentials
read -p "Enter Datadog API Key: " DATADOG_API_KEY
read -sp "Enter Datadog App Key: " DATADOG_APP_KEY
echo ""
echo ""

# Store secrets in Vault
echo "💾 Storing Datadog credentials in Vault at $VAULT_SECRET_PATH..."
curl -s -X POST \
  -H "X-Vault-Token: $VAULT_TOKEN" \
  -d "{\"data\": {\"api_key\": \"$DATADOG_API_KEY\", \"app_key\": \"$DATADOG_APP_KEY\"}}" \
  "$VAULT_ADDR/v1/$VAULT_SECRET_PATH" > /dev/null

if [ $? -eq 0 ]; then
    echo "✅ Credentials stored in Vault"
else
    echo "❌ Failed to store credentials in Vault"
    exit 1
fi
echo ""

# Verify secrets
echo "🔍 Verifying stored secrets..."
curl -s -H "X-Vault-Token: $VAULT_TOKEN" \
  "$VAULT_ADDR/v1/$VAULT_SECRET_PATH" | grep -q "api_key"

if [ $? -eq 0 ]; then
    echo "✅ Secrets verified in Vault"
else
    echo "❌ Failed to verify secrets"
    exit 1
fi
echo ""

# Export environment variables for Terraform
echo "🔧 Setting up Terraform environment variables..."
export VAULT_ADDR
export VAULT_TOKEN
export TF_VAR_vault_address="$VAULT_ADDR"
export TF_VAR_vault_token="$VAULT_TOKEN"
export TF_VAR_vault_secret_path="$VAULT_SECRET_PATH"

echo "✅ Environment variables configured"
echo ""
echo "📝 Run this to export variables in your shell:"
echo "   export VAULT_ADDR='$VAULT_ADDR'"
echo "   export VAULT_TOKEN='$VAULT_TOKEN'"
echo "   export TF_VAR_vault_address='$VAULT_ADDR'"
echo "   export TF_VAR_vault_token='$VAULT_TOKEN'"
echo "   export TF_VAR_vault_secret_path='$VAULT_SECRET_PATH'"
echo ""
echo "🚀 Ready to run Terraform:"
echo "   cd terraform"
echo "   terraform init"
echo "   terraform plan"
echo "   terraform apply"
