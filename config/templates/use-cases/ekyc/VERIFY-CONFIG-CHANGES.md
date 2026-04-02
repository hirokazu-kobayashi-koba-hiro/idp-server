# 設定値変更の動作確認ガイド - eKYC (Identity Verification)

`identity-verification-config.json` の各設定値を変更した際の動作確認手順書です。

> **前提**: `setup.sh` で初期環境が構築済みであること。

## 共通手順

### モックサーバー起動

```bash
# 別ターミナルで起動（ポート 4002）
node config/templates/use-cases/ekyc/mock-server.js
```

idp-server は Docker 内で動作するため、モックサーバーへの接続は `host.docker.internal` を使います。

**テストシナリオ**（`last_name` の値で動作を制御）:

| last_name | レスポンス | 用途 |
|-----------|----------|------|
| 任意の名前（例: `Tanaka`） | 200 成功 | 正常系 |
| `error` | 500 サーバーエラー | エラーハンドリング確認 |
| `timeout` | 504（10秒後） | タイムアウト確認 |
| `retry` | 1回目 503 → 2回目 200 | リトライ確認（パターン6） |
| `unauthorized` | 1回目 401 → 2回目 200 | OAuth自動リトライ確認（応用パターン12） |
| `business_error` | 200 だが `status: "error"` | response_resolve_configs 確認（パターン6-2） |

### ヘルパー読み込み + 初期準備

```bash
source config/templates/use-cases/ekyc/helpers.sh
get_admin_token

# ユーザー登録 + トークン取得（身元確認APIに必要）
start_auth_flow
register_user
complete_auth_flow
```

これで以下の変数・関数が使えます:

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `update_iv_config` | 身元確認設定を jq フィルタで更新 | `update_iv_config '.processes.apply.execution.type = "http_request"'` |
| `restore_iv_config` | 身元確認設定を初期状態に復元 | `restore_iv_config` |
| `get_iv_config` | 現在の身元確認設定を取得 | `get_iv_config \| jq .` |
| `update_auth_server` | 認可サーバー設定を jq フィルタで更新 | `update_auth_server '.extension.required_identity_verification_scopes = ["transfers"]'` |
| `restore_auth_server` | 認可サーバー設定を初期状態に復元 | `restore_auth_server` |
| `update_client` | クライアント設定を jq フィルタで更新 | `update_client '.scope = "openid profile"'` |
| `iv_apply` | 身元確認申請 | `iv_apply "Tanaka" "Taro" "1990-01-15"` |
| `iv_evaluate` | 審査結果判定 | `iv_evaluate approved` |
| `iv_process` | 任意プロセス実行 | `iv_process "callback-result" '{"result":"approved"}'` |
| `iv_list_applications` | 申請一覧 | `iv_list_applications \| jq .` |
| `iv_get_results` | 身元確認結果取得 | `iv_get_results \| jq .` |
| `show_verified_claims` | ID Token の verified_claims 表示 | `show_verified_claims` |
| `restore_all` | 全設定を初期状態に復元 | `restore_all` |

---

## パターン 1: 外部eKYCサービスの接続先変更

**ユースケース**: eKYCベンダーの切替、ステージング→本番URL変更

### 1-1. execution を no_action → http_request に変更

**変更前** (`identity-verification-config.json`):

```json
"apply": {
  "execution": { "type": "no_action" }
}
```

**変更後**（モックサーバーを使用）:

```json
"apply": {
  "execution": {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    }
  },
  "store": {
    "application_details_mapping_rules": [
      { "from": "$.request_body", "to": "*" },
      { "from": "$.response_body.application_id", "to": "external_application_id" }
    ]
  }
}
```

> **Note**: idp-server は Docker 内で動作するため、ホストマシンのモックサーバーには `host.docker.internal` でアクセスします。

**確認手順**:

