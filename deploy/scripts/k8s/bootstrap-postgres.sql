-- Shared lab DB bootstrap + per-service databases for Flyway isolation.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth') THEN
    CREATE ROLE auth LOGIN PASSWORD 'auth';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit') THEN
    CREATE ROLE audit LOGIN PASSWORD 'audit';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notification') THEN
    CREATE ROLE notification LOGIN PASSWORD 'notification';
  END IF;
END
$$;

SELECT 'CREATE DATABASE auth_service OWNER auth'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_service')\gexec

SELECT 'CREATE DATABASE datadog_lab_db OWNER audit'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'datadog_lab_db')\gexec

SELECT 'CREATE DATABASE notification_service OWNER notification'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_service')\gexec

GRANT ALL PRIVILEGES ON DATABASE auth_service TO auth;
GRANT ALL PRIVILEGES ON DATABASE datadog_lab_db TO audit;
GRANT ALL PRIVILEGES ON DATABASE notification_service TO notification;
