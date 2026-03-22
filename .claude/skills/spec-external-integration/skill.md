---
name: spec-external-integration
description: 外部サービス連携（External Service Integration）機能の開発・修正を行う際に使用。HTTP Request Executor, MappingRule, OAuth/HMAC認証実装時に役立つ。
---

# 外部サービス連携（External Service Integration）開発ガイド

## ドキュメント

- `documentation/docs/content_03_concepts/06-security-extensions/concept-02-external-service-integration.md` - 外部連携概念

## 機能概要

外部サービス連携は、HTTP経由で外部APIと連携する層。
- **HTTP Request Executor**: リトライロジック付きHTTPクライアント
- **MappingRule**: JSONPath + 変換関数によるデータマッピング
- **認証**: OAuth 2.0, HMAC, Basic認証
- **冪等性**: Idempotency-Keyヘッダー対応
- **Rate Limiting**: Retry-Afterヘッダー対応

## モジュール構成

```
libs/
└── idp-server-platform/                     # プラットフォーム基盤
    └── .../platform/
        ├── http/
        │   ├── HttpRequestExecutor.java
        │   └── retry/
        │       └── RetryStrategy.java
        ├── mapper/
        │   ├── MappingRule.java            # マッピングルール
        │   ├── FunctionSpec.java           # 関数仕様
        │   ├── ConditionSpec.java          # 条件仕様
        │   ├── TypeConverter.java          # 型変換
        │   ├── ObjectCompositor.java       # オブジェクト合成
        │   └── functions/
        │       ├── FormatFunction.java
        │       ├── TrimFunction.java
        │       ├── ReplaceFunction.java
        │       ├── RegexReplaceFunction.java
        │       └── ... (その他のマッピング関数)
        └── auth/
            ├── OAuth2Authenticator.java
            └── HmacAuthenticator.java
```

## MappingRule

`idp-server-platform/mapper/MappingRule.java` 内の実際の構造:

```java
public class MappingRule {
    String from;             // JSONPathソース
    Object staticValue;      // 静的値（fromの代わり）
    String to;              // マッピング先
    List<FunctionSpec> functions;  // 変換関数
    ConditionSpec condition;       // 条件

    public MappingRule(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public MappingRule(
        String from,
        String to,
        List<FunctionSpec> functions
    ) {
        this.from = from;
        this.to = to;
        this.functions = functions;
    }
}
```

## Mapping Rule設定

```json
{
  "mapping_rules": [
    {
      "from": "$.response.user.id",
      "to": "external_user_id"
    },
    {
      "from": "$.response.user.roles",
      "to": "custom_properties.roles",
      "functions": [
        {
          "name": "join",
          "args": {
            "separator": ","
          }
        }
      ]
    }
  ]
}
```

## Mapping Functions

`idp-server-platform/mapper/functions/` 内に実装:

| クラス | 説明 | 使用例 |
|--------|------|--------|
| `FormatFunction` | テンプレート置換 | `{"template": "Bearer {{value}}"}` |
| `TrimFunction` | 空白除去 | - |
| `ReplaceFunction` | 文字列置換 | `{"from": "a", "to": "b"}` |
| `RegexReplaceFunction` | 正規表現置換 | `{"pattern": "...", "replacement": "..."}` |

**使用場所**: Federation（userinfo_mapping_rules）、Identity Verification（mapping_rules）

## 認証設定の内部/外部で異なるマッピングパス

認証設定（authentication-config）の `execution.function` により、`response.body_mapping_rules` で参照できるパスが異なる。

### 内部ビルトイン関数（`email_authentication_challenge`, `email_authentication`, `sms_authentication_challenge` 等）

executor が直接返す `Map<String, Object>` がマッピング対象。

```
マッピングコンテキスト:
  $ → executor の contents() そのもの
```

```json
// 成功時: executor は Map.of() を返す（空）→ static_value で補完
// エラー時: executor は {error, error_description} を返す
"response": {
  "body_mapping_rules": [
    { "static_value": "sent", "to": "status", "condition": { "operation": "missing", "path": "$.error" } },
    { "from": "$.error", "to": "error", "condition": { "operation": "exists", "path": "$.error" } },
    { "from": "$.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.error_description" } }
  ]
}
```

### 外部 HTTP リクエスト（`http_request`）

> **重要**: `oauth_authorization` を使う場合、同じ `http_request` オブジェクトに `"auth_type": "oauth2"` が必須。これが無いと OAuth 認証が実行されない。

executor の結果は `execution_http_request` でラップされる。

```
マッピングコンテキスト:
  $.execution_http_request.status_code → HTTP ステータスコード
  $.execution_http_request.response_headers → レスポンスヘッダー
  $.execution_http_request.response_body → レスポンスボディ
```

