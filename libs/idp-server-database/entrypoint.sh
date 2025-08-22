#!/bin/sh
set -eu

# default action is "migrate" if nothing is provided
ACTION="${1:-migrate}"

# map the given action to the corresponding Gradle Flyway task
case "$ACTION" in
  migrate|info|repair|clean)
    TASK="flyway$(echo "$ACTION" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
    ;;
  *)
    echo "Unknown action: $ACTION" >&2
    exit 2
    ;;
esac

# run Gradle with no daemon; cache directory is set by GRADLE_USER_HOME
echo "===> Running $TASK (DB_TYPE=${DB_TYPE:-postgresql})"
exec ./gradlew --no-daemon "$TASK"
