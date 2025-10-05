# idp-server

[![GitHub Stars](https://img.shields.io/github/stars/hirokazu-kobayashi-koba-hiro/idp-server?style=social)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server)
[![GitHub Issues](https://img.shields.io/github/issues/hirokazu-kobayashi-koba-hiro/idp-server)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues)
[![GitHub License](https://img.shields.io/github/license/hirokazu-kobayashi-koba-hiro/idp-server)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/blob/main/LICENSE)

## What is `idp-server`?

`idp-server` is not just an identity provider —  
**it is a full-featured, extensible identity framework** designed for modern, multi-tenant SaaS environments.  
It supports OAuth 2.0, OpenID Connect, CIBA, FAPI, and verifiable credentials.

Designed with a clear separation of concerns, `idp-server` is built as a **framework-agnostic core** —  
capable of integrating into any application stack while keeping protocol logic, authentication flows, and session
control modular and customizable.

### Key Features

- Framework-independent core logic
- Fully pluggable authentication and authorization flows
- Dynamic, per-tenant configuration
- Built-in support for session control, hooks, and federation
- Production-ready support for multi-tenancy and database isolation

Yes — **idp-server is a framework**.  
It empowers developers to build enterprise-grade identity platforms with flexibility, structure, and control.

## Getting Started

This section helps you quickly set up and launch the IdP server in your local environment.

Whether you're testing OpenID Connect flows etc, or benchmarking performance —
you’ll be up and running in just a few steps.

### requirements

- Java 21 or later
- Docker & Docker Compose
- Node.js 18 later（for e2e）

### Quick Start Guide

Get idp-server running in just two commands:

#### Simple Setup (Recommended)

```shell
# Build all services
docker compose build

# Start all services (includes automatic database initialization)
docker compose up -d
```

That's it! The setup includes:
- ✅ Automatic image building
- ✅ PostgreSQL Primary/Replica replication setup  
- ✅ Database migration execution
- ✅ All services with proper health checks
- ✅ Default environment variables (no .env.local required)

#### Verify Setup

Check service health:

```shell
curl -v http://localhost:8080/actuator/health
```

Verify PostgreSQL replication:

```shell
./scripts/verify-replication.sh
```

#### Advanced Setup (Optional)

For custom environment variables, create `.env.local`:

```shell
./init.sh  # Generates .env.local with API keys
docker compose --env-file .env.local up -d
```

#### Step-by-Step Setup (Debugging)

If you need to troubleshoot, start services individually:

```shell
# 1. Start databases first
docker compose up -d postgres-primary postgres-replica mysql redis

# 2. Run database migration
docker compose up flyway-migrator

# 3. Start application services
docker compose up -d idp-server-1 idp-server-2 nginx
```

---

## Database Configuration

### PostgreSQL Primary/Replica Setup

The Docker Compose configuration includes PostgreSQL with streaming replication for development and testing of read/write separation.

#### Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐
│  postgres-primary │    │ postgres-replica │
│   (Write/Read)    │───▶│   (Read-only)   │
│   Port: 5432      │    │   Port: 5433    │
└─────────────────┘    └─────────────────┘
      │                        │
      └────────────────────────┘
           Streaming WAL
```

#### Service Details

| Service | Role | Port | Purpose | Connection |
|---------|------|------|---------|------------|
| `postgres-primary` | Primary | 5432 | Write + Read operations | `postgresql://idpserver:idpserver@localhost:5432/idpserver` |
| `postgres-replica` | Standby | 5433 | Read-only operations | `postgresql://idpserver:idpserver@localhost:5433/idpserver` |

#### Application Configuration

The idp-server automatically uses the appropriate database:

```yaml
# docker-compose.yaml configuration
DB_WRITER_URL: jdbc:postgresql://postgres-primary:5432/idpserver
DB_READER_URL: jdbc:postgresql://postgres-replica:5432/idpserver
```

**Note:** While the external port is 5433, containers communicate internally on port 5432.

#### Troubleshooting

**Check replication status:**

```shell
# Verify replication is working
./scripts/verify-replication.sh

# Check primary status
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "SELECT pg_is_in_recovery();"

# Check replica status
docker compose exec postgres-replica psql -U idpserver -d idpserver -c "SELECT pg_is_in_recovery();"

# View replication lag
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "SELECT slot_name, active, restart_lsn FROM pg_replication_slots;"
```

**Reset replication (if needed):**

```shell
# Stop services and remove volumes
docker compose down -v

# Restart services (replica will re-sync from primary)
docker compose up -d postgres-primary postgres-replica
```

#### ⚠️ Important Notes

- **Development Only**: Not suitable for production use
- **No Automatic Failover**: Manual intervention required if primary fails  
- **Replication Delay**: Small delay expected between writes and reads
- **Data Persistence**: Uses Docker volumes - data survives container restarts

---

## Application Configuration

### Health Check

```shell
curl -v http://localhost:8080/actuator/health
```

### Setup Configuration

```shell
./setup.sh
```

* admin-tenant

```shell
./config/scripts/test-data.sh
 ```

* test-tenant

```shell
./config/scripts/test-tenant-data.sh -t 1e68932e-ed4a-43e7-b412-460665e42df3
 ```

### e2e

Once the setup configuration is complete, you can immediately run E2E tests to verify that your IdP server is functioning correctly.

#### Test Structure
The test suite is organized into three categories:

* 📘 scenario/: Realistic user and system behavior — e.g., user registration, SSO login, CIBA flow, MFA registration.
* 📕 spec/: Specification compliance tests based on OpenID Connect, FAPI, JARM, and Verifiable Credentials.
* 🐒 monkey/: Fault injection and edge-case validation — intentionally invalid sequences, parameters, or protocol violations.

#### run

```shell
cd e2e
npm install
npm test
```

### performance-test

* Load testing powered by k6
* Covers CIBA, Token, and Multi-tenant login scenarios
* Supports horizontal scalability testing
* ➡️ See [performance-test/README.md](./performance-test/README.md) for full usage

## documentation

### English

```shell
cd documentation
npm install
npx run start
```

### Japanese

```shell
cd documentation
npm install
npx docusaurus write-translations --locale ja
npm run start -- --locale ja
```


## 🗂 System Architecture (Container Level)

```mermaid
graph TD
subgraph UserSide
browser[🧑‍💻 Web Browser]
end

subgraph Frontend
frontend[🌐 React / Next.js]
end

subgraph RpBackend
rp-backend[🎯 RP Backend]
end

subgraph Backend
backend[🔧 Spring Boot API]
idp-engine[🔥🚀 IdP Engine]
authz-provider[AuthZProvider]
hook[📡 Hook Executors]
authentication[🛡️ Authentication Interactors]
credential-issuers[🏷️ Credential Issuers]
federatable-oidc-providers[🌐 Federatable OIDC Providers]
ssf[📬 SSF Notifier]
datasource[🗄 DataSource]
end

subgraph External
slack[🔔 Slack Webhook]
webhook[🔄 Generic Webhook Endpoint]
oidc[🌍 External OIDC Provider]
credential-issuer[🌟 Public Credential Issuer]
end

browser --> frontend
frontend --> backend
rp-backend --> backend
backend --> idp-engine

idp-engine --> datasource
idp-engine --> authz-provider
idp-engine --> hook
idp-engine --> authentication
idp-engine --> credential-issuers
idp-engine --> federatable-oidc-providers
idp-engine --> ssf

authz-provider --> idp-server
authz-provider --> external

subgraph AuthZProvider
idp-server[Idp-Server]
external[External Authorization Server]
end

hook --> slack
hook --> webhook
credential-issuers --> credential-issuer
federatable-oidc-providers --> oidc
ssf --> rp-backend

subgraph Authentication
password[Password Interactor]
webauthn[🔐 Webauthn Interactor]
email[📧 Email Interactor]
legacy[LegacyID Interactor]
end

authentication --> password
authentication --> webauthn
authentication --> email
authentication --> legacy

subgraph DataSource
postgresql[(🗄️ PostgreSQL)]
mysql[(🗄️ MySQL)]
end

datasource --> postgresql
datasource --> mysql

```

### 🗂 System Architecture (Container Level)

This diagram illustrates the container-level architecture of the idp-server, a modular and extensible Identity Provider
built with Java and React.

* The Frontend is implemented with React / Next.js and handles user interactions for login, consent.
* The Backend API is built with Spring Boot and exposes REST endpoints for OIDC/OAuth flows, clientAttributes management, tenant
  operations, and hook configuration.
* The IdP Engine encapsulates the core logic for authentication, authorization, grant handling, and token issuance.
* Authentication Interactors are pluggable components that support various methods such as Password, Email OTP,
  WebAuthn (Passkey), and Legacy system login.
* SecurityEventHook Executors trigger external actions such as Slack notifications and generic Webhooks based on
  security events and authentication lifecycle.
* Federatable OIDC Providers enable enterprise federation with external identity providers using OIDC or SAML protocols.
* SSF Notifier streams security events (Shared Signal Framework) to relying parties for audit or incident response.
* PostgreSQL serves as the primary database, with support for MySQL.
* The architecture supports multi-tenant deployments and allows per-tenant databaseType and configuration control.
* Redis or Memory Cache is optionally used for caching ServerConfig, ClientConfig, and Grant data to improve performance
  and scalability.

This architecture is designed to deliver high security, customization flexibility, and developer-friendly extensibility,
making it suitable for real-world enterprise deployments and Verifiable Credential issuance.

---


## License

Apache License, Version 2.0

## Contributing

Please read our [Contributing Guidelines](./CONTRIBUTING.md) before submitting changes.

## Security Policy

If you discover a security vulnerability, please refer to our [Security Policy](./SECURITY.md)  
or report it privately via [GitHub Security Advisories](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/security/advisories).

## Contributing

Contributions are welcome – whether it's code, feedback, or questions!

Before participating, please check out our [Code of Conduct](./CODE_OF_CONDUCT.md) to help keep our community open, safe, and respectful.