```bash
# 0. モックサーバーの動作確認
curl -s http://localhost:4002/ekyc/apply \
  -X POST -H 'Content-Type: application/json' \
  -d '{"last_name":"test","first_name":"test","birthdate":"2000-01-01"}' | jq .

# 1. 設定更新（jq フィルタで apply プロセスの execution を変更）
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
        { "from": "$.request_body", "to": "*" }
      ]
    }
  } |
  .processes.apply.store.application_details_mapping_rules = [
    { "from": "$.request_body", "to": "*" },
    { "from": "$.response_body.application_id", "to": "external_application_id" }
  ]'

# 2. 設定取得で反映確認
get_iv_config | jq '.processes.apply.execution'

# 3. 身元確認申請（正常系）
iv_apply

# 4. 身元確認申請（エラー系: モックが 500 を返す）
iv_apply "error"

# 5. 申請一覧で external_application_id の保存を確認
iv_list_applications | jq '.list[0].application_details.external_application_id'

# 6. 元に戻す
restore_iv_config
```

> モックサーバーのターミナルにリクエストログが表示されます。ヘッダー・ボディの内容をそこで確認できます。

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 設定更新が成功する | `update_iv_config` がエラーなし |
| 2 | execution.type が `http_request` になっている | `get_iv_config` で確認 |
| 3 | `iv_apply` で申請 → 成功 | HTTP 200 + Application ID |
| 4 | `iv_apply "error"` で申請 → エラー | HTTP 500（モックが500を返す） |
| 5 | `external_application_id` が保存される | 申請一覧で確認 |
| 6 | モックサーバーのログにリクエストが記録される | ターミナルでヘッダー・ボディを確認 |

### 1-2. 認証方式の変更

**OAuth 2.0 認証**（モックサーバーの `/oauth/token` を使用）:

```bash
# OAuth 認証付き http_request に変更
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "auth_type": "oauth2",
      "oauth_authorization": {
        "type": "password",
        "token_endpoint": "http://host.docker.internal:4002/oauth/token",
        "client_id": "ekyc-client",
        "username": "ekyc-user",
        "password": "ekyc-pass",
        "scope": "application"
      },
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    }
  }'

# 申請実行 → モックログで /oauth/token と /ekyc/apply の両方を確認
iv_apply

# OAuth 認証失敗テスト: client_id を "invalid" に設定
update_iv_config '.processes.apply.execution.http_request.oauth_authorization.client_id = "invalid"'
iv_apply  # → 500 エラー（トークン取得が 401 で失敗するため）

# 元に戻す
restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | モックの `/oauth/token` にトークン取得リクエストが飛ぶ | モックログで確認 |
| 2 | `/ekyc/apply` に `Authorization: Bearer {token}` が付与される | モックログで確認 |
| 3 | `client_id: "invalid"` で認証失敗 → 申請失敗 | HTTP 500（トークン取得 401 失敗） |

### 1-3. ヘッダー・ボディマッピングの変更

外部APIの仕様に合わせてリクエストの変換ルールを変更します。

**ボディマッピング例**（フィールド名を変換）:

```bash
# body_mapping_rules を変更して外部API仕様に合わせる
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
        { "from": "$.request_body.last_name", "to": "family_name" },
        { "from": "$.request_body.first_name", "to": "given_name" },
        { "from": "$.request_body.birthdate", "to": "date_of_birth" },
        { "static_value": "identity_verification", "to": "request_type" }
      ]
    }
  }'

# 申請実行
iv_apply

# モックサーバーのターミナルに以下のように表示されるはず:
#   body: {"family_name":"Tanaka","given_name":"Taro","date_of_birth":"1990-01-15","request_type":"identity_verification"}

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | header_mapping_rules で指定したヘッダーがモックに送信される | モックログで確認 |
| 2 | body_mapping_rules でフィールド名が変換される | `last_name` → `family_name` 等 |
| 3 | `static_value` の固定値がボディに含まれる | `request_type: "identity_verification"` |

---

## パターン 2: 申込み項目（request.schema）の変更

**ユースケース**: 業務要件変更で必須項目を追加・削除

### 2-1. 必須項目の変更

**変更後**（電話番号を必須化、メールアドレスを任意化）:

