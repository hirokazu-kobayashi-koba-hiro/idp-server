# Role Management API Refactoring Analysis

**Commit**: `efca6cfb5` - refactor role management api
**Date**: 2025-10-24
**Impact**: 37 files changed, 967 insertions(+), 1206 deletions(-)

---

## Main Changes

### 1. Context Consolidation

**Deleted** (6 files):
- RoleRegistrationContext.java
- RoleRegistrationContextCreator.java
- RoleUpdateContext.java
- RoleUpdateContextCreator.java
- RoleRemovePermissionContext.java
- RoleRemovePermissionContextCreator.java

**Created** (2 files):
- RoleManagementContext.java
- RoleManagementContextBuilder.java

**Key Design**:
```java
public class RoleManagementContext implements AuditableContext {
  TenantIdentifier tenantIdentifier;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  Role before;  // nullable - for update/delete/get
  Role after;   // nullable - for create/update
  RoleManagementRequest request;
  boolean dryRun;
  ManagementApiException exception;  // for error context
}
```

### 2. Request Type Reorganization

**Moved to io/ package**:
- RoleManagementResult.java (from handler/)
- RoleUpdateRequest.java (from handler/)
- RoleRemovePermissionsRequest.java (from handler/)

**Created**:
- RoleManagementRequest.java (interface)
- RoleFindListRequest.java
- RoleFindRequest.java
- RoleDeleteRequest.java

### 3. API Interface Simplification

**Before**:
```java
RoleManagementResponse create(
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    RoleRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun);
```

**After**:
```java
RoleManagementResponse create(
    AdminAuthenticationContext authenticationContext,
    TenantIdentifier tenantIdentifier,
    RoleRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun);
```

**Note**: TenantIdentifier remains as a separate parameter (method-specific identifier)

### 4. Handler Layer Improvement

**New Flow**:
```java
public RoleManagementResult handle(
    String method,
    AdminAuthenticationContext authenticationContext,
    TenantIdentifier tenantIdentifier,
    RoleManagementRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  // 1. Extract from context
  User operator = authenticationContext.operator();
  OAuthToken oAuthToken = authenticationContext.oAuthToken();

  // 2. Service selection
  RoleManagementService<?> service = services.get(method);

  // 3. Context Builder creation (BEFORE Tenant retrieval)
  RoleManagementContextBuilder builder =
      new RoleManagementContextBuilder(...);

  try {
    // 4. Tenant retrieval
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 5. Permission verification
    AdminPermissions required = api.getRequiredPermissions(method);
    apiPermissionVerifier.verify(operator, required);

    // 6. Service execution
    RoleManagementResponse response = executeService(...);

    // 7. Success context
    AuditableContext context = builder.build();
    return RoleManagementResult.success(context, response);

  } catch (NotFoundException e) {
    // Error context (partial)
    AuditableContext context = builder.buildPartial(new ResourceNotFoundException(e));
    return RoleManagementResult.error(context, notFound);

  } catch (ManagementApiException e) {
    // Error context (partial)
    AuditableContext errorContext = builder.buildPartial(e);
    return RoleManagementResult.error(errorContext, e);
  }
}
```

**Key Points**:
- Context Builder created BEFORE Tenant retrieval (enables audit logging on early errors)
- Unified error handling in Handler layer
- buildPartial() for error contexts

### 5. Service Layer Changes

**Interface**:
```java
RoleManagementResponse execute(
    RoleManagementContextBuilder builder,  // Added
    Tenant tenant,
    User operator,
    OAuthToken oAuthToken,
    REQUEST request,
    RequestAttributes requestAttributes,
    boolean dryRun);
```

**Implementation Example**:
```java
@Override
public RoleManagementResponse execute(
    RoleManagementContextBuilder builder,
    Tenant tenant,
    User operator,
    OAuthToken oAuthToken,
    RoleRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  // Business logic
  Role newRole = Role.create(...);

  if (!dryRun) {
    roleCommandRepository.create(tenant, newRole);
  }

  // Set context data
  builder.setAfter(newRole);  // create operation

  return RoleManagementResponse.success(newRole);
}
```

### 6. EntryService Layer Simplification

**Before** (28 lines):
```java
RoleManagementResult result = handler.handle(...);

if (result.hasException()) {
  AuditLog auditLog = AuditLogCreator.createOnError(
      "RoleManagementApi.create",
      result.tenant(),
      operator,
      oAuthToken,
      result.getException(),
      requestAttributes);
  auditLogPublisher.publish(auditLog);
  return result.toResponse(dryRun);
}

AuditLog auditLog = AuditLogCreator.create(result.context());
auditLogPublisher.publish(auditLog);
return result.toResponse(dryRun);
```

**After** (12 lines):
```java
RoleManagementResult result = handler.handle(...);

AuditLog auditLog = AuditLogCreator.create(result.context());
auditLogPublisher.publish(auditLog);

return result.toResponse(dryRun);
```

**Reduction**: -57% (all methods similar reduction)

---

## Context Builder Pattern

### before/after Usage

