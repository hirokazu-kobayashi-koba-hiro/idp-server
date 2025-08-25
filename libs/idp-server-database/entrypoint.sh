#!/bin/sh
set -eu

# default migrate
if [ "$#" -eq 0 ]; then
  set -- migrate
fi

for ACTION in "$@"; do
  case "$ACTION" in
    migrate|info|repair|clean)
      TASK="flyway$(echo "$ACTION" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
      ;;
    *)
      echo "Unknown action: $ACTION" >&2
      exit 2
      ;;
  esac

  echo "===> Running $TASK (DB_TYPE=${DB_TYPE:-postgresql})"
  ./gradlew --no-daemon "$TASK"
done