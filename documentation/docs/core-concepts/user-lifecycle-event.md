# User Deletion Design Policy (`idp_user`)

## 🧹 Basic Deletion Policy

| Category     | Strategy         | Description |
|--------------|------------------|-------------|
| Core Data    | Physical Deletion | e.g., `idp_user`, `idp_user_roles` |
| Logs & History | Logical Deletion / Retain | Keep audit logs, consents, applications |
| External Services | Asynchronous Deletion | FIDO, VC are handled via hooks |

---

## ✅ Tables to Be Physically Deleted

| Table Name | Description |
|------------|-------------|
| `idp_user` | Main user record |
| `idp_user_roles` | Role assignments |
| `user_permission_override` | User-specific permission overrides |
| `oauth_token` | Issued access/refresh tokens |
| `authorization_code_grant` | Authorization code grant info |
| `authentication_transaction` | Auth transaction records (with user_id) |
| `authentication_interactions` | MFA / passkey interaction history |
| `federation_sso_session` | Federation login sessions |
| `ciba_grant` | CIBA authentication grants |

---

## 🔄 Tables to Be Logically Deleted or Revoked

| Table Name | Action | Description |
|------------|--------|-------------|
| `authorization_granted` | Set `revoked_at` | Record of user consent |
| `identity_verification_applications` | Set `status = 'deleted'` | KYC application history |
| `identity_verification_results` | Set `source = 'deleted_user'` or similar | Record of identity proof |
| `verifiable_credential_transaction` | Set `status = 'revoked'` | VC issuance and revocation logs |

---

## 🕵️‍♀️ Audit & Logging Data (Should Not Be Deleted)

| Table Name | Description | Strategy |
|------------|-------------|----------|
| `security_event` | Audit log of actions | Consider anonymizing `user_id` |
| `security_event_hook_results` | Hook execution results | Keep as-is for history |

---

## ✋ External Services Integration (Async)

| Target       | Method |
|--------------|--------|
| FIDO Server  | Enqueue `delete_account` hook event |
| VC Issuer    | Enqueue VC revocation event via hook |

---

## 🚦 Recommended Deletion Sequence

1. `authentication_interactions`
2. `authentication_transaction`
3. `idp_user_roles`
4. `user_permission_override`
5. `oauth_token`
6. `authorization_code_grant`
7. `ciba_grant`
8. `federation_sso_session`
9. Logical updates for records (revoked, status, etc.)
10. Delete `idp_user` physically
11. Add audit entry to `security_event` (or anonymize)
12. Enqueue `delete_account` hook to external services

---
