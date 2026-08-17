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
                          ip_address / user_agent / resource / action / request_url / headers
$.interaction           → previous_interaction で取得した前のインタラクションの保存データ
                          ※ $.previous_interaction ではない
$.user                  → 認証済みユーザーの許可リスト投影（external-api-authentication のみ）
```

**送信ボディでは** `$.user` は `external-api-authentication` の interaction でのみ注入される（`ExternalApiAuthenticationInteractor:204` の `setTransactionUser` が唯一の設定元）。他の interactor（password 等）では**キー自体が存在しない**ため、書いても解決しない。`user_mapping_rules` は注入条件が違う（後述）。

公開されるのは `sub` / `provider_id` / `email` / `phone_number` / `name` / `given_name` / `family_name` / `middle_name` / `roles` / `custom_properties` のみ。パスワードハッシュ・認証情報・`verified_claims` は含まれない。1要素目ではユーザーが未確立のため空になる。

```json
"http_request": {
  "body_mapping_rules": [
    { "from": "$.user.sub", "to": "user_id" },
    { "from": "$.user.custom_properties.rank", "to": "rank" }
  ]
}
```

```json
"http_request": {
  "body_mapping_rules": [
    { "from": "$.request_body", "to": "*" },
    { "from": "$.interaction.transaction_id", "to": "transaction_id" }
  ]
}
```

### `user_resolve.user_mapping_rules` の `$.user`

解決するユーザーの組み立て（`user_resolve.user_mapping_rules`）でも同じ投影を参照できるが、**注入されるかどうかは要素の位置で決まる**。送信ボディと違い、`external-api-authentication` 限定ではない。

| 位置 | `$.user` | 中身 | 実装 |
|------|---------|------|------|
| 1要素目 pass1（検索キーの生成） | なし | — | — |
| 1要素目 pass2（属性の取り込み） | あり | 見つかった既存ユーザー | `ExternalApiAuthenticationInteractor:481` / `PasswordAuthenticationInteractor:581` |
| 2要素目の enrichment | あり | 認証済みユーザー | `PasswordAuthenticationInteractor:503`（`password-authentication`） |
| 2要素目の同一性照合 | **なし** | — | `ExternalApiAuthenticationInteractor#handleSecondFactor` |

1要素目は同じルールが2回実行され、採用されるのは pass2 の結果。検索キー（`provider_id` / `external_user_id`）だけは pass1 の値で固定されるため、`$.user` から組み立てても「探したキー」と「保存するキー」は食い違わない。`uuid4` / `now` / `random_string` も2回実行され、採用されるのは2回目の値。

2要素目の同一性照合に `$.user` が入らないのは意図的。`identity_match_field` の被検体を `$.user` から作れると照合が自己参照になり、外部APIが誰を返しても一致してしまう（CWE-287）。原則は「**同一性の判断材料は `$.user` から作らない / 属性の取り込みには使う**」。

### 解決結果は既存ユーザーがベース（#1792）

既存ユーザーが見つかった場合、1要素目の解決結果は `existingUser.enrichWith(mapped)`。マッピングの出力単体ではない。この結果が認可グラントに写し取られトークンのクレームになるため、出力単体にすると外部が返さなかった属性（`custom_properties` / `roles` / `verified_claims`）がそのセッションのトークンから欠落する（DB と UserInfo は正常なので気づきにくい）。

`status` は既存値で明示的に固定する。`updateWith` が `patchUser.hasStatus() ? patch : this.status` なので、固定しないとマッピングが `status` を出せば `LOCKED` を復活させられてしまう。

適用対象は外部ソースで1要素目を解決する4経路。

| 経路 | 実装 |
|------|------|
| フェデレーション | `OidcFederationInteractor#resolveUser` |
| 外部API認証 | `ExternalApiAuthenticationInteractor#resolveUser` |
| 外部パスワード認証 | `PasswordAuthenticationInteractor#resolveUserFromExternalAuth` |
| 外部トークン認証 | `ExternalTokenAuthenticationInteractor#interact` |

既存ユーザーの読み取りは1要素目、DB保存は認可成立後（`OAuthFlowEntryService:499`）。その間に管理APIで同じユーザーを更新すると認証開始時点の値で巻き戻る窓がある。

詳細: `documentation/docs/content_03_concepts/03-authentication-authorization/concept-11-user-resolution.md`

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

### レスポンス解決（response_resolve_configs）

外部APIの HTTP ステータスとレスポンスボディの内容から、idp-server 内部のステータスコードを導出する。「HTTP は 200 だがボディが失敗を示す」外部サービス（例: イントロスペクションが `active: false`）を失敗として扱いたい場合に使う。

