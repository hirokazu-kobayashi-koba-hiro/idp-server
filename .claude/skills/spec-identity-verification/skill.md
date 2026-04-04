---
name: spec-identity-verification
description: 身元確認（Identity Verification/IDA）機能の開発・修正を行う際に使用。eKYC連携、本人確認フロー、設定管理APIの実装時に役立つ。
---

# 身元確認（Identity Verification）機能 開発ガイド

## ドキュメント

詳細なドキュメントは以下を参照：
- `documentation/docs/content_05_how-to/phase-4-extensions/identity-verification/`
  - `01-guide.md` - ガイド
  - `02-application.md` - アプリケーション詳細
  - `03-registration.md` - 登録

## 機能概要

身元確認機能（IDA: Identity Assurance）は、外部サービスと連携してユーザーの本人確認を行う機能。
eKYCサービス、本人確認書類のOCR、顔認証などの外部APIと連携し、身元確認フローを実行する。

## モジュール構成

```
libs/
├── idp-server-core-extension-ida/     # 身元確認コアロジック
│   └── .../identity/verification/
│       ├── configuration/             # 設定クラス
│       │   ├── IdentityVerificationConfiguration.java
│       │   └── process/               # プロセス設定
│       ├── application/               # アプリケーション実行
│       │   └── execution/             # 外部API実行
│       ├── io/                        # 入出力
│       └── repository/                # リポジトリIF
│
├── idp-server-control-plane/          # 管理API
│   └── .../management/identity/verification/
│       ├── *ManagementApi.java
│       └── handler/
│
└── idp-server-core-adapter/           # DB実装
    └── .../identity/verification/
        ├── config/
        ├── application/
        └── result/
```

## 設定構造（JSON）

```json
{
  "id": "uuid",
  "type": "ekyc_type",
  "enabled": true,
  "attributes": {},
  "common": {
    "auth_type": "oauth|hmac|basic"
  },
  "processes": {
    "process_name": {
      "request": { "schema": {} },
      "pre_hook": {},
      "execution": { "http_request": {}, "mock": {} },
      "post_hook": {},
      "transition": { "approved": {}, "rejected": {} },
      "store": { "application_details_mapping_rules": [] },
      "response": { "body_mapping_rules": [] }
    }
  },
  "result": {
    "verified_claims_mapping_rules": []
  }
}
```

> **重要**: `result` セクションは `processes` の外側（トップレベル）に配置する。プロセス内ではない。

## 処理フロー

```
[リクエスト] → [request] スキーマ検証
            → [pre_hook] 事前処理
            → [execution] 外部API実行
            → [post_hook] 事後処理
            → [transition] 状態遷移
            → [store] 結果保存（mapping_rules）
            → [response] レスポンス生成
```

## 主要クラス

| クラス | 役割 |
|-------|------|
| `IdentityVerificationConfiguration` | メイン設定 |
| `IdentityVerificationProcessConfiguration` | プロセス設定 |
| `IdentityVerificationStoreConfig` | 結果保存設定 |
| `IdentityVerificationResponseConfig` | レスポンス設定 |
| `IdentityVerificationApplicationHandler` | アプリケーションハンドラー |
| `IdentityVerificationConfigManagementHandler` | 設定管理ハンドラー |

## APIアクセススコープ

身元確認のエンドユーザー向けAPI（`/v1/me/identity-verification/`）には、Spring Security でスコープ制限が設定されている。

| エンドポイント | メソッド | 必要スコープ |
|--------------|---------|-------------|
| `/v1/me/identity-verification/applications/{type}/{process}` | POST | `identity_verification_application` |
| `/v1/me/identity-verification/applications/{type}/{id}/{process}` | POST | `identity_verification_application` |
| `/v1/me/identity-verification/applications` | GET | `identity_verification_application` |
| `/v1/me/identity-verification/applications/{type}/{id}` | DELETE | `identity_verification_application_delete` |
| `/v1/me/identity-verification/results` | GET | スコープ制限なし（要対応: Issue #1319） |