```bash
# request.schema を変更
update_iv_config '
  .processes.apply.request.schema.required = ["last_name", "first_name", "birthdate", "phone_number"] |
  .processes.apply.request.schema.properties.phone_number = {
    "type": "string",
    "pattern": "^[0-9]{10,11}$",
    "store": true
  }'

# テスト 1: phone_number なし → 失敗すべき
iv_apply

# テスト 2: phone_number あり → 成功すべき（iv_apply は4引数目がemail、直接curlで）
curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"last_name":"Tanaka","first_name":"Taro","birthdate":"1990-01-15","phone_number":"09012345678"}'

# テスト 3: phone_number パターン違反 → 失敗すべき
curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"last_name":"Tanaka","first_name":"Taro","birthdate":"1990-01-15","phone_number":"090-1234-5678"}'

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 | 確認内容 |
|---|--------|--------|---------|
| 1 | phone_number なし | HTTP 400 | `phone_number` が必須エラー |
| 2 | phone_number あり（正しい形式） | HTTP 200 | 申請成功 |
| 3 | phone_number パターン不一致 | HTTP 400 | パターンエラー |
| 4 | email_address なしで申請 | HTTP 200 | 任意化により成功 |

### 2-2. バリデーション制約の追加

JSON Schema の各種制約（`minimum/maximum`, `enum`, `pattern`）が正しく動作することを確認します。

```bash
# age, status, postal_code を schema に追加
update_iv_config '
  .processes.apply.request.schema.properties.age = {
    "type": "integer", "minimum": 18, "maximum": 100, "store": true
  } |
  .processes.apply.request.schema.properties.status = {
    "type": "string", "enum": ["active", "inactive"], "store": true
  } |
  .processes.apply.request.schema.properties.postal_code = {
    "type": "string", "pattern": "^[0-9]{3}-[0-9]{4}$", "store": true
  }'

# 共通の必須フィールド
BASE='{"last_name":"Tanaka","first_name":"Taro","birthdate":"1990-01-15","email_address":"test@example.com"}'

# テスト 1: age=17（minimum 未満）→ 400
echo "${BASE}" | jq '. + {"age": 17}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

# テスト 2: age=25（範囲内）→ 200
echo "${BASE}" | jq '. + {"age": 25}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

# テスト 3: status="pending"（enum 外）→ 400
echo "${BASE}" | jq '. + {"status": "pending"}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

# テスト 4: status="active"（enum 内）→ 200
echo "${BASE}" | jq '. + {"status": "active"}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

# テスト 5: postal_code="1234567"（ハイフンなし）→ 400
echo "${BASE}" | jq '. + {"postal_code": "1234567"}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

# テスト 6: postal_code="123-4567"（正しい形式）→ 200
echo "${BASE}" | jq '. + {"postal_code": "123-4567"}' | \
  curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" -d @-

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | age=17（minimum 未満） | HTTP 400 |
| 2 | age=25（範囲内） | HTTP 200 |
| 3 | status="pending"（enum 外） | HTTP 400 |
| 4 | status="active"（enum 内） | HTTP 200 |
| 5 | postal_code="1234567"（ハイフンなし） | HTTP 400 |
| 6 | postal_code="123-4567"（正しい形式） | HTTP 200 |

---

## パターン 3: 審査結果の判定条件（transition）変更

**ユースケース**: 審査ワークフローの判定ロジック変更、判定ステータスの多値化

### 3-1. 基本的な条件変更

**変更前**（boolean フラグで判定）→ **変更後**（文字列ステータスで判定）:

```bash
# transition を文字列ベースに変更
update_iv_config '
  .processes["evaluate-result"].transition = {
    "approved": {
      "any_of": [[
        { "path": "$.request_body.verification_result", "type": "string", "operation": "eq", "value": "approved" }
      ]]
    },
    "rejected": {
      "any_of": [[
        { "path": "$.request_body.verification_result", "type": "string", "operation": "in", "value": ["rejected", "failed", "expired"] }
      ]]
    }
  }'

# テスト 1: 承認
iv_apply
iv_process "evaluate-result" '{"verification_result": "approved"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "approved"

# テスト 2: 拒否（各テストで新しい申請が必要）
iv_apply
iv_process "evaluate-result" '{"verification_result": "rejected"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "rejected"

# テスト 3: expired も拒否に含まれる（in 演算子）
iv_apply
iv_process "evaluate-result" '{"verification_result": "expired"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "rejected"

# テスト 4: どちらにも該当しない
iv_apply
iv_process "evaluate-result" '{"verification_result": "pending"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "applying"（遷移なし）

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 | 結果ステータス |
|---|--------|--------|--------------|
| 1 | `"approved"` | HTTP 200 | approved |
| 2 | `"rejected"` | HTTP 200 | rejected |
| 3 | `"expired"` | HTTP 200 | rejected（`in` 演算子） |
| 4 | `"pending"` | HTTP 200 | applying（遷移なし） |

### 3-2. 複合条件（any_of の AND/OR）

`any_of` は条件グループの配列です。各グループ内の条件は **AND**（全条件一致）、グループ間は **OR**（いずれかのグループ一致）で評価されます。

```
any_of: [
  [条件A, 条件B],   ← グループ1: A AND B
  [条件C]            ← グループ2: C
]
→ (A AND B) OR (C) で判定
```

**スコアと結果の組み合わせで判定**:

```bash
update_iv_config '
  .processes["evaluate-result"].transition = {
    "approved": {
      "any_of": [[
        { "path": "$.request_body.score", "type": "integer", "operation": "gte", "value": 80 },
        { "path": "$.request_body.result", "type": "string", "operation": "eq", "value": "pass" }
      ]]
    },
    "rejected": {
      "any_of": [
        [{ "path": "$.request_body.score", "type": "integer", "operation": "lt", "value": 50 }],
        [{ "path": "$.request_body.result", "type": "string", "operation": "eq", "value": "fail" }]
      ]
    }
  }'

