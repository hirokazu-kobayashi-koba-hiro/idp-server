# http_requests（複数API チェーン）実験ガイド

`http_requests`（複数形）executor を使って、**複数の外部 API を順番に呼び出し、結果を統合する**パターンを体験するガイドです。

> **`http_request`（単数）との違い**:
>
> | | `http_request` | `http_requests` |
> |---|---|---|
> | リクエスト数 | 1つ | 複数（順番に実行） |
> | 設定キー | `execution.http_request` (object) | `execution.http_requests` (array) |
> | 結果パス | `$.execution_http_request.response_body.*` | `$.execution_http_requests[0].response_body.*` |
> | チェーン | 不可 | 前のレスポンスを次のリクエストで使える |
> | エラー時 | 即座に返却 | 失敗した時点で中断、それまでの結果を返却 |

> **前提**: `setup.sh` が正常に完了し、モックサーバー（`mock-server.js`）が起動していること。

---

## 共通準備

```bash
cd config/templates/use-cases/external-password-auth
source helpers.sh

get_admin_token
```

### モックサーバーの起動

```bash
# 別ターミナルで起動
node mock-server.js
# → Mock auth server running on http://localhost:4001
```

**エンドポイント**:

| パス | 用途 | レスポンス |
|------|------|-----------|
| `POST /auth/password` | パスワード認証 | `{user_id, email, name}` or `{error, error_description}` |
| `POST /user/details` | ユーザー詳細取得 | `{user_id, birthdate, phone_number, zoneinfo, locale, role}` |

```bash
# 動作確認
curl -s -X POST http://localhost:4001/auth/password \
  -H 'Content-Type: application/json' \
  -d '{"username":"test@example.com","password":"correct"}' | jq .

curl -s -X POST http://localhost:4001/user/details \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"ext-user-test-example-com"}' | jq .
```

---

## Experiment 1: 認証 → ユーザー詳細を2段階で取得する（基本）

> **やりたいこと**: パスワード認証（API 1）→ ユーザー詳細取得（API 2）を1回の認証フローで実行したい
>
> **変わる設定**: `authentication-configurations` の `execution` を `http_request` → `http_requests` に変更
>
> **ポイント**: API 1 のレスポンス（`user_id`）を API 2 のリクエストボディで使う **チェーン** パターン。

### 1. ベースライン確認（http_request 単数 = 現在の設定）

```bash
start_auth_flow
password_login "chain-$(date +%s)@example.com" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- ベースライン: UserInfo（name, email のみ） ---"
get_userinfo | jq '{name, email, birthdate, phone_number, zoneinfo}'
```

> `birthdate`, `phone_number`, `zoneinfo` は `null` のはず（単一APIでは取得していない）。

### 2. 設定変更：execution を http_requests に切り替え

```bash
update_auth_config '
  .interactions["password-authentication"].execution = {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "http://host.docker.internal:4001/auth/password",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body.username", "to": "username" },
          { "from": "$.request_body.password", "to": "password" }
        ]
      },
      {
        "url": "http://host.docker.internal:4001/user/details",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.execution_http_requests[0].response_body.user_id", "to": "user_id" }
        ]
      }
    ]
  }
  |
  .interactions["password-authentication"].user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_requests[0].response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
    { "from": "$.execution_http_requests[0].response_body.name", "to": "name" },
    { "from": "$.execution_http_requests[1].response_body.birthdate", "to": "birthdate" },
    { "from": "$.execution_http_requests[1].response_body.phone_number", "to": "phone_number" },
    { "from": "$.execution_http_requests[1].response_body.zoneinfo", "to": "zoneinfo" },
    { "from": "$.execution_http_requests[1].response_body.locale", "to": "locale" },
    { "static_value": "external-auth", "to": "provider_id" }
  ]
  |
  .interactions["password-authentication"].response.body_mapping_rules = [
    {
      "from": "$.execution_http_requests[0].response_body.user_id",
      "to": "user_id",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.user_id" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.email",
      "to": "email",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.email" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error",
      "to": "error",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error_description",
      "to": "error_description",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error_description" }
    }
  ]
' | jq '.interactions["password-authentication"].execution.function // .'
```

### 3. 挙動確認

