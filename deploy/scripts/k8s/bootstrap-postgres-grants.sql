\c auth_service
GRANT ALL ON SCHEMA public TO auth;
ALTER SCHEMA public OWNER TO auth;

\c datadog_lab_db
GRANT ALL ON SCHEMA public TO audit;
GRANT ALL ON SCHEMA public TO notification;
ALTER SCHEMA public OWNER TO audit;