# テスト 1: score=90, result="pass" → approved（グループ内 AND: 両方満たす）
iv_apply
iv_process "evaluate-result" '{"score": 90, "result": "pass"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "approved"

# テスト 2: score=90, result="fail" → rejected（グループ間 OR: result="fail" に該当）
iv_apply
iv_process "evaluate-result" '{"score": 90, "result": "fail"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "rejected"

# テスト 3: score=40, result="pass" → rejected（グループ間 OR: score<50 に該当）
iv_apply
iv_process "evaluate-result" '{"score": 40, "result": "pass"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "rejected"

# テスト 4: score=70, result="pass" → 遷移なし（approved の score>=80 を満たさない、rejected にも該当しない）
iv_apply
iv_process "evaluate-result" '{"score": 70, "result": "pass"}'
iv_list_applications | jq '.list[0] | {id, status}'
# → status: "applying"

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 | 理由 |
|---|--------|--------|------|
| 1 | score=90, result="pass" | approved | グループ内 AND: 両方満たす |
| 2 | score=90, result="fail" | rejected | グループ間 OR: result="fail" に該当 |
| 3 | score=40, result="pass" | rejected | グループ間 OR: score<50 に該当 |
| 4 | score=70, result="pass" | applying（遷移なし） | approved の score>=80 を満たさない、rejected にも該当しない |

### 3-3. 利用可能な演算子一覧

| 演算子 | 説明 | 値の例 |
|--------|------|--------|
| `eq` | 等しい | `"value": "approved"` |
| `ne` | 等しくない | `"value": "rejected"` |
| `gt` | より大きい | `"value": 80` |
| `gte` | 以上 | `"value": 80` |
| `lt` | より小さい | `"value": 50` |
| `lte` | 以下 | `"value": 50` |
| `in` | いずれかに含まれる | `"value": ["a", "b", "c"]` |
| `nin` | いずれにも含まれない | `"value": ["x", "y"]` |
| `exists` | フィールドが存在する | - |
| `missing` | フィールドが存在しない | - |
| `contains` | 文字列を含む | `"value": "pass"` |
| `regex` | 正規表現マッチ | `"value": "^OK-[0-9]+$"` |

---

## パターン 4: verified_claims マッピングの変更

**ユースケース**: 返す検証済み情報のカスタマイズ、trust_framework の変更

### 4-1. trust_framework の変更

```bash
# 1. 認可サーバーの trust_frameworks_supported を更新
update_auth_server '.trust_frameworks_supported = ["jp_aml"]'

# 2. 身元確認設定の verified_claims_mapping_rules を更新
update_iv_config '.result.verified_claims_mapping_rules[0].static_value = "jp_aml"'

# 3. Discovery で確認
get_discovery | jq '{trust_frameworks_supported}'

# 4. 身元確認フロー実行（申請 → 承認）
iv_apply
iv_evaluate approved

# 5. claims パラメータ付きで再認可 → ID Token 取得
start_auth_flow_with_claims "openid+profile+email+transfers+identity_verification_application" \
  '{"id_token":{"verified_claims":{"verification":{"trust_framework":"jp_aml"},"claims":{"given_name":null,"family_name":null,"birthdate":null}}}}'
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

# 6. verified_claims 確認
show_verified_claims

# 7. 元に戻す
restore_auth_server
restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | Discovery の `trust_frameworks_supported` が `["jp_aml"]` | `get_discovery` で確認 |
| 2 | `show_verified_claims` で `trust_framework` が `"jp_aml"` | ID Token に反映 |

### 4-2. マッピング対象クレームの追加

```bash
# 1. claims_in_verified_claims_supported に phone_number を追加
update_auth_server '.claims_in_verified_claims_supported += ["phone_number"]'