- **スキーマは配列形式 `[...]`** が正準。`authentication-configurations` と `identity-verification-configurations` で**共通**（#1500 で統一）。`toMap()`／GET 出力も配列。
- **後方互換**: #1500 以前に保存された旧ラッパー形式 `{"configs": [...]}` も読み込み可能。`JsonConverter` 中央登録の `HttpResponseResolveConfigsDeserializer`（読み＝両形式受理）と `HttpResponseResolveConfigsSerializer`（書き＝常に配列出力）のペアにより、**入力・GET・DB保存のすべてが配列**に統一される。旧wrapperで保存済みのデータも、設定の再保存時に配列へ自動正規化（遅延移行）。新規は配列で書くこと。
- 各エントリは先頭から順に評価され、**最初にマッチしたもの**の `mapped_status_code` が結果ステータスになる（`HttpResponseResolver.findMatchingConfig()`）。どれもマッチしなければ実レスポンスのステータスがそのまま使われる。
- 配置場所は `http_request` オブジェクト直下（`retry_configuration` や `body_mapping_rules` と同階層）。

```json
"http_request": {
  "url": "https://api.example.com/introspect",
  "method": "POST",
  "response_resolve_configs": [
    {
      "conditions": [
        { "path": "$.response_body.active", "operation": "eq", "value": false }
      ],
      "match_mode": "ALL",
      "mapped_status_code": 401
    }
  ]
}
```

| フィールド | 説明 |
|-----------|------|
| `conditions` | 条件リスト（`ConditionDefinition`: `path` / `operation` / `value`） |
| `match_mode` | `ALL`（AND）または `ANY`（OR）。大文字小文字を問わず、未指定時は `ALL` |
| `mapped_status_code` | マッチ時に割り当てる内部ステータスコード |

**条件 `path` の評価コンテキスト**（`HttpResponseResolver.createResultContext()` が snake_case で構築）:

| パス | 内容 |
|------|------|
| `$.status_code` | 外部レスポンスの HTTP ステータスコード |
| `$.response_headers.*` | レスポンスヘッダー |
| `$.response_body.*` | レスポンスボディ（配列ボディはリストとして展開） |

- `mapped_status_code` が 400 以上かつ実ステータスと異なる場合、warn ログが出力される。
- 認証インタラクション（`function: http_request`）では、解決後ステータスが 400 以上だと `AuthenticationExecutionResult.error(status, ...)` となり、その**ステータスがクライアントへ伝播**する。

**実装クラス**: `HttpResponseResolveConfig` / `HttpResponseResolveConfigs`（内部ラッパー、フィールドは `configs`） / `HttpResponseResolver`（`idp-server-platform/.../http/`）

## 複数API連鎖（http_requests）

単一の `http_request` に加え、`http_requests`（複数形）で複数APIを順次呼び出すことができる。

### 単一 vs 連鎖

| 設定キー | 用途 |
|---------|------|
| `http_request` | 1つのAPIを呼び出す |
| `http_requests` | 複数のAPIを順次呼び出す（前のレスポンスを次のリクエストで参照可能） |

### 設定例: 認証 → ユーザー詳細取得の2段階

```json
{
  "http_requests": [
    {
      "url": "https://api.example.com/auth",
      "method": "POST",
      "body_mapping_rules": [
        { "from": "$.request_body.email", "to": "username" },
        { "from": "$.request_body.password", "to": "password" }
      ]
    },
    {
      "url": "https://api.example.com/users/me",
      "method": "GET",
      "header_mapping_rules": [
        {
          "from": "$.execution_http_requests[0].response_body.token",
          "to": "Authorization",
          "functions": [{ "name": "format", "args": { "template": "Bearer {{value}}" } }]
        }
      ]
    }
  ]
}
```

### Cross-step参照パス

前のAPIのレスポンスを次のAPIで参照する場合:

```
$.execution_http_requests[0].status_code        → 1番目のAPIのステータスコード
$.execution_http_requests[0].response_body.*    → 1番目のAPIのレスポンスボディ
$.execution_http_requests[0].response_headers.* → 1番目のAPIのレスポンスヘッダー
$.execution_http_requests[1].response_body.*    → 2番目のAPIのレスポンスボディ
```

### 認証結果は interaction ごとに記録（#1771）

`external-api-authentication` は1設定に複数 interaction を持つため、結果は**合計 + 内訳**の2階層。

```json
"external-api-authentication": {
  "success_count": 3,                                    // 全 interaction の合計（従来どおり）
  "interactions": {
    "step-a": { "success_count": 1, "failure_count": 0, "call_count": 1 },
    "step-b": { "success_count": 2, "failure_count": 0, "call_count": 2 }
  }
}
```

