---
name: use-case-mfa-fido-uaf
description: MFA FIDO-UAF（パスワード + デバイス生体認証）ユースケースの設定ガイド。FIDO-UAFデバイス登録、Push通知、login_hint付き認可コードフロー、認証ステータスポーリングのヒアリングと設定JSONを提供。
---

# MFA FIDO-UAF（パスワード + デバイス生体認証）

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | FIDO-UAFサーバーURL | URL（デフォルト: `http://host.docker.internal:4000`） | `authentication-config-fido-uaf.json` |
| 2 | デバイスシークレットアルゴリズム | `HS256`（デフォルト）/ `HS384` / `HS512` | `authentication_device_rule.device_secret_algorithm` |
| 3 | 最大デバイス数 | 数値（デフォルト: 5） | `authentication_device_rule.max_devices` |
| 4 | Push通知サービス | FCM（デフォルト）/ APNS | `authentication-config-device-notification.json` |
| 5 | パスワードポリシー | 最小文字数、文字種制約 | テナント `password_policy` |
| 6 | アカウントロック閾値 | 回数（デフォルト: 5） | 認証ポリシー `lock_conditions` |

---

## 環境変数マッピング

### FIDO-UAF / デバイス設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| FIDO-UAFサーバーURL | `FIDO_UAF_SERVER_URL` | `http://host.docker.internal:4000` |
| デバイスシークレットアルゴリズム | `DEVICE_SECRET_ALGORITHM` | `HS256` |
| デバイスシークレット有効期限 | `DEVICE_SECRET_EXPIRES_IN` | `31536000` |
| 最大デバイス数 | `MAX_DEVICES` | `5` |

### 共通設定

| 環境変数 | デフォルト値 | 説明 |
|---------|-------------|------|
| `ORGANIZATION_NAME` | `mfa-fido-uaf` | 組織名 |
| `SESSION_TIMEOUT_SECONDS` | `86400` | セッション有効期限 |
| `PASSWORD_MIN_LENGTH` | `8` | パスワード最小文字数 |
| `ACCESS_TOKEN_DURATION` | `3600` | AT有効期限 |
| `ID_TOKEN_DURATION` | `3600` | IDT有効期限 |
| `REFRESH_TOKEN_DURATION` | `86400` | RT有効期限 |

---

## 設定対象と手順

### 1. テナント作成（FIDO-UAF + authentication_device_rule付き）

認可サーバーに以下の設定が含まれること:

```json
{
  "grant_types_supported": [
    "authorization_code",
    "refresh_token",
    "urn:ietf:params:oauth:grant-type:jwt-bearer"
  ],
  "scopes_supported": [
    "openid", "profile", "email",
    "claims:authentication_devices"
  ],
  "extension": {
    "custom_claims_scope_mapping": true
  }
}
```

テナントに `authentication_device_rule` が含まれること:

```json
{
  "identity_policy_config": {
    "authentication_device_rule": {
      "max_devices": 5,
      "required_identity_verification": false,
      "authentication_type": "device_secret_jwt",
      "issue_device_secret": true,
      "device_secret_algorithm": "HS256",
      "device_secret_expires_in_seconds": 31536000
    }
  }
}
```

### 2. FIDO-UAF認証設定

FIDO-UAFサーバーに接続する認証設定:

```json
{
  "type": "fido-uaf",
  "interactions": {
    "fido-uaf-registration-challenge": { "...registration challenge..." },
    "fido-uaf-registration": { "...registration complete..." },
    "fido-uaf-authentication-challenge": { "...authentication challenge..." },
    "fido-uaf-authentication": { "...authentication complete..." }
  }
}
```

### 3. デバイス通知設定（FCM）

```json
{
  "type": "authentication-device-notification",
  "interactions": {
    "authentication-device-notification": {
      "execution": {
        "function": "fcm_notification",
        "notification_template": {
          "title": "認証リクエスト",
          "body": "アプリを開いて認証してください"
        }
      }
    }
  }
}
```

### 4. クライアント設定（JWT Bearer grant + device federation）

```json
{
  "grant_types": [
    "authorization_code",
    "refresh_token",
    "urn:ietf:params:oauth:grant-type:jwt-bearer"
  ],
  "scope": "openid profile email claims:authentication_devices",
  "extension": {
    "available_federations": [{
      "issuer": "device",
      "type": "device",
      "subject_claim_mapping": "sub",
      "jwt_bearer_grant_enabled": true
    }]
  }
}
```

### 5. 認証ポリシー（2つのポリシー）