**設定要件**:
- 認可サーバーの `scopes_supported` に `identity_verification_application` を含めること
- クライアントの `scope` に `identity_verification_application` を含めること
- ユーザーがこのスコープを含むアクセストークンを取得していること

**参照**: `SecurityConfig.java`, `IdPApplicationScope.java`

## API規約

APIレスポンスのJSONキーは**snake_case**を使用：
- `body_mapping_rules` (NOT `bodyMappingRules`)
- `application_details_mapping_rules`
- `pre_hook`, `post_hook`, `http_request`

`toMap()`メソッドのキー名は必ずsnake_caseで出力すること。

## Discovery設定

eKYC機能を有効にするには、認可サーバーに以下の設定が必要:

- `verified_claims_supported: true` — Discoveryで身元確認サポートを公開
- `identity_verification_application` スコープ — `/v1/me/identity-verification/applications` APIへのアクセスに必要

## マッピング関数リファレンス

`body_mapping_rules` / `header_mapping_rules` で使用可能な関数。外部API連携（eKYC, SMS, Email等）全般で共通。

### 文字列操作

| 関数 | 用途 | 例 |
|------|------|-----|
| `trim` | 前後の空白除去 | — |
| `case` | 大文字/小文字変換 | `{"name":"case","args":{"mode":"upper"}}` |
| `replace` | 文字列置換 | `{"name":"replace","args":{"target":"-","replacement":""}}` |
| `regex_replace` | 正規表現置換 | `{"name":"regex_replace","args":{"pattern":"\\d+","replacement":"*"}}` |
| `substring` | 部分文字列抽出 | `{"name":"substring","args":{"start":0,"end":10}}` |
| `format` | テンプレート置換 | `{"name":"format","args":{"template":"Bearer {{value}}"}}` |

### コレクション操作

| 関数 | 用途 |
|------|------|
| `join` | 配列を文字列に結合 |
| `split` | 文字列を配列に分割 |
| `filter` | 条件に合う要素を抽出 |
| `map` | 各要素に変換を適用 |

### 条件・変換

| 関数 | 用途 |
|------|------|
| `switch` | 値に応じた分岐 |
| `if` | 条件付き値設定 |
| `convert_type` | 型変換 |
| `exists` | 値の存在チェック |

### ID・日時生成

| 関数 | 用途 |
|------|------|
| `uuid4` | UUID v4生成 |
| `uuid5` | UUID v5生成 |
| `uuid_short` | 短縮UUID |
| `random_string` | ランダム文字列生成 |
| `now` | 現在時刻 |

### エンコーディング

| 関数 | 用途 |
|------|------|
| `mimeEncodedWord` | RFC 2047 MIMEエンコード |

### 関数チェーン

複数の関数を配列で指定すると、左から右へ順に適用される:

```json
{
  "from": "$.request_body.name",
  "to": "display_name",
  "functions": [
    {"name": "trim"},
    {"name": "case", "args": {"mode": "upper"}}
  ]
}
```

### 入力非依存関数

`"from": "$.unused"` で入力値に依存しない関数（`random_string`, `now`等）を使用できる:

```json
{"from": "$.unused", "to": "request_id", "functions": [{"name": "random_string", "args": {"length": 32}}]}
```

## E2Eテスト

```
e2e/src/tests/
├── integration/ida/                   # 統合テスト
│   ├── integration-01-identity_verification-condition.test.js
│   ├── integration-02-identity-verification-retry.test.js
│   └── ...
└── scenario/control_plane/organization/
    └── organization_identity_verification_config_management*.test.js
```

## 7-Phase 実装フロー

身元確認リクエストは `IdentityVerificationApplicationHandler` で 7 Phase に分かれて処理される。

```
Phase 1: Request  → JSON Schema バリデーション
Phase 2: Pre Hook → AdditionalRequestParameterResolvers（外部パラメータ解決）
Phase 3: Execute  → IdentityVerificationApplicationExecutors（外部 eKYC API 呼び出し）
Phase 4: Post Hook → 後処理
Phase 5: Transition → ステータス遷移（12 種の条件演算子で判定）
Phase 6: Store    → IdentityVerificationApplicationRepository に永続化
Phase 7: Response → IdentityVerificationApplyingResult を返却
```

