#!/bin/sh
set -eu

# User password configuration
: "${POSTGRES_USER:=idpserver}"
: "${POSTGRES_PASSWORD:=idpserver}"
: "${POSTGRES_DB:=idpserver}"
: "${DB_OWNER_USER:=idp}"

# Require password environment variables (no defaults for security)
if [ -z "${IDP_DB_ADMIN_PASSWORD:-}" ]; then
  echo "ERROR: IDP_DB_ADMIN_PASSWORD environment variable is required" >&2
  exit 1
fi

if [ -z "${IDP_DB_APP_PASSWORD:-}" ]; then
  echo "ERROR: IDP_DB_APP_PASSWORD environment variable is required" >&2
  exit 1
fi

# Set password for psql connection (use superuser credentials)
export PGPASSWORD="$POSTGRES_PASSWORD"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- Admin user with BYPASSRLS
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'idp_admin_user') THEN
      CREATE USER idp_admin_user WITH PASSWORD '${IDP_DB_ADMIN_PASSWORD}' BYPASSRLS;
      RAISE NOTICE 'User idp_admin_user created';
    ELSE
      RAISE NOTICE 'User idp_admin_user already exists, skipping';
    END IF;
  END
  \$\$;

  GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO idp_admin_user;
  GRANT USAGE ON SCHEMA public TO idp_admin_user;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_admin_user;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_admin_user;

  --- Grant permissions for future tables
  ALTER DEFAULT PRIVILEGES
      FOR ROLE ${DB_OWNER_USER}
      IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_admin_user;

  --- for future sequence
  ALTER DEFAULT PRIVILEGES
      FOR ROLE ${DB_OWNER_USER}
      IN SCHEMA public
      GRANT USAGE, SELECT ON SEQUENCES TO idp_admin_user;

  -- App user
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'idp_app_user') THEN
      CREATE USER idp_app_user WITH PASSWORD '${IDP_DB_APP_PASSWORD}';
      RAISE NOTICE 'User idp_app_user created';
    ELSE
      RAISE NOTICE 'User idp_app_user already exists, skipping';
    END IF;
  END
  \$\$;

  GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO idp_app_user;
  GRANT USAGE ON SCHEMA public TO idp_app_user;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_app_user;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_app_user;

  --- for future table
  ALTER DEFAULT PRIVILEGES
      FOR ROLE ${DB_OWNER_USER}
      IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_app_user;

  --- for future sequence
  ALTER DEFAULT PRIVILEGES
      FOR ROLE ${DB_OWNER_USER}
      IN SCHEMA public
      GRANT USAGE, SELECT ON SEQUENCES TO idp_app_user;
EOSQL
