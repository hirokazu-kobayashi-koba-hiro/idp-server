#!/bin/sh
set -eu

# Application owner user configuration
: "${DB_OWNER_USER:=idp}"
: "${DB_OWNER_PASSWORD:=idp}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- Create application owner user
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_OWNER_USER}') THEN
      CREATE USER ${DB_OWNER_USER} WITH PASSWORD '${DB_OWNER_PASSWORD}';
      GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${DB_OWNER_USER};
      GRANT USAGE ON SCHEMA public TO ${DB_OWNER_USER};
      GRANT CREATE ON SCHEMA public TO ${DB_OWNER_USER};
      GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB} TO ${DB_OWNER_USER};
      RAISE NOTICE 'Application owner user "%" created', '${DB_OWNER_USER}';
    ELSE
      RAISE NOTICE 'Application owner user "%" already exists', '${DB_OWNER_USER}';
    END IF;
  END
  \$\$;
EOSQL
