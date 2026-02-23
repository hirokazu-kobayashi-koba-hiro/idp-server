# 外部サービス連携

> **関連ドキュメント**
> - [HTTP Request Executor](impl-16-http-request-executor.md) - リトライ制御と認証設定
> - [Mapping Functions 開発ガイド](impl-20-mapping-functions.md) - データ変換の詳細（19個のFunction）

## このドキュメントの目的

**HttpRequestExecutor**と**Mapping Functions**を使って、外部APIと連携する機能を実装できるようになることが目標です。

### 所要時間
⏱️ **約30分**（実装 + テスト）

### 前提知識
- [03. 共通実装パターン](../03-common-patterns.md)
- [Mapping Functions 開発ガイド](impl-20-mapping-functions.md) - リクエスト/レスポンス変換

---

## HttpRequestExecutorとは

外部HTTPサービスと連携するための統合クライアント。

**機能**:
- ✅ HTTP GET/POST/PUT/DELETE
- ✅ OAuth 2.0認証
- ✅ HMAC認証
- ✅ リトライ・タイムアウト設定
- ✅ 冪等性保証

---

## 実装例: 外部身元確認サービス連携

外部の身元確認API（KYC: Know Your Customer）と連携する例。

---

## Step 1: HttpRequest設定作成

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/identity/verification/ExternalVerificationService.java`

```java
package org.idp.server.core.identity.verification;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.httpclient.*;
import org.idp.server.platform.converter.JsonConverter;

public class ExternalVerificationService {

  private final HttpRequestExecutor httpRequestExecutor;
  private final JsonConverter converter = JsonConverter.snakeCaseInstance();

  public ExternalVerificationService(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  /**
   * 外部APIで身元確認実行
   *
   * @param user ユーザー情報
   * @return 確認結果
   */
  public VerificationResult verify(User user) {
    // 1. リクエストボディ作成
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("user_id", user.identifier().value());
    requestBody.put("name", user.name());
    requestBody.put("email", user.email().value());
    requestBody.put("date_of_birth", user.dateOfBirth());

    // 2. HttpRequest設定
    HttpRequest httpRequest = HttpRequest.builder()
        .url("https://external-kyc-service.com/api/v1/verify")
        .method(HttpMethod.POST)
        .headers(Map.of(
            "Content-Type", "application/json",
            "Accept", "application/json"
        ))
        .body(converter.write(requestBody))
        .build();

    // 3. 認証設定（OAuth 2.0）
    OAuth2Configuration oAuth2Config = OAuth2Configuration.builder()
        .tokenEndpoint("https://external-kyc-service.com/oauth/token")
        .clientId("your-client-id")
        .clientSecret("your-client-secret")
        .scope(Set.of("kyc:verify"))
        .build();

    // 4. リトライ設定
    RetryConfiguration retryConfig = RetryConfiguration.builder()
        .maxRetries(3)
        .retryableStatusCodes(Set.of(502, 503, 504))
        .idempotencyRequired(true)
        .backoffDelays(List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4)
        ))
        .build();

    // 5. HTTP実行設定
    HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
        .httpRequest(httpRequest)
        .oauth2Configuration(oAuth2Config)
        .retryConfiguration(retryConfig)
        .timeout(Duration.ofSeconds(30))
        .build();

    // 6. 実行
    HttpRequestResult result = httpRequestExecutor.execute(executionConfig);

    // 7. レスポンス解析
    if (result.isSuccess()) {
      Map<String, Object> responseBody = converter.read(result.responseBody());
      String status = (String) responseBody.get("status");
      String verificationId = (String) responseBody.get("verification_id");

      return VerificationResult.success(verificationId, status);
    } else {
      return VerificationResult.failure(result.errorMessage());
    }
  }
}
```

---

## Step 2: 認証パターン

### OAuth 2.0認証

```java
OAuth2Configuration oAuth2Config = OAuth2Configuration.builder()
    .tokenEndpoint("https://external-service.com/oauth/token")
    .clientId("your-client-id")
    .clientSecret("your-client-secret")
    .scope(Set.of("api:read", "api:write"))
    .grantType("client_credentials")  // デフォルト: client_credentials
    .build();
```

### HMAC認証

```java
HmacAuthenticationConfiguration hmacConfig = HmacAuthenticationConfiguration.builder()
    .secretKey("your-hmac-secret-key")
    .algorithm("HmacSHA256")
    .headers(Set.of("X-Request-Timestamp", "X-Request-Body"))
    .build();

HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
    .httpRequest(httpRequest)
    .hmacAuthenticationConfiguration(hmacConfig)
    .build();
```

### 認証なし

```java
HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
    .httpRequest(httpRequest)
    // 認証設定なし
    .build();
```

---

## Step 3: リトライ戦略

### 基本リトライ

```java
RetryConfiguration retryConfig = RetryConfiguration.builder()
    .maxRetries(3)  // 最大3回リトライ
    .retryableStatusCodes(Set.of(502, 503, 504))  // リトライ対象ステータス
    .backoffDelays(List.of(
        Duration.ofSeconds(1),   // 1回目: 1秒待機
        Duration.ofSeconds(2),   // 2回目: 2秒待機
        Duration.ofSeconds(4)    // 3回目: 4秒待機
    ))
    .build();
```

### 冪等性保証

```java
RetryConfiguration retryConfig = RetryConfiguration.builder()
    .maxRetries(3)
    .idempotencyRequired(true)  // ✅ 冪等性保証（Idempotency-Keyヘッダー自動付与）
    .retryableStatusCodes(Set.of(502, 503, 504))
    .backoffDelays(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)))
    .build();
```

**重要**: `idempotencyRequired = true`の場合、HttpRequestExecutorが自動的に`Idempotency-Key`ヘッダーを付与します。

---

## Step 4: E2Eテスト（Mockoon使用）

### Mockoon設定

**ファイル**: `e2e/mockoon/external-kyc-service.json`

```json
{
  "routes": [
    {
      "method": "POST",
      "endpoint": "/api/v1/verify",
      "responses": [
        {
          "statusCode": 200,
          "headers": {
            "Content-Type": "application/json"
          },
          "body": {
            "status": "approved",
            "verification_id": "{{uuid}}",
            "verified_at": "{{now 'yyyy-MM-dd'T'HH:mm:ss'Z'}}"
          }
        }
      ]
    },
    {
      "method": "POST",
      "endpoint": "/api/v1/verify",
      "responses": [
        {
          "statusCode": 503,
          "headers": {
            "Content-Type": "application/json"
          },
          "body": {
            "error": "service_unavailable",
            "error_description": "Service temporarily unavailable"
          },
          "rules": [
            {
              "target": "header",
              "modifier": "X-Test-Retry",
              "value": "true"
            }
          ]
        }
      ]
    }
  ]
}
```

### E2Eテスト

**ファイル**: `e2e/spec/integration/external-verification.spec.js`

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('External Verification Integration', () => {
  let accessToken;
  let tenantId;
  let userId;

  beforeAll(async () => {
    // トークン取得
    const tokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'admin-client',
      client_secret: 'admin-secret',
      scope: 'identity:verify'
    });
    accessToken = tokenResponse.data.access_token;

    // テナント・ユーザー作成
    tenantId = uuidv4();
    userId = uuidv4();
  });

  test('should verify user with external KYC service', async () => {
    const response = await axios.post(
      `http://localhost:8080/v1/identity/verification`,
      {
        user_id: userId,
        name: 'John Doe',
        email: 'john@example.com',
        date_of_birth: '1990-01-01'
      },
      {
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty('status', 'approved');
    expect(response.data).toHaveProperty('verification_id');
  });

  test('should retry on 503 error', async () => {
    const startTime = Date.now();

    const response = await axios.post(
      `http://localhost:8080/v1/identity/verification`,
      {
        user_id: userId,
        name: 'Jane Doe',
        email: 'jane@example.com',
        date_of_birth: '1992-05-15'
      },
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'X-Test-Retry': 'true'  // Mockoonでリトライトリガー
        }
      }
    );

    const duration = Date.now() - startTime;

    // リトライ実行確認（合計待機時間: 1 + 2 + 4 = 7秒以上）
    expect(duration).toBeGreaterThanOrEqual(7000);
    expect(response.status).toBe(200);
  });
});
```

---

## チェックリスト

外部サービス連携実装前に以下を確認：

### HttpRequest設定
- [ ] URL指定
- [ ] HTTPメソッド指定（GET/POST/PUT/DELETE）
- [ ] ヘッダー設定（Content-Type, Accept等）
- [ ] リクエストボディ設定（POST/PUT）

### 認証設定
- [ ] OAuth 2.0設定（トークンエンドポイント、Client ID/Secret、スコープ）
- [ ] HMAC設定（秘密鍵、アルゴリズム）
- [ ] 認証なし（公開API）

### リトライ設定
- [ ] 最大リトライ回数
- [ ] リトライ対象ステータスコード（502, 503, 504等）
- [ ] バックオフ遅延（指数バックオフ推奨）
- [ ] 冪等性保証（`idempotencyRequired`）

### エラーハンドリング
- [ ] `HttpRequestResult.isSuccess()`チェック
- [ ] レスポンス解析（JSON → ドメインモデル）
- [ ] エラーレスポンス処理

---

## よくあるエラー

### エラー1: タイムアウト設定なし

```java
// ❌ 間違い: タイムアウト設定なし（無限待機リスク）
HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
    .httpRequest(httpRequest)
    .build();

// ✅ 正しい: タイムアウト設定
HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
    .httpRequest(httpRequest)
    .timeout(Duration.ofSeconds(30))  // ✅ 30秒タイムアウト
    .build();
```

### エラー2: リトライ設定誤り

```java
// ❌ 間違い: 冪等性保証なしでPOSTリトライ（重複リスク）
RetryConfiguration retryConfig = RetryConfiguration.builder()
    .maxRetries(3)
    .retryableStatusCodes(Set.of(502, 503, 504))
    .idempotencyRequired(false)  // ❌ POST/PUT/DELETEで危険
    .build();

// ✅ 正しい: 冪等性保証付き
RetryConfiguration retryConfig = RetryConfiguration.builder()
    .maxRetries(3)
    .retryableStatusCodes(Set.of(502, 503, 504))
    .idempotencyRequired(true)  // ✅ Idempotency-Key自動付与
    .build();
```

---

## 次のステップ

✅ 外部サービス連携をマスターした！

### 📖 次に読むべきドキュメント

1. [Phase 3: 既存ファイル拡充](../DEVELOPER_GUIDE_TOC.md#phase-3-既存機能ガイド拡充済み) - 技術詳細リンク追加

### 🔗 詳細情報

- [developer-guide: HTTP Request Executor詳細](../developer-guide/http-request-executor.md)

---

**情報源**: [HttpRequestExecutor.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/httpclient/HttpRequestExecutor.java)
**最終更新**: 2025-10-12
