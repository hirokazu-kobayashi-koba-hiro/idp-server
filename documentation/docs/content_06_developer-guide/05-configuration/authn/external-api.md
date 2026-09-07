# External API認証

このドキュメントは、`external-api-authentication` 方式による外部API連携認証の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

External API認証は、**リクエストボディの `interaction` フィールドで処理を動的にルーティング**し、設定ベースで任意の外部APIと連携する汎用認証方式です。

### external-token との違い

| 項目 | external-token | external-api-authentication |
|------|---------------|---------------------------|
| エンドポイント | `/external-token` | `/external-api-authentication` |
| interaction | 1つ固定 | 複数定義可能（`interaction` フィールドで選択） |
| 用途 | 外部トークンによる認証 | 任意の外部API連携（認証・リスク判定・OTP等） |
| user_resolve | 必須 | interaction ごとに有無を選択 |
| MFA 2段階目 | 非対応 | 対応（ユーザー一致検証あり） |

### 主な用途

- 外部認証サービスへの委譲（LDAP、RADIUS、レガシーシステム等）
- リスクベース認証（外部リスク判定APIの結果で追加認証を要求）
- 外部OTPサービス連携（Challenge-Response パターン）
- MFA の2段階目としての外部本人確認
- CRM / 会員基盤連携（外部会員DBでの認証 + 自動プロビジョニング）

### 処理フロー

```
1. クライアントが POST /external-api-authentication に interaction を指定して送信
2. idp-server が interactions[interaction] の設定を取得
3. JSON Schema バリデーション（設定がある場合）
4. 設定に従って外部APIを呼び出し
5. レスポンスマッピング
6. ユーザー解決（user_resolve 設定がある場合のみ）
7. 認証結果を返却
```

:::warning 同梱のサインイン画面（app-view）は非対応
`external-api-authentication` は**専用のサインイン画面が必要**です。

同梱の汎用画面は `password` / `email` / `sms` / `fido2` / `fido-uaf` のステップだけを描画でき、`external-api` のステップには `Unsupported authentication step` と表示されます（`app-view/src/components/auth/StepRenderer.tsx`）。

interaction ごとに入力項目が `request.schema` でテナント固有に定義されるため、汎用画面は「何を入力させればよいか」を知り得ません。これは実装漏れではなく、この認証方式の性質によるものです。
:::

---

## 設定

External API認証を使用するには、テナントに `type = "external-api-authentication"` の認証設定を登録する必要があります。

### 基本構造

```json
{
  "id": "UUID",
  "type": "external-api-authentication",
  "attributes": {},
  "metadata": {
    "description": "外部API認証の説明"
  },
  "interactions": {
    "interaction名": {
      "request": { "schema": { /* JSON Schema */ } },
      "execution": { "function": "http_request", "http_request": { /* HTTP設定 */ } },
      "user_resolve": { "user_mapping_rules": [ /* ユーザーマッピング */ ] },
      "response": { "body_mapping_rules": [ /* レスポンスマッピング */ ] }
    }
  }
}
```

`interactions` の各キーが interaction 名になり、リクエストボディの `interaction` フィールドで選択されます。

---

## マッピングソースで参照できるパス

`http_request.body_mapping_rules` / `header_mapping_rules` および `response.body_mapping_rules` の `from` で参照できるトップレベルパス:

| パス | 内容 | 利用可能なタイミング |
|------|------|------------------|
| `$.request_body.*` | interaction リクエストボディ | 常時 |
| `$.request_attributes.*` | リクエスト属性（IP, User-Agent 等） | 常時 |
| `$.interaction.*` | `previous_interaction` で保存した前段データ | `previous_interaction` 設定時 |
| `$.user.*` | 認証済みユーザー属性（下記） | 利用者の確立源による（後述） |
| `$.execution_http_request.response_body.*` | 外部APIのレスポンス | `response` マッピングと `user_resolve` のみ |

### `$.request_attributes.*` — リクエスト属性

idp-server が受け取ったリクエスト自体から取れる値です。RP からの申告値ではないため、リスク判定・不正検知の判断材料として使えます。

