# 設定値変更の動作確認ガイド（応用編） - マッピング関数（functions）

`body_mapping_rules` / `header_mapping_rules` で使用できるマッピング関数の動作確認手順です。

> **前提**: `setup.sh` で初期環境が構築済みであること。基本編（`VERIFY-CONFIG-CHANGES.md`）の共通手順を参照。

---

## 利用可能な関数一覧

### 文字列変換

| 関数名 | 説明 | 主要 args | 入力 → 出力 |
|--------|------|-----------|------------|
| `trim` | 前後空白除去 | `mode`: both/start/end, `normalize`: true | `"  Tanaka  "` → `"Tanaka"` |
| `case` | 大文字/小文字変換 | `mode`: upper/lower/title/camel/pascal | `"tanaka taro"` → `"TANAKA TARO"` |
| `replace` | 文字列置換 | `target`, `replacement` | `"090-1234-5678"` → `"09012345678"` |
| `regex_replace` | 正規表現置換 | `pattern`, `replacement` | `"1000001"` → `"100-0001"` |
| `substring` | 部分文字列抽出 | `start`, `end` or `length` | `"2025-03-13"` → `"2025"` |
| `format` | テンプレート置換 | `template` | `"abc123"` → `"Bearer abc123"` |

### コレクション操作

| 関数名 | 説明 | 主要 args | 入力 → 出力 |
|--------|------|-----------|------------|
| `join` | 配列→文字列結合 | `separator`, `prefix`, `suffix` | `["admin","user"]` → `"admin,user"` |
| `split` | 文字列→配列分割 | `separator`, `trim` | `"admin,user"` → `["admin","user"]` |
| `filter` | 条件フィルタ | `condition` | `["admin","guest"]` → `["admin"]` |
| `map` | 各要素に関数適用 | `function`, `function_args` | `["hello","world"]` → `["HELLO","WORLD"]` |

### 条件・変換

| 関数名 | 説明 | 主要 args | 入力 → 出力 |
|--------|------|-----------|------------|
| `switch` | 値マッピング | `cases`, `default` | `"M"` → `"男性"` |
| `if` | 条件分岐 | `condition`, `then`, `else` | `null` → `"未設定"` |
| `convert_type` | 型変換 | `type`: string/integer/boolean | `"123"` → `123` |
| `exists` | 存在チェック | なし | `"hello"` → `true` |

### ID・日時生成

| 関数名 | 説明 | 主要 args | 出力例 |
|--------|------|-----------|--------|
| `uuid4` | ランダムUUID v4 | なし | `"550e8400-e29b-41d4-..."` |
| `uuid5` | 決定的UUID v5 | `namespace`: DNS/URL/OID/X500 | 同一入力→同一UUID |
| `uuid_short` | 短縮ID | `length`, `uppercase` | `"a3k7m9x2"` |
| `random_string` | ランダム文字列 | `length`, `charset` | `"aBcDeFgH"` |
| `now` | 現在日時 | `zone`, `pattern` | `"2025-03-13 14:00:00"` |

### エンコーディング

| 関数名 | 説明 | 主要 args | 入力 → 出力 |
|--------|------|-----------|------------|
| `mimeEncodedWord` | RFC 2047メールヘッダ | `charset`, `encoding`: B/Q | `"日本語"` → `"=?UTF-8?B?..."` |

複数の関数を `functions` 配列に指定すると、順番に適用される（チェーン）。

---

## 設定例と確認手順

### モックサーバー起動

```bash
node config/templates/use-cases/ekyc/mock-server.js
```

### ヘルパー読み込み + 初期準備

```bash
source config/templates/use-cases/ekyc/helpers.sh
get_admin_token
start_auth_flow
register_user
complete_auth_flow
```

---

### テスト 1: trim + case（氏名正規化）

名前の前後空白を除去し、大文字に変換する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        {
          "from": "$.request_body.last_name",
          "to": "family_name",
          "functions": [
            { "name": "trim" },
            { "name": "case", "args": { "mode": "upper" } }
          ]
        },
        {
          "from": "$.request_body.first_name",
          "to": "given_name",
          "functions": [
            { "name": "trim" },
            { "name": "case", "args": { "mode": "upper" } }
          ]
        },
        { "from": "$.request_body.birthdate", "to": "date_of_birth" },
        { "static_value": "identity_verification", "to": "request_type" }
      ]
    }
  }'

