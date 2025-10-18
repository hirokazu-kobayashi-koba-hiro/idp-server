#!/bin/sh

echo "Starting idp-server..."

# Clean up tmp directory before starting
rm -rf /tmp/tomcat.* 2>/dev/null || true

exec java -jar /app/idp-server.jar