```json
// 必要なフィールドだけ明示的にマッピングする（ワイルドカード禁止）
// NG: { "from": "$.execution_http_request.response_body", "to": "*" }
//     → 外部サービスの内部データ（verification_code 等）がクライアントに漏洩するリスク
"response": {
  "body_mapping_rules": [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]
}
```

### 外部 HTTP リクエスト: 送信ボディのマッピングコンテキスト

`execution.http_request.body_mapping_rules`（送信リクエストの組み立て）で参照できるパス:

```
$.request_body          → クライアントからのリクエストボディ
$.request_attributes    → HTTP リクエスト属性
$.interaction           → previous_interaction で取得した前のインタラクションの保存データ
                          ※ $.previous_interaction ではない
```

```json
"http_request": {
  "body_mapping_rules": [
    { "from": "$.request_body", "to": "*" },
    { "from": "$.interaction.transaction_id", "to": "transaction_id" }
  ]
}
```

### http_request_store / previous_interaction パターン

チャレンジ→検証のような2段階フローで、チャレンジの結果を検証時に引き継ぐ:

```json
// Step 1: チャレンジ — レスポンスから transaction_id を保存
"http_request_store": {
  "key": "email-authentication-challenge",
  "interaction_mapping_rules": [
    { "from": "$.response_body.transaction_id", "to": "transaction_id" }
  ]
}

// Step 2: 検証 — 保存した transaction_id を送信ボディに注入
"previous_interaction": { "key": "email-authentication-challenge" },
"http_request": {
  "body_mapping_rules": [
    { "from": "$.request_body", "to": "*" },
    { "from": "$.interaction.transaction_id", "to": "transaction_id" }
  ]
}
```

`interaction_mapping_rules` の `$.response_body` は外部サービスの生レスポンスを参照する（`$.execution_http_request` ではない）。

## HTTP Request Executor

`idp-server-platform/http/` 内:

HTTP Request Executorは、リトライロジックとRate Limiting対応を提供します。

### 例外 → HTTP ステータスコードマッピング

外部HTTP通信でネットワーク例外が発生した場合、`HttpResponseResolver.mapExceptionToStatusCode()` が適切なHTTPステータスコードに変換する。

| 例外 | ステータスコード | 意味 | リトライ対象 |
|------|----------------|------|------------|
| `ConnectException` | **503** Service Unavailable | 接続確立不可（ホスト到達不能、ポート閉鎖等） | はい |
| `SocketTimeoutException` | **504** Gateway Timeout | ソケットレベルのタイムアウト | はい |
| `HttpTimeoutException` | **504** Gateway Timeout | HTTPクライアントレベルのタイムアウト | はい |
| `InterruptedException` | **503** Service Unavailable | スレッド中断 | はい |
| `IOException` | **502** Bad Gateway | その他のI/Oエラー | はい |
| その他の例外 | **500** Internal Server Error | 予期しないエラー | いいえ |

**レスポンスボディ**: エラー時は以下の構造で返却される:
```json
{
  "error": "network_error",
  "error_description": "例外メッセージ",
  "exception_type": "ConnectException",
  "retry_info": {
    "retryable": true,
    "reason": "connection_failed",
    "category": "network_connectivity"
  }
}
```

**実装クラス**: `HttpResponseResolver`（`idp-server-platform/.../http/HttpResponseResolver.java`）

### リトライ設定

`retry_configuration` で外部API呼び出しのリトライ動作を制御する。

```json
{
  "retry_configuration": {
    "max_retries": 3,
    "retryable_status_codes": [502, 503, 504],
    "idempotency_required": true,
    "backoff_delays": ["PT1S", "PT2S", "PT4S"]
  }
}
```

| 設定 | 説明 |
|------|------|
| `max_retries` | 最大リトライ回数 |
| `retryable_status_codes` | リトライ対象のHTTPステータスコード（上記の例外マッピング結果も対象） |
| `idempotency_required` | `true` の場合 `Idempotency-Key` ヘッダーを自動付与 |
| `backoff_delays` | リトライ間隔（ISO 8601 Duration） |

### OAuth 401/403 自動リトライ

外部APIが401または403を返した場合、OAuthトークンを再取得して自動リトライする（`HttpRequestExecutor` 内蔵）。

## E2Eテスト

```
e2e/src/tests/
└── (外部連携は各機能のテスト内で検証)
    ├── integration/ida/           # Identity Verification外部連携
    └── usecase/advance/          # Federation外部連携
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-platform:compileJava

# テスト
cd e2e && npm test -- integration/ida/
cd e2e && npm test -- usecase/advance/
```

## トラブルシューティング

### HTTP Request失敗
- URLが正しいか確認
- 認証情報（OAuth, HMAC）を確認

### MappingRuleが動作しない
- JSONPath (`from`) が正しいか確認
- ソースデータの構造を確認
- FunctionSpecの設定を確認

### 変換関数が失敗
- 関数名が正しいか確認（FormatFunction, TrimFunction等）
- 関数のargsが正しいか確認
