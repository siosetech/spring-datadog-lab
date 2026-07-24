#!/bin/bash
# Wait for Vault to start
echo "Waiting for Vault to start..."
sleep 3

export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root'

echo "Seeding Vault secrets..."

# Dev mode Vault starts with a v2 KV secret engine mounted at secret/
vault kv put secret/auth-service DB_USERNAME=auth DB_PASSWORD=auth
vault kv put secret/user-profile-service DB_USERNAME=user_profile DB_PASSWORD=user_profile
vault kv put secret/audit-log-service DB_USERNAME=audit DB_PASSWORD=audit
vault kv put secret/notification-service DB_USERNAME=notification DB_PASSWORD=notification
vault kv put secret/dashboard-service DB_USERNAME=dashboard DB_PASSWORD=dashboard

echo "Vault initialization complete."
