# Secrets Management

## 🔒 Overview

This directory contains sensitive information that **MUST NOT** be committed to Git.

## 📁 Structure

```
config/secrets/
├── local/               # Local development (unencrypted)
│   ├── jwks.json       # JWKS keypair
│   ├── client-secrets.json
│   └── encryption-keys.json
├── development/         # Development env (encrypted recommended)
│   ├── jwks.json.enc
│   ├── client-secrets.json.enc
│   └── encryption-keys.json.enc
└── production/          # Production env (encrypted, must use vault)
    ├── jwks.json.enc
    ├── client-secrets.json.enc
    └── encryption-keys.json.enc
```

## 🚀 Initial Setup

### For Local Development

1. Run the migration script to extract secrets from existing templates:
```bash
./config/scripts/migrate-secrets.sh
```

This will create:
- `config/secrets/local/jwks.json`
- `config/secrets/local/client-secrets.json`
- `config/secrets/local/encryption-keys.json`

2. (Optional) Generate your own keys for local development:
```bash
# Generate JWKS keypair
./config/scripts/generate-jwks.sh > config/secrets/local/jwks.json

# Generate client secrets
./config/scripts/generate-secrets.sh > config/secrets/local/client-secrets.json
```

### For Production

**Never store unencrypted secrets for production!**

Use one of:
- **SOPS** (recommended):
  ```bash
  sops -e config/secrets/production/jwks.json > config/secrets/production/jwks.json.enc
  ```
- **git-crypt**:
  ```bash
  git-crypt init && git-crypt add-gpg-user <key-id>
  ```
- **Vault**: Store in HashiCorp Vault and reference by path

## 📝 File Format

### jwks.json
```json
{
  "keys": [
    {
      "kty": "EC",
      "d": "...",  // Private key
      "use": "sig",
      "crv": "P-256",
      "kid": "access_token",
      "x": "...",
      "y": "...",
      "alg": "ES256"
    }
  ]
}
```

### client-secrets.json
```json
{
  "admin_client": {
    "client_id": "uuid",
    "client_id_alias": "clientSecretPost",
    "client_secret": "secret"
  },
  "org_client": {
    "client_id": "uuid",
    "client_secret": "secret"
  }
}
```

### encryption-keys.json
```json
{
  "api_key": "uuid",
  "api_secret": "base64-encoded-secret",
  "encryption_key": "base64-encoded-key"
}
```

## ⚠️ Security Checklist

- [ ] `config/secrets/` is in `.gitignore` (root level)
- [ ] No `.env` files contain actual secrets
- [ ] Production secrets are encrypted
- [ ] Encryption keys are stored separately (e.g., in vault)
- [ ] Team members use their own local secrets
- [ ] No secrets in git commit history

## 🔄 Usage in Configuration

Secrets are automatically loaded by the configuration generator:

```bash
# Generate configuration with secrets
./config/scripts/generate-config.sh -e local

# The script will read from config/secrets/local/ and inject into templates
```

## 🆘 Troubleshooting

### "Secrets not found" error

```bash
# Check if secrets exist
ls -la config/secrets/local/

# If missing, run migration
./config/scripts/migrate-secrets.sh
```

### "Permission denied" error

```bash
# Ensure proper permissions
chmod 600 config/secrets/local/*.json
```

### Need to rotate secrets

```bash
# 1. Generate new secrets
./config/scripts/generate-jwks.sh > config/secrets/local/jwks.json.new

# 2. Test with new secrets
./config/scripts/generate-config.sh -e local

# 3. If successful, replace old secrets
mv config/secrets/local/jwks.json.new config/secrets/local/jwks.json
```

## 📚 Related Documentation

- [Configuration Management Guide](../documentation/docs/content_05_how-to/authorization-server-configuration.md)
- [Organization Initialization Guide](../documentation/docs/content_05_how-to/organization-initialization.md)
