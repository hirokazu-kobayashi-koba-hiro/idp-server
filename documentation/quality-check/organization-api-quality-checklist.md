# Organization Management API Quality Checklist

## Overview
This checklist is used to verify the quality of organization-level management APIs according to Issue #449.

## Quality Check Items

### 1. OpenAPI Specification Quality (API仕様書品質)

#### 1.1 Path Definition (パス定義)
- [ ] Path exists in `swagger-control-plane-ja.yaml`
- [ ] Path follows organization-level pattern: `/v1/management/organizations/{organization-id}/tenants/{tenant-id}/...`
- [ ] All required path parameters defined with `$ref`
- [ ] Correct HTTP methods defined (POST, GET, PUT, DELETE as needed)

#### 1.2 Request Schema (リクエストスキーマ)
- [ ] `requestBody` defined for POST/PUT operations
- [ ] Schema reference to correct component: `$ref: '#/components/schemas/...'`
- [ ] Required fields properly marked in schema
- [ ] Optional `dry_run` query parameter defined where applicable

#### 1.3 Response Schema (レスポンススキーマ)
- [ ] Success response (200) properly defined
- [ ] Response schema matches implementation
- [ ] List operations include: `list`, `total_count`, `limit`, `offset`
- [ ] Create/Update operations include: `dry_run`, `result`
- [ ] Delete operations return 204 No Content or appropriate response

#### 1.4 Error Responses (エラーレスポンス)
- [ ] 400 Bad Request defined with `$ref: '#/components/schemas/ErrorResponse'`
- [ ] 403 Forbidden defined with appropriate description
- [ ] 404 Not Found defined with appropriate description
- [ ] 500 Internal Server Error (if applicable)

#### 1.5 Documentation (ドキュメント)
- [ ] `summary` field in Japanese
- [ ] `description` field with clear explanation
- [ ] Correct `tags` assigned (organization-xxx)
- [ ] All parameters have proper descriptions

### 2. Implementation Consistency (実装整合性)

#### 2.1 API Interface (API インターフェース)
- [ ] Interface exists: `Org{Domain}ManagementApi.java`
- [ ] All methods defined in OpenAPI spec exist in interface
- [ ] Method signatures match OpenAPI parameters
- [ ] Javadoc exists and is comprehensive
- [ ] `getRequiredPermissions()` method properly implemented

#### 2.2 Method Signature Check (メソッドシグネチャ確認)
For each operation:
- [ ] **create**: `(OrganizationIdentifier, TenantIdentifier, User, OAuthToken, Request, RequestAttributes, boolean dryRun)`
- [ ] **findList**: `(OrganizationIdentifier, TenantIdentifier, User, OAuthToken, int limit, int offset, RequestAttributes)`
- [ ] **get**: `(OrganizationIdentifier, TenantIdentifier, User, OAuthToken, Identifier, RequestAttributes)`
- [ ] **update**: `(OrganizationIdentifier, TenantIdentifier, User, OAuthToken, Identifier, Request, RequestAttributes, boolean dryRun)`
- [ ] **delete**: `(OrganizationIdentifier, TenantIdentifier, User, OAuthToken, Identifier, RequestAttributes, boolean dryRun)`

#### 2.3 Permission Definition (権限定義)
- [ ] CREATE permission: `{DOMAIN}_CREATE`
- [ ] READ permission: `{DOMAIN}_READ`
- [ ] UPDATE permission: `{DOMAIN}_UPDATE`
- [ ] DELETE permission: `{DOMAIN}_DELETE`
- [ ] Permissions correctly mapped in `getRequiredPermissions()`

### 3. E2E Test Coverage (E2Eテストカバレッジ)

#### 3.1 Test File Existence (テストファイル存在)
- [ ] Test file exists: `organization_{domain}_management.test.js`
- [ ] Structured test exists (if applicable): `organization_{domain}_management_structured.test.js`

#### 3.2 CRUD Operations Coverage (CRUD操作カバレッジ)
- [ ] **CREATE** operation tested
- [ ] **LIST** operation tested
- [ ] **GET** operation tested
- [ ] **UPDATE** operation tested
- [ ] **DELETE** operation tested

#### 3.3 Dry-Run Testing (ドライランテスト)
- [ ] CREATE dry-run tested
- [ ] UPDATE dry-run tested
- [ ] DELETE dry-run tested
- [ ] Dry-run response validation

#### 3.4 Error Case Testing (エラーケーステスト)
- [ ] Invalid organization ID (403 expected)
- [ ] Invalid tenant ID (403 expected)
- [ ] Resource not found (404 expected)
- [ ] Invalid request data (400 expected)

