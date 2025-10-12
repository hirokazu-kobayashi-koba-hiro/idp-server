# 身元確認申込み実装ガイド

## このドキュメントの目的

**動的な身元確認申込みAPI**の実装を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [外部サービス連携](../04-implementation-guides/external-integration.md)

---

## 身元確認申込みとは

**テンプレート設定に基づいて動的に生成されるエンドポイント**

リソースオーナー（エンドユーザー）が外部KYCサービスに身元確認を申し込む。

---

## エンドポイント

### 動的生成API

```
# エンドポイント形式
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{process}
Authorization: Bearer {access-token}

# 例
POST /{tenant-id}/v1/me/identity-verification/applications/kyc-basic/submit
POST /{tenant-id}/v1/me/identity-verification/applications/kyc-enhanced/verify
```

**実装**: [IdentityVerificationApplicationV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/IdentityVerificationApplicationV1Api.java)

---

## 🚨 重要な制約

### 1. リソースオーナーのアクセストークン必須

**Basic認証やクライアント認証は使用不可**。エンドユーザー自身のトークンが必要：

```bash
# ✅ 正しい: リソースオーナー（エンドユーザー）のトークン
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/me/identity-verification/applications/kyc-basic/submit" \
  -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "document_number": "AB1234567"}'

# ❌ 間違い: Basic認証
curl -X POST "..." \
  -H "Authorization: Basic $(echo -n 'client:secret' | base64)"  # 401 Unauthorized

# ❌ 間違い: Client Credentials Token
curl -X POST "..." \
  -H "Authorization: Bearer ${CLIENT_TOKEN}"  # 403 Forbidden (ユーザーコンテキストなし)
```

**理由**: 身元確認は**特定ユーザーに紐づく**操作のため、ユーザーコンテキストが必須

**URL構造の意味**: `/v1/me/` = 「自分自身」= リソースオーナー専用

---

### 2. Management APIで事前設定必須

動的エンドポイントは設定がないと404エラー：

```bash
# 設定なしで実行 → 404
POST /{tenant-id}/v1/me/identity-verification/applications/unknown-type/submit
→ HTTP 404 Not Found: "configuration not found for type: unknown-type"

# 先にManagement APIで設定登録が必要
POST /v1/management/tenants/{tenant-id}/identity-verification-configurations
{
  "id": "550e8400-e29b-41d4-a716-446655440000",  // UUIDv4形式必須
  "type": "kyc-basic",     // この値が{verification-type}になる
  "processes": {
    "submit": {            // この値が{process}になる
      "execution": { ... }
    }
  }
}
```

**設定 → エンドポイントのマッピング**:
```
type: "kyc-basic" + process: "submit"
→ POST /{tenant-id}/v1/me/identity-verification/applications/kyc-basic/submit
```

---

### 3. UUID v4形式の ID 必須

設定のIDは必ずUUIDv4形式：

```javascript
import { v4 as uuidv4 } from 'uuid';

const configId = uuidv4();  // 必須: UUIDv4形式
// 例: "550e8400-e29b-41d4-a716-446655440000"

// ❌ "kyc-config-1" のような文字列は不可
// ❌ 連番も不可
```

**テストでの注意**: E2Eテストでも必ずUUIDv4を使用（固定文字列は不可）

---

## 7フェーズ処理

**実装**: [IdentityVerificationApplicationHandler.java:60-129](../../../../libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/identity/verification/application/IdentityVerificationApplicationHandler.java#L60-L129)

### フェーズ概要

```
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{process}
    ↓
IdentityVerificationApplicationHandler.executeRequest()
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 1: Request Verification（リクエスト検証）        │
├──────────────────────────────────────────────────────┤
│  IdentityVerificationApplicationRequestVerifiers    │
│  ├─ 必須パラメータチェック                             │
│  ├─ パラメータ形式検証                                │
│  └─ ビジネスルール検証                                │
│                                                      │
│  エラー時 → 即座に終了（Fail Fast）                   │
└──────────────────────────────────────────────────────┘
    ↓ OK
┌──────────────────────────────────────────────────────┐
│ Phase 2: Pre Hook（外部APIから追加パラメータ取得）     │
├──────────────────────────────────────────────────────┤
│  AdditionalRequestParameterResolvers                │
│  ├─ 外部APIを呼び出し（設定による）                    │
│  ├─ ユーザー情報補完                                  │
│  └─ 追加パラメータをコンテキストに追加                 │
│                                                      │
│  設定例: GET https://kyc-service.com/api/user-info  │
│  → { "credit_score": 750, "risk_level": "low" }    │
│                                                      │
│  Fail Fast対応: エラー時は即座に終了可能              │
└──────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 3: Execution（外部KYC APIに申込み実行）         │
├──────────────────────────────────────────────────────┤
│  IdentityVerificationApplicationExecutor            │
│  ├─ HttpRequestExecutor使用                          │
│  ├─ リクエストマッピング（JSONPath）                  │
│  ├─ 認証（OAuth2/HMAC/なし）                         │
│  ├─ リトライ処理（502/503/504）                      │
│  └─ レスポンス取得                                    │
│                                                      │
│  設定例: POST https://kyc-service.com/api/verify    │
│  Body: {                                            │
│    "user_id": "{{$.user.sub}}",                    │
│    "document_number": "{{$.request.document_number}}"│
│  }                                                  │
│  → { "status": "approved", "verification_id": "..." }│
└──────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 4: Post Hook（レスポンス加工）                  │
├──────────────────────────────────────────────────────┤
│  - レスポンスデータ変換                               │
│  - エラーハンドリング                                 │
│  - カスタムロジック実行（設定による）                  │
└──────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 5: Transition（ステータス遷移判定）             │
├──────────────────────────────────────────────────────┤
│  - success_condition評価                            │
│    例: "$.status == 'approved'"                     │
│  - next_status決定                                  │
│    例: "verified" / "rejected" / "pending"          │
└──────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 6: Store（結果保存）                           │
├──────────────────────────────────────────────────────┤
│  IdentityVerificationApplication保存                │
│  - application_id（UUID）                           │
│  - status                                           │
│  - execution_result                                 │
│  - created_at/updated_at                            │
└──────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ Phase 7: Response（レスポンス返却）                   │
├──────────────────────────────────────────────────────┤
│  {                                                  │
│    "application_id": "uuid",                       │
│    "status": "verified",                           │
│    "verification_id": "external-id",               │
│    "created_at": "2025-10-13T10:00:00Z"            │
│  }                                                  │
└──────────────────────────────────────────────────────┘
```

### 実装の重要ポイント

#### 1. Fail Fast設計

各フェーズでエラー検出時は即座に処理を中断：

```java
// Phase 1: Request Verification
if (verifyResult.isError()) {
  return IdentityVerificationApplyingResult.requestVerificationError(verifyResult);
}

// Phase 2: Pre Hook
if (resolverResult.isFailFast()) {
  return IdentityVerificationApplyingResult.preHookError(verifyResult, resolverResult);
}

// Phase 3: Execution
if (!executionResult.isOk()) {
  return IdentityVerificationApplyingResult.executionError(verifyResult, executionResult);
}
```

#### 2. Context Builder パターン

各フェーズの結果をコンテキストに蓄積：

```java
IdentityVerificationContextBuilder contextBuilder = buildContext(...);
contextBuilder.additionalParams(resolverResult.getData());  // Pre Hook結果
contextBuilder.executionResult(executionResult.result());   // Execution結果
```

#### 3. HttpRequestExecutor統合

Phase 3で外部API呼び出し：
- JSONPathによるデータマッピング
- OAuth2/HMAC認証
- リトライ処理（指定ステータスコード）
- Idempotency対応

**詳細**: [実装ガイド: HTTP Request Executor](../04-implementation-guides/http-request-executor.md)

---

## EntryService実装

**実装**: [IdentityVerificationApplicationEntryService.java:93](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/IdentityVerificationApplicationEntryService.java#L93)

```java
@Transaction
public class IdentityVerificationApplicationEntryService
    implements IdentityVerificationApplicationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationHandler identityVerificationApplicationHandler;
  UserQueryRepository userQueryRepository;
  UserEventPublisher eventPublisher;

  @Override
  public IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    // 1. Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. 設定取得（テンプレート）
    IdentityVerificationConfiguration configuration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationProcessConfiguration processConfig =
        configuration.getProcess(process);

    // 3. Handler実行（7フェーズ処理）
    IdentityVerificationApplyingResult result =
        identityVerificationApplicationHandler.apply(
            tenant,
            user,
            oAuthToken,
            type,
            process,
            processConfig,
            request,
            requestAttributes);

    // 4. イベント発行
    if (result.isSuccess()) {
      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_success,
          requestAttributes);
    }

    // 5. レスポンス返却
    return result.toResponse();
  }
}
```

**ポイント**:
- ✅ テンプレート駆動（`IdentityVerificationConfiguration`）
- ✅ 7フェーズ処理（`IdentityVerificationApplicationHandler`）
- ✅ HttpRequestExecutor使用（外部API連携）

---

## テンプレート設定例

**Management APIで事前登録**:

```json
{
  "id": "kyc-basic-config-uuid",
  "type": "kyc-basic",
  "processes": {
    "submit": {
      "pre_hook": {
        "enabled": true,
        "http_request": {
          "url": "https://kyc-service.com/api/user-info",
          "method": "GET"
        }
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "https://kyc-service.com/api/verify",
          "method": "POST",
          "auth_type": "oauth2",
          "retry_configuration": {
            "max_retries": 3,
            "retryable_status_codes": [502, 503, 504]
          }
        }
      },
      "transition": {
        "success_condition": "$.status == 'approved'",
        "next_status": "verified"
      }
    }
  }
}
```

---

## E2Eテスト例

```javascript
describe('Identity Verification Application', () => {
  let tenantId = '18ffff8d-8d97-460f-a71b-33f2e8afd41e';
  let accessToken;

  beforeAll(async () => {
    // Access Token取得
    accessToken = ...;

    // Management APIで設定登録
    await axios.post(
      `http://localhost:8080/v1/management/tenants/${tenantId}/identity-verification-configurations`,
      {
        id: uuidv4(),
        type: 'kyc-basic',
        processes: {
          submit: {
            execution: {
              type: 'http_request',
              http_request: {
                url: 'http://localhost:3000/kyc/verify',
                method: 'POST'
              }
            }
          }
        }
      },
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );
  });

  test('should submit identity verification', async () => {
    const response = await axios.post(
      `http://localhost:8080/${tenantId}/v1/me/identity-verification/applications/kyc-basic/submit`,
      {
        name: 'John Doe',
        date_of_birth: '1990-01-01',
        document_type: 'passport',
        document_number: 'AB1234567'
      },
      {
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty('application_id');
    expect(response.data).toHaveProperty('status');
  });
});
```

---

## チェックリスト

身元確認申込み実装時の確認項目：

### 設定（Management API）
- [ ] IdentityVerificationConfiguration作成
- [ ] Process設定（submit/verify等）
- [ ] Execution設定（HttpRequest）
- [ ] Transition設定（ステータス遷移条件）

### EntryService（UseCase層）
- [ ] 設定取得
- [ ] Handler実行（7フェーズ）
- [ ] イベント発行

### E2Eテスト
- [ ] 申込み実行
- [ ] 外部API連携確認
- [ ] リトライ動作確認

---

## 次のステップ

✅ 身元確認申込みの実装を理解した！

### 🔗 詳細情報

- [AI開発者向け: IDA Extension](../../content_10_ai_developer/ai-33-extension-ida.md)
- [実装ガイド: HTTP Request Executor](../../04-implementation-guides/http-request-executor.md)

---

**情報源**: [IdentityVerificationApplicationEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/IdentityVerificationApplicationEntryService.java)
**最終更新**: 2025-10-12
