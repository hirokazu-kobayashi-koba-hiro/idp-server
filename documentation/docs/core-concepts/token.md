# Token

This section describes the implementation of the Token Service within the `idp-server`, which handles token issuance, introspection, and revocation in accordance with OAuth 2.0 and OpenID Connect specifications.

---

## 🧱 API Layer - `TokenV1Api`

This is the SpringBoot REST controller for token-related endpoints:

* `POST /{tenant-id}/api/v1/tokens`: Token issuance
* `POST /{tenant-id}/api/v1/tokens/introspection`: Token introspection
* `POST /{tenant-id}/api/v1/tokens/revocation`: Token revocation

It transforms incoming HTTP requests into internal calls to `TokenApi`, providing tenant-aware access and extracting headers such as `Authorization` and `x-ssl-cert`.

---

## 🧩 Use Case Layer - `TokenEntryService`

Implements `TokenApi` and is responsible for:

* Calling the appropriate `TokenProtocol` implementation for each flow
* Extracting tenant and protocol context
* Publishing security events (`issued`, `introspected`, `revoked`)

It serves as the main entry point for token handling logic.

---

## 🧠 Protocol Layer

### `TokenProtocols`

* Registry that maps `AuthorizationProtocolProvider` to corresponding `TokenProtocol`
* Enables multi-protocol support per tenant

### `TokenProtocol` Interface

* Defines methods:

    * `request(TokenRequest)`
    * `inspect(TokenIntrospectionRequest)`
    * `revoke(TokenRevocationRequest)`

### `DefaultTokenProtocol`

Main implementation used by `idp-server`, which delegates to:

* `TokenRequestHandler`: token issuance
* `TokenIntrospectionHandler`: token validation
* `TokenRevocationHandler`: token invalidation

Supports:

* `PasswordCredentialsGrantDelegate`
* Error handling

---

## 🧬 Component Diagram (Mermaid)

```mermaid
flowchart TD
  A[🌐 TokenV1Api] --> B[🧩 TokenEntryService]
  B --> C[🔁 TokenProtocols]
  C --> D[🧠 DefaultTokenProtocol]
  
  D --> D1[🛠️ TokenRequestHandler]
  D --> D2[🔍 TokenIntrospectionHandler]
  D --> D3[🧹 TokenRevocationHandler]

  D1 --> E1[🔑 ClientAuthenticatorHandler]
  D1 --> E2[🎁 OAuthTokenCreationService]
  
  D2 --> F1[🧪 TokenIntrospectionVerifier]
  D3 --> G1[🗑️ OAuthTokenRepository]
```

---

## 🔐 Client Authentication Support

* Handled by `ClientAuthenticatorHandler`
* Supported methods:

    * `client_secret_basic`, `client_secret_post`
    * `client_secret_jwt`, `private_key_jwt`
    * `tls_client_auth`, `self_signed_tls_client_auth`
    * `none`

### 📘 Code Reference

```java
public class ClientAuthenticatorHandler {

  static Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();

  static {
    map.put(client_secret_basic, new ClientSecretBasicAuthenticator());
    map.put(client_secret_post, new ClientSecretPostAuthenticator());
    map.put(client_secret_jwt, new ClientSecretJwtAuthenticator());
    map.put(private_key_jwt, new PrivateKeyJwtAuthenticator());
    map.put(tls_client_auth, new TlsClientAuthAuthenticator());
    map.put(self_signed_tls_client_auth, new SelfSignedTlsClientAuthAuthenticator());
    map.put(none, context -> new ClientCredentials());
  }

  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator = map.get(context.clientAuthenticationType());
    ClientAuthenticationVerifier verifier =
        new ClientAuthenticationVerifier(
            context.clientAuthenticationType(), clientAuthenticator, context.authorizationServerConfiguration());
    verifier.verify();
    return clientAuthenticator.authenticate(context);
  }
}

```

---

## 🎁 Token Issuance - `TokenRequestHandler`

* Validates grant type, parameters
* Authenticates client
* Dispatches to appropriate `OAuthTokenCreationService`

### Example Implementation: `AuthorizationCodeGrantService`

* Validates authorization code
* Verifies grant-client-user relationship
* Issues:

    * Access Token
    * Refresh Token
    * ID Token (if OIDC)
    * c\_nonce / c\_nonce\_expires\_in (if VC request)
* Registers `AuthorizationGranted`

---

## 🔍 Token Introspection - `TokenIntrospectionHandler`

* Accepts access or refresh token
* Looks up token in `OAuthTokenRepository`
* Verifies status and claims
* Responds with structured metadata (RFC 7662)

---

## ❌ Token Revocation - `TokenRevocationHandler`

* Validates request and client authentication
* Finds matching token (access or refresh)
* Deletes from `OAuthTokenRepository`

---

## 🗂️ Grant Storage & Management

### Grant Persistence:

* `AuthorizationGrantedRepository`
* Ensures:

    * Existing grants are merged and updated
    * New grants are created per RFC 8705 / FAPI

### Token Storage:

* `OAuthTokenRepository`
* Tokens are persisted per tenant and grant
* Supports lookup by access/refresh token

---

## 🔄 Extensibility

* `TokenProtocol` can be extended to support:

    * FAPI 2.0
    * OID4IDA
    * custom grant types
* `OAuthTokenCreationService` is injectable per grant

---

## 📘 API Examples

### 🔑 Token Request

```http
POST /{tenant-id}/api/v1/tokens
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=abc123&redirect_uri=https%3A%2F%2Fclient.example.com%2Fcb
```

### 🧪 Introspection Request

```http
POST /{tenant-id}/api/v1/tokens/introspection
Content-Type: application/x-www-form-urlencoded

token=ACCESS_TOKEN_HERE
```

### ❌ Revocation Request

```http
POST /{tenant-id}/api/v1/tokens/revocation
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW
Content-Type: application/x-www-form-urlencoded

token=ACCESS_TOKEN_HERE
```

---
