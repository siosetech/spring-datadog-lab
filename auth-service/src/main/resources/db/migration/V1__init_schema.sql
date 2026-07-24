CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    sso_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE dashboards (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE dashboard_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    dashboard_id BIGINT NOT NULL REFERENCES dashboards(id),
    access_level VARCHAR(50) NOT NULL
);

INSERT INTO tenants (domain, name, status) VALUES ('default.local', 'Default Tenant', 'ACTIVE');
