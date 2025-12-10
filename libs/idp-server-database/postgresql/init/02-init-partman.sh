#!/bin/sh
set -eu

# Application owner user configuration
: "${DB_OWNER_USER:=idp}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- ================================================
  -- Initialize pg_cron and pg_partman extensions
  -- These require superuser privileges
  -- ================================================

  -- pg_cron for scheduled execution
  -- Note: pg_cron must be in shared_preload_libraries
  CREATE EXTENSION IF NOT EXISTS pg_cron;

  -- pg_partman for partition management
  CREATE SCHEMA IF NOT EXISTS partman;
  CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;

  -- ================================================
  -- Grant permissions to application owner user
  -- ================================================

  -- partman schema permissions
  GRANT USAGE, CREATE ON SCHEMA partman TO ${DB_OWNER_USER};
  GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO ${DB_OWNER_USER};
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA partman TO ${DB_OWNER_USER};
  GRANT ALL ON ALL SEQUENCES IN SCHEMA partman TO ${DB_OWNER_USER};

  -- cron schema permissions
  GRANT USAGE ON SCHEMA cron TO ${DB_OWNER_USER};
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO ${DB_OWNER_USER};

  RAISE NOTICE 'pg_cron and pg_partman extensions initialized with permissions for "%"', '${DB_OWNER_USER}';
EOSQL
