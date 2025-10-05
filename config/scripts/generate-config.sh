#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$CONFIG_DIR")"

# Default values
ENV="local"
TEMPLATE_FILE=""
OUTPUT_FILE=""

# Usage
usage() {
  cat <<EOF
Usage: $0 [OPTIONS]

Generate configuration files from templates with secrets injection.

OPTIONS:
  -e, --env ENV          Environment (local, development, production). Default: local
  -t, --template FILE    Template file path (relative to config/templates/)
  -o, --output FILE      Output file path (relative to config/generated/)
  -h, --help             Show this help message

EXAMPLES:
  # Generate tenant configuration for local environment
  $0 -e local -t tenant-template.json -o local/tenant.json

  # Use default environment (local)
  $0 -t tenant-template.json -o local/tenant.json

EOF
  exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -e|--env)
      ENV="$2"
      shift 2
      ;;
    -t|--template)
      TEMPLATE_FILE="$2"
      shift 2
      ;;
    -o|--output)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown option: $1"
      usage
      ;;
  esac
done

# Validate required arguments
if [[ -z "$TEMPLATE_FILE" ]]; then
  echo "Error: Template file is required (-t option)"
  usage
fi

if [[ -z "$OUTPUT_FILE" ]]; then
  echo "Error: Output file is required (-o option)"
  usage
fi

# Paths
TEMPLATE_PATH="$CONFIG_DIR/templates/$TEMPLATE_FILE"
OUTPUT_PATH="$CONFIG_DIR/generated/$OUTPUT_FILE"
SECRETS_DIR="$CONFIG_DIR/secrets/$ENV"
JWKS_FILE="$SECRETS_DIR/jwks.json"
CLIENT_SECRETS_FILE="$SECRETS_DIR/client-secrets.json"
ENCRYPTION_KEYS_FILE="$SECRETS_DIR/encryption-keys.json"

# Validation
if [[ ! -f "$TEMPLATE_PATH" ]]; then
  echo "❌ Error: Template file not found: $TEMPLATE_PATH"
  exit 1
fi

if [[ ! -f "$JWKS_FILE" ]]; then
  echo "❌ Error: JWKS file not found: $JWKS_FILE"
  echo "💡 Run: ./config/scripts/migrate-secrets.sh"
  exit 1
fi

# Load .env file if exists
ENV_FILE="$PROJECT_ROOT/.env"
if [[ -f "$ENV_FILE" ]]; then
  echo "📝 Loading environment variables from .env"
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "⚠️  Warning: .env file not found. Using defaults from .env.template"
fi

# Export environment-specific variables
export ENV="$ENV"
export SECRETS_DIR="$SECRETS_DIR"
export JWKS_FILE="$JWKS_FILE"
export CLIENT_SECRETS_FILE="$CLIENT_SECRETS_FILE"
export ENCRYPTION_KEYS_FILE="$ENCRYPTION_KEYS_FILE"

# Load secrets
echo "🔐 Loading secrets from $SECRETS_DIR"

# Read JWKS and escape for JSON
if command -v jq >/dev/null 2>&1; then
  # Use jq to properly escape JSON for embedding in JSON template
  JWKS_CONTENT=$(jq -c '.' "$JWKS_FILE" | python3 -c "import sys, json; escaped = json.dumps(sys.stdin.read().strip()); print(escaped[1:-1])")
else
  echo "❌ Error: jq is required. Please install jq."
  exit 1
fi

export JWKS_CONTENT

# Read client secrets if file exists
if [[ -f "$CLIENT_SECRETS_FILE" ]]; then
  ADMIN_CLIENT_SECRET=$(jq -r '.admin_client.client_secret' "$CLIENT_SECRETS_FILE" 2>/dev/null || echo "")
  if [[ -n "$ADMIN_CLIENT_SECRET" ]]; then
    export ADMIN_CLIENT_SECRET
  fi
fi

# Read encryption keys if file exists
if [[ -f "$ENCRYPTION_KEYS_FILE" ]]; then
  IDP_SERVER_API_KEY=$(jq -r '.api_key' "$ENCRYPTION_KEYS_FILE" 2>/dev/null || echo "")
  IDP_SERVER_API_SECRET=$(jq -r '.api_secret' "$ENCRYPTION_KEYS_FILE" 2>/dev/null || echo "")
  ENCRYPTION_KEY=$(jq -r '.encryption_key' "$ENCRYPTION_KEYS_FILE" 2>/dev/null || echo "")

  [[ -n "$IDP_SERVER_API_KEY" ]] && export IDP_SERVER_API_KEY
  [[ -n "$IDP_SERVER_API_SECRET" ]] && export IDP_SERVER_API_SECRET
  [[ -n "$ENCRYPTION_KEY" ]] && export ENCRYPTION_KEY
fi

# Create output directory
OUTPUT_DIR=$(dirname "$OUTPUT_PATH")
mkdir -p "$OUTPUT_DIR"

# Generate configuration by substituting environment variables
echo "🔨 Generating configuration: $OUTPUT_FILE"
envsubst < "$TEMPLATE_PATH" > "$OUTPUT_PATH"

# Validate generated JSON
if command -v jq >/dev/null 2>&1; then
  if jq empty "$OUTPUT_PATH" 2>/dev/null; then
    echo "✅ Configuration generated successfully: $OUTPUT_PATH"
    echo ""
    echo "📊 Summary:"
    echo "  Environment: $ENV"
    echo "  Template: $TEMPLATE_FILE"
    echo "  Output: $OUTPUT_FILE"
    echo "  Size: $(wc -c < "$OUTPUT_PATH") bytes"
  else
    echo "❌ Error: Generated file is not valid JSON"
    exit 1
  fi
else
  echo "✅ Configuration generated: $OUTPUT_PATH"
  echo "⚠️  Warning: jq not found. JSON validation skipped."
fi
