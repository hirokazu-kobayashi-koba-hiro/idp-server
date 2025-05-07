# 📜 AuthenticationPolicy - Specification for Authentication Control Policies

`AuthenticationPolicy` defines the behavior of user authentication flows in OAuth/OIDC/CIBA authorization processes.  
This policy allows for flexible control over user identification, MFA enforcement, success/failure criteria, and lockout conditions.

---

## 🎯 Purpose

- Balance **security strength and UX** during authentication
- Enable conditional application of **Multi-Factor Authentication (MFA)**
- Support for **MFA flow control in backchannel scenarios** like CIBA
- Unified evaluation of **success, failure, and lockout logic**

---

## 🏗️ Structure

```json
{
  "authentication_policy": {
    "conditions": {
      "acr_values": ["urn:mfa:required"],
      "scopes": ["read", "write"],
      "authorization_flow": "ciba"
    },
    "available_methods": [
      "password",
      "email",
      "sms",
      "webauthn",
      "fido-uaf"
    ],
    "success_conditions": {
      "all_of": [
        {
          "type": "password",
          "success_count": 1
        },
        {
          "type": "fido-uaf-authentication",
          "success_count": 1
        }
      ]
    },
    "failure_conditions": {
      "any_of": [
        {
          "type": "password",
          "failure_count": 5
        }
      ]
    },
    "lock_conditions": {
      "any_of": [
        {
          "type": "fido-uaf-authentication",
          "failure_count": 5
        }
      ]
    }
  }
}
```

---

## 🧩 Field Descriptions

| Field               | Description                                                                 |
|--------------------|-----------------------------------------------------------------------------|
| `conditions`        | Defines when the policy is applied (e.g., `acr_values`, `scopes`, `authorization_flow`) |
| `available_methods` | A list of authentication methods presented to the user or used internally  |
| `success_conditions`| Conditions under which authentication is considered successful             |
| `failure_conditions`| Conditions that trigger warnings, metrics, or partial failure flags        |
| `lock_conditions`   | Criteria for locking the user account or denying authorization entirely    |

---

## 🔁 Evaluation Flow Example

1. The policy is selected by matching `acr_values`, `authorization_flow`, etc.
2. Authentication methods are presented (`available_methods`)
3. Results of each `AuthenticationInteraction` are collected
4. If `success_conditions` are satisfied → **Authentication success**
5. If `failure_conditions` are satisfied → **Trigger warning**
6. If `lock_conditions` are satisfied → **Lock account or deny**

---

## 🛠️ Extensibility

You can easily extend the policy definition with:

- `identification_conditions`: User identification strategy
- `time_window_minutes`: Evaluation window for success/lock
- `ui_hints`: Preferred method hints for frontend display

---

## ✅ Operational Suggestions

- Apply strict `success_conditions` for sensitive operations (e.g. money transfer)
- Combine `lock_conditions` for brute-force protection
- Define minimal authentication for low-risk, seamless login (e.g. device-trusted context)