# 2. verified_claims_mapping_rules に phone_number を追加
update_iv_config '.result.verified_claims_mapping_rules += [
  {"from": "$.application.application_details.phone_number", "to": "claims.phone_number"}
]'

# 3. Discovery で確認
get_discovery | jq '{claims_in_verified_claims_supported}'

# 4. request.schema に phone_number を追加（store: true で保存対象にする）
update_iv_config '
  .processes.apply.request.schema.properties.phone_number = {
    "type": "string", "store": true
  }'

# 5. phone_number を含めて申請（iv_apply は phone_number 未対応なので curl で）
APPLICATION_ID=$(curl -s -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
  -H "Content-Type: application/json" -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"last_name":"Tanaka","first_name":"Taro","birthdate":"1990-01-15","email_address":"test@example.com","phone_number":"09012345678"}' \
  | jq -r '.id')
echo "Application ID: ${APPLICATION_ID}"

# 6. 承認
iv_evaluate approved

# 7. claims パラメータ付きで再認可 → ID Token 取得
start_auth_flow_with_claims "openid+profile+email+transfers+identity_verification_application" \
  '{"id_token":{"verified_claims":{"verification":{"trust_framework":"eidas"},"claims":{"given_name":null,"family_name":null,"birthdate":null,"phone_number":null}}}}'
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

# 8. verified_claims 確認（phone_number が含まれることを確認）
show_verified_claims

# 9. 元に戻す
restore_auth_server
restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | `claims_in_verified_claims_supported` に `phone_number` が含まれる | `get_discovery` |
| 2 | ID Token の `verified_claims.claims.phone_number` にマッピングされる | `show_verified_claims` |

### 参考: マッピング値の指定方法

| 方式 | JSON | 用途 |
|------|------|------|
| 静的値 | `{ "static_value": "jp_aml", "to": "verification.trust_framework" }` | 固定値（trust_framework等） |
| JSONPath | `{ "from": "$.application.application_details.first_name", "to": "claims.given_name" }` | 申込データからの動的マッピング |

`to` のパスは2系統:
- `verification.*` — 信頼フレームワーク、証拠情報
- `claims.*` — ユーザークレーム（given_name, family_name, address 等）

---

## パターン 5: プロセス構成の変更

**ユースケース**: 2ステップ → 多段プロセスへの拡張

### 5-1. callback-result プロセスの追加（非同期コールバック受付）

外部eKYCサービスが非同期で審査結果を返す場合、`callback-result` プロセスを追加します。

**変更前**（2ステップ: apply → evaluate-result）→ **変更後**（3ステップ: apply → request-ekyc → callback-result）

