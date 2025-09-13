#!/bin/sh
set -eu

: "${IDP_DB_ADMIN_PASSWORD:=idp_admin_user}"
: "${IDP_DB_APP_PASSWORD:=idp_app_user}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- Admin user with BYPASSRLS
  CREATE USER idp_admin_user WITH PASSWORD '${IDP_DB_ADMIN_PASSWORD}' BYPASSRLS;
  GRANT CONNECT ON DATABASE idpserver TO idp_admin_user;
  GRANT USAGE ON SCHEMA public TO idp_admin_user;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_admin_user;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_admin_user;
  ALTER USER idp_admin_user CONNECTION LIMIT 25;

  --- Grant permissions for future tables
  ALTER DEFAULT PRIVILEGES
      FOR ROLE idpserver
      IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_admin_user;

  --- for future sequence
  ALTER DEFAULT PRIVILEGES
      FOR ROLE idpserver
      IN SCHEMA public
      GRANT USAGE, SELECT ON SEQUENCES TO idp_admin_user;

  -- App user
  CREATE USER idp_app_user WITH PASSWORD '${IDP_DB_APP_PASSWORD}';
  GRANT CONNECT ON DATABASE idpserver TO idp_app_user;
  GRANT USAGE ON SCHEMA public TO idp_app_user;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_app_user;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_app_user;

  --- for future table
  ALTER DEFAULT PRIVILEGES
      FOR ROLE idpserver
      IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_app_user;

  --- for future sequence
  ALTER DEFAULT PRIVILEGES
      FOR ROLE idpserver
      IN SCHEMA public
      GRANT USAGE, SELECT ON SEQUENCES TO idp_app_user;
EOSQL