ポリシーからは `$.external-api-authentication.interactions.step-a.success_count`（ドット記法でOK）。合計だけで `gte 3` を書くと**1つを3回呼んでも成立**するため、多段フローで各段を必須にするなら内訳を使う。

- 未実行の interaction はキーが無い → path 未解決 → null → `gte` は false（fail closed、#1646）
- 内訳を持つのは `external-api-authentication` のみ。他は 1 interactor = 1 要素なので `interactions` キー自体が出ない
- 刻印は `ExternalApiAuthenticationInteractor#interact`（ラッパー）で出口1箇所。return が12箇所あるため各所に書くと欠落する
- 復元経路は**2つ**（`AuthenticationInteractionResults#fromMap` = OPSession / `ModelConverter#toAuthenticationInteractionResults` = 認証トランザクションDB）。片方だけ直すとリクエストをまたいだ時点で内訳が消える

### 条件付き実行（#1789）

各リクエストに `condition`（`ConditionSpec`）を書くと、false のときそのリクエストを**送らずにスキップ**する。前段の応答で後段を呼ぶか決められる。

```json
"http_requests": [
  { "url": "https://api.example.com/assess", "method": "POST" },
  {
    "url": "https://api.example.com/notify",
    "method": "POST",
    "condition": {
      "operation": "eq",
      "path": "$.execution_http_requests[0].response_body.result",
      "value": "HIGH"
    }
  }
]
```

`condition` 未指定は無条件実行（後方互換）。演算子一覧・fail-closed 挙動の正典は `documentation/docs/content_06_developer-guide/04-implementation-guides/advanced/condition-spec.md`。

**コンテキストは「常にある」とは限らない。**

| パス | 存在条件 | 認証 | federation |
|------|---------|:----:|:----------:|
| `$.request_body` | 常に | ✅ | ✅（`{access_token}` のみ）|
| `$.request_attributes` | 常に | ✅ | ✕ |
| `$.user` | **`external-api-authentication` の interaction のみ** かつユーザー確立時 | △ | ✕ |
| `$.interaction` | `previous_interaction` 設定時のみ | ✅ | ✕ |
| `$.execution_http_requests` | **2本目以降のリクエストのみ** | ✅ | ✅ |

`$.request_body` / `$.request_attributes` は最初からあるので **1本目を条件付きにできる**（e2e 実測済み）。一方 `$.execution_http_requests` を1本目で書くと常に null → 常にスキップ（#1646 で無言）。federation は条件側が `$.execution_http_requests`、`userinfo_mapping_rules` 側が `$.userinfo_execution_http_requests` で**接頭辞が違う**。

`$.user` は他の2つと性質が違う。executor が入れるのではなく **interactor が入れる**（`setTransactionUser` の呼び出しは `ExternalApiAuthenticationInteractor:205` の1箇所のみ）。したがって `external-token` / `fido-uaf` / `sms` / `email` の chain には**一度も入らない**。要素の位置ではなく **どの interactor か** で決まる。

`external-api-authentication` の中でも、投影が空なら `hasTransactionUser()`（`isEmpty` 判定）が false でキーごと入らない。よって演算子ごとに倒れ方が変わる。

| 条件 | 未確立 / 非対応 interactor | 確立済み（external-api）|
|------|:------------------------:|:---------------------:|
| `exists $.user.sub` | スキップ | 実行 |
| `missing $.user.sub` | 実行 | スキップ |
| `eq $.user.email` | スキップ | 値で判定 |

確立していれば chain の1本目からでも使える。値の中身もそのまま `body_mapping_rules` に載せられる（e2e `integration-04` は登録メールのエコーを固定。`external-api-authentication` で書いてあるのはこの理由）。

**全スキップはエラー。** 設定した全リクエストがスキップされたら 500 で失敗する（`nothingRan()` / `noExecutionResult()`）。executor の実行そのものが検証なので、1本も走っていなければ何も検証できていない。条件のパスを1文字間違えるだけで「外部に問い合わせず認証ステップが通る」状態になるため、成功にはしない。空の `http_requests` は対象外（条件が無ければ全スキップは起こりえないので後方互換の影響もゼロ）。一部スキップの通常分岐は影響なし。

**HTTP エラーのガードには不要。** 前段が 4xx/5xx なら後段はもともと実行されない（早期終了）。`condition` の出番は「前段は成功扱いだが後段を呼びたくない」場合のみ。「200 だがボディが業務エラー」は `response_resolve_configs`（interaction 全体を失敗にする）と `condition`（成功のまま後段だけ省く）の使い分け。

