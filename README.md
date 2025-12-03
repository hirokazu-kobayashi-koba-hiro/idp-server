# idp-server

[![GitHub Stars](https://img.shields.io/github/stars/hirokazu-kobayashi-koba-hiro/idp-server?style=social)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server)
[![GitHub Issues](https://img.shields.io/github/issues/hirokazu-kobayashi-koba-hiro/idp-server)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues)
[![GitHub License](https://img.shields.io/github/license/hirokazu-kobayashi-koba-hiro/idp-server)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/blob/main/LICENSE)

## What is idp-server?

**idp-server** is an enterprise-grade identity framework for multi-tenant SaaS environments.

It provides a framework-agnostic core with pluggable authentication flows, dynamic per-tenant configuration, and built-in support for session control, hooks, and federation.

### Supported Standards

- OAuth 2.0 / OpenID Connect
- CIBA (Client Initiated Backchannel Authentication)
- FAPI (Financial-grade API)
- Verifiable Credentials

## Quick Start

### Requirements

- Docker & Docker Compose

### Setup

```bash
# Generate environment variables
./init-generate-env.sh

# Setup admin tenant configuration
./init-admin-tenant-config.sh

# Build and start all services
docker compose build
docker compose up -d
```

For MySQL:
```bash
./init-generate-env.sh mysql
docker compose -f docker-compose-mysql.yaml build
docker compose -f docker-compose-mysql.yaml up -d
```

### Verify

```bash
curl http://localhost:8080/actuator/health
```

## Features

- **Multi-tenancy** - Per-tenant configuration and database isolation
- **Pluggable Authentication** - Password, WebAuthn, Email OTP, Legacy ID
- **Federation** - External OIDC/SAML provider integration
- **Security Hooks** - Slack, Webhook notifications on security events
- **SSF (Shared Signals Framework)** - Security event streaming to relying parties
- **Credential Issuance** - Verifiable Credentials support

## Documentation

📚 **https://hirokazu-kobayashi-koba-hiro.github.io/idp-server/**

For local development:

```bash
cd documentation
npm install
npm run start
```

Japanese version:
```bash
npm run start -- --locale ja
```

## Architecture

```
┌──────────┐    ┌──────────────┐    ┌─────────────────┐
│  Client  │───▶│  Spring Boot │───▶│   IdP Engine    │
└──────────┘    └──────────────┘    └────────┬────────┘
                                             │
                 ┌───────────────────────────┼───────────────────────────┐
                 │                           │                           │
                 ▼                           ▼                           ▼
        ┌────────────────┐         ┌────────────────┐         ┌────────────────┐
        │ Authentication │         │     Hooks      │         │   Federation   │
        │  Interactors   │         │   Executors    │         │   Providers    │
        └────────────────┘         └────────────────┘         └────────────────┘
                                             │
                                             ▼
                                   ┌────────────────┐
                                   │ PostgreSQL/MySQL│
                                   └────────────────┘
```

## Development

### E2E Tests

```bash
# Setup test data
./config/scripts/e2e-test-data.sh
./config/scripts/e2e-test-tenant-data.sh -t 1e68932e-ed4a-43e7-b412-460665e42df3

# Run tests
cd e2e
npm install
npm test
```

Test categories:
- `scenario/` - User flows (registration, SSO, CIBA, MFA)
- `spec/` - Protocol compliance (OIDC, FAPI, JARM, VC)
- `monkey/` - Edge cases and fault injection

### Performance Tests

Load testing with k6. See [performance-test/README.md](./performance-test/README.md).

### Database Setup (Advanced)

PostgreSQL Primary/Replica configuration for read/write separation:

```bash
# Verify replication
./scripts/verify-replication.sh

# Start databases only
docker compose up -d postgres-primary postgres-replica redis
```

See [Database Configuration](./docs/database.md) for details.

## License

Apache License, Version 2.0

## Contributing

Contributions welcome! Please read our [Contributing Guidelines](./CONTRIBUTING.md) and [Code of Conduct](./CODE_OF_CONDUCT.md).

## Security

Report vulnerabilities via [GitHub Security Advisories](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/security/advisories) or see [Security Policy](./SECURITY.md).