### Conditional Execution（Phase 5）

ステータス遷移は `ConditionEvaluator` + JSONPath で動的に評価される。

| 演算子 | 意味 |
|--------|------|
| `eq`, `ne` | 等値・非等値 |
| `gt`, `gte`, `lt`, `lte` | 大小比較 |
| `in`, `nin` | 包含・非包含 |
| `exists`, `missing` | 存在チェック |
| `contains`, `regex` | 文字列マッチ |

`allOf` / `anyOf` でネスト可能。

### verified_claims マッピング

トップレベルの `result.verified_claims_mapping_rules` で外部 API レスポンスから verified_claims を動的生成:

```json
{
  "static_value": "eidas",
  "to": "verification.trust_framework"
}
```

```json
{
  "from": "$.application.application_details.first_name",
  "to": "claims.given_name"
}
```

`to` のパスは `verification.*`（信頼フレームワーク等）と `claims.*`（クレーム値）の2系統。
値の指定方法は `static_value`（固定値）と `from`（JSONPath）の2種。

### SSOクレデンシャル連携（pre_hook: sso_credentials）

`additional_parameters` で `type: "sso_credentials"` を指定すると、フェデレーションログインで取得したリフレッシュトークンを使って外部IdPのアクセストークンを取得できる。

**エラー分類**:
- 401/403 → `AUTHENTICATION_ERROR`, retryable=false（トークン無効・取消）
- 5xx → `SERVER_ERROR`, retryable=true（一時的障害）
- SSOクレデンシャルなし → `UNEXPECTED_ERROR`, retryable=false
- 接続失敗等 → `UNEXPECTED_ERROR`, retryable=false

**前提**: フェデレーション設定で `store_credentials: true` が必要。

**実装**: `SsoCredentialsParameterResolver`（idp-server-core-extension-ida）

### verified_claims ライフサイクル

身元確認が承認されると `verified_claims_mapping_rules` で生成された値がユーザーに保存される。

**マージ戦略**:
- 申込み承認・コールバック承認: `User.mergeVerifiedClaims()` → `putAll` によるキーレベルマージ
- 外部サービス直接登録: `User.setVerifiedClaims()` → 完全上書き

**putAllの挙動**:
- 同じキー → 新しい値で上書き
- 新しい方に無いキー → 既存値が残る
- 新しい方にnull値 → 既存値がnullで上書き

**注意**: `verification` や `claims` はトップレベルキーとして putAll されるため、部分更新ではなくオブジェクト全体が置き換わる。

**空マッピングルールの場合**: `verified_claims_mapping_rules: []` で承認しても、`putAll({})` なので既存値は保持される。新規ユーザーならnullのまま。

**TODO**: テナントIDポリシーでマージ戦略選択（#1269）

### verified_claims を後続申込みで参照

`$.user.verified_claims.claims.*` パスで、承認済みの verified_claims を申込みの body_mapping_rules 等から参照可能:

```json
{ "from": "$.user.verified_claims.claims.external_application_id", "to": "previous_application_id" }
```

**実装箇所**:
- `IdentityVerificationApplicationEntryService:299` → `mergeVerifiedClaims`
- `IdentityVerificationCallbackEntryService:209` → `mergeVerifiedClaims`
- `IdentityVerificationEntryService:135` → `setVerifiedClaims`

### 外部 eKYC サービス連携

`IdentityVerificationApplicationHttpRequestExecutor` が `HttpRequestExecutor` を使って外部 API を呼び出す:
- URL プレースホルダー解決（`{{external_application_id}}`）
- OAuth 認証サポート
- リトライ設定（exponential backoff）

**探索起点**: `libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/ida/`

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core-extension-ida:compileJava

# テスト
cd e2e && npm test -- --grep "identity.*verification"
```
