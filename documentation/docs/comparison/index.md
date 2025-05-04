# Comparison

This section compares **idp-server** with other popular identity solutions.

| Feature                                | idp-server ✅              | Keycloak 🏰             | Auth0 🔒         | Firebase Auth 🔥  |
|----------------------------------------|---------------------------|-------------------------|------------------|-------------------|
| OAuth 2.0 / OIDC Support               | ✅ Full                    | ✅ Full                  | ✅ Full           | ✅ Basic (limited) |
| FAPI Support                           | ✅ Full (1 & 2)            | ❌ (partial)             | ⚠️ Baseline only | ❌ Not supported   |
| CIBA Support                           | ✅ Poll, Ping, Push        | ⚠️ Partial (Poll)       | ❌                | ❌                 |
| OIDC for Identity Assurance (OIDC4IDA) | ✅ Verified Claims         | ❌                       | ❌                | ❌                 |
| Verifiable Credentials                 | ✅ Native VC Issuer        | ❌                       | ❌                | ❌                 |
| Multi-Tenant Architecture              | ✅ Flexible (shared/split) | ✅ Yes                   | ✅ Yes            | ❌                 |
| Hook System                            | ✅ Customizable Hooks      | ⚠️ SPI-heavy            | ✅ Webhook-based  | ❌                 |
| MFA Support                            | ✅ FIDO-UAF / Passkey      | ✅ TOTP / WebAuthn       | ✅ SMS / App      | ✅ SMS             |
| Federation (OIDC/SAML)                 | ✅ Extensible              | ✅ Built-in              | ✅ Built-in       | ❌                 |
| Self-hosted                            | ✅ Yes                     | ✅ Yes                   | ❌ Cloud only     | ❌ Cloud only      |
| Plugin Architecture                    | ✅ Yes                     | ✅ Mature SPI            | ❌                | ❌                 |
| Database Flexibility                   | ✅ PostgreSQL / MySQL      | ⚠️ PostgreSQL preferred | ❌ N/A            | ❌ N/A             |

---

## Summary

- 🏆 **idp-server** excels in:
    - Spec coverage (FAPI, CIBA, OIDC)
    - Flexibility (self-hosting, tenant model, hooks)
    - Developer focus (Java-based, extensible, clean architecture)
    - Verifiable Credentials support (rare in OSS IdPs)

- ⚠️ **Keycloak** is feature-rich but heavy-weight and SPI customization is complex.
- 🔒 **Auth0** is great for rapid SaaS setup, but limited in control and extensibility.
- 🔥 **Firebase Auth** is frontend-focused, not suited for enterprise use.

---

👉 Next: [Contributing](../contributing/index.md) if you want to improve this project.

## NOTE

### What is OIDC4IDA?

OIDC for Identity Assurance (OIDC4IDA) is an OpenID Connect extension for delivering **verified claims** — such as
passport info, eKYC results, or government-issued identity proofs — in a structured and trustworthy format.

**idp-server** supports:

- `verified_claims` in `id_token` and `userinfo`
- `trust_framework`, `evidence`, and `verification` fields
- Tied to credential issuance and eKYC flows