| パス | 内容 |
|------|------|
| `$.request_attributes.ip_address` | 送信元IP |
| `$.request_attributes.user_agent` | User-Agent |
| `$.request_attributes.resource` | リクエストパス（例: `/{tenant-id}/v1/authorizations/{id}/...`） |
| `$.request_attributes.request_url` | フルURL（リバースプロキシのヘッダーを考慮して解決） |
| `$.request_attributes.action` | HTTPメソッド |
| `$.request_attributes.headers.*` | 受信ヘッダー全体（キーにハイフンを含む場合は `$.request_attributes.headers['User-Agent']`） |

```json
"body_mapping_rules": [
  { "from": "$.request_attributes.ip_address", "to": "event.device.ip" },
  { "from": "$.request_attributes.user_agent", "to": "event.device.user_agent" }
]
```

:::warning headers には Authorization / Cookie も含まれます
`$.request_attributes.headers.*` は受信ヘッダーをそのまま公開します。`{ "from": "$.request_attributes.headers", "to": "*" }` のような一括マッピングを書くと、認証情報を含むヘッダーが外部APIへ送信されます。必要なヘッダーだけを個別に指定してください。
:::

### `$.user.*` — 認証済みユーザー属性

外部APIのリクエストに、認証済みユーザーの属性を送れます（例: リスク判定APIに「誰のリスクか」を渡す）。RP が再送する必要はありません。

```json
"http_request": {
  "url": "https://risk.example.com/assess",
  "method": "POST",
  "body_mapping_rules": [
    { "from": "$.user.sub", "to": "user_id" },
    { "from": "$.user.email", "to": "email" },
    { "from": "$.user.custom_properties.member_rank", "to": "rank" }
  ]
}
```

**利用可能なタイミング（重要）**: `$.user.*` が値を持つかどうかは、**その利用者が誰によって確立されたか**で決まります。ポリシーの `requires_user` とは別の判定です。

| 利用者の確立源 | `$.user.*` | 理由 |
|---|---|---|
| このトランザクションで認証 interaction が成功している | ✅ 値を持つ | 要素を 1 つ通っている |
| CIBA の `login_hint` | ✅ **1 回目の interaction から**値を持つ | backchannel エンドポイントはクライアント認証を通してからリクエストを受理するため、名指しした利用者をクライアントが保証している |
| 認可エンドポイントの `login_hint` | ❌ 空 | 認可エンドポイントにはクライアント認証が無く、誰でも任意の利用者を名指しできるため |
| 利用者が未確立 | ❌ 空 | まだ誰の認証か決まっていない |

空の場合、マッピングしても何も送信されません（エラーにはなりません）。この判定は password / sms / email / external-api のすべての execution で共通です（Issue #1862）。

**公開される属性（allow-list）**: セキュリティのため、外部送信できる属性は以下に限定されます。新しいユーザー属性は明示的に追加するまで露出しません（fail-safe）。

| パス | 内容 |
|------|------|
| `$.user.sub` | 内部ユーザーID |
| `$.user.provider_id` | プロバイダーID |
| `$.user.email` | メールアドレス |
| `$.user.phone_number` | 電話番号 |
| `$.user.name` | 氏名（フルネーム） |
| `$.user.given_name` | 名 |
| `$.user.family_name` | 姓 |
| `$.user.middle_name` | ミドルネーム |
| `$.user.roles` | ロール名の配列 |
| `$.user.custom_properties.*` | テナント管理のカスタム属性 |

`hashed_password` / `credentials` / `verified_claims`（身元確認データ）等の機微情報は外部送信されません。各属性は `body_mapping_rules` で明示的にマッピングした場合のみ送信されます（opt-in）。`$.user.custom_properties` はネスト全体を1ルールで渡すこともできる（`{ "from": "$.user.custom_properties", "to": "props" }`）ため、機微なカスタム属性を含む場合は必要なキーだけを個別にマッピングしてください。

:::warning PII の外部送信とログ
`$.user.email` / `$.user.phone_number` / `$.user.name` 等の PII が外部APIに送信されます。外部APIがエラー（4xx/5xx）を返すと、そのレスポンスボディがログに記録される場合があります。リスク判定APIが応答に PII を含める構成では、ログ集約基盤への PII 混入に注意してください。

:::warning ロール・カスタム属性の完全性
`$.user.roles` / `$.user.custom_properties` は、ユーザーを確立した段の認証方式に依存します。通常のログイン（password 等）や CIBA login_hint で確立されたユーザーは DB からロードされるためこれらを保持しますが、external-api 認証自体を1段階目としてユーザー解決した場合は保持されません。ロール依存の分岐が必要な場合は、認証ポリシー条件（`$.user.*`）側での判定を検討してください。
:::