#### 3.5 Response Validation (レスポンス検証)
- [ ] Success response structure validated
- [ ] All required fields checked
- [ ] Field types validated
- [ ] Pagination fields validated (for list operations)

### 4. Data Integrity (データ整合性)

#### 4.1 Organization Access Control (組織アクセス制御)
- [ ] Organization membership verification implemented
- [ ] Tenant access verification implemented
- [ ] Organization-tenant relationship verification implemented
- [ ] Permission verification implemented

#### 4.2 Data Scope (データスコープ)
- [ ] Only organization-scoped data returned
- [ ] No cross-organization data leakage
- [ ] Tenant isolation properly enforced

#### 4.3 Audit Logging (監査ログ)
- [ ] CREATE operation logged
- [ ] UPDATE operation logged
- [ ] DELETE operation logged
- [ ] Audit log includes operator information
- [ ] Audit log includes organization/tenant context

#### 4.4 Input Validation (入力検証)
- [ ] Required fields validation
- [ ] Field type validation
- [ ] Field length validation
- [ ] Business rule validation
- [ ] Proper error messages returned

### 5. JsonSchema Validation (JsonSchemaバリデーション)

#### 5.1 Request Schema Components (リクエストスキーマコンポーネント)
- [ ] Schema defined in `components/schemas` section
- [ ] All fields have `type` definition
- [ ] Required fields marked with `required: []`
- [ ] Field constraints defined (`minLength`, `maxLength`, `pattern`, etc.)
- [ ] Enum values defined where applicable

#### 5.2 Response Schema Components (レスポンススキーマコンポーネント)
- [ ] Response object schema defined
- [ ] Success response structure complete
- [ ] Error response structure standardized
- [ ] Nested object schemas properly referenced

#### 5.3 Schema Consistency (スキーマ整合性)
- [ ] Request schema matches implementation DTO
- [ ] Response schema matches implementation response
- [ ] Field naming follows snake_case convention
- [ ] Schema reused across similar endpoints

## API-Specific Checklist Template

### API: {API_NAME}
**OpenAPI Path**: `/v1/management/organizations/{organization-id}/tenants/{tenant-id}/{resource}`
**Implementation**: `Org{Domain}ManagementApi.java`
**E2E Test**: `organization_{domain}_management.test.js`

| Category | Item | Status | Notes |
|----------|------|--------|-------|
| **OpenAPI** | Path definition | ⬜ | |
| | Request schema | ⬜ | |
| | Response schema | ⬜ | |
| | Error responses | ⬜ | |
| | Documentation | ⬜ | |
| **Implementation** | Interface exists | ⬜ | |
| | Methods complete | ⬜ | |
| | Permissions defined | ⬜ | |
| | Javadoc complete | ⬜ | |
| **E2E Test** | CRUD operations | ⬜ | |
| | Dry-run tested | ⬜ | |
| | Error cases | ⬜ | |
| | Response validation | ⬜ | |
| **Data Integrity** | Access control | ⬜ | |
| | Data scope | ⬜ | |
| | Audit logging | ⬜ | |
| | Input validation | ⬜ | |
| **JsonSchema** | Request schema | ⬜ | |
| | Response schema | ⬜ | |
| | Schema consistency | ⬜ | |

## Priority Levels

- 🔴 **High Priority**: OpenAPI specification, Implementation consistency
- 🟡 **Medium Priority**: E2E test coverage, JsonSchema validation
- 🟢 **Low Priority**: Documentation improvements, Additional error cases

## Automated Check Commands

```bash
# Check OpenAPI path exists
grep -q "/v1/management/organizations/{organization-id}/tenants/{tenant-id}/{resource}" documentation/openapi/swagger-control-plane-ja.yaml

# Check implementation exists
find libs/idp-server-control-plane -name "Org*ManagementApi.java" | grep {Domain}

# Check E2E test exists
find e2e -name "organization_{domain}_management*.test.js"

# Run specific E2E test
npm test -- organization_{domain}_management.test.js
```

## Review Workflow

1. **Initial Assessment**: Run automated checks to identify missing items
2. **Manual Review**: Review OpenAPI spec, implementation, and tests
3. **Gap Analysis**: Document missing or incomplete items
4. **Prioritization**: Assign priority based on impact
5. **Implementation**: Fix issues in priority order
6. **Validation**: Re-run all checks to confirm completion

## Notes

- This checklist is based on Issue #449 quality improvement requirements
- All organization-level APIs should meet these quality standards
- Systematic review ensures consistency across all APIs
- Automated checks can be integrated into CI/CD pipeline