**添字はスキップしても詰まらない。** `execution_http_requests[N]` は「**設定の N 番目**」であって「実行された N 番目」ではない。スキップされた枠には `{"skipped": true}` が入る。

```
設定: [A, B(条件false), C]
  $.execution_http_requests[0] → A の結果
  $.execution_http_requests[1] → {"skipped": true}
  $.execution_http_requests[2] → C の結果   ← 条件の真偽で位置が変わらない
```

詰める設計にすると、条件が false のときだけ後続の mapping が別のリクエストを読むことになり、しかも外れた JSONPath は例外でなく null になる（#1646）ので誰も気づけない。

| 適用される executor | 設定キー |
|---|---|
| 認証 interaction（`HttpRequestsAuthenticationExecutor`）| `execution.http_requests[]` |
| federation userinfo（`UserinfoHttpRequestsExecutor`）| `userinfo_execution.http_requests[]` |

`condition` は共有クラス `HttpRequestExecutionConfig` のフィールドなので**単発の `http_request` にも書けてしまうが、そこでは無視される**（分岐する相手がいないため）。

#### 分岐（A のあと B か C）— マッピング側にもガードが要る

排他条件で片方だけ実行できるが、**両方の枠が残る**ため同じ `to` に書くと壊れる。`writeResult` は後勝ちかつ null も無条件 put（`MappingRuleObjectMapper:326`）なので、スキップ側のルールが実行側の結果を null で上書きする。

```json
// http_requests: [probe, B(eq HIGH), C(ne HIGH)]
"body_mapping_rules": [
  { "from": "$.execution_http_requests[1].response_body.decision", "to": "decision",
    "condition": { "operation": "missing", "path": "$.execution_http_requests[1].skipped" } },
  { "from": "$.execution_http_requests[2].response_body.decision", "to": "decision",
    "condition": { "operation": "missing", "path": "$.execution_http_requests[2].skipped" } }
]
```

`{"skipped": true}` プレースホルダは、この「その枠は実行されたか」の判定手段でもある（詰める設計だとこのレシピが書けない）。ガード無しで null 上書きが起きることは e2e `integration-04` の `decision_unguarded` で固定済み。

:::warning 条件の評価に失敗するとスキップされる
`ConditionSpec.evaluate()` は例外時に warn ログを出して `false` を返す。パス誤り・型不一致などで評価が壊れると「実行しない」に倒れる。無条件実行に倒れるより安全だが、設定ミスが**静かなスキップ**として現れる点に注意。
:::

### レスポンスマッピング

`http_requests` の場合、`response.body_mapping_rules` では全ステップの結果を参照できる:

```json
"response": {
  "body_mapping_rules": [
    { "from": "$.execution_http_requests[1].response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_requests[1].response_body.email", "to": "email" },
    { "from": "$.execution_http_requests[1].response_body.name", "to": "name" }
  ]
}
```

### 動的ヘッダー生成関数

header_mapping_rules で使える関数:

| 関数 | 用途 | 例 |
|------|------|-----|
| `format` | テンプレート置換 | `"Bearer {{value}}"` |
| `base64` | Base64 エンコード（RFC 4648） | Basic 認証ヘッダーの組み立て |
| `random_string` | ランダム文字列生成 | リクエストID、Nonce |
| `now` | 現在時刻 | タイムスタンプ |

`auth_type` に `basic` は無い。Basic 認証は `base64` + `format` で組み立てる。設定には生の資格情報を置ける。

```json
{
  "static_value": "<client_id>:<client_secret>",
  "to": "Authorization",
  "functions": [
    { "name": "base64" },
    { "name": "format", "args": { "template": "Basic {{value}}" } }
  ]
}
```

`base64` の引数は `url_safe`（既定 `false`）/ `padding`（既定 `true`）/ `charset`（既定 `UTF-8`）。`url_safe: true` + `padding: false` が Base64URL。

> `auth_type` が `none` 以外だと、`oauth2` は Bearer トークン、`hmac_sha256` は署名値で `Authorization` を**上書きする**。Basic を使う場合は `auth_type` を `none` にすること。

### エラーハンドリング

連鎖リクエストでは、途中のAPIが失敗した場合に後続のAPIは実行されない（早期終了）。`response.body_mapping_rules` の `condition` で成功/失敗を判定する:

```json
{
  "condition": { "operation": "eq", "path": "$.execution_http_requests[0].status_code", "value": 200 }
}
```

### ワイルドカード展開

`"to": "*"` でリクエストボディ全体をそのまま転送する:

```json
{ "from": "$.request_body", "to": "*" }  // クライアントのリクエストをそのまま外部APIに転送
```

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
