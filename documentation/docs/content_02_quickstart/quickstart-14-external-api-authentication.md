# 外部API認証

[← 導入ガイドに戻る](./quickstart-03-common-use-cases.md)

---

**外部APIと連携した認証を、設定だけで構築します。パスワード検証、リスク判定、OTPなど、1つのエンドポイントで複数の外部APIを使い分けます。**

---

## こんなときに使います

| やりたいこと | 具体例 |
|------------|--------|
| **旧サービスのAPIをそのまま使いたい** | 既存の認証API・会員DB・LDAPを捨てずに、idp-server でOIDC対応したい |
| **idp-server の認証フローに外部APIを組み込みたい** | リスク分析API、不正検知サービス、外部OTPプロバイダーなどを認証時に呼びたい |

いずれも **設定JSONを書くだけ** で実現でき、コード変更は不要です。

### ユーザー体験

**ユーザーから見ると通常の認証フローと同じです。** 裏側で外部APIが呼ばれていることは意識しません。

```
クライアント                idp-server                  外部API群
  │                           │                           │
  │  認可リクエスト             │                           │
  │ ─────────────────────────→│                           │
  │                           │                           │
  │  認証 (interaction指定)    │   設定に基づき             │
  │ ─────────────────────────→│   外部APIを自動選択        │
  │                           │ ─────────────────────────→│
  │                           │   レスポンスをマッピング     │
  │                           │ ←─────────────────────────│
  │  認証結果                  │                           │
  │ ←─────────────────────────│                           │
  │                           │                           │
  │  トークン発行              │                           │
  │ ←─────────────────────────│                           │
```

---

## 利用パターン

### パターン1: 外部認証サービスへの委譲

既存の認証基盤（LDAP、RADIUS、レガシーシステム等）をそのまま活用。

```
{ "interaction": "password_verify", "username": "...", "password": "..." }
→ 外部認証APIでパスワード検証 → ユーザー自動同期
```

### パターン2: リスクベース認証

認証フロー中にリスク判定を挟む。結果に応じてクライアント側で追加認証を要求。

```
{ "interaction": "risk_check", "device_fingerprint": "...", "ip_address": "..." }
→ 外部リスク判定API → { "risk_score": 0.2, "risk_level": "low" }
```

### パターン3: 外部OTPサービス連携（Challenge-Response）

2ステップで外部OTPサービスと連携。`previous_interaction` でデータを自動受け渡し。

```
Step 1: { "interaction": "otp_send", "phone_number": "+81..." }
        → OTPコード送信 → transaction_id を保存

Step 2: { "interaction": "otp_verify", "code": "123456" }
        → 保存された transaction_id + 検証コードで確認
```

### パターン4: MFA の2段階目

パスワード（1段階目）+ 外部API認証（2段階目）の多要素認証。

```
Step 1: POST /password-authentication { ... }   → 1段階目
Step 2: POST /external-api-authentication { ... } → 2段階目（ユーザー一致検証あり）
Step 3: POST /authorize                           → トークン発行
```

---

## 導入時に決めること

### 1. 連携する外部API

| 決めること | 説明 |
|-----------|------|
| **エンドポイントURL** | 外部APIのURL |
| **HTTPメソッド** | POST / GET 等 |
| **認証方式** | `oauth2`（Bearer Token）/ `hmac_sha256` / `none` |
| **リクエスト形式** | どのフィールドを送るか（body_mapping_rules で制御） |
| **レスポンス形式** | ユーザー情報のフィールド名（user_mapping_rules で制御） |

### 2. interaction の設計

| 決めること | 説明 |
|-----------|------|
| **interaction 名** | 設定の `interactions` キー名（例: `password_verify`, `risk_check`） |
| **user_resolve の有無** | ユーザー解決が必要か（認証型: あり / 補助判定型: なし） |
| **Challenge-Response** | 2ステップが必要か（`http_request_store` + `previous_interaction`） |

### 3. MFA で使う場合

| 決めること | 選択肢 |
|-----------|--------|
| **認証ポリシー** | `step_definitions` で order と requires_user を設定 |
| **ユーザー一致検証** | 2段階目で email / externalUserId の一致を自動検証 |

---

## まとめ

### 必ず決めること
1. **外部APIの仕様**: URL、認証方式、リクエスト/レスポンス形式
2. **interaction 設計**: 名前、user_resolve 有無、Challenge-Response の要否
3. **MFA 利用時**: 認証ポリシーの step_definitions

### idp-serverが提供すること
- リクエストボディの `interaction` フィールドによる動的ルーティング
- 設定ベースの外部API呼び出し（OAuth2 / HMAC / 認証なし）
- JSONPath ベースのリクエスト/レスポンスマッピング
- `previous_interaction` による Challenge-Response データ受け渡し
- MFA 2段階目でのユーザー一致検証（`user_identity_mismatch` 検出）
- interaction ごとの動的セキュリティイベント

### 自分で用意すること
- 連携先の外部API
- ログイン画面（UI）
- 認証フローの制御ロジック（どの interaction をどの順で呼ぶか）

### セキュリティの注意点
- `user_mapping_rules` の `provider_id` は `static_value` で固定値にする（外部APIの値を使わない）
- MFA 2段階目では、外部APIが返すユーザー情報が1段階目と一致しないと `user_identity_mismatch` で拒否される
- 外部APIの認証情報（client_secret 等）は認証設定の `oauth_authorization` に安全に保管される

---

## 他のテンプレートとの違い

| 項目 | External Token | External Password Auth | External API認証（これ） |
|------|---------------|----------------------|------------------------|
| エンドポイント | `/external-token` | `/password-authentication` | `/external-api-authentication` |
| interaction | 1つ固定 | 1つ固定 | 複数定義可能 |
| Challenge-Response | 非対応 | 非対応 | `previous_interaction` で対応 |
| MFA 2段階目 | 非対応 | 対応 | 対応（ユーザー一致検証あり） |
| user_resolve | 必須 | 設定による | interaction ごとに有無を選択 |
| 用途 | 外部トークン認証 | パスワード委譲 | 汎用（認証・判定・OTP等） |

---

## 設定方法

設定の詳細は以下を参照してください:

| ガイド | 内容 |
|--------|------|
| [How-to: 外部API認証](../content_05_how-to/phase-4-extensions/06-external-api-authentication.md) | Step by Step の設定手順（curl コマンド付き） |
| [設定リファレンス](../content_06_developer-guide/05-configuration/authn/external-api.md) | 全設定項目の詳細 |
| [認証ポリシー設定](../content_06_developer-guide/05-configuration/authentication-policy.md) | MFA・条件付き認証の設定 |

---

## 関連ドキュメント

- [Quickstart: 外部パスワード認証委譲](./quickstart-10-external-password-auth.md)
- [Quickstart: MFA](./quickstart-05-mfa.md)
- [Quickstart: 既存IDサービスからの移行](./quickstart-13-id-service-migration.md)
- [How-to: セキュリティイベントフック](../content_05_how-to/phase-4-extensions/04-security-event-hooks.md)

---

**最終更新**: 2026-03-24