```bash
# 多段プロセス設定に変更（JSON全体を渡す）
update_iv_config_json '{
  "id": "'${IV_CONFIG_ID}'",
  "type": "authentication-assurance",
  "external_service": "ekyc-vendor",
  "external_application_id_param": "application_id",
  "processes": {
    "apply": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["last_name", "first_name", "birthdate", "email_address"],
          "properties": {
            "last_name": { "type": "string", "maxLength": 255, "store": true },
            "first_name": { "type": "string", "maxLength": 255, "store": true },
            "birthdate": { "type": "string", "format": "date", "store": true },
            "email_address": { "type": "string", "maxLength": 255, "store": true }
          }
        }
      },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "http://host.docker.internal:4002/ekyc/apply",
          "method": "POST",
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        }
      },
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.request_body", "to": "*" },
          { "from": "$.response_body.application_id", "to": "external_application_id" }
        ]
      }
    },
    "request-ekyc": {
      "dependencies": { "required_processes": ["apply"], "allow_retry": false },
      "pre_hook": { "verifications": [{ "type": "process_sequence" }] },
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "http://host.docker.internal:4002/ekyc/{{external_application_id}}/request",
          "method": "POST",
          "path_mapping_rules": [
            { "from": "$.application.application_details.external_application_id", "to": "external_application_id" }
          ],
          "body_mapping_rules": [{ "from": "$.application.application_details", "to": "*" }]
        }
      },
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.response_body.request_id", "to": "ekyc_request_id" }
        ]
      }
    },
    "callback-result": {
      "dependencies": { "required_processes": ["request-ekyc"], "allow_retry": false },
      "pre_hook": { "verifications": [{ "type": "process_sequence" }] },
      "execution": { "type": "no_action" },
      "transition": {
        "approved": { "any_of": [[{ "path": "$.request_body.result", "type": "string", "operation": "eq", "value": "approved" }]] },
        "rejected": { "any_of": [[{ "path": "$.request_body.result", "type": "string", "operation": "in", "value": ["rejected", "failed"] }]] }
      }
    }
  },
  "result": {
    "verified_claims_mapping_rules": [
      { "static_value": "eidas", "to": "verification.trust_framework" },
      { "from": "$.application.application_details.first_name", "to": "claims.given_name" },
      { "from": "$.application.application_details.last_name", "to": "claims.family_name" },
      { "from": "$.application.application_details.birthdate", "to": "claims.birthdate" }
    ]
  }
}'

# テスト 1: apply
iv_apply

# テスト 2: request-ekyc（apply 済みなので成功するはず）
iv_process "request-ekyc"

# テスト 3: callback-result を先に実行（依存関係テスト - request-ekyc 完了前）
# ※ 新しい申請で試す
iv_apply
iv_process "callback-result" '{"result": "approved"}'  # → 400 依存関係エラー

# テスト 4: 正しい順序で実行
iv_process "request-ekyc"
iv_process "callback-result" '{"result": "approved"}'  # → 200 成功

# 結果確認
iv_list_applications | jq '.list[] | {id, status}'

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | apply 成功 | HTTP 200 + Application ID |
| 2 | request-ekyc 成功（apply 済み） | HTTP 200 |
| 3 | callback-result（request-ekyc 未完了） | HTTP 400（依存関係エラー） |
| 4 | callback-result（request-ekyc 完了後） | HTTP 200 + ステータス approved |
| 5 | callback-result のリトライ（allow_retry=false） | HTTP 400（リトライ不可） |

### 5-2. pre_hook の追加（重複申請防止・ユーザークレーム検証）

#### テスト A: duplicate_application（重複申請防止）

```bash
# 新しいユーザーで開始（前のテストの申請が残っているため）
start_auth_flow
register_user
complete_auth_flow

# apply に duplicate_application を追加
update_iv_config '
  .processes.apply.pre_hook = {
    "verifications": [
      { "type": "duplicate_application" }
    ]
  }'

# テスト 1: 初回申請 → 成功
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"

# テスト 2: 重複申請 → 拒否
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"  # → 400
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 初回申請 | HTTP 200 |
| 2 | 同一ユーザーの2回目の申請 | HTTP 400（Duplicate application found） |

#### テスト B: user_claim（ユーザークレーム検証）

```bash
# 新しいユーザーで開始（duplicate_application の影響を避ける）
start_auth_flow
register_user
complete_auth_flow

# apply に user_claim のみを追加
update_iv_config '
  .processes.apply.pre_hook = {
    "verifications": [
      {
        "type": "user_claim",
        "details": {
          "verification_parameters": [{
            "request_json_path": "$.email_address",
            "user_claim_json_path": "email"
          }]
        }
      }
    ]
  }'

# テスト 1: ユーザーの email と一致 → 成功
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"

# テスト 2: ユーザーの email と不一致 → 拒否
iv_apply "Tanaka" "Taro" "1990-01-15" "wrong@example.com"  # → 400

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | ユーザーの email と一致する email_address で申請 | HTTP 200 |
| 2 | ユーザーの email と異なる email_address で申請 | HTTP 400 |

### 5-3. 条件付き verification（condition）

verification に `condition` を付けると、条件に一致する場合のみ検証が実行される。

```bash
# 新しいユーザーで開始
start_auth_flow
register_user
complete_auth_flow

# condition 付き duplicate_application を追加
# @vip.example.com ドメインのメールの場合のみ重複チェックを実行
update_iv_config '
  .processes.apply.pre_hook = {
    "verifications": [
      {
        "type": "duplicate_application",
        "condition": {
          "operation": "regex",
          "path": "$.request_body.email_address",
          "value": ".*@vip\\.example\\.com$"
        }
      }
    ]
  }'

