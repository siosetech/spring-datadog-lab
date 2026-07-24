#!/bin/bash
# Setup Terraform Cloud integration with Vault for remote state backup

set -e

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"
TF_CLOUD_SECRET_PATH="secret/terraform-cloud"

echo "🌐 Terraform Cloud + Vault Setup"
echo "=================================="
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

# Get Terraform Cloud API Token
read -p "Enter your Terraform Cloud API token: " TF_CLOUD_TOKEN
echo ""

if [ -z "$TF_CLOUD_TOKEN" ]; then
    echo "❌ API token cannot be empty"
    exit 1
fi

# Store in Vault
echo "💾 Storing Terraform Cloud token in Vault at $TF_CLOUD_SECRET_PATH..."
curl -s -X POST \
  -H "X-Vault-Token: $VAULT_TOKEN" \
  -d "{\"data\": {\"token\": \"$TF_CLOUD_TOKEN\"}}" \
  "$VAULT_ADDR/v1/$TF_CLOUD_SECRET_PATH" > /dev/null

if [ $? -eq 0 ]; then
    echo "✅ Token stored in Vault"
else
    echo "❌ Failed to store token in Vault"
    exit 1
fi
echo ""

# Verify token
echo "🔍 Verifying stored token..."
curl -s -H "X-Vault-Token: $VAULT_TOKEN" \
  "$VAULT_ADDR/v1/$TF_CLOUD_SECRET_PATH" | grep -q "token"

if [ $? -eq 0 ]; then
    echo "✅ Token verified in Vault"
else
    echo "❌ Failed to verify token"
    exit 1
fi
echo ""

# Get Terraform Cloud Organization
read -p "Enter your Terraform Cloud organization name: " TF_CLOUD_ORG

if [ -z "$TF_CLOUD_ORG" ]; then
    echo "❌ Organization name cannot be empty"
    exit 1
fi
echo ""

# Export environment variables
echo "🔧 Setting up environment variables..."
export VAULT_ADDR
export VAULT_TOKEN
export TF_VAR_vault_address="$VAULT_ADDR"
export TF_VAR_vault_token="$VAULT_TOKEN"
export TF_VAR_vault_terraform_cloud_path="$TF_CLOUD_SECRET_PATH"
export TF_VAR_terraform_cloud_enabled="true"
export TF_VAR_terraform_cloud_org="$TF_CLOUD_ORG"
export TF_VAR_terraform_cloud_workspace="spring-datadog-lab"

echo "✅ Environment variables configured"
echo ""

# Export Terraform Cloud token for CLI authentication
export TF_TOKEN_app_terraform_io="$TF_CLOUD_TOKEN"
echo "✅ Terraform Cloud CLI token exported"
echo ""

echo "📋 Summary:"
echo "   - Terraform Cloud organization: $TF_CLOUD_ORG"
echo "   - Workspace: spring-datadog-lab"
echo "   - State storage: Remote (Terraform Cloud)"
echo ""

echo "📝 Export these variables in your shell:"
cat << EOF
export VAULT_ADDR='$VAULT_ADDR'
export VAULT_TOKEN='$VAULT_TOKEN'
export TF_VAR_vault_address='$VAULT_ADDR'
export TF_VAR_vault_token='$VAULT_TOKEN'
export TF_VAR_vault_terraform_cloud_path='$TF_CLOUD_SECRET_PATH'
export TF_VAR_terraform_cloud_enabled='true'
export TF_VAR_terraform_cloud_org='$TF_CLOUD_ORG'
export TF_VAR_terraform_cloud_workspace='spring-datadog-lab'
export TF_TOKEN_app_terraform_io='$TF_CLOUD_TOKEN'
EOF
echo ""

echo "🚀 Ready to enable Terraform Cloud remote state:"
echo "   1. Update terraform.tfvars with the variables above"
echo "   2. Uncomment the 'cloud' block in providers.tf"
echo "   3. Run: terraform init"
echo "   4. When prompted, choose 'yes' to migrate state to Terraform Cloud"
echo ""
echo "✨ Your tfstate will now be safely backed up in Terraform Cloud!"