#### ポリシー1: login_hint + FIDO-UAFデバイス認証（優先度高）

- `priority: 10`
- `conditions.acr_values: ["urn:idp:acr:device"]`
- `available_methods`: authentication-device-notification, authentication-device-deny, fido-uaf
- login_hintでユーザー事前解決 → Push通知 → FIDO-UAF認証

#### ポリシー2: パスワードフォールバック（優先度低）

- `priority: 1`
- `conditions: {}`（無条件）
- `available_methods`: password, email, initial-registration, fido-uaf
- login_hintなしの場合はパスワード認証で対応

---

## 認証フローパターン

### パターン1: login_hint付き（FIDO-UAFデバイス認証）

```
認可リクエスト（login_hint=sub:{userId}, acr_values=urn:idp:acr:device）
  → User 事前解決 + AuthenticationDevice セット
  → SPA: デバイス通知送信（オプション）
  → Device: Push通知受信 or ポーリングで認証検知
  → Device: FIDO-UAF 生体認証
  → SPA: authentication-status ポーリング → success
  → authorize → トークン発行
```

### パターン2: login_hintなし（パスワードフォールバック）

```
認可リクエスト（login_hintなし）
  → SPA: パスワード認証
  → authorize → トークン発行
```

### パターン3: パスワード + FIDO-UAF（MFA）

```
認可リクエスト（login_hintなし）
  → パスワード認証（1st factor）
  → デバイス通知（2nd factor）
  → FIDO-UAF認証
  → authentication-status: success
  → authorize → トークン発行
```

---

## 制限事項

### login_hintなし + FIDO-UAFのみはサポートしない

デバイスへのPush通知送信（`authentication-device-notification`）は、`AuthenticationTransaction`にユーザーが解決されていることを前提としている。`login_hint`なしでは通知APIが `"User does not exist"` エラーとなり、フローが成立しない。

この仕様はPush通知疲労攻撃（Push Notification Fatigue Attack）を防ぐ意図も含んでいる。ログイン画面でメールアドレス等を入力するだけでPush通知を送信できてしまうと、攻撃者が大量通知によりユーザーの誤承認を誘発できるため。

### Push通知はオプション

Push通知を受け取れない環境（通知オフ等）でも、デバイスが自発的に認証トランザクションをポーリングすることでフローは成立する。

---

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `grant_types_supported` に `urn:ietf:params:oauth:grant-type:jwt-bearer` が含まれる | 認可サーバー | JWT Bearer Grant 未追加でデバイス認証不可 |
| 3 | クライアントの `grant_types` に JWT Bearer が含まれる | クライアント | 認可サーバーに追加してもクライアントに設定し忘れ |
| 4 | `authentication_device_rule` が設定済み | テナント `identity_policy_config` | 未設定だとデバイス登録・device_secret 発行不可 |
| 5 | `issue_device_secret: true` が設定済み | テナント `authentication_device_rule` | false だと device_secret が発行されない |
| 6 | FIDO-UAF認証設定が作成済み | 認証設定 | 未作成だと FIDO-UAF 登録・認証不可 |
| 7 | デバイス通知設定が作成済み | 認証設定 | 未作成でPush通知送信不可 |
| 8 | クライアントに `available_federations` (device) が設定済み | クライアント `extension` | 未設定だと device_secret_jwt 認証不可 |
| 9 | `scopes_supported` に `claims:authentication_devices` が含まれる | 認可サーバー | 未設定だとデバイス情報がスコープに含まれない |
| 10 | `custom_claims_scope_mapping: true` が設定済み | 認可サーバー `extension` | 未設定だと `claims:*` スコープが機能しない |
| 11 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 12 | 認証ポリシーに `device_fido_uaf_authentication`（priority高）と `password_fallback`（priority低）の2つ | 認証ポリシー | ポリシー1つだけで login_hint 付きフローが動かない |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

---

## テンプレートファイル参照

- テンプレート: `config/templates/use-cases/mfa-fido-uaf/`
- FIDO-UAF設定: `config/templates/use-cases/mfa-fido-uaf/authentication-config-fido-uaf.json`
- デバイス通知設定: `config/templates/use-cases/mfa-fido-uaf/authentication-config-device-notification.json`
- 認証ポリシー: `config/templates/use-cases/mfa-fido-uaf/authentication-policy.json`
- 検証スクリプト: `config/templates/use-cases/mfa-fido-uaf/verify.sh`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/02-registration.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md`
- `documentation/docs/content_02_quickstart/quickstart-14-mfa-fido-uaf.md`

$ARGUMENTS