iv_apply "  tanaka  " "taro" "1990-01-15"

# モックサーバーのログで確認:
#   body: {"family_name":"TANAKA","given_name":"TARO","date_of_birth":"1990-01-15","request_type":"identity_verification"}

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | trim: 前後空白が除去される | `"  tanaka  "` → `"tanaka"` |
| 2 | case: 大文字に変換される | `"tanaka"` → `"TANAKA"` |
| 3 | static_value が注入される | `request_type: "identity_verification"` |

---

### テスト 2: replace + regex_replace（電話番号・郵便番号フォーマット）

電話番号からハイフンを除去し、郵便番号にハイフンを挿入する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "from": "$.request_body.phone_number",
          "to": "phone_number",
          "functions": [
            { "name": "replace", "args": { "target": "-", "replacement": "" } }
          ]
        },
        {
          "from": "$.request_body.postal_code",
          "to": "postal_code",
          "functions": [
            { "name": "regex_replace", "args": { "pattern": "^(\\d{3})(\\d{4})$", "replacement": "$1-$2" } }
          ]
        }
      ]
    }
  }'

# phone_number と postal_code は request_body の追加フィールドとして送信される
# （request.schema の additionalProperties がデフォルト許可のため）
curl -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "last_name": "Tanaka",
    "first_name": "Taro",
    "birthdate": "1990-01-15",
    "phone_number": "090-1234-5678",
    "postal_code": "1000001",
    "email_address": "tanaka@example.com"
  }' | jq .

# モックサーバーのログで確認:
#   phone_number: "09012345678"（ハイフン除去）
#   postal_code: "100-0001"（ハイフン挿入）

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | replace: ハイフン除去 | `"090-1234-5678"` → `"09012345678"` |
| 2 | regex_replace: フォーマット変換 | `"1000001"` → `"100-0001"` |

---

### テスト 3: switch + if（性別コード変換・デフォルト値設定）

性別コードを日本語に変換し、未入力フィールドにデフォルト値を設定する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "from": "$.request_body.gender",
          "to": "gender_label",
          "functions": [
            {
              "name": "switch",
              "args": {
                "cases": { "M": "male", "F": "female", "O": "other" },
                "default": "unknown"
              }
            }
          ]
        },
        {
          "from": "$.request_body.nationality",
          "to": "nationality",
          "functions": [
            { "name": "if", "args": { "condition": "empty", "then": "JP" } }
          ]
        }
      ]
    }
  }'

curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "last_name": "Tanaka",
    "first_name": "Taro",
    "birthdate": "1990-01-15",
    "gender": "M",
    "nationality": "",
    "email_address": "tanaka@example.com"
  }' | jq .

# モックサーバーのログで確認:
#   gender_label: "male"
#   nationality: "JP"

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | switch: コード→ラベル変換 | `"M"` → `"male"` |
| 2 | switch: 未知の値にデフォルト | 存在しないコード → `"unknown"` |
| 3 | if: 空値にデフォルト設定 | `""` → `"JP"` |

---

### テスト 4: uuid_short + now（トラッキングID・タイムスタンプ生成）

リクエストにトラッキングIDとタイムスタンプを自動付与する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" },
        {
          "static_value": "dummy",
          "to": "X-Request-Id",
          "functions": [
            { "name": "uuid_short", "args": { "length": 12 } }
          ]
        }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "static_value": "dummy",
          "to": "request_timestamp",
          "functions": [
            { "name": "now", "args": { "zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss" } }
          ]
        },
        {
          "static_value": "dummy",
          "to": "tracking_id",
          "functions": [
            { "name": "uuid4" }
          ]
        }
      ]
    }
  }'

iv_apply "Tanaka" "Taro" "1990-01-15"

# モックサーバーのログで確認:
#   header: x-request-id: a3k7m9x2b5c1（12文字の短縮ID）
#   body: request_timestamp: "2025-03-13 23:00:00"
#   body: tracking_id: "550e8400-e29b-41d4-a716-446655440000"（UUID v4）

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | uuid_short: ヘッダーに短縮IDが生成される | 12文字の英数字 |
| 2 | now: タイムスタンプが生成される | ISO 8601形式（Asia/Tokyo） |
| 3 | uuid4: トラッキングIDが生成される | UUID v4形式 |

