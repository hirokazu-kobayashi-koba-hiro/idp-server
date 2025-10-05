# Configuration Templates

This directory contains reusable configuration templates for idp-server.

## 🔄 Phase 2: Template-Based Configuration with Secrets Separation

Templates use placeholders (e.g., `${TENANT_ID}`, `${JWKS_CONTENT}`) that are replaced with actual values during configuration generation.

### Directory Structure

```
config/
├── templates/          # Reusable templates (this directory)
│   ├── tenant-template.json
│   ├── user-template.json
│   └── client-template.json
├── secrets/            # Sensitive data (gitignored)
│   ├── local/
│   ├── development/
│   └── production/
├── scripts/            # Configuration management scripts
│   ├── migrate-secrets.sh
│   └── generate-config.sh
└── generated/          # Generated configs (gitignored)
    ├── local/
    └── development/
```

## 🚀 Quick Start

### 1. Extract Secrets (First Time Only)

```bash
# Extract secrets from old templates to config/secrets/local/
./config/scripts/migrate-secrets.sh
```

This creates:
- `config/secrets/local/jwks.json`
- `config/secrets/local/client-secrets.json`
- `config/secrets/local/encryption-keys.json`

### 2. Generate Configuration

```bash
# Generate tenant configuration for local environment
./config/scripts/generate-config.sh \
  -e local \
  -t tenant-template.json \
  -o local/tenant.json

# Output: config/generated/local/tenant.json
```

### 3. Use Generated Configuration

```bash
# Upload to server
curl -X POST http://localhost:8080/v1/management/tenants \
  -H "Content-Type: application/json" \
  -d @config/generated/local/tenant.json
```

## 📝 Available Templates

### tenant-template.json

Complete tenant + authorization server configuration.

**Placeholders:**
- `${TENANT_ID}` - Tenant UUID
- `${BASE_URL}` - Base URL (e.g., http://localhost:8080)
- `${JWKS_CONTENT}` - JWKS keypair (loaded from secrets/)

**Usage:**
```bash
./config/scripts/generate-config.sh \
  -e local \
  -t tenant-template.json \
  -o local/my-tenant.json
```

### user-template.json

User configuration template.

**Placeholders:**
- `${USER_SUB}` - User subject UUID
- `${USER_EMAIL}` - User email
- `${USER_PASSWORD}` - User password

### client-template.json

OAuth client configuration template.

**Placeholders:**
- `${CLIENT_ID}` - Client UUID
- `${CLIENT_SECRET}` - Client secret
- `${REDIRECT_URIS}` - Redirect URIs

## 🔧 Advanced Usage

### Custom Environment Variables

Create a `.env` file in the project root:

```bash
# .env
ENV=local
BASE_URL=http://localhost:8080
TENANT_ID=$(uuidgen | tr 'A-Z' 'a-z')
```

Variables are automatically loaded during configuration generation.

### Generate Multiple Configurations

```bash
# Generate tenant
./config/scripts/generate-config.sh \
  -e local \
  -t tenant-template.json \
  -o local/tenant.json

# Generate user
./config/scripts/generate-config.sh \
  -e local \
  -t user-template.json \
  -o local/user.json

# Generate client
./config/scripts/generate-config.sh \
  -e local \
  -t clientSecretPost-template.json \
  -o local/client.json
```

### Production Deployment

```bash
# Extract production secrets (encrypted)
# (Use SOPS, git-crypt, or vault)

# Generate production configuration
./config/scripts/generate-config.sh \
  -e production \
  -t tenant-template.json \
  -o production/tenant.json
```

## 🛠️ Helper Scripts

### Generate UUIDs

```bash
# Single UUID
uuidgen | tr 'A-Z' 'a-z'

# Multiple UUIDs
for i in $(seq 1 4); do uuidgen | tr 'A-Z' 'a-z'; done
```

### Legacy Scripts (Moved to config/scripts/)

**Note:** Helper scripts have been moved to `config/scripts/` directory.

```bash
# Available scripts in config/scripts/:
./config/scripts/generate-config.sh    # Generate configs from templates
./config/scripts/migrate-secrets.sh    # Extract secrets from old configs
./config/scripts/get-access-token.sh   # Get admin access token
./config/scripts/upsert-tenant.sh      # Create/update tenant
./config/scripts/upsert-user.sh        # Create/update user
./config/scripts/upsert-client.sh      # Create/update client
./config/scripts/config-upsert.sh      # (Deprecated) Legacy config script
```

## 🔐 Security Best Practices

### ✅ Do

- Store secrets in `config/secrets/` (gitignored)
- Use `generate-config.sh` to inject secrets into templates
- Encrypt production secrets (SOPS, git-crypt, Vault)
- Generate unique secrets for each environment

### ❌ Don't

- Commit `config/secrets/` to git
- Hardcode secrets in templates
- Share secrets via email or chat
- Use production secrets in local development

## 📚 Related Documentation

- [Secrets Management](../secrets/README.md)
- [Organization Initialization How-to](../../documentation/docs/content_05_how-to/organization-initialization.md)
- [Authorization Server Configuration How-to](../../documentation/docs/content_05_how-to/authorization-server-configuration.md)