---

## Interaction の種類

### 1. ユーザー認証型（user_resolve あり）

外部APIでユーザーを認証し、レスポンスからユーザー情報を解決します。

> 1要素目と2要素目で何が変わるか、属性がどう反映されるかの全体像は [ユーザー解決](../../../content_03_concepts/03-authentication-authorization/concept-11-user-resolution.md) を参照してください。ここでは設定項目を扱います。

```json
{
  "password_verify": {
    "request": {
      "schema": {
        "type": "object",
        "required": ["interaction", "username", "password"],
        "properties": {
          "interaction": { "type": "string" },
          "username": { "type": "string", "minLength": 1 },
          "password": { "type": "string", "minLength": 1 }
        }
      }
    },
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://auth.example.com/verify",
        "method": "POST",
        "auth_type": "oauth2",
        "oauth_authorization": {
          "type": "client_credentials",
          "token_endpoint": "https://auth.example.com/token",
          "client_id": "idp-client",
          "client_secret": "secret",
          "scope": "authentication"
        },
        "body_mapping_rules": [
          { "from": "$.request_body.username", "to": "username" },
          { "from": "$.request_body.password", "to": "password" }
        ]
      }
    },
    "user_resolve": {
      "identity_match_field": "$.email",
      "user_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" },
        { "from": "$.execution_http_request.response_body.name", "to": "name" },
        { "static_value": "external-auth-provider", "to": "provider_id" }
      ]
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" }
      ]
    }
  }
}
```

> **MFA 2段階目で使う場合**: `identity_match_field` を設定して、1段階目のユーザーとの一致検証を有効にしてください。

#### 解決結果は既存ユーザーがベース

既存ユーザーが見つかった場合、`user_resolve` の結果は **既存ユーザー + `user_mapping_rules` の出力** です（`User#enrichWith`）。マッピングの出力だけを持つユーザーにはなりません。

この結果がそのまま認可グラントに写し取られ、ID Token / アクセストークンのクレームがそこから作られます。マッピングの出力だけを結果にすると、外部APIが今回返さなかった属性（他方式が書いた `custom_properties`、`roles`、`verified_claims`）がそのセッションのトークンから欠落します（Issue #1792）。

| フィールド | 挙動 |
|------|------|
| `sub` / `provider_id` / `external_user_id` | 既存値のまま（`updateWith` で immutable 扱い） |
| `status` | 既存値のまま。`user_mapping_rules` から変更できません |
| `verified_claims` / `hashedPassword` / `credentials` / `permissions` | 既存値のまま（patch 対象外） |
| 標準クレーム（`name` / `email` 等） | ルールが値を生成すれば上書き、しなければ既存値を保持 |
| `custom_properties` | キー単位でマージ（下記） |

:::warning 認証中の管理API更新は巻き戻ることがあります
既存ユーザーの読み取りは1要素目の時点で、データベースへの保存は認可成立後です。その間に管理APIで同じユーザーを更新すると、認証開始時点の値で上書きされる場合があります。
:::

#### custom_properties の適用ルール

`user_mapping_rules` が `custom_properties.*` に書いた値は、**キー単位でマージ**されます。

| 対象 | 挙動 |
|------|------|
| ルールが生成したキー | 上書き |
| ルールが生成しなかった既存キー | 保持 |

`custom_properties` は Federation・外部API認証・MFA 2段階目・身元確認が書き込む共有のフラットなキー集合です。各認証方式は自分が生成するキーしか知らないため、全置換にすると後から通った方式が他方式のキーを消してしまいます。身元確認の [`custom_properties_update_policy`](../identity-verification.md) と同じ規則です。

:::warning この経路ではキーを削除できません
マージのため、外部IdPが返さなくなった属性や条件を満たさなくなったランク等は残り続けます。削除は管理APIのユーザー更新で行ってください。`PUT`（更新）はリクエストの内容でユーザーを組み直すため全置換、`PATCH`（部分更新）も `custom_properties` を含めた場合はそのキー集合で全置換されます。
:::

#### 認証結果は interaction ごとに記録されます

1つの設定に複数の interaction を持つため、認証結果には**合計と interaction ごとの内訳の両方**が記録されます。

