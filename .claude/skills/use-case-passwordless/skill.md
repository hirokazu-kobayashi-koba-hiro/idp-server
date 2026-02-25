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

### 5. 認証ポリシー更新（パスワードレス対応）

**パスワードレスのみ**:
```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "fido2_only",
      "priority": 1,
      "available_methods": ["fido2"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**パスワードとの選択式（移行期）**:
```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "fido2_or_password",
      "priority": 1,
      "available_methods": ["fido2", "password", "initial-registration"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
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

### 6. クレーム設定（認可サーバー更新）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。
> 詳細は `use-case-login` スキルの「クレーム設定」セクションを参照。

認可サーバーの `claims_supported` に返したいクレーム一覧を設定する。
標準的な設定は `config/templates/tenant-template.json` を参照。

## 設定例ファイル参照

- WebAuthn: `config/examples/e2e/.../authentication-config/webauthn/`
- FIDO-UAF: `config/examples/e2e/.../authentication-config/fido-uaf/`
- 認証デバイス: `config/examples/e2e/.../authentication-config/authentication-device/`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/02-registration.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md`
- `documentation/docs/content_02_quickstart/quickstart-06-passwordless.md`

$ARGUMENTS
