#!/bin/sh
set -eu

# Application owner user configuration
: "${DB_OWNER_USER:=idp}"

# ================================================
# Initialize pg_cron in postgres database (cross-database setup)
# ================================================
# pg_cron is configured with cron.database_name=postgres
# This allows scheduling jobs that run on any database using
# cron.schedule_in_database() function.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
  -- pg_cron for scheduled execution (cross-database mode)
  -- Note: pg_cron must be in shared_preload_libraries
  CREATE EXTENSION IF NOT EXISTS pg_cron;

  -- Grant cron schema permissions to application owner user
  -- This allows the user to create and manage scheduled jobs
  GRANT USAGE ON SCHEMA cron TO ${DB_OWNER_USER};
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO ${DB_OWNER_USER};

  -- Grant execute permission on cron functions for cross-database scheduling
  GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA cron TO ${DB_OWNER_USER};
EOSQL

# ================================================
# Initialize pg_partman in application database
# ================================================
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
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

  -- ================================================
  -- Grant file write permission for archive export
  -- ================================================
  -- Required for COPY TO file in archive.export_partition_to_external_storage()
  -- This allows the DB owner to export archived partitions to local files.
  -- Note: In production with AWS RDS/Aurora, use aws_s3 extension instead.
  GRANT pg_write_server_files TO ${DB_OWNER_USER};
EOSQL

# ================================================
# Create archive export directory
# ================================================
# pg_partman archive export writes CSV files to this directory.
# Located inside the data volume so it persists and is writable by postgres.
ARCHIVE_DIR="/var/lib/postgresql/data/archive"
mkdir -p "$ARCHIVE_DIR"
chown postgres:postgres "$ARCHIVE_DIR"
echo "Archive export directory created: $ARCHIVE_DIR"

echo "pg_cron and pg_partman extensions initialized with permissions for '${DB_OWNER_USER}'"