| Operation | before | after | Reason |
|-----------|--------|-------|--------|
| create | null | set | No existing data |
| update | set | set | Track changes |
| delete | set | null | Record deleted data |
| get | set | null | Record retrieved data |
| findList | null | null | List operation |

### Builder Methods

```java
public class RoleManagementContextBuilder {
  // Constructor with base data
  public RoleManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      RoleManagementRequest request,
      boolean dryRun) { ... }

  // Set data
  public void setBefore(Role before) { ... }
  public void setAfter(Role after) { ... }

  // Build success context
  public RoleManagementContext build() {
    return new RoleManagementContext(..., null); // exception = null
  }

  // Build error context (partial)
  public RoleManagementContext buildPartial(ManagementApiException exception) {
    return new RoleManagementContext(..., exception);
  }
}
```

---

## Key Patterns

### 1. Early Construction
Create Context Builder BEFORE Tenant retrieval to enable audit logging on all errors.

### 2. Unified Context
Single context for all operations (create/update/delete/get/findList) with nullable fields.

### 3. Partial Context
buildPartial() enables audit logging even when operation fails early.

### 4. Marker Interface
RoleManagementRequest interface for type-safe polymorphism.

### 5. Single Audit Log Pattern
AuditLogCreator.create(result.context()) for all operations (no createOnError/createOnRead).

---

## Code Reduction

| File | Before | After | Reduction |
|------|--------|-------|-----------|
| RoleManagementEntryService | 251 lines | 157 lines | -37% |
| OrgRoleManagementEntryService | 245 lines | 151 lines | -38% |
| Context files | 6 files | 2 files | -67% |
| **Total** | **1206 lines** | **967 lines** | **-20%** |

---

## Migration Template

### Step 1: Create Context & Builder
```java
public class {Domain}ManagementContext implements AuditableContext {
  TenantIdentifier tenantIdentifier;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  {DomainModel} before;
  {DomainModel} after;
  {Domain}ManagementRequest request;
  boolean dryRun;
  ManagementApiException exception;
}

public class {Domain}ManagementContextBuilder {
  // Constructor + setBefore/setAfter + build/buildPartial
}
```

### Step 2: Create Request Types
```java
public interface {Domain}ManagementRequest {}

public record {Domain}FindListRequest(Queries queries)
    implements {Domain}ManagementRequest {}

public record {Domain}FindRequest(Identifier id)
    implements {Domain}ManagementRequest {}

public record {Domain}DeleteRequest(Identifier id)
    implements {Domain}ManagementRequest {}
```

### Step 3: Update API Interface
```java
{Domain}ManagementResponse method(
    AdminAuthenticationContext authenticationContext,
    TenantIdentifier tenantIdentifier,  // Keep if needed
    {Domain}Request request,
    RequestAttributes requestAttributes,
    boolean dryRun);
```

### Step 4: Update Handler
```java
public {Domain}ManagementResult handle(
    String method,
    AdminAuthenticationContext authenticationContext,
    TenantIdentifier tenantIdentifier,
    {Domain}ManagementRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  User operator = authenticationContext.operator();
  OAuthToken oAuthToken = authenticationContext.oAuthToken();

  {Domain}ManagementService<?> service = services.get(method);

  {Domain}ManagementContextBuilder builder =
      new {Domain}ManagementContextBuilder(...);

  try {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AdminPermissions required = api.getRequiredPermissions(method);
    apiPermissionVerifier.verify(operator, required);

    {Domain}ManagementResponse response = executeService(...);

    AuditableContext context = builder.build();
    return {Domain}ManagementResult.success(context, response);

  } catch (NotFoundException e) {
    AuditableContext context = builder.buildPartial(new ResourceNotFoundException(e));
    return {Domain}ManagementResult.error(context, notFound);

  } catch (ManagementApiException e) {
    AuditableContext errorContext = builder.buildPartial(e);
    return {Domain}ManagementResult.error(errorContext, e);
  }
}
```

### Step 5: Update Service
```java
{Domain}ManagementResponse execute(
    {Domain}ManagementContextBuilder builder,
    Tenant tenant,
    User operator,
    OAuthToken oAuthToken,
    REQUEST request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  // Business logic
  {DomainModel} result = ...;

  // Set context
  builder.setAfter(result);  // or setBefore for update/delete

  return {Domain}ManagementResponse.success(result);
}
```

### Step 6: Simplify EntryService
```java
{Domain}ManagementResult result = handler.handle(...);

AuditLog auditLog = AuditLogCreator.create(result.context());
auditLogPublisher.publish(auditLog);

return result.toResponse(dryRun);
```

---

## Important Notes

### TenantIdentifier Handling
Keep TenantIdentifier as separate parameter when it identifies the target resource (not just authentication context).

### Builder Early Construction
Create Builder BEFORE Tenant retrieval to enable audit logging on all errors.

### Nullable Fields
before/after are nullable - different operations use different combinations.

### Single Audit Log Method
Use AuditLogCreator.create(context) for all operations (success and error).

---

**Last Updated**: 2025-01-24
**Reference Commit**: efca6cfb5