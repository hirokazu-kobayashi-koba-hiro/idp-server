#!/bin/sh
set -eu

# Application owner user configuration
: "${DB_OWNER_USER:=idp}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- Grant BYPASSRLS to application owner user
  DO \$\$
  BEGIN
    ALTER USER ${DB_OWNER_USER} WITH BYPASSRLS;
    RAISE NOTICE 'BYPASSRLS granted to application owner user "%"', '${DB_OWNER_USER}';
  END
  \$\$;
EOSQL
