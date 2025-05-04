# Overview

## FAQ

Frequently Asked Questions about **idp-server**

---

## 🛠 General

### Q: What Java version is required?

A: Java 21 or later is required.

---

### Q: Which databases are supported?

A: PostgreSQL (recommended), MySQL (supported), and Google Cloud Spanner (experimental).

---

### Q: Does this support multi-tenancy?

A: Yes! You can use a shared schema (with tenant_id columns) or a schema-per-tenant model.

---

## 🔐 Authentication & MFA

### Q: How do I enable MFA?

A: Add `mfa_enabled: true` in the tenant `features` and configure the preferred method (Passkey, Email, etc.).

---

### Q: Can I use Passkey (WebAuthn)?

A: Yes, Passkey is supported. Public key credentials are registered via WebAuthn-compliant clients.

---

### Q: How do I add a custom authentication method?

A: You can implement your own `AuthenticationInteractor` and register it via SPI.

---

## 🔄 Federation & Federation

### Q: Can I integrate with external IdPs?

A: Yes, via OIDC federation. SAML support is planned using a wrapper adapter.

---

### Q: Is social login supported?

A: Not built-in, but you can use OIDC federation with providers like Google, GitHub, etc.

---

## 🧪 Testing & Development

### Q: How can I test OIDC flows?

A: Use a client like [oidc-client-js](https://github.com/IdentityModel/oidc-client-js), Postman, or curl to test the flows manually.

---

### Q: How can I simulate CIBA flow?

A: Use the `POST /backchannel-authentications` endpoint and track the polling or notification behavior.

---

## 📄 Misc

### Q: Where are the logs?

A: Standard output (JSON format). You can redirect logs to ELK/Datadog via structured log forwarders.

---

### Q: How do I contribute?

A: See [Contributing](../contributing/index.md) for dev setup and PR guidelines.