```bash
start_auth_flow
password_login "chain2-$(date +%s)@example.com" "correct-password" > /dev/null
complete_auth_flow > /dev/null

echo "--- http_requests 後: UserInfo（詳細情報が追加されているはず） ---"
get_userinfo | jq '{name, email, birthdate, phone_number, zoneinfo, locale}'
```

### 4. 期待結果

| タイミング | UserInfo の内容 | 理由 |
|-----------|----------------|------|
| 変更前（http_request） | `name`, `email` のみ | `/auth/password` の返却値のみ |
| 変更後（http_requests） | `name`, `email` + `birthdate`, `phone_number`, `zoneinfo`, `locale` | `/auth/password`(API 1) + `/user/details`(API 2) の統合 |

```
実行フロー:
  API 1: POST /auth/password   → { user_id, email, name }
                                     ↓ user_id をチェーン
  API 2: POST /user/details    → { birthdate, phone_number, zoneinfo, locale, role }
                                     ↓
  user_mapping_rules: [0] から email/name、[1] から birthdate/phone_number/zoneinfo/locale
```

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 2: functions でヘッダー値を動的生成する

> **やりたいこと**: トレースID、タイムスタンプ、Bearer トークンなどのヘッダーを動的に生成したい
>
> **変わる設定**: `header_mapping_rules` の `functions`
>
> **使える functions**: `format`（テンプレート変換）、`random_string`（ランダム文字列）、`now`（現在時刻）

### 1. 設定変更：functions 付きヘッダーを追加

```bash
update_auth_config '
  .interactions["password-authentication"].execution = {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "http://host.docker.internal:4001/auth/password",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body.username", "to": "username" },
          { "from": "$.request_body.password", "to": "password" }
        ]
      },
      {
        "url": "http://host.docker.internal:4001/user/details",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" },
          {
            "from": "$.unused",
            "to": "x-request-id",
            "functions": [
              { "name": "random_string", "args": { "length": 8 } },
              { "name": "format", "args": { "template": "trace-{{value}}" } }
            ]
          },
          {
            "from": "$.unused",
            "to": "issued_at",
            "functions": [
              { "name": "now", "args": { "zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss" } }
            ]
          }
        ],
        "body_mapping_rules": [
          { "from": "$.execution_http_requests[0].response_body.user_id", "to": "user_id" }
        ]
      }
    ]
  }
  |
  .interactions["password-authentication"].user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_requests[0].response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
    { "from": "$.execution_http_requests[0].response_body.name", "to": "name" },
    { "from": "$.execution_http_requests[1].response_body.birthdate", "to": "birthdate" },
    { "from": "$.execution_http_requests[1].response_body.zoneinfo", "to": "zoneinfo" },
    { "static_value": "external-auth", "to": "provider_id" }
  ]
  |
  .interactions["password-authentication"].response.body_mapping_rules = [
    {
      "from": "$.execution_http_requests[0].response_body.user_id",
      "to": "user_id",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.user_id" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error",
      "to": "error",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error" }
    }
  ]
' | jq '.interactions["password-authentication"].execution.http_requests[1].header_mapping_rules // .'
```

### 2. 挙動確認

```bash
start_auth_flow
echo "--- functions 付きヘッダーで認証 ---"
password_login "func-$(date +%s)@example.com" "correct-password" | jq .
```

> モックサーバーのターミナルで、`x-request-id: trace-xxxxxxxx` と `issued_at: 2026-03-11 ...` のヘッダーが受信されていることを確認。
>
> モックサーバーのレスポンスに `requested_at` が含まれている（`issued_at` ヘッダーの値をエコーバック）。

### 3. functions 一覧

| function | 用途 | args | 例 |
|---|---|---|---|
| `format` | テンプレート変換 | `{"template": "Bearer {{value}}"}` | 入力 `abc` → `Bearer abc` |
| `random_string` | ランダム文字列生成 | `{"length": 8}` | `a3kf9xm2` |
| `now` | 現在時刻生成 | `{"zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss"}` | `2026-03-11 15:30:00` |
| `exists` | 存在チェック（boolean化） | `{}` | 値あり → `true` |

**`from: "$.unused"`**: 入力値なしで functions を実行する特殊パス。`random_string` や `now` のように入力不要な functions で使う。

