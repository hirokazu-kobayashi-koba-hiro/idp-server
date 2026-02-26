---
name: use-case-ciba
description: CIBA（Client-Initiated Backchannel Authentication）ユースケースの設定ガイド。FIDO-UAFデバイス登録、device_secret_jwt認証、CIBA配信モード、ポーリング設定のヒアリングと設定JSONを提供。
---

# CIBA（モバイル承認 + FIDO-UAF）

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | Delivery Mode | `poll`（推奨）/ `ping` / `push` | 認可サーバー `backchannel_token_delivery_modes_supported` |
| 2 | User Code 要否 | `true` / `false`（デフォルト） | 認可サーバー `backchannel_user_code_parameter_supported` |
| 3 | Polling Interval | 秒数（デフォルト: 5） | 認可サーバー拡張 `backchannel_authentication_polling_interval` |
| 4 | リクエスト有効期限 | 秒数（デフォルト: 120） | 認可サーバー拡張 `backchannel_authentication_request_expires_in` |
| 5 | FIDO-UAFサーバーURL | URL（デフォルト: `http://host.docker.internal:4000`） | `authentication-config-fido-uaf.json` |
| 6 | デバイスシークレットアルゴリズム | `HS256`（デフォルト）/ `HS384` / `HS512` | `authentication_device_rule.device_secret_algorithm` |
| 7 | 最大デバイス数 | 数値（デフォルト: 5） | `authentication_device_rule.max_devices` |

---

## 環境変数マッピング

### CIBA 固有設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| Delivery Mode | `CIBA_DELIVERY_MODE` | `poll` |
| リクエスト有効期限 | `CIBA_REQUEST_EXPIRES_IN` | `120` |
| Polling Interval | `CIBA_POLLING_INTERVAL` | `5` |
| User Code 要否 | `CIBA_USER_CODE_REQUIRED` | `false` |

### FIDO-UAF / デバイスシークレット設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| FIDO-UAFサーバーURL | `FIDO_UAF_SERVER_URL` | `http://host.docker.internal:4000` |
| デバイスシークレットアルゴリズム | `DEVICE_SECRET_ALGORITHM` | `HS256` |
| デバイスシークレット有効期限 | `DEVICE_SECRET_EXPIRES_IN` | `31536000` |
| 最大デバイス数 | `MAX_DEVICES` | `5` |

### 共通設定（login-password-only と同じ）

| 環境変数 | デフォルト値 | 説明 |
|---------|-------------|------|
| `ORGANIZATION_NAME` | `ciba` | 組織名 |
| `SESSION_TIMEOUT_SECONDS` | `86400` | セッション有効期限 |
| `PASSWORD_MIN_LENGTH` | `8` | パスワード最小文字数 |
| `ACCESS_TOKEN_DURATION` | `3600` | AT有効期限 |
| `ID_TOKEN_DURATION` | `3600` | IDT有効期限 |
| `REFRESH_TOKEN_DURATION` | `86400` | RT有効期限 |

---

## 設定対象と手順

### 1. テナント作成（CIBA + FIDO-UAF + authentication_device_rule付き）

認可サーバーに以下の設定が含まれること:

```json
{
  "grant_types_supported": [
    "authorization_code",
    "refresh_token",
    "urn:openid:params:grant-type:ciba",
    "urn:ietf:params:oauth:grant-type:jwt-bearer"
  ],
  "scopes_supported": [
    "openid", "profile", "email",
    "claims:authentication_devices"
  ],
  "backchannel_authentication_endpoint": "${BASE_URL}/${PUBLIC_TENANT_ID}/v1/backchannel/authentications",
  "backchannel_token_delivery_modes_supported": ["poll"],
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

FIDO-UAFモックサーバーに接続する認証設定:

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

### 3. クライアント設定（CIBA + JWT Bearer grant + device federation）

```json
{
  "grant_types": [
    "authorization_code",
    "refresh_token",
    "urn:openid:params:grant-type:ciba",
    "urn:ietf:params:oauth:grant-type:jwt-bearer"
  ],
  "scope": "openid profile email claims:authentication_devices",
  "extension": {
    "available_federations": [{
      "issuer": "device",
      "type": "device",
      "subject_claim_mapping": "sub",
      "jwt_bearer_grant_enabled": true
    }],
    "default_ciba_authentication_interaction_type": "authentication-device-notification-no-action"
  }
}
```

### 4. 認証ポリシー（2つ必要）

#### OAuth フロー用（ブラウザ認証）
- `flow: "oauth"`
- password + fido-uaf + initial-registration

#### CIBA フロー用（デバイス認証）
- `flow: "ciba"`
- fido-uaf認証のみ
- デバイス側で FIDO-UAF 認証を実行

---

## CIBA フロー概要

```
=== Phase 1: ユーザー登録 + デバイス登録 ===
1. ユーザー → Webでアカウント登録（initial-registration）
2. アプリ → FIDO-UAF登録チャレンジ取得
3. アプリ → FIDO-UAF登録完了 → device_id + device_secret 発行
4. 認可完了 → トークン交換 → ユーザーとデバイスがDBに永続化

=== Phase 2: CIBAフロー ===
5. クライアント → backchannel_authentication_endpoint
   login_hint: "device:${DEVICE_ID},idp:idp-server"
   → auth_req_id を取得

6. デバイス → device_secret_jwt で認証トランザクション取得
   GET /authentication-devices/${DEVICE_ID}/authentications
   Authorization: Bearer ${device_secret_jwt}

7. デバイス → FIDO-UAF認証チャレンジ + 認証完了

8. クライアント → token_endpoint（auth_req_id でポーリング）
   → 認証完了後、access_token を取得
```

---

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `backchannel_authentication_endpoint` が設定済み | 認可サーバー | エンドポイント未設定で CIBA リクエスト不可 |
| 3 | `grant_types_supported` に `urn:openid:params:grant-type:ciba` が含まれる | 認可サーバー | grant_type 未追加で CIBA トークン交換不可 |
| 4 | `grant_types_supported` に `urn:ietf:params:oauth:grant-type:jwt-bearer` が含まれる | 認可サーバー | JWT Bearer Grant 未追加でデバイス認証不可 |
| 5 | クライアントの `grant_types` に CIBA + JWT Bearer が含まれる | クライアント | 認可サーバーに追加してもクライアントに設定し忘れ |
| 6 | `backchannel_token_delivery_mode` がクライアントに設定済み | クライアント | 未設定だと CIBA リクエスト時にエラー |
| 7 | CIBA 認証ポリシー（`flow: "ciba"`）が作成済み | 認証ポリシー | OAuth ポリシーのみで CIBA ポリシー未作成 |
| 8 | CIBA ポリシーの `available_methods` に `fido-uaf` が含まれる | 認証ポリシー | password のままだとデバイス認証不可 |
| 9 | `authentication_device_rule` が設定済み | テナント `identity_policy_config` | 未設定だとデバイス登録・device_secret 発行不可 |
| 10 | `issue_device_secret: true` が設定済み | テナント `authentication_device_rule` | false だと device_secret が発行されない |
| 11 | FIDO-UAF認証設定が作成済み | 認証設定 | 未作成だと FIDO-UAF 登録・認証不可 |
| 12 | クライアントに `available_federations` (device) が設定済み | クライアント `extension` | 未設定だと device_secret_jwt 認証不可 |
| 13 | `scopes_supported` に `claims:authentication_devices` が含まれる | 認可サーバー | 未設定だとデバイス情報がスコープに含まれない |
| 14 | `custom_claims_scope_mapping: true` が設定済み | 認可サーバー `extension` | 未設定だと `claims:*` スコープが機能せず UserInfo/ID Token にカスタムクレームが含まれない |
| 15 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

---

## テンプレートファイル参照

- テンプレート: `config/templates/use-cases/ciba/`
- FIDO-UAF設定: `config/templates/use-cases/ciba/authentication-config-fido-uaf.json`
- デバイス承認スクリプト: `config/templates/use-cases/ciba/ciba-device-auth.sh`

## 関連ドキュメント

- `content_06_developer-guide/03-application-plane/06-ciba-flow.md`

$ARGUMENTS
