#!/bin/sh

echo "Starting idp-server..."

# Clean up tmp directory before starting
rm -rf /tmp/tomcat.* 2>/dev/null || true

# Import custom CA certificate if provided (for local development with mkcert)
JAVA_OPTS=""
if [ -f "/app/certs/rootCA.pem" ]; then
    echo "Importing custom CA certificate..."
    TRUSTSTORE_PATH="/tmp/cacerts"
    JAVA_CACERTS="${JAVA_HOME}/lib/security/cacerts"

    # Copy default truststore to writable location
    cp "$JAVA_CACERTS" "$TRUSTSTORE_PATH"

    # Import the custom CA
    keytool -importcert -alias mkcert-local \
        -keystore "$TRUSTSTORE_PATH" \
        -file /app/certs/rootCA.pem \
        -storepass changeit \
        -noprompt 2>/dev/null

    if [ $? -eq 0 ]; then
        echo "Custom CA imported successfully"
        JAVA_OPTS="-Djavax.net.ssl.trustStore=$TRUSTSTORE_PATH -Djavax.net.ssl.trustStorePassword=changeit"
    else
        echo "Warning: Failed to import custom CA"
    fi
fi

exec java $JAVA_OPTS -jar /app/idp-server.jar
