# Configuration Overview

This section describes how to configure key components of the **idp-server**, including tenants, clients, users, credentials, and MFA.

## Tenant Configuration

Each tenant has its own configuration, stored in a shared or separate schema.  
Configurations include:

- `name`, `domain`, and `type`
- `attributes` (JSONB for feature toggles)
- Authentication policy
- Hook settings (Slack, Webhook, etc.)

### Example (JSON format):

```json
{
  "name": "Acme Corp",
  "domain": "acme.example.com",
  "type": "production",
  "attributes": {
    "mfa_enabled": true,
    "federation_enabled": false
  }
}
```

## Client Registration

Clients are registered per-tenant and may include:

- `client_id`, `client_secret`
- `redirect_uris`
- `response_types`, `grant_types`
- `id_token_signed_response_alg`
- `token_endpoint_auth_method`
- `request_uris`

Client metadata follows the OIDC Dynamic Client Registration spec (RFC 7591).

## User Configuration

Users can be registered manually or via federation/SCIM.  
Attributes:

- `subject` (unique per tenant)
- `name`, `email`, `phone_number`
- `claims` (customizable per client)
- `authentication_methods` (password, passkey, email OTP, etc.)

## Credential Setup

### Password-based
Store password hash (e.g., bcrypt) linked to user subject.

### WebAuthn / Passkey
Register public key from client device.

### Email OTP
Enable email sender and store verification code metadata temporarily.

## MFA Settings

You can enable MFA at:

- Tenant level (`attributes.mfa_enabled = true`)
- Client level (`acr_values` required)
- User level (`required_acrs`)

Supported MFA methods:

- FIDO-UAF
- Passkey (WebAuthn)
- SMS code
- Email code


## Hook Configuration

Each hook is defined per tenant and supports:

- Target (Slack, Webhook URL)
- Trigger events (`LOGIN_FAILURE`, `TOKEN_REVOKED`, etc.)
- Retry policy

---

👉 See [API Reference](../api-reference/index.md) for programmatic configuration access.
