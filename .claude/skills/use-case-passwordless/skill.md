---
name: use-case-passwordless
description: パスワードレス認証ユースケースの設定ガイド。FIDO2/WebAuthn、Passkey、FIDO UAF + CIBA、デバイス登録ルールのヒアリングと設定JSONを提供。
---

# パスワードレス認証

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | パスワードレス方式 | FIDO2/WebAuthn / Passkey / FIDO UAF + CIBA | 認証メソッド設定 |
| 2 | パスワード併用 | パスワードレスのみ / 推奨 / 選択式 | 認証ポリシー |
| 3 | デバイス登録上限 | 無制限 / 3台 / 5台 | テナント `identity_policy_config.authentication_device_rule` |
| 4 | デバイス登録時の身元確認必須 | はい / いいえ | テナント `authentication_device_rule.required_identity_verification` |
| 5 | CIBA設定（FIDO UAF利用時） | 通知タイムアウト, ポーリング間隔 | 認可サーバー拡張設定 |

---

## 設定対象と手順

### 1. デバイス登録ルール（テナント更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "identity_policy_config": {
    "authentication_device_rule": {
      "max_devices": 5,
      "required_identity_verification": false,
      "authentication_type": "none",
      "issue_device_secret": false,
      "device_secret_algorithm": "HS256"
    }
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| 最大デバイス登録数 | `max_devices` | `3`, `5`, `10` |
| 身元確認必須 | `required_identity_verification` | `true`/`false` |
| デバイスシークレット発行 | `issue_device_secret` | `true`/`false` |
| シークレット有効期限（秒） | `device_secret_expires_in_seconds` | `86400`, `2592000` |

### 2. FIDO2/WebAuthn認証メソッド設定

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "id": "{uuid}",
  "type": "fido2",
  "metadata": {
    "rp_id": "{Relying Party ID（ドメイン名）}",
    "rp_name": "{サービス名}",
    "attestation": "none",
    "authenticator_attachment": "platform",
    "user_verification": "required",
    "resident_key": "required"
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| RP ID | `metadata.rp_id` | `example.com` |
| 認証器の種類 | `metadata.authenticator_attachment` | `platform`（内蔵）/ `cross-platform`（外部キー） |
| Passkey対応 | `metadata.resident_key` | `required`（Passkey有効）/ `discouraged` |

### 3. FIDO-UAF認証メソッド設定（CIBA用）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "id": "{uuid}",
  "type": "fido_uaf",
  "metadata": {
    "rp_id": "{Relying Party ID}",
    "rp_name": "{サービス名}"
  }
}
```

### 4. CIBA設定（認可サーバー拡張設定更新）

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "backchannel_token_delivery_modes_supported": ["poll"],
  "backchannel_authentication_endpoint": "https://{domain}/{tenant-id}/v1/backchannel/authentications",
  "extension": {
    "backchannel_authentication_request_expires_in": 300,
    "backchannel_authentication_polling_interval": 5,
    "default_ciba_authentication_interaction_type": "authentication-device-notification"
  }
}
```

### 5. email認証設定（FIDO2ブラウザ利用時に必須）

FIDO2をブラウザUIで利用する場合、**email認証によるユーザー識別（Step 1）が必須**。
未設定だと `Authentication Configuration Not Found (email)` エラーが発生する。

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

ローカル開発では `no-action` タイプ（実際のメール送信なし）を使用:
- テンプレート: `config/templates/use-cases/passwordless-fido2/authentication-config-email-template.json`
- リファレンス: `config/examples/financial-grade/authentication-config/email/no-action.json`

### 6. 認証ポリシー更新（パスワードレス対応）

#### step_definitions（FIDO2ブラウザ利用時に必須）

FIDO2をブラウザUIで利用する場合、email認証（Step 1: ユーザー識別）→ FIDO2認証（Step 2）の順序を定義する。