# テスト 1: 条件不一致（通常ドメイン）→ 重複チェックがスキップされる
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"  # → 200（スキップされるので通る）

# テスト 2: 条件一致（VIPドメイン）→ 重複チェックが実行される
# duplicate_application はユーザー単位で判定するため、新しいユーザーで実行
start_auth_flow
register_user
complete_auth_flow

iv_apply "Tanaka" "Taro" "1990-01-15" "user@vip.example.com"   # → 200（初回）
iv_apply "Tanaka" "Taro" "1990-01-15" "user@vip.example.com"   # → 400（重複拒否）

restore_iv_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 条件不一致（通常ドメイン）の2回目申請 | HTTP 200（重複チェックがスキップされる） |
| 2 | 条件一致（VIPドメイン）の2回目申請 | HTTP 400（重複チェックが実行される） |

### 5-4. pre_hook additional_parameters（事前データ取得）

execution の前に別のHTTPリクエストでデータを取得し、本体のリクエストに注入する。

```bash
# apply に pre_hook additional_parameters（HTTP request）を追加
# モックの /oauth/token でトークン取得 → /ekyc/apply の Authorization ヘッダーに注入
update_iv_config '
  .processes.apply.pre_hook = {
    "additional_parameters": [
      {
        "type": "http_request",
        "details": {
          "url": "http://host.docker.internal:4002/oauth/token",
          "method": "POST",
          "static_headers": {
            "Content-Type": "application/x-www-form-urlencoded"
          },
          "static_body": "client_id=ekyc-client&client_secret=ekyc-secret&grant_type=client_credentials"
        }
      }
    ]
  } |
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" },
        {
          "from": "$.pre_hook_additional_parameters[0].response_body.access_token",
          "to": "Authorization",
          "functions": [
            { "name": "format", "args": { "template": "Bearer {{value}}" } }
          ]
        }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    }
  }'

# テスト: 申請 → モックログで /oauth/token → /ekyc/apply の順にリクエストされることを確認
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"

# モックログで Authorization: Bearer mock-token-... ヘッダーが付与されていることを確認

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | pre_hook で `/oauth/token` にリクエストが飛ぶ | モックログで確認 |
| 2 | 取得した access_token が `Authorization: Bearer {token}` に注入される | モックログで `/ekyc/apply` のヘッダーを確認 |
| 3 | pre_hook が失敗した場合（到達不可等）、本体の execution も失敗する | HTTP エラー |

---

## パターン 6: リトライ設定の変更

**ユースケース**: 外部APIの信頼性に合わせた調整

### 6-1. リトライ設定の追加

```bash
# execution を http_request + リトライ設定に変更
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "retry_configuration": {
        "max_retries": 3,
        "retryable_status_codes": [502, 503, 504],
        "idempotency_required": true,
        "backoff_delays": ["PT1S", "PT2S", "PT4S"]
      },
      "body_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
    }
  }'

# 設定確認
get_iv_config | jq '.processes.apply.execution.http_request.retry_configuration'

# テスト 1: リトライ動作確認（モックが1回目 503、2回目 200 を返す）
iv_apply "retry"
# → レスポンス時間が 1 秒以上なら backoff_delays が効いている

# テスト 2: リトライ対象外（モックが 500 を返す、retryable_status_codes に含まれない）
iv_apply "error"
# → 即座にエラーレスポンス