---

### テスト 5: substring + format（生年月日から年齢区分ヘッダー生成）

生年月日から年だけを抽出し、ヘッダーに注入する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" },
        {
          "from": "$.request_body.birthdate",
          "to": "X-Birth-Year",
          "functions": [
            { "name": "substring", "args": { "start": 0, "length": 4 } }
          ]
        }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "from": "$.request_body.last_name",
          "to": "display_name",
          "functions": [
            { "name": "format", "args": { "template": "applicant:{{value}}" } }
          ]
        }
      ]
    }
  }'

iv_apply "Tanaka" "Taro" "1990-01-15"

# モックサーバーのログで確認:
#   header: x-birth-year: 1990
#   body: display_name: "applicant:Tanaka"

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | substring: 先頭4文字を抽出 | `"1990-01-15"` → `"1990"` |
| 2 | format: テンプレート置換 | `"Tanaka"` → `"applicant:Tanaka"` |

---

### テスト 6: convert_type + exists（型変換・存在チェック）

文字列を数値に変換し、フィールドの存在をブール値で送信する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "from": "$.request_body.age",
          "to": "age",
          "functions": [
            { "name": "convert_type", "args": { "type": "integer" } }
          ]
        },
        {
          "from": "$.request_body.email_address",
          "to": "has_email",
          "functions": [
            { "name": "exists" }
          ]
        }
      ]
    }
  }'

curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "last_name": "Tanaka",
    "first_name": "Taro",
    "birthdate": "1990-01-15",
    "age": "34",
    "email_address": "tanaka@example.com"
  }' | jq .

# モックサーバーのログで確認:
#   age: 34（数値、文字列ではない）
#   has_email: true

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | convert_type: 文字列→整数 | `"34"` → `34` |
| 2 | exists: 値があれば true | `"tanaka@example.com"` → `true` |

---

### テスト 7: 実践的チェーン（trim + replace + case + format）

実務を想定した複数関数チェーン: 入力値の正規化→フォーマット変換→整形。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" },
        {
          "static_value": "dummy",
          "to": "X-Correlation-Id",
          "functions": [
            { "name": "uuid_short", "args": { "length": 16, "uppercase": true } }
          ]
        }
      ],
      "body_mapping_rules": [
        {
          "from": "$.request_body.last_name",
          "to": "family_name",
          "functions": [
            { "name": "trim" },
            { "name": "case", "args": { "mode": "upper" } }
          ]
        },
        {
          "from": "$.request_body.first_name",
          "to": "given_name",
          "functions": [
            { "name": "trim" },
            { "name": "case", "args": { "mode": "upper" } }
          ]
        },
        { "from": "$.request_body.birthdate", "to": "date_of_birth" },
        {
          "from": "$.request_body.phone_number",
          "to": "phone_number",
          "functions": [
            { "name": "trim" },
            { "name": "replace", "args": { "target": "-", "replacement": "" } },
            { "name": "replace", "args": { "target": " ", "replacement": "" } }
          ]
        },
        {
          "static_value": "dummy",
          "to": "submitted_at",
          "functions": [
            { "name": "now", "args": { "zone": "Asia/Tokyo", "pattern": "yyyy-MM-dd HH:mm:ss" } }
          ]
        },
        { "static_value": "ekyc_basic", "to": "verification_type" }
      ]
    }
  }'

curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "last_name": "  tanaka  ",
    "first_name": " taro ",
    "birthdate": "1990-01-15",
    "phone_number": " 090-1234-5678 ",
    "email_address": "tanaka@example.com"
  }' | jq .

# モックサーバーのログで確認:
#   header: x-correlation-id: A3K7M9X2B5C1D4E6（16文字の大文字短縮ID）
#   body: family_name: "TANAKA"
#   body: given_name: "TARO"
#   body: phone_number: "09012345678"
#   body: submitted_at: "2025-03-13 23:00:00"
#   body: verification_type: "ekyc_basic"

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | trim + case チェーン | `"  tanaka  "` → `"TANAKA"` |
| 2 | trim + replace チェーン | `" 090-1234-5678 "` → `"09012345678"` |
| 3 | uuid_short: 大文字短縮ID | 16文字の大文字英数字 |
| 4 | now: タイムスタンプ自動生成 | ISO 8601 形式 |
| 5 | static_value: 固定値注入 | `verification_type: "ekyc_basic"` |

