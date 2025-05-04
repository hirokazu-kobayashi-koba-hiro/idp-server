# OIDC API Reference

The **idp-server** provides a set of REST APIs and standard OpenID Connect endpoints.

## Authorization Endpoint

**`GET /authorizations`**

Handles authorization requests for OAuth2/OIDC.

Supports:

- `response_type=code`, `token`, `id_token`
- `scope`, `acr_values`, `prompt`, `login_hint`
- Request Object via `request` or `request_uri`
- PKCE (`code_challenge`, `code_challenge_method`)

## Token Endpoint

**`POST /token`**

Issues tokens based on:

- Authorization Code
- Client Credentials
- Resource Owner Password Credentials
- CIBA (via `grant_type=urn:openid:params:grant-type:ciba`)

Supports:

- JWT access token
- `id_token` for OIDC clients
- Token introspection and custom claims

## UserInfo Endpoint

**`GET /userinfo`**

Returns claims about the authenticated user.  
Subject is derived from the access token.

Supports:

- Standard OIDC claims
- Custom claims per client / tenant

## Token Introspection

**`POST /introspect`**

OAuth2 Token Introspection (RFC 7662)

- Verifies access/refresh token validity
- Returns token metadata and active flag

## Token Revocation

**`POST /revoke`**

OAuth2 Token Revocation (RFC 7009)

- Supports revoking access and refresh tokens

## CIBA (Backchannel Auth Endpoint)

**`POST /backchannel-authentication`**

Receives CIBA requests from clients.

Supports:

- Poll, Ping, and Push modes
- `login_hint`, `binding_message`, `user_code`

## Admin APIs (per tenant)

**`/admin/tenants/{tenant_id}/...`**

- Manage clients
- Manage users
- Configure hooks
- Fetch audit logs and events

## Metadata & Discovery

- `GET /.well-known/openid-configuration`
- `GET /.well-known/oauth-authorization-server`
- `GET /.well-known/webfinger`

---

👉 See [Configuration](../configuration/index.md) to understand how to manage clients, users, and hooks.