```json
"external-api-authentication": {
  "success_count": 3,
  "interactions": {
    "password_verify": { "success_count": 1, "failure_count": 0, "call_count": 1 },
    "risk_check":     { "success_count": 2, "failure_count": 0, "call_count": 2 }
  }
}
```

`authentication-policy` からは内訳を名指しで参照できます。

```json
{ "path": "$.external-api-authentication.interactions.risk_check.success_count",
  "operation": "gte", "value": 1 }
```

合計だけを使うと「1つの interaction を複数回呼ぶ」でも条件が成立してしまうため、**多段フローで各段を必須にしたい場合は内訳を使ってください**。詳細は [Authentication Policy の設定](../authentication-policy.md#interactionsinteraction-ごとの内訳) を参照してください。

### 2. 補助判定型（user_resolve なし）

外部APIの結果だけを返します。リスク判定やステータスチェック等に使用します。

:::warning セキュリティ: 認証ポリシーで interaction を明示する
`user_resolve` なしの補助判定型は、`requires_user: false` の場合ユーザーを確立せず `SUCCESS` を返します。認証ポリシーの `success_conditions` を `external-api-authentication.success_count >= 1` のような interaction 非依存の条件にすると、補助判定 interaction 単独でフローが完了してしまいます。1段階目の認証を必須にするには、`step_definitions` で認証メソッドの順序と `requires_user` を明示し、`success_conditions` は各 interaction の成功を個別に要求してください。
:::

```json
{
  "risk_check": {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://risk.example.com/assess",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.request_body.session_context", "to": "context" }
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" },
        { "from": "$.execution_http_request.response_body.risk_level", "to": "risk_level" }
      ]
    }
  }
}
```

### 3. Challenge-Response 型（previous_interaction）

2つの interaction を組み合わせて、Challenge → Verify のフローを実現します。

```json
{
  "otp_send": {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "https://otp.example.com/send",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.request_body.phone_number", "to": "phone" }
        ]
      },
      "http_request_store": {
        "key": "otp_send",
        "interaction_mapping_rules": [
          { "from": "$.response_body.transaction_id", "to": "transaction_id" }
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body.transaction_id", "to": "transaction_id" }
      ]
    }
  },
  "otp_verify": {
    "execution": {
      "function": "http_request",
      "previous_interaction": { "key": "otp_send" },
      "http_request": {
        "url": "https://otp.example.com/verify",
        "method": "POST",
        "body_mapping_rules": [
          { "from": "$.interaction.transaction_id", "to": "transaction_id" },
          { "from": "$.request_body.code", "to": "verification_code" }
        ]
      }
    },
    "user_resolve": {
      "user_mapping_rules": [
        { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
        { "from": "$.execution_http_request.response_body.email", "to": "email" },
        { "static_value": "otp-provider", "to": "provider_id" }
      ]
    }
  }
}
```

**ポイント**:
- `http_request_store`: 1つ目の interaction のレスポンスを保存
- `previous_interaction`: 2つ目の interaction から保存データを参照
- `$.interaction.*`: 保存されたデータへのアクセスパス

---

## 利用方法

### エンドポイント

```http
POST /{tenantId}/v1/authorizations/{authorizationId}/external-api-authentication
Content-Type: application/json
```

### リクエスト例

```json
{
  "interaction": "password_verify",
  "username": "user@example.com",
  "password": "secret"
}
```

`interaction` フィールドで、設定の `interactions` キーを指定します。

### Challenge-Response の場合

```
// Step 1: Challenge
POST /external-api-authentication
{ "interaction": "otp_send", "phone_number": "+819012345678" }
→ { "transaction_id": "abc-123" }

// Step 2: Verify
POST /external-api-authentication
{ "interaction": "otp_verify", "code": "123456" }
→ { "user": { "sub": "...", "email": "..." } }
```

---

## MFA での利用（2段階目）

認証ポリシーで `external-api` を2段階目に設定できます。

:::info 複数の interaction で 1st / 2nd factor を分ける場合
下の例のように `method` だけを書いた定義は、**この設定内の全 interaction に適用されます**（どの interaction を実行しても `method` は `"external-api"` のため）。

interaction ごとに `requires_user` を変えたい場合は、step 定義に `interaction` を指定してください。書き忘れた interaction はチェックが行われないため、`method` 単位の既定を1つ置く形を推奨します。

→ [step_definitions の interaction 単位の定義](../authentication-policy.md#interaction-単位の定義)
:::

### 認証ポリシー設定

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "password + external API verification",
      "priority": 1,
      "available_methods": ["password", "external-api", "initial-registration"],
      "step_definitions": [
        {
          "method": "password",
          "order": 1,
          "requires_user": false,
          "user_identity_source": "username"
        },
        {
          "method": "external-api",
          "order": 2,
          "requires_user": true
        }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

### セキュリティ: ユーザー一致検証（identity_match_field）

2段階目（`requires_user: true`）では以下のセキュリティチェックが行われます:

1. **1段階目未完了チェック**: トランザクションに認証済みユーザーがいなければ `400 user_not_found`
2. **ユーザー一致検証**: `identity_match_field` で指定した JSONPath のフィールドで、1段階目のユーザーと外部APIが返したユーザーを比較。不一致なら `400 user_identity_mismatch`
3. **一致する場合のみ**: 1段階目のユーザーをそのまま返す（外部APIのユーザー情報では上書きしない）

#### identity_match_field の設定

`user_resolve` 内に JSONPath 式で比較フィールドを指定します:

```json
{
  "user_resolve": {
    "identity_match_field": "$.email",
    "user_mapping_rules": [
      { "from": "$.execution_http_request.response_body.email", "to": "email" },
      { "..." : "..." }
    ]
  }
}
```

| identity_match_field | 比較対象 | ユースケース |
|---------------------|---------|------------|
| `$.email` | メールアドレス | パスワード認証委譲の2段階目 |
| `$.external_user_id` | 外部ユーザーID | 外部システム連携の2段階目 |
| `$.phone_number` | 電話番号 | SMS検証の2段階目 |
| `$.custom_properties.member_id` | カスタムプロパティ | 会員基盤連携の2段階目 |
| 未設定 | 比較スキップ | リスク分析API等（ユーザー識別不要） |

**未設定の場合**: `identity_match_field` を設定しないと、1段階目のユーザーの存在チェック（`hasUser`）のみが行われ、フィールド比較はスキップされます。リスク判定APIなど、ユーザー識別を返さない外部APIを2段階目に使う場合に適しています。

:::warning ユーザー識別を返す外部APIでは必須
外部APIがユーザー識別情報（メール・電話番号・外部ユーザーID等）を返す2段階目では、`identity_match_field` を**必ず設定**してください。未設定だと、外部APIが1段階目とは別のユーザーを返しても比較されず、ユーザー入れ替え攻撃を検知できません。比較スキップは、識別情報を一切返さない補助判定APIに限定してください。
:::

#### user_resolve なしの2段階目

`user_resolve` 自体を設定しない場合でも、`requires_user: true` のとき:
- 1段階目のユーザーの存在チェックは実行される（スキップ攻撃防止）
- 外部APIの結果だけを返し、1段階目のユーザーをそのまま引き継ぐ

---

## セキュリティイベント

interaction ごとに動的なセキュリティイベントが発行されます。

| ケース | イベント名 |
|--------|-----------|
| `password_verify` 成功 | `external_api_password_verify_success` |
| `password_verify` 失敗 | `external_api_password_verify_failure` |
| `risk_check` 成功 | `external_api_risk_check_success` |
| interaction 未指定 / 未登録 | `external_api_authentication_failure` |

形式: `external_api_{interaction名}_{success|failure}`

レスポンスボディにも `interaction` フィールドが含まれるため、ログやWebhookでの識別が可能です。

---

## エラーレスポンス

| エラー | ステータス | 説明 |
|--------|----------|------|
| `invalid_request` | 400 | `interaction` フィールド未指定 |
| `invalid_request` | 400 | 未登録の `interaction` 名 |
| `invalid_request` | 400 | JSON Schema バリデーション失敗 |
| `user_not_found` | 400 | 2段階目で1段階目の認証済みユーザーが不在 |
| `user_identity_mismatch` | 400 | 2段階目で外部APIのユーザーと1段階目のユーザーが不一致 |
| (外部APIのステータス) | 透過 | 外部APIが返した 401, 429, 500 等がそのまま返る |

:::warning レスポンスの素通しに注意
`response.body_mapping_rules` を設定しない interaction は、外部APIのレスポンスボディをそのままクライアントに返します。外部APIが内部情報（スタックトレース、内部ID、他ユーザーの情報等）を含むレスポンスを返す場合、`body_mapping_rules` で必要なフィールドだけを明示的に抽出してください。
:::

---

## 外部API認証方式

外部APIへのリクエストで使用できる認証方式:

| auth_type | 説明 |
|-----------|------|
| `oauth2` | OAuth 2.0 Bearer Token（client_credentials / password フロー） |
| `hmac_sha256` | HMAC SHA-256 署名 |
| `none` | 認証なし |

### Basic 認証

`auth_type` に `basic` はありません。Basic 認証はリクエストごとに変わる要素がないため、`header_mapping_rules` で組み立てます。

```json
"http_request": {
  "auth_type": "none",
  "header_mapping_rules": [
    {
      "static_value": "<client_id>:<client_secret>",
      "to": "Authorization",
      "functions": [
        { "name": "base64" },
        { "name": "format", "args": { "template": "Basic {{value}}" } }
      ]
    }
  ]
}
```

`base64` 関数があるため、設定には生の資格情報をそのまま置けます（base64 済みの派生値を別途管理する必要はありません）。

:::warning auth_type は手組みの Authorization を上書きします
`auth_type` が `none` 以外の場合、`header_mapping_rules` で組んだ `Authorization` ヘッダーは**上書きされます**。`HttpRequestBuilder` はマッピング由来のヘッダーを構築した後に、`oauth2` なら `Bearer <token>`、`hmac_sha256` なら署名値を `Authorization` に put するためです。

Basic 認証を使う場合は `auth_type` を `none` にしてください。
:::

---

## ID Token の amr クレーム

External API認証が成功すると、ID Token の `amr`（Authentication Methods References）クレームに `external-api` が含まれます。

MFA の場合は両方の認証方式が含まれます:

```json
{
  "amr": ["password", "external-api"]
}
```

---

## 完全な設定例

### パスワード認証委譲 + リスク判定

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "external-api-authentication",
  "attributes": {},
  "metadata": {
    "description": "External password auth + risk assessment"
  },
  "interactions": {
    "password_verify": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["interaction", "username", "password"],
          "properties": {
            "interaction": { "type": "string" },
            "username": { "type": "string", "minLength": 1, "maxLength": 256 },
            "password": { "type": "string", "minLength": 1, "maxLength": 128 }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://auth.example.com/verify",
          "method": "POST",
          "header_mapping_rules": [
            { "static_value": "application/json", "to": "Content-Type" }
          ],
          "body_mapping_rules": [
            { "from": "$.request_body.username", "to": "username" },
            { "from": "$.request_body.password", "to": "password" }
          ]
        }
      },
      "user_resolve": {
        "user_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" },
          { "from": "$.execution_http_request.response_body.name", "to": "name" },
          { "static_value": "auth-service", "to": "provider_id" }
        ]
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" }
        ]
      }
    },
    "risk_check": {
      "request": {
        "schema": {
          "type": "object",
          "required": ["interaction"],
          "properties": {
            "interaction": { "type": "string" },
            "device_fingerprint": { "type": "string" },
            "ip_address": { "type": "string" }
          }
        }
      },
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://risk.example.com/assess",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body.device_fingerprint", "to": "fingerprint" },
            { "from": "$.request_body.ip_address", "to": "ip" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" },
          { "from": "$.execution_http_request.response_body.risk_level", "to": "risk_level" }
        ]
      }
    }
  }
}
```

---

## 関連ドキュメント

- [External Token認証](./external-token.md) - 外部トークンによる認証（単一 interaction）
- [認証ポリシー設定](../authentication-policy.md) - MFA・ステップアップ認証の設定
- [Mapping Functions 開発ガイド](../04-implementation-guides/impl-20-mapping-functions.md) - マッピング関数の詳細
- [HTTP Request Executor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTPリクエスト実行の詳細

---

**情報源**:
- `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/external_api/ExternalApiAuthenticationInteractor.java`
- `e2e/src/tests/usecase/advance/advance-13-external-api-authentication.test.js`
- `e2e/src/tests/security/external-api-authentication-2nd-factor-bypass.test.js`

**最終更新**: 2026-03-24
**作成者**: Claude Code（AI開発支援）