# モックログで idempotency-key ヘッダーを確認
restore_iv_config
```

| 設定項目 | 説明 | 設定例 |
|---------|------|--------|
| `max_retries` | 最大リトライ回数 | `3` |
| `retryable_status_codes` | リトライ対象のHTTPステータスコード | `[502, 503, 504]` |
| `idempotency_required` | 冪等性ヘッダー（Idempotency-Key）付与 | `true` |
| `backoff_delays` | リトライ間隔（ISO 8601 Duration） | `["PT1S", "PT2S", "PT4S"]` |

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 設定が正しく保存される | `get_iv_config` で確認 |
| 2 | `iv_apply "retry"` → リトライで成功 | HTTP 200（レスポンス時間 1秒以上） |
| 3 | `iv_apply "error"` → リトライされない | 即座にエラー |
| 4 | Idempotency-Key ヘッダーが付与される | モックログで確認 |

### 6-2. response_resolve_configs（レスポンス条件による成否判定）

外部APIが HTTP 200 を返しても、レスポンスボディの内容でエラーとみなす場合の設定。


```bash
# execution に response_resolve_configs を追加
update_iv_config '
  .processes.apply.execution = {
    "type": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4002/ekyc/apply",
      "method": "POST",
      "body_mapping_rules": [{ "from": "$.request_body", "to": "*" }],
      "response_resolve_configs": [
        {
          "conditions": [
            { "path": "$.status_code", "operation": "in", "value": [200, 201] },
            { "path": "$.response_body.status", "operation": "eq", "value": "error" }
          ],
          "match_mode": "ALL",
          "mapped_status_code": 400
        }
      ]
    }
  }'

# 設定確認
get_iv_config | jq '.processes.apply.execution.http_request.response_resolve_configs'

# テスト 1: 正常（200 + status: "applied"）→ 条件不一致で通常処理
iv_apply "Tanaka" "Taro" "1990-01-15" "${TEST_EMAIL}"

# テスト 2: 業務エラー（200 + status: "error"）→ 400 にマッピング
# モックサーバーは last_name=business_error で 200 + status:"error" を返す
iv_apply "business_error" "Taro" "1990-01-15" "${TEST_EMAIL}"

restore_iv_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 設定が正しく保存される | `get_iv_config` で確認 |
| 2 | `iv_apply "Tanaka"` → 正常処理 | HTTP 200（status: "applied" は条件不一致） |
| 3 | `iv_apply "business_error"` → エラーにマッピング | HTTP 400（200 + status: "error" が条件一致） |

---

## チェックリスト

### パターン 1: 外部eKYCサービスの接続先変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 1-1 | execution を no_action → http_request に変更して申請が動作する | |
| 1-2a | OAuth 認証で外部APIにトークンが付与される | |
| 1-2b | OAuth 認証失敗（invalid client_id）でエラーになる | |
| 1-3a | header_mapping_rules でヘッダーが変換される | |
| 1-3b | body_mapping_rules でボディフィールドが変換される | |
| 1-3c | static_value が正しく適用される | |

### パターン 2: 申込み項目（request.schema）の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 2-1a | 新しい required 項目が欠けると 400 エラー | |
| 2-1b | 旧 required 項目が任意化され、省略可能 | |
| 2-1c | pattern 制約が正しくバリデーションされる | |
| 2-2a | type, minimum/maximum, enum のバリデーション | |

### パターン 3: 審査結果の判定条件（transition）変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 3-1a | 文字列 eq で approved に遷移する | |
| 3-1b | in 演算子で複数値が rejected に該当する | |
| 3-1c | どちらにも該当しない値は遷移しない | |
| 3-2a | any_of グループ内 AND で全条件一致時のみ approved | |
| 3-2b | any_of でいずれか一致時に rejected | |

### パターン 4: verified_claims マッピングの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 4-1 | trust_framework が ID Token に反映される | |
| 4-2 | 追加したクレームが ID Token の verified_claims.claims に含まれる | |
| 4-3 | static_value と from（JSONPath）の両方が正しく動作する | |

### パターン 5: プロセス構成の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 5-1a | プロセス間の依存関係（required_processes）が機能する | |
| 5-1b | allow_retry=false でリトライが拒否される | |
| 5-1c | allow_retry=true でリトライが許可される | |
| 5-2A | duplicate_application: 初回成功、2回目拒否 | |
| 5-2B | user_claim: email 一致で成功、不一致で拒否 | |
| 5-3a | condition 不一致時は verification がスキップされる | |
| 5-3b | condition 一致時は verification が実行される | |
| 5-4a | pre_hook HTTP request で事前データ取得が実行される | |
| 5-4b | 取得データが header_mapping_rules で注入される | |

### パターン 6: リトライ設定の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 6-1a | リトライ設定が保存・取得できる | |
| 6-1b | retryable_status_codes に該当する場合リトライされる | |
| 6-1c | 非該当のステータスコードではリトライされない | |
| 6-2a | 正常レスポンス（status: "applied"）は条件不一致で通常処理 | |
| 6-2b | 業務エラー（200 + status: "error"）が 400 にマッピングされる | |
