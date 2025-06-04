# User Lifecycle & Security Event Handling

This document describes how `idp-server` handles user-related events, particularly focusing on the relationship between **UserLifecycleEvents** and **SecurityEvents**, and how these events contribute to user management logic such as account locking.

---

## 🎯 Purpose

To ensure robust identity lifecycle control and security monitoring by clearly separating and coordinating:

* **Lifecycle operations** (e.g., delete, lock)
* **Security observations** (e.g., password failures, suspicious activity)

---

## 🔁 Event System Overview

| Event Type           | Purpose                            | Trigger Source                  | Effects                                                   |
| -------------------- | ---------------------------------- | ------------------------------- | --------------------------------------------------------- |
| `SecurityEvent`      | Detect & notify suspicious actions | Auth flows, login, token access | Notification, audit, optional trigger to lifecycle events |
| `UserLifecycleEvent` | Mutate user state or delete data   | Admin actions, auto-lock        | Internal user update, deletion, propagation               |

---

## 🔐 Account Lock Flow (Example: 5 failed password attempts)

```plaintext
[Login Failure x5]
   ↓
SecurityEvent (type=password_failure)
   ↓
FailureCounter.increment(userId)
   ↓
IF failureCount >= 5:
   ↓
→ UserLifecycleEvent (operation=LOCK)
→ SecurityEvent (type=user_locked)
   ↓
User status updated to LOCKED
```

### Implementation Components

* `SecurityEventHandler`: Watches for `password_failure`, increments counter
* `FailureCounter`: Redis-backed count tracker (per user, expiring)
* `UserLifecycleEventPublisher`: Emits lifecycle events like LOCK
* `UserCommandRepository`: Updates user status (e.g., LOCKED)
* `SecurityEventPublisher`: Emits `user_locked` event for audit/notification

---

## ✅ Responsibilities

### `SecurityEvent`

* Observes runtime behavior (failures, abnormal requests)
* Triggers alerts or event chains (e.g., lock detection)
* Used for audit trails and real-time monitoring

### `UserLifecycleEvent`

* Explicit user state changes (LOCK, DELETE, SUSPEND)
* Can trigger cleanup actions (e.g., delete credentials, clear roles)
* May enqueue recovery workflows (e.g., account unlock)

---

## 🧩 Combined Use Case: User Deletion

| Step | Event              | Action                                                         |
| ---- | ------------------ | -------------------------------------------------------------- |
| 1    | Admin deletes user | `UserLifecycleEvent(DELETE)`                                   |
| 2    | Lifecycle handler  | Triggers data deletion (`UserRelatedDataDeletionExecutor`)     |
| 3    | Notifies systems   | Emits `SecurityEvent(type=user_delete)` for webhook/slack etc. |

---

## 📘 Related Topics

* `UserRelatedDataDeletionExecutor`
* `SecurityEventHookConfiguration`
* `Redis-backed Failure Counter`
* `UserCommandRepository`
