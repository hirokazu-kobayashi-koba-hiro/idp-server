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
* The Backend API is built with Spring Boot and exposes REST endpoints for OIDC/OAuth flows, client management, tenant
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

## 🔥 Technical Highlights

### ⚙️ Modular & Composable Architecture

Each core capability—Authorization, Authentication, MFA, Consent, VC Issuance, Hooks—is implemented as independent,
composable modules.  
You can disable or replace modules without breaking the entire system.

> 🧩 Easy to maintain, easy to embed.

---

### 🔌 Plug-and-Play

Built-in extensibility via interfaces

1. `AuthenticationInteractor`
2. `SecurityEventHookExecutor`
3. `OAuthProtocol` etc

Swap out mechanisms with minimal code.

## Getting Started

### preparation

* set up generate api-key and api-secret

```shell
./init.sh
```

※ fix your configuration

```shell
export IDP_SERVER_DOMAIN=http://localhost:8080/
export IDP_SERVER_API_KEY=xxx
export IDP_SERVER_API_SECRET=xxx
export ENCRYPTION_KEY=xxx
export ENV=local or develop or ...

docker compose up -d
docker compose logs -f idp-server
```

* init table

```shell
./gradlew flywayClean flywayMigrate
```

### setup configuration

```shell
./setup.sh
```

```shell
./sample-config/test-data.sh \
-e "local" \
-u ito.ichiro \
-p successUserCode001 \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-b http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-d false
 ```

```shell
./sample-config/test-tenant-data.sh \
-e "local" \
-u ito.ichiro \
-p successUserCode001 \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-b http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-n 1e68932e-ed4a-43e7-b412-460665e42df3 \
-l clientSecretPost \
-m clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \ 
-d false
 ```

### debug access token

```shell
./sample-config/get-access-token.sh \
-u ito.ichiro@gmail.com \
-p successUserCode \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-e http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890
```

### e2e

```shell
cd e2e
npm install
npm test
```

* if you want to test identity-verification application, you install mockoon and setup config
  * [identity_verification_apply.test.js](e2e/src/tests/application/identity_verification_apply.test.js)
  * change xdescribe to describe at identity_verification_apply.test.js

```shell
brew install --cask mockoon
```

* setup mockoon with [mockoon.json](mock/mockoon.json)

### docker 

```shell
docker build -t idp-server .
```

```shell
docker run -p 8080:8080 \
  -e IDP_SERVER_API_KEY=local-key \
  -e IDP_SERVER_API_SECRET=local-secret \
  -e ENCRYPTION_KEY=supersecret \
  -e DB_WRITE_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e DB_READ_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e REDIS_HOST=host.docker.internal \
  idp-server:latest -it idp-server ls /app/providers
```

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
