# Idempotent bootstrap for an already-initialized Compose Postgres volume.
# (docker-entrypoint-initdb.d only runs on first empty data dir.)
$ErrorActionPreference = "Stop"
$container = "spring-datadog-lab-postgres-1"

Write-Host "Bootstrapping auth_service on $container ..."

docker exec $container psql -U lab -d datadog_lab_db -v ON_ERROR_STOP=1 -c @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth') THEN
    CREATE ROLE auth LOGIN PASSWORD 'auth';
  END IF;
END
`$`$;
"@

$exists = (docker exec $container psql -U lab -d datadog_lab_db -tAc "SELECT 1 FROM pg_database WHERE datname='auth_service'").Trim()
if ($exists -ne "1") {
  docker exec $container psql -U lab -d datadog_lab_db -v ON_ERROR_STOP=1 -c "CREATE DATABASE auth_service OWNER lab;"
}

docker exec $container psql -U lab -d datadog_lab_db -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE auth_service TO auth;"

docker exec $container psql -U lab -d auth_service -v ON_ERROR_STOP=1 -c @"
GRANT ALL ON SCHEMA public TO auth;
GRANT ALL ON SCHEMA public TO lab;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO auth;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO auth;
GRANT ALL ON ALL TABLES IN SCHEMA public TO auth;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO auth;
"@

Write-Host "Done. Restart auth-service so Flyway can migrate if tables are missing."