> **参考**: `format` 関数は基本編パターン 5-4 の `Authorization: Bearer {{value}}` で確認済み。

---

### テスト 8: split + map + join（コレクション操作）

カンマ区切り文字列を分割し、各要素を変換して再結合する。

```bash
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.last_name", "to": "last_name" },
        { "from": "$.request_body.first_name", "to": "first_name" },
        { "from": "$.request_body.birthdate", "to": "birthdate" },
        {
          "from": "$.request_body.tags",
          "to": "tags_upper",
          "functions": [
            { "name": "split", "args": { "separator": ",", "trim": true } },
            { "name": "map", "args": { "function": "case", "function_args": { "mode": "upper" } } },
            { "name": "join", "args": { "separator": "|" } }
          ]
        },
        {
          "from": "$.request_body.tags",
          "to": "tag_count",
          "functions": [
            { "name": "split", "args": { "separator": ",", "trim": true } },
            { "name": "join", "args": { "separator": ",", "prefix": "[", "suffix": "]" } }
          ]
        }
      ]
    }
  }'

curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "last_name": "Tanaka",
    "first_name": "Taro",
    "birthdate": "1990-01-15",
    "email_address": "tanaka@example.com",
    "tags": "kyc, aml, pep"
  }' | jq .

# モックサーバーのログで確認:
#   tags_upper: "KYC|AML|PEP"
#   tag_count: "[kyc,aml,pep]"

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | split: カンマ区切りで分割 | `"kyc, aml, pep"` → `["kyc","aml","pep"]` |
| 2 | map + case: 各要素を大文字変換 | `["kyc","aml","pep"]` → `["KYC","AML","PEP"]` |
| 3 | join: パイプ区切りで結合 | `["KYC","AML","PEP"]` → `"KYC\|AML\|PEP"` |
| 4 | join + prefix/suffix: 括弧付き | `["kyc","aml","pep"]` → `"[kyc,aml,pep]"` |

---

## 関数リファレンス（詳細）

### trim

```json
{ "name": "trim" }
{ "name": "trim", "args": { "mode": "start" } }
{ "name": "trim", "args": { "chars": ".", "normalize": true } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `mode` | string | `"both"` | `"both"`, `"start"`, `"end"` |
| `chars` | string | - | 除去する文字（例: `"."`） |
| `normalize` | boolean | `false` | 内部の連続空白を1つに正規化 |

### case

```json
{ "name": "case", "args": { "mode": "upper" } }
{ "name": "case", "args": { "mode": "title" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `mode` | string | **必須** | `"upper"`, `"lower"`, `"title"`, `"camel"`, `"pascal"` |
| `locale` | string | `"en"` | ロケール |
| `delimiter` | string | - | 単語区切り文字 |

### replace

```json
{ "name": "replace", "args": { "target": "-", "replacement": "" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `target` | string | **必須** | 検索文字列 |
| `replacement` | string | **必須** | 置換文字列 |
| `ignoreCase` | boolean | `false` | 大文字小文字無視 |
| `replaceFirst` | boolean | `false` | 最初の一致のみ置換 |

### regex_replace

```json
{ "name": "regex_replace", "args": { "pattern": "^(\\d{3})(\\d{4})$", "replacement": "$1-$2" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `pattern` | string | **必須** | 正規表現パターン |
| `replacement` | string | **必須** | 置換文字列（`$1`, `$2`でグループ参照） |
| `flags` | string | - | `"i"`, `"m"`, `"s"`, `"x"` |
| `replaceFirst` | boolean | `false` | 最初の一致のみ置換 |

### substring

```json
{ "name": "substring", "args": { "start": 0, "length": 4 } }
{ "name": "substring", "args": { "start": -5 } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `start` | integer | `0` | 開始位置（負数は末尾から） |
| `end` | integer | - | 終了位置（排他） |
| `length` | integer | - | 抽出文字数（`end` の代替） |

### format

```json
{ "name": "format", "args": { "template": "Bearer {{value}}" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `template` | string | `"{{value}}"` | `{{value}}` を入力値で置換 |

### switch

```json
{ "name": "switch", "args": { "cases": { "M": "male", "F": "female" }, "default": "unknown" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `cases` | object | **必須** | キー→値のマッピング |
| `default` | any | 入力値そのまま | マッチしない場合の値 |
| `ignoreCase` | boolean | `false` | 大文字小文字無視 |

### if

```json
{ "name": "if", "args": { "condition": "empty", "then": "default_value" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `condition` | string | **必須** | `"null"`, `"not_null"`, `"empty"`, `"not_empty"`, `"exists"`, `"equals:値"`, `"not_equals:値"` |
| `then` | any | **必須** | 条件が真の場合の値 |
| `else` | any | 入力値そのまま | 条件が偽の場合の値 |

### convert_type

```json
{ "name": "convert_type", "args": { "type": "integer" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `type` | string | **必須** | `"string"`, `"integer"`, `"long"`, `"double"`, `"boolean"`, `"datetime"` |
| `onError` | string | `"null"` | `"null"`, `"default"`, `"throw"` |
| `default` | any | - | `onError="default"` 時の値 |

### exists

```json
{ "name": "exists" }
```

引数なし。null / 空文字 / 空コレクション → `false`、それ以外 → `true`。

### join

```json
{ "name": "join", "args": { "separator": ", " } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `separator` | string | `","` | 区切り文字 |
| `skipNull` | boolean | `false` | null要素をスキップ |
| `skipEmpty` | boolean | `false` | 空文字要素をスキップ |
| `prefix` | string | - | 結果の前に付与 |
| `suffix` | string | - | 結果の後に付与 |

### split

```json
{ "name": "split", "args": { "separator": ",", "trim": true } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `separator` | string | `","` | 分割文字 |
| `trim` | boolean | `false` | 各要素の空白除去 |
| `removeEmpty` | boolean | `false` | 空要素除去 |
| `limit` | integer | `-1` | 最大分割数 |

### filter

```json
{ "name": "filter", "args": { "condition": "{{value}} != 'guest'" } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `condition` | string | **必須** | `==`, `!=`, `contains`, `startsWith`, `endsWith` |
| `negate` | boolean | `false` | 条件を反転 |

### map

```json
{ "name": "map", "args": { "function": "case", "function_args": { "mode": "upper" } } }
```

| args | 型 | デフォルト | 説明 |
|------|------|-----------|------|
| `function` | string | **必須** | 適用する関数名 |
| `function_args` | object | - | 関数の引数 |

### uuid4 / uuid5 / uuid_short / random_string / now / mimeEncodedWord

上記の関数一覧テーブルを参照。

---

## チェックリスト

| # | テスト | 確認項目 | 結果 |
|---|--------|---------|------|
| 1 | テスト 1 | trim + case: 空白除去 + 大文字変換 | |
| 2 | テスト 1 | static_value: 固定値注入 | |
| 3 | テスト 2 | replace: ハイフン除去 | |
| 4 | テスト 2 | regex_replace: フォーマット変換 | |
| 5 | テスト 3 | switch: コード→ラベル変換 | |
| 6 | テスト 3 | if: 空値にデフォルト設定 | |
| 7 | テスト 4 | uuid_short: 短縮ID生成 | |
| 8 | テスト 4 | now: タイムスタンプ生成 | |
| 9 | テスト 4 | uuid4: UUID生成 | |
| 10 | テスト 5 | substring: 部分文字列抽出 | |
| 11 | テスト 5 | format: テンプレート置換 | |
| 12 | テスト 6 | convert_type: 文字列→整数 | |
| 13 | テスト 6 | exists: 存在チェック→ブール値 | |
| 14 | テスト 7 | 実践的チェーン: 複数関数の連鎖 | |
| 15 | テスト 8 | split + map + join: コレクション操作 | |
| 16 | テスト 8 | join + prefix/suffix: 括弧付き結合 | |
| 17 | - | format: Bearer トークン（基本編 5-4 で確認） | |
