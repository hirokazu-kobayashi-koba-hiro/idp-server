# ID Token Structure

## 1. Overview

An **ID Token** is a JSON Web Token (JWT) issued as part of the OpenID Connect authentication flow to convey the authentication result and user information to the client.

* Serves as proof that authentication was successful
* Contains information about the authenticated user (`sub`)
* Issued in flows such as Authorization Code, CIBA, and Hybrid Flow

## 2. Structure of ID Token (Standard Claims)

The following are standard claims defined by OpenID Connect:

| Claim       | Description                                                            |
| ----------- | ---------------------------------------------------------------------- |
| `iss`       | Issuer Identifier (e.g., `https://idp.example.com`)                    |
| `sub`       | Subject Identifier (unique user ID)                                    |
| `aud`       | Audience (the recipient client\_id)                                    |
| `exp`       | Expiration time (Unix timestamp)                                       |
| `iat`       | Issued at (Unix timestamp)                                             |
| `auth_time` | Time when the user last authenticated (e.g., after MFA)                |
| `acr`       | Authentication Context Class Reference (e.g., `urn:acr:1`)             |
| `amr`       | Authentication Methods Reference (e.g., `password`, `mfa`, `webauthn`) |
| `nonce`     | Unique token to prevent replay attacks                                 |
| `azp`       | Authorized party (used when `aud` has multiple values)                 |

## 3. Custom Claims

The `idp-server` allows custom claims to be added, including user attributes and consent information:

```json
{
  "email": "user@example.com",
  "name": "Taro Yamada",
  "user_id": "abc123",
  "terms": {
    "version": "1.0",
    "acceptedAt": "2025-05-04T01:00:00Z"
  },
  "verified_claims": {
    "verification": { "trust_framework": "eKYC" },
    "claims": { "birthdate": "1990-01-01" }
  }
}
```

## 4. Verified Claims (OIDC for Identity Assurance)

To support identity assurance (OIDC4IDA), the ID Token can include `verified_claims` containing formally verified user attributes.

### Structure of `verified_claims`

```json
"verified_claims": {
  "verification": {
    "trust_framework": "de_aml",
    "time": "2025-04-01T12:00:00Z",
    "evidence": [
      {
        "type": "id_document",
        "method": "pipp",
        "time": "2025-04-01T11:00:00Z"
      }
    ]
  },
  "claims": {
    "given_name": "Taro",
    "family_name": "Yamada",
    "birthdate": "1990-01-01"
  }
}
```

* `verification`: Metadata about how and when the user information was verified.
* `claims`: The actual verified attributes.
* Trust frameworks (e.g., `de_aml`, `eidas`) and evidence types are aligned with the OID4IDA specification.

## 5. Signature and Encryption

* **Signature (JWS)**: Default is `RS256`; `ES256` is also supported
* **Encryption (JWE)**: Configured via `id_token_encrypted_response_alg`

## 6. ID Token Example (JWT)

### Base64 Encoded

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2lkcC5leGFtcGxlLmNvbSIsInN1YiI6ImFiYzEyMyIsImF1ZCI6IjEyMzQiLCJleHAiOjE2OTU1NTU2MDAsImlhdCI6MTY5NTU1MjAwMCwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIn0.XYZ
```

### Payload (Decoded)

```json
{
  "iss": "https://idp.example.com",
  "sub": "abc123",
  "aud": "1234",
  "exp": 1695555600,
  "iat": 1695552000,
  "email": "user@example.com"
}
```

## 7. Implementation Notes

* Issued by `OAuthAuthorizeHandler`, `CibaAuthorizeHandler`
* Built using `IdTokenBuilder`
* Claims are assembled via `UserClaimsBuilder` → `UserClaimsConverter`
* For FAPI, the `sub` becomes pairwise
* In OID4IDA (Identity Assurance), `verified_claims` are included


---

This structure enables flexible and secure issuance of ID Tokens. Customization is supported based on profiles and tenant settings.
