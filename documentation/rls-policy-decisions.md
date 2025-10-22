# Row Level Security (RLS) Policy Decisions

Related Issue: #734 - マルチテナント分離のための行レベルセキュリティ(RLS)の完全性を検証

## Overview

This document explains the rationale behind RLS policy application decisions for each table in the idp-server database.

## RLS Policy Pattern

Our RLS policies follow the `tenant_isolation_policy` pattern:

```sql
ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON {table_name}
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE {table_name} FORCE ROW LEVEL SECURITY;
```

**Note**: The `tenant` table uses `id` instead of `tenant_id` in its policy.

## Tables WITH RLS Applied

### 1. `tenant` - Tenant Master Table
**Policy**: `id = current_setting('app.tenant_id')::uuid`

**Rationale**:
- Tenants should only see their own configuration
- Prevents information disclosure of other tenants' settings (features, attributes, configs)
- Critical security requirement: tenant configuration contains sensitive information

**Risk if RLS not applied**:
- Cross-tenant information leakage
- Exposure of security configurations (CORS, session settings)
- Potential attack vector via configuration analysis

---

### 2. `audit_log` - Audit Log Table
**Policy**: `tenant_id = current_setting('app.tenant_id')::uuid`

**Rationale**:
- Audit logs must be strictly isolated per tenant
- Compliance requirement: tenants must not access other tenants' audit trails
- Privacy protection: logs may contain PII and sensitive operations

**Risk if RLS not applied**:
- Privacy violation: cross-tenant activity monitoring
- Compliance violation: GDPR, SOC2, ISO27001 requirements
- Security risk: attacker could analyze other tenants' behavior patterns

---

## Tables WITHOUT RLS Applied

### 1. `idp_user_assigned_tenants` - User-Tenant Assignment
**Filtering Strategy**: User-based filtering (`WHERE user_id = ?`)

**Rationale**:
- **Cross-tenant functionality required**: A single user can belong to multiple tenants
- Application needs to retrieve ALL tenants assigned to a user, not just current tenant
- RLS would break tenant switching functionality

**Example Use Case**:
```sql
-- User needs to see all their assigned tenants for tenant switching
SELECT tenant_id FROM idp_user_assigned_tenants
WHERE user_id = '3ec055a8-8000-44a2-8677-e70ebff414e2';

-- Expected result (with RLS, only 1 row; without RLS, 3 rows):
-- 67e7eae6-62b0-4500-9eff-87459f63fc66 (Tenant A)
-- 779ff73a-7c8a-4966-9813-92f94f2fd2e8 (Tenant X)
-- 1e68932e-ed4a-43e7-b412-460665e42df3 (Tenant Federation)
```

**Application-Level Protection**:
- Repository: `AssignedTenantQueryDataSource.findByUser(User user)` - filters by `user_id`
- Access Control: User can only query their own assignments via authenticated session

**Why Tenant-based RLS Breaks This**:
If we apply `tenant_id = current_setting('app.tenant_id')`, user will only see 1 row instead of all 3 assigned tenants.

---

### 2. `idp_user_current_tenant` - Current Tenant Context Per User
**Filtering Strategy**: User-based filtering (`WHERE user_id = ?`)

**Rationale**:
- Stores per-user current tenant selection (similar to session state)
- Not tenant-scoped data - it's user-scoped data that references a tenant
- Single row per user (PRIMARY KEY is `user_id`, not `tenant_id`)

**Example Use Case**:
```sql
-- User switches to different tenant - updates their current tenant
UPDATE idp_user_current_tenant
SET tenant_id = '779ff73a-7c8a-4966-9813-92f94f2fd2e8'
WHERE user_id = '3ec055a8-8000-44a2-8677-e70ebff414e2';
```

**Application-Level Protection**:
- Repository: `CurrentTenantDataSource.findByUser(User user)` - filters by `user_id`
- Access Control: User can only modify their own current tenant

**Why Tenant-based RLS Doesn't Apply**:
This table stores user preference, not tenant data. The `tenant_id` is a reference, not a filtering key.

---

### 3. `idp_user_assigned_organizations` - User-Organization Assignment
**Filtering Strategy**: User-based filtering (`WHERE user_id = ?`)

**Rationale**:
- Organizations are **higher than tenants** in the hierarchy
- A user in multiple organizations should see all their organization assignments
- Cross-tenant concept: organizations can contain multiple tenants

**Hierarchy**:
```
Organization (top level)
  └── Tenant A
  └── Tenant B
```

**Application-Level Protection**:
- Repository: `AssignedOrganizationDataSource.findByUser(User user)` - filters by `user_id`
- Access Control: User can only query their own organization assignments

**Why Tenant-based RLS Doesn't Apply**:
Organizations are a higher abstraction level than tenants. Tenant-based RLS would incorrectly restrict organization-level operations.

---

### 4. `idp_user_current_organization` - Current Organization Context Per User
**Filtering Strategy**: User-based filtering (`WHERE user_id = ?`)