**functions チェーン**: 配列で複数指定すると左から順に適用される。
```json
"functions": [
  { "name": "random_string", "args": { "length": 6 } },
  { "name": "format", "args": { "template": "trace-id-{{value}}" } }
]
// → "abc123" → "trace-id-abc123"
```

### 4. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 3: 途中のリクエストが失敗した場合の挙動

> **やりたいこと**: 2段階 API のうち1つが失敗した場合にどうなるか確認したい
>
> **確認する挙動**: `http_requests` は失敗した時点で中断し、それまでの結果を返却する
>
> **ポイント**: `response.body_mapping_rules` の `condition` で **どの API が失敗したか** を判定し、適切なエラーレスポンスを返す。

### 1. 設定：API 2 のエラーも API 1 のエラーも condition で拾う

```bash
update_auth_config '
  .interactions["password-authentication"].execution = {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "http://host.docker.internal:4001/auth/password",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body.username", "to": "username" },
          { "from": "$.request_body.password", "to": "password" }
        ]
      },
      {
        "url": "http://host.docker.internal:9999/not-exist",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.execution_http_requests[0].response_body.user_id", "to": "user_id" }
        ]
      }
    ]
  }
  |
  .interactions["password-authentication"].user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_requests[0].response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
    { "from": "$.execution_http_requests[0].response_body.name", "to": "name" },
    { "static_value": "external-auth", "to": "provider_id" }
  ]
  |
  .interactions["password-authentication"].response.body_mapping_rules = [
    {
      "from": "$.execution_http_requests[0].response_body.user_id",
      "to": "user_id",
      "condition": {
        "operation": "allOf",
        "value": [
          { "operation": "exists", "path": "$.execution_http_requests[0].response_body.user_id" },
          { "operation": "missing", "path": "$.execution_http_requests[1].response_body.error" }
        ]
      }
    },
    {
      "from": "$.execution_http_requests[0].response_body.email",
      "to": "email",
      "condition": {
        "operation": "allOf",
        "value": [
          { "operation": "exists", "path": "$.execution_http_requests[0].response_body.email" },
          { "operation": "missing", "path": "$.execution_http_requests[1].response_body.error" }
        ]
      }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error",
      "to": "error",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error_description",
      "to": "error_description",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error_description" }
    },
    {
      "from": "$.execution_http_requests[1].response_body.error",
      "to": "error",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[1].response_body.error" }
    },
    {
      "from": "$.execution_http_requests[1].response_body.error_description",
      "to": "error_description",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[1].response_body.error_description" }
    }
  ]
' | jq '.interactions["password-authentication"].response.body_mapping_rules | length'
```

> **設定のポイント**:
> - 成功フィールド（`user_id`, `email`）は `allOf` 複合条件で **自身が存在 AND API 2 のエラーが missing** の両方を満たす場合のみ出力
> - `[0].response_body.error` → API 1（認証）のエラー（`invalid_credentials` 等）
> - `[1].response_body.error` → API 2（詳細取得）のエラー（`network_error` 等）
> - これにより、API 2 が失敗した場合はエラー情報のみ返り、成功フィールドは含まれない

### 2. 挙動確認：API 2 が接続不可

```bash
start_auth_flow
echo "--- API 1 は成功するが API 2 が接続不可 ---"
password_login "error-$(date +%s)@example.com" "correct-password" | jq .
```

> `ConnectException` → idp-server が API 2 の結果を `{error: "network_error", error_description: "HTTP request failed"}` として保持。
> `body_mapping_rules` の `[1].response_body.error` condition にマッチし、`error` / `error_description` が返る。

### 3. 挙動確認：API 1 が認証失敗

```bash
start_auth_flow
echo "--- API 1 が認証失敗 → API 2 はスキップ ---"
password_login "error2@example.com" "invalid" | jq .
```

> API 1 が 401 → `isClientError()` で中断。API 2 は実行されない。
> `[0].response_body.error` condition にマッチし、`invalid_credentials` が返る。

### 4. 期待結果

| シナリオ | API 1 | API 2 | idp-server レスポンス | body の内容 |
|---------|-------|-------|---------------------|-------------|
| API 1 が認証失敗 | 401 | スキップ | 200 | `{error: "invalid_credentials", error_description: "..."}` |
| API 2 が接続不可 | 200 OK | ConnectException (503) | 500 | `{error: "network_error", error_description: "HTTP request failed"}` |
| 両方正常 | 200 OK | 200 OK | 200 | `{user_id: "...", email: "..."}` |

