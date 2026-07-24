#!/bin/bash
# Runs only on first Postgres data dir init (empty volume).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth') THEN
            CREATE ROLE auth LOGIN PASSWORD 'auth';
        END IF;
    END
    \$\$;
EOSQL

DB_EXISTS=$(psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "SELECT 1 FROM pg_database WHERE datname='auth_service'" | tr -d '[:space:]')
if [ "$DB_EXISTS" != "1" ]; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -c "CREATE DATABASE auth_service OWNER \"${POSTGRES_USER}\";"
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -c "GRANT ALL PRIVILEGES ON DATABASE auth_service TO auth;"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "auth_service" <<-EOSQL
    GRANT ALL ON SCHEMA public TO auth;
    GRANT ALL ON SCHEMA public TO "${POSTGRES_USER}";
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO auth;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO auth;
EOSQL

echo "Postgres init: auth_service DB + auth role ready (Flyway creates tables on app start)."