**Rationale**:
- Similar to `idp_user_current_tenant` but for organization context
- User-scoped preference, not tenant-scoped data
- Single row per user (PRIMARY KEY is `user_id`)

**Application-Level Protection**:
- Repository: `CurrentOrganizationDataSource.findByUser(User user)` - filters by `user_id`
- Access Control: User can only modify their own current organization

---

### 5. `organization` - Organization Master Table
**Filtering Strategy**: Organization-level access control in application layer

**Rationale**:
- Organizations are **above tenants** in the hierarchy
- No `tenant_id` column exists (organizations contain tenants, not vice versa)
- Organization-level access control is handled by `OrganizationAccessVerifier`

**Access Control Pattern**:
```java
// OrganizationAccessVerifier - 4-step verification
1. Verify organization membership
2. Verify tenant access
3. Verify organization-tenant relationship
4. Verify required permissions
```

**Why Tenant-based RLS Doesn't Apply**:
- No `tenant_id` column - cannot apply tenant isolation
- Organization-level security is enforced by application layer, not database layer
- Multiple tenants can belong to same organization

---

## Summary Table

| Table | RLS Applied | Filtering Strategy | Reason |
|-------|-------------|-------------------|--------|
| `tenant` | ✅ Yes | Tenant-based (`id = app.tenant_id`) | Tenant configuration isolation |
| `audit_log` | ✅ Yes | Tenant-based (`tenant_id = app.tenant_id`) | Audit trail isolation (compliance) |
| `idp_user_assigned_tenants` | ❌ No | User-based (`user_id = ?`) | Cross-tenant user assignment list |
| `idp_user_current_tenant` | ❌ No | User-based (`user_id = ?`) | User preference (not tenant data) |
| `idp_user_assigned_organizations` | ❌ No | User-based (`user_id = ?`) | Organization-level (above tenant) |
| `idp_user_current_organization` | ❌ No | User-based (`user_id = ?`) | User preference (organization context) |
| `organization` | ❌ No | Organization-level access control | No tenant_id column (higher level) |

---

## Multi-Layer Defense Strategy

Our security model implements **defense in depth** with multiple layers:

### Layer 1: Application Layer (Primary Defense)
- **Repository Pattern**: All data access filtered by `Tenant` or `User` parameters
- **Access Control Verifier**: `OrganizationAccessVerifier` for organization-level operations
- **Entry Service Validation**: Request validation before database access

### Layer 2: Database Layer (RLS - Secondary Defense)
- **RLS Policies**: Applied to tenant-scoped tables (`tenant`, `audit_log`, etc.)
- **Purpose**: Protection against SQL injection and application layer bypass
- **Scope**: Only applied where tenant-based filtering makes functional sense

### Layer 3: Network Layer
- **VPC Isolation**: Database not accessible from public internet
- **Authentication**: Strong database credentials
- **TLS Encryption**: All database connections encrypted

---

## Testing Strategy

### API-Level Isolation Tests
**File**: `e2e/src/tests/security/multi_tenant_isolation.test.js`

**Tests**:
- User information access across tenants (401 expected)
- Token introspection across tenants (400 expected)
- Resource owner endpoint access across tenants (401 expected)

**Coverage**: Validates API layer (Layer 1) tenant isolation

---

### Database-Level RLS Tests
**File**: `scripts/verify-rls.sh`

**Tests**:
- RLS enabled status verification
- RLS policy existence verification
- Direct database access with `set_config('app.tenant_id', ...)`
- Cross-tenant data access blocking verification

**Coverage**: Validates database layer (Layer 2) RLS policies

---

## Design Principles

### 1. Functional Correctness First
RLS is only applied where it **does not break required functionality**.

**Example**: `idp_user_assigned_tenants` needs cross-tenant visibility for tenant switching.

### 2. User-Based vs Tenant-Based Data
- **Tenant-based data**: Apply RLS (e.g., `audit_log`, `tenant`)
- **User-based data**: Use application-layer filtering (e.g., `idp_user_assigned_tenants`)

### 3. Hierarchy-Aware Security
- Organizations > Tenants > Users
- Higher-level entities (organizations) do not use tenant-based RLS

### 4. Defense in Depth
- Application layer is the primary defense
- RLS is the secondary defense (SQL injection protection)
- Both layers must work together

---

## References

- **Issue**: #734 - マルチテナント分離のための行レベルセキュリティ(RLS)の完全性を検証
- **PostgreSQL RLS Documentation**: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- **RFC**: Not applicable (internal security design)
- **CLAUDE.md**: マルチテナント原則 - TenantIdentifier/OrganizationIdentifier による完全分離

---

## Migration Notes

When applying this RLS policy:

1. **DDL Application**: Run `V0_9_0__init_lib.sql` migration
2. **Verification**: Execute `scripts/verify-rls.sh` to confirm RLS is working
3. **E2E Testing**: Run `e2e/src/tests/security/multi_tenant_isolation.test.js`
4. **Monitoring**: Check application logs for any RLS-related errors during first deployment

---

Last Updated: 2025-10-21
Author: Claude Code (AI-assisted development)