> **ポイント**:
> - `http_requests` は**前から順番に実行**し、**エラーが出た時点で中断**する
> - API 1 が失敗すれば API 2 は実行されない。API 2 が失敗しても API 1 の結果は保持される
> - `response.body_mapping_rules` の `condition` で **各 API のエラーを個別に検出**し、適切なエラー情報をレスポンスに含める

### 5. 元に戻す

```bash
restore_auth_config
```

---

## Experiment 4: ワイルドカード展開でリクエストボディ全体を転送する

> **やりたいこと**: リクエストボディのフィールドを1つずつ指定するのではなく、全体を一括転送したい
>
> **変わる設定**: `body_mapping_rules` で `{ "from": "$.request_body", "to": "*" }` を使う
>
> **ポイント**: `"to": "*"` はソースオブジェクトの全キーをそのまま展開する。

### 1. 設定変更：ワイルドカード展開を使用

```bash
update_auth_config '
  .interactions["password-authentication"].execution = {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "http://host.docker.internal:4001/auth/password",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body", "to": "*" }
        ]
      },
      {
        "url": "http://host.docker.internal:4001/user/details",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.execution_http_requests[0].response_body.user_id", "to": "user_id" }
        ]
      }
    ]
  }
  |
  .interactions["password-authentication"].user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_requests[0].response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_requests[0].response_body.email", "to": "email" },
    { "from": "$.execution_http_requests[0].response_body.name", "to": "name" },
    { "from": "$.execution_http_requests[1].response_body.birthdate", "to": "birthdate" },
    { "static_value": "external-auth", "to": "provider_id" }
  ]
  |
  .interactions["password-authentication"].response.body_mapping_rules = [
    {
      "from": "$.execution_http_requests[0].response_body.user_id",
      "to": "user_id",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.user_id" }
    },
    {
      "from": "$.execution_http_requests[0].response_body.error",
      "to": "error",
      "condition": { "operation": "exists", "path": "$.execution_http_requests[0].response_body.error" }
    }
  ]
' | jq '.interactions["password-authentication"].execution.http_requests[0].body_mapping_rules // .'
```

### 2. 挙動確認

```bash
start_auth_flow
echo "--- ワイルドカード展開で認証（username/password が全体転送される） ---"
password_login "wild-$(date +%s)@example.com" "correct-password" | jq .
```

### 3. 明示指定 vs ワイルドカードの比較

| パターン | body_mapping_rules | 外部サービスに送るJSON |
|---------|---|---|
| 明示指定 | `{"from": "$.request_body.username", "to": "username"}, ...` | `{"username": "...", "password": "..."}` |
| ワイルドカード | `{"from": "$.request_body", "to": "*"}` | `{"username": "...", "password": "..."}` + リクエストの全フィールド |

> **ポイント**: ワイルドカードはリクエストボディの全フィールドを転送するため、
> 外部サービスが想定外のフィールドを受け取る可能性がある。
> 明示指定の方がセキュリティ面で安全（必要なフィールドだけ送る）。
>
> **使いどころ**: 外部サービスのフィールドが動的に変わる場合や、
> プロトタイプ段階で素早く連携したい場合に便利。

### 4. 元に戻す

```bash
restore_auth_config
```

---

## 実験一覧

| # | やりたいこと | 使う機能 | 確認できること |
|---|------------|---------|--------------|
| 1 | 2つの API を順番に呼びたい | `http_requests` + チェーン | API 1 → API 2 の結果を統合して UserInfo に反映 |
| 2 | ヘッダーを動的生成したい | `functions` (format, random_string, now) | トレースID、タイムスタンプ等の自動生成 |
| 3 | 途中で失敗した場合を確認 | `http_requests` エラーハンドリング | 失敗時点で中断、それまでの結果は保持 |
| 4 | ボディ全体を一括転送したい | `"to": "*"` ワイルドカード | 明示指定 vs 全体転送の違い |

## 関連

- `EXPERIMENTS.md` - `http_request`（単数）の基本実験
- `config/examples/e2e/test-tenant/authentication-config/external-token/mocky.json` - `http_requests` の実例
