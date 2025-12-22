#!/bin/sh
set -eu

# Database type (postgresql or mysql)
DB_TYPE=${DB_TYPE:-postgresql}

# Build Flyway URL based on DB_TYPE if not explicitly set
if [ -z "${DB_URL:-}" ]; then
  if [ "$DB_TYPE" = "postgresql" ]; then
    DB_URL="jdbc:postgresql://localhost:5432/idpserver"
  elif [ "$DB_TYPE" = "mysql" ]; then
    DB_URL="jdbc:mysql://localhost:3306/idpserver"
  else
    echo "Unknown DB_TYPE: $DB_TYPE" >&2
    exit 1
  fi
fi

# Default credentials
DB_USER_NAME=${DB_USER_NAME:-idpserver}
DB_PASSWORD=${DB_PASSWORD:-idpserver}

# SQL location based on DB_TYPE
LOCATIONS="filesystem:/flyway/sql/${DB_TYPE}"

# Export Flyway environment variables (avoids password in command line)
export FLYWAY_URL="${DB_URL}"
export FLYWAY_USER="${DB_USER_NAME}"
export FLYWAY_PASSWORD="${DB_PASSWORD}"
export FLYWAY_LOCATIONS="${LOCATIONS}"
export FLYWAY_ENCODING="UTF-8"
export FLYWAY_CLEAN_DISABLED="false"

# Default action is migrate
if [ "$#" -eq 0 ]; then
  set -- migrate
fi

for ACTION in "$@"; do
  case "$ACTION" in
    migrate|info|repair|clean|validate|baseline)
      echo "===> Running flyway $ACTION (DB_TYPE=${DB_TYPE})"
      flyway "$ACTION"
      ;;
    *)
      echo "Unknown action: $ACTION" >&2
      echo "Available actions: migrate, info, repair, clean, validate, baseline" >&2
      exit 2
      ;;
  esac
done
