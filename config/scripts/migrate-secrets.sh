#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$CONFIG_DIR")"

echo "🔐 Phase 1: Migrating secrets from templates to config/secrets/"
echo ""

# Create secrets directory structure
mkdir -p "$CONFIG_DIR/secrets/local"
mkdir -p "$CONFIG_DIR/secrets/development"
mkdir -p "$CONFIG_DIR/secrets/production"

# =========================
# 1. Extract JWKS from tenant-template.json
# =========================
echo "📦 [1/3] Extracting JWKS from tenant-template.json..."

TENANT_TEMPLATE="$CONFIG_DIR/templates/tenant-template.json"

if [[ ! -f "$TENANT_TEMPLATE" ]]; then
  echo "❌ Error: $TENANT_TEMPLATE not found"
  exit 1
fi

# Extract and validate JWKS using jq
if command -v jq >/dev/null 2>&1; then
  # Extract jwks field and unescape JSON string
  jq -r '.authorization_server.jwks' "$TENANT_TEMPLATE" | \
    python3 -c "import sys, json; jwks_str = sys.stdin.read(); jwks = json.loads(jwks_str); print(json.dumps(jwks, indent=2))" \
    > "$CONFIG_DIR/secrets/local/jwks.json"

  echo "✅ JWKS extracted to config/secrets/local/jwks.json"
else
  echo "❌ Error: jq is required for JWKS extraction. Please install jq."
  exit 1
fi

# =========================
# 2. Extract client secrets from .env.template
# =========================
echo ""
echo "📦 [2/3] Extracting client secrets from .env.template..."

ENV_TEMPLATE="$PROJECT_ROOT/.env.template"

if [[ ! -f "$ENV_TEMPLATE" ]]; then
  echo "❌ Error: $ENV_TEMPLATE not found"
  exit 1
fi

ADMIN_CLIENT_ID=$(grep "^ADMIN_CLIENT_ID=" "$ENV_TEMPLATE" | cut -d'=' -f2)
ADMIN_CLIENT_ID_ALIAS=$(grep "^ADMIN_CLIENT_ID_ALIAS=" "$ENV_TEMPLATE" | cut -d'=' -f2)
ADMIN_CLIENT_SECRET=$(grep "^ADMIN_CLIENT_SECRET=" "$ENV_TEMPLATE" | cut -d'=' -f2)

# Create client-secrets.json
cat > "$CONFIG_DIR/secrets/local/client-secrets.json" <<EOF
{
  "admin_client": {
    "client_id": "$ADMIN_CLIENT_ID",
    "client_id_alias": "$ADMIN_CLIENT_ID_ALIAS",
    "client_secret": "$ADMIN_CLIENT_SECRET"
  }
}
EOF

if command -v jq >/dev/null 2>&1; then
  jq '.' "$CONFIG_DIR/secrets/local/client-secrets.json" > "$CONFIG_DIR/secrets/local/client-secrets.json.tmp"
  mv "$CONFIG_DIR/secrets/local/client-secrets.json.tmp" "$CONFIG_DIR/secrets/local/client-secrets.json"
fi

echo "✅ Client secrets extracted to config/secrets/local/client-secrets.json"

# =========================
# 3. Extract encryption keys from .env.template
# =========================
echo ""
echo "📦 [3/3] Extracting encryption keys from .env.template..."

API_KEY=$(grep "^IDP_SERVER_API_KEY=" "$ENV_TEMPLATE" | cut -d'=' -f2)
API_SECRET=$(grep "^IDP_SERVER_API_SECRET=" "$ENV_TEMPLATE" | cut -d'=' -f2)
ENCRYPTION_KEY=$(grep "^ENCRYPTION_KEY=" "$ENV_TEMPLATE" | cut -d'=' -f2)

# Create encryption-keys.json
cat > "$CONFIG_DIR/secrets/local/encryption-keys.json" <<EOF
{
  "api_key": "$API_KEY",
  "api_secret": "$API_SECRET",
  "encryption_key": "$ENCRYPTION_KEY"
}
EOF

if command -v jq >/dev/null 2>&1; then
  jq '.' "$CONFIG_DIR/secrets/local/encryption-keys.json" > "$CONFIG_DIR/secrets/local/encryption-keys.json.tmp"
  mv "$CONFIG_DIR/secrets/local/encryption-keys.json.tmp" "$CONFIG_DIR/secrets/local/encryption-keys.json"
fi

echo "✅ Encryption keys extracted to config/secrets/local/encryption-keys.json"

# =========================
# 4. Set appropriate permissions
# =========================
echo ""
echo "🔒 Setting file permissions (600 for secrets)..."
chmod 600 "$CONFIG_DIR/secrets/local"/*.json
echo "✅ Permissions set"

# =========================
# 5. Summary
# =========================
echo ""
echo "🎉 Migration complete!"
echo ""
echo "Generated files:"
echo "  - config/secrets/local/jwks.json ($(stat -f%z "$CONFIG_DIR/secrets/local/jwks.json" 2>/dev/null || stat -c%s "$CONFIG_DIR/secrets/local/jwks.json") bytes)"
echo "  - config/secrets/local/client-secrets.json ($(stat -f%z "$CONFIG_DIR/secrets/local/client-secrets.json" 2>/dev/null || stat -c%s "$CONFIG_DIR/secrets/local/client-secrets.json") bytes)"
echo "  - config/secrets/local/encryption-keys.json ($(stat -f%z "$CONFIG_DIR/secrets/local/encryption-keys.json" 2>/dev/null || stat -c%s "$CONFIG_DIR/secrets/local/encryption-keys.json") bytes)"
echo ""
echo "Next steps:"
echo "  1. Review generated files in config/secrets/local/"
echo "  2. Update config/templates/tenant-template.json to reference secrets"
echo "  3. Update .env.template to reference secret files (not the secrets themselves)"
echo "  4. Add 'config/secrets/' to root .gitignore"
echo ""
echo "⚠️  IMPORTANT: Never commit config/secrets/local/*.json to git!"