```json
"step_definitions": [
  { "method": "email", "order": 1, "requires_user": false, "allow_registration": true, "user_identity_source": "email" },
  { "method": "fido2", "order": 2, "requires_user": true, "allow_registration": false, "user_identity_source": "sub" }
]
```

#### device_registration_conditions（デバイス登録時のMFA）

**FIDO2などの認証デバイスの登録はMFAを実施するのが脆弱性対策。**
email認証成功を条件にデバイス登録を許可する。

```json
"device_registration_conditions": {
  "any_of": [
    [{ "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
  ]
}
```

#### ポリシー全体例（パスワードとの選択式 + email MFA）

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [{
    "description": "fido2_or_password",
    "priority": 1,
    "available_methods": ["fido2", "password", "email", "initial-registration"],
    "step_definitions": [
      { "method": "email", "order": 1, "requires_user": false, "allow_registration": true, "user_identity_source": "email" },
      { "method": "fido2", "order": 2, "requires_user": true, "allow_registration": false, "user_identity_source": "sub" }
    ],
    "device_registration_conditions": {
      "any_of": [
        [{ "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
      ]
    },
    "success_conditions": {
      "any_of": [
        [{ "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
        [{ "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
        [{ "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
        [{ "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }]
      ]
    },
    "failure_conditions": {
      "any_of": [
        [{ "path": "$.fido2-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }],
        [{ "path": "$.password-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }],
        [{ "path": "$.email-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }]
      ]
    },
    "lock_conditions": {
      "any_of": [
        [{ "path": "$.fido2-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }],
        [{ "path": "$.password-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }],
        [{ "path": "$.email-authentication.failure_count", "type": "integer", "operation": "gte", "value": 5 }]
      ]
    }
  }]
}
```

### 7. クレーム設定（認可サーバー更新）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。
> 詳細は `use-case-login` スキルの「クレーム設定」セクションを参照。

認可サーバーの `claims_supported` に返したいクレーム一覧を設定する。
標準的な設定は `config/templates/tenant-template.json` を参照。

## FIDO2 ブラウザ動作の設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `signin_page` が `/signin/fido2/` | テナント `ui_config` | `/signin/` のままだとFIDO2 UI画面が表示されない |
| 2 | `base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURL（`api.local.dev`）を設定してしまう |
| 3 | `cors_config` に `allow_headers`, `allow_methods`, `allow_credentials` | テナント `cors_config` | `allow_origins` だけ設定してクロスオリジンリクエストが失敗 |
| 4 | `rp_id` が認証UIオリジンの登録可能ドメイン | FIDO2認証設定 | `auth.local.dev` に対して `auth.local.dev` を設定（正しくは `local.dev`） |
| 5 | `allowed_origins` が `ui_config.base_url` と一致 | FIDO2認証設定 | 不一致で `BadOriginException` が発生 |
| 6 | email認証設定が存在する | authentication-config | 未作成で `Authentication Configuration Not Found (email)` |
| 7 | `step_definitions` でemail→fido2の順序定義 | 認証ポリシー | 未設定だとFIDO2ブラウザUIでユーザー識別ができない |
| 8 | `device_registration_conditions` にMFA条件 | 認証ポリシー | 未設定だとMFAなしでデバイス登録可能（脆弱性） |
| 9 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 10 | setup.sh で `UI_BASE_URL` が `FIDO2_ALLOWED_ORIGIN` より先に定義 | setup.sh | 変数参照順序ミスで `allowed_origins` が空になる |

## 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| FIDO2認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/passwordless-fido2/`
- FIDO2リファレンス: `config/examples/financial-grade/` (FIDO2 + 認証UI構成の実績ある構成)
- WebAuthn: `config/examples/e2e/.../authentication-config/webauthn/`
- FIDO-UAF: `config/examples/e2e/.../authentication-config/fido-uaf/`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/02-registration.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md`
- `documentation/docs/content_02_quickstart/quickstart-06-passwordless.md`

$ARGUMENTS
