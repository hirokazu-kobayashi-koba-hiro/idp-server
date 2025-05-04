# Multi-Tenancy

This document describes how **idp-server** supports multi-tenant environments, ensuring secure and isolated authentication and authorization flows across different tenants.

---

## 🧱 Design Principles

- **Explicit Tenant Context**  
  All APIs accept a `TenantIdentifier`, which is resolved to a `Tenant` using `TenantRepository`.  
  The resolved tenant is passed explicitly to all downstream services.

- **No Global TenantContext**  
  `idp-server` does **not** use thread-local or static tenant context.  
  This enables:
  - Safe concurrent request handling
  - Clean unit testing
  - No accidental tenant leakage

---

## 🧭 Tenant-Aware Service Implementation

The `OAuthFlowEntryService` demonstrates a consistent design pattern:

```java
Tenant tenant = tenantRepository.get(tenantIdentifier);
OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
AuthorizationRequest authorizationRequest = oAuthProtocol.get(tenant, authorizationRequestIdentifier);
```

Every operation — such as authentication, authorization, or logout — uses the tenant explicitly.

---

## 🛠 Dynamic Behavior per Tenant

Each `Tenant` configures its own:

- Supported protocol: `authorizationProtocolProvider`
- MFA policy
- Federation settings
- Session expiration rules

This dynamic behavior is resolved via:

```java
OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
```

---

## 🗃 Repository Access Pattern

All repository calls are scoped per tenant:

```java
authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);
authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);
```

This makes data boundaries explicit and lays the foundation for future DB sharding or schema-based isolation.

---

## 📊 Benefits

| Feature              | Description                                                                 |
|----------------------|-----------------------------------------------------------------------------|
| ✅ Strong Isolation   | Prevents data leakage between tenants                                       |
| ✅ Easy Testing       | No global state; each test can inject its own tenant                       |
| ✅ Scalable Design    | Ready for future partitioning or horizontal scaling                        |
| ✅ Flexible Behavior  | Per-tenant customization of protocols, policies, and flows                 |

---

## 🔒 Security Impact

Tenant isolation is enforced at the application layer. Combined with strict validation and per-tenant configuration, this ensures that even in complex multi-tenant deployments:

- Users are always authenticated in the correct tenant context
- Tokens and sessions are segregated per tenant
- Federation and MFA strategies follow tenant-defined logic

---

## 🧩 Example: OAuth Flow Entry

```java
public Pairs<Tenant, OAuthRequestResponse> request(
    TenantIdentifier tenantIdentifier,
    Map<String, String[]> params,
    RequestAttributes requestAttributes) {

  Tenant tenant = tenantRepository.get(tenantIdentifier);
  OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

  OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
  OAuthRequestResponse requestResponse = oAuthProtocol.request(oAuthRequest);

  if (requestResponse.isOK()) {
    AuthenticationTransaction tx =
        AuthenticationTransaction.createOnOAuthFlow(tenant, requestResponse);
    authenticationTransactionCommandRepository.register(tenant, tx);
  }

  return new Pairs<>(tenant, requestResponse);
}
```

This shows how every request is scoped and processed within the correct tenant context.

---

## 📘 Related Topics

- [Authorization Flow](./authorization-flow.md)
- [Authentication Interactors](./authentication-interactors.md)
- [Federation Handling](./federation.md)
- [Session Management](./session.md)
