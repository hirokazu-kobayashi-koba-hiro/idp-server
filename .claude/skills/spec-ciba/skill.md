---
name: spec-ciba
description: CIBA（Client Initiated Backchannel Authentication）機能の開発・修正を行う際に使用。Poll/Push/Pingモード、Login Hint解決、FCM通知実装時に役立つ。
---

# CIBA（Client Initiated Backchannel Authentication）開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/03-application-plane/06-ciba-flow.md` - CIBA実装ガイド
- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-04-authorization.md` - 認可概念（CIBA含む）

## 機能概要

CIBAは、クライアントがバックチャネル経由でユーザー認証を要求するフロー。
- **3つのモード**: Poll（ポーリング）、Push（通知）、Ping（通知+ポーリング）
- **Login Hint解決**: sub:, email:, phone:, device: プレフィックスでユーザー特定
- **Binding Message**: ユーザーへの確認メッセージ表示
- **ID Token Hint**: 既存ID Tokenでユーザー特定
- **User Code**: ユーザー入力コードによる認証
- **デバイス通知**: FCM push通知
- **デバイスシークレット認証**: device_secret_jwtによるデバイスエンドポイント認証

## デバイスシークレット認証（CIBAフロー）

CIBAフローでモバイルアプリがデバイスエンドポイントにアクセスする際、デバイスシークレットJWTによる認証を要求できます。

### 設定（テナントポリシー）

```json
{
  "identity_policy_config": {
    "authentication_device_rule": {
      "authentication_type": "device_secret_jwt",
      "issue_device_secret": true,
      "device_secret_algorithm": "HS256"
    }
  }
}
```

### フロー

1. **FIDO-UAF登録時**: `device_secret`が自動発行される
2. **CIBAリクエスト**: `login_hint=device:{deviceId}`でデバイス指定
3. **デバイスエンドポイントアクセス**: `Authorization: Bearer {device_secret_jwt}`で認証
4. **FIDO-UAF認証**: 生体認証で本人確認

### 関連ドキュメント

- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-10-device-credential.md` - デバイスクレデンシャル管理（詳細）

### 関連E2Eテスト

- `e2e/src/tests/usecase/device-credential/device-credential-04-device-secret-issuance.test.js` - デバイスシークレット発行+CIBA認証

## モジュール構成

```
libs/
├── idp-server-core-extension-ciba/          # CIBA拡張モジュール
│   └── .../core/extension/ciba/
│       ├── handler/
│       │   └── CibaRequestHandler.java     # CIBA認証リクエスト処理
│       ├── CibaRequestContext.java         # CIBAリクエストコンテキスト
│       ├── grant/
│       │   ├── CibaGrant.java
│       │   ├── CibaGrantFactory.java
│       │   └── CibaGrantService.java       # CIBAグラント管理
│       ├── request/
│       │   └── BackchannelAuthenticationRequest.java
│       ├── response/
│       │   └── BackchannelAuthenticationResponse.java
│       └── repository/
│           └── CibaGrantRepository.java
│
├── idp-server-notification-fcm-adapter/      # FCM通知アダプター
│   └── .../notification/fcm/
│       └── FcmNotificationSender.java
│
└── idp-server-control-plane/                # 管理API
    └── .../management/ciba/
        └── CibaConfigManagementApi.java
```

## CIBA認証リクエスト処理

`idp-server-core-extension-ciba/.../core/extension/ciba/handler/CibaRequestHandler.java` 内:

CibaRequestHandlerは、BackchannelAuthenticationRequestを処理し、
BackchannelAuthenticationResponseを生成します。

処理フロー（概念的）:
1. クライアント認証
2. Login Hint解決（ユーザー特定）
3. auth_req_id生成
4. CibaGrant作成・保存
5. デバイス通知（FCM）
6. BackchannelAuthenticationResponse返却

## CIBAグラント管理

`idp-server-core-extension-ciba/grant/` 内:

```java
// CibaGrantは認証状態を管理
public class CibaGrant {
    AuthReqId authReqId;
    UserId userId;
    ClientId clientId;
    Scope scope;
    GrantStatus status;  // PENDING, AUTHENTICATED, EXPIRED

    public boolean isAuthenticated() {
        return status == GrantStatus.AUTHENTICATED;
    }
}
```

CibaGrantServiceとCibaGrantVerifierが、グラントのライフサイクルを管理します。

## FCM通知

`idp-server-notification-fcm-adapter/` モジュール内:

FCM (Firebase Cloud Messaging)を使用して、認証デバイスに通知を送信します。

## User Code認証

CIBA リクエスト送信元が本当にユーザーの意図を持っているかを追加検証する仕組み。

### 設定

| 設定 | 場所 | 説明 |
|------|------|------|
| `backchannel_user_code_parameter_supported` | 認可サーバー | Discovery に能力を公開 |
| `required_backchannel_auth_user_code` | 認可サーバー extension | 全CIBAリクエストにuser_code必須化 |
| `backchannel_auth_user_code_type` | 認可サーバー extension | 検証タイプ（現在 `"password"` のみ） |
| `backchannel_user_code_parameter` | クライアント | クライアント単位のopt-in |

### 実装

`UserCodeAsPasswordVerifier` がuser_codeの値を**ユーザーのパスワードハッシュと照合**する。user_codeは別のPINではなく、ユーザーのパスワードそのもの。

```java
// UserCodeAsPasswordVerifier.java
passwordVerificationDelegation.verify(userCode.value(), user.hashedPassword())
```

エラーコード:
- `missing_user_code`: `required_backchannel_auth_user_code=true` だがuser_code未送信
- `invalid_user_code`: user_codeがパスワードと不一致

## Login Hint解決

login_hintパラメータのプレフィックスでユーザー特定方法が決まる。

| プレフィックス | 例 | 解決方法 |
|--------------|-----|---------|
| `sub:` | `sub:user-123` | ユーザーIDで検索 |
| `email:` | `email:user@example.com` | メールアドレスで検索 |
| `phone:` | `phone:+819012345678` | 電話番号で検索 |
| `device:` | `device:device-uuid` | デバイスIDで検索（FIDO-UAFデバイス） |
| `ex-sub:` | `ex-sub:external-id` | 外部ユーザーIDで検索 |

## CIBA固有のトークン・クレーム設定

認可サーバーのトークン設定はCIBAフローにも適用される。

### トークン有効期限

```json
{
  "access_token_duration": 3600,
  "id_token_duration": 3600
}
```

### リフレッシュトークン戦略

| strategy | rotation | 動作 |
|----------|----------|------|
| `EXTENDS` | `false` | リフレッシュ時に期限延長。同一RTを再利用 |
| `EXTENDS` | `true` | リフレッシュ時に期限延長。新RT発行、旧RT無効化 |
| `FIXED` | `false` | 元のRT期限を維持。同一RTを再利用 |
| `FIXED` | `true` | 元のRT期限を維持。新RT発行、旧RT無効化 |

### id_token_strict_mode

`id_token_strict_mode: true` の場合、ID Tokenから標準クレーム（email, name等）が除外される。CIBAではclaimsパラメータが使用できないため、strict_mode有効時はID Tokenが最小限（sub, iss, aud, exp, iat）になる。UserInfoエンドポイント経由でクレームを取得する設計。

**`claims:*` カスタムスコープへの追加影響**: `id_token_strict_mode: true` の場合、`claims:*` カスタムスコープのクレームは **UserInfo からも除外される**（`UserinfoScopeMappingCustomClaimsCreator.shouldCreate()` が `isIdTokenStrictMode()` をチェック）。標準OIDCクレーム（profile, emailスコープ）のUserInfoには影響しない。

### custom_claims_scope_mapping

`custom_claims_scope_mapping: true` の場合、`claims:*` プレフィックス付きスコープ（例: `claims:authentication_devices`）でカスタムクレームをUserInfo/ID Tokenに含めることができる。`false` の場合、`claims:*` スコープは無視される。

**`id_token_strict_mode` との違い**: `custom_claims_scope_mapping` は `claims:*` カスタムスコープのみに影響し、標準OIDCクレーム（name, email等）には影響しない。一方、`id_token_strict_mode` は標準クレームをID Tokenから除外し、さらに `claims:*` クレームをUserInfoからも除外する。

### claims_supported

認可サーバーの `claims_supported` に含まれないクレームはUserInfo/ID Tokenから除外される。CIBAでも同様に適用される。

## Failure Conditions（認証拒否検出）

CIBAではユーザーがデバイス上で認証を拒否できる。`failure_conditions` でデバイスレスポンスのどの値を「拒否」と判定するかを設定する。

`CibaGrant.isFailureSatisfied()` が条件を評価し、満たされた場合はトークンリクエスト時に `access_denied` エラーを返す。

## E2Eテスト

```
e2e/src/tests/
├── spec/
│   ├── ciba_authentication_request.test.js   # CIBA認証リクエスト
│   ├── ciba_token_request.test.js            # CIBAトークンリクエスト
│   ├── ciba_push.test.js                     # Pushモード
│   ├── ciba_ping.test.js                     # Pingモード
│   └── ciba_discovery.test.js                # CIBA Discovery
│
├── scenario/application/
│   └── scenario-04-ciba-mfa.test.js          # CIBA MFAシナリオ
│
├── usecase/ciba/
│   ├── ciba-01-require-rar.test.js           # RAR必須
│   ├── ciba-02-multi-device-priority.test.js # マルチデバイス優先度
│   └── ciba-04-security-event-device-ids.test.js
│
└── monkey/
    └── ciba-monkey.test.js                   # CIBAファジングテスト
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core-extension-ciba:compileJava
./gradlew :libs:idp-server-notification-fcm-adapter:compileJava

# テスト
cd e2e && npm test -- spec/ciba_authentication_request.test.js
cd e2e && npm test -- spec/ciba_push.test.js
cd e2e && npm test -- usecase/ciba/
```

## トラブルシューティング

### Login Hint解決失敗
- プレフィックス（sub:, email:, phone:）が正しいか確認
- ユーザーが存在するか確認

### Pollモードでauthorization_pending
- ユーザーがまだ認証を完了していない（正常動作）
- `interval`秒待ってから再ポーリング

### FCM通知が届かない
- FCMトークンが登録されているか確認
- Firebase設定（credentials.json）が正しいか確認
- `idp-server-notification-fcm-adapter` モジュールが有効か確認

---

## CibaRequestHandler 実装フロー

`CibaRequestHandler.handle()` は以下の4ステップで処理:

1. **Validator** - 入力形式チェック（`CibaRequestValidator`）
2. **Verifier** - ビジネスルール検証
3. **Context 生成 & 永続化** - `CibaGrant` を生成し Repository に保存
4. **Response 生成** - `auth_req_id` を含むレスポンスを返却

**探索起点**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/handler/CibaRequestHandler.java`

## CibaGrant ライフサイクル

`CibaGrant` は `CibaGrantStatus` で状態管理:
- `authorization_pending` → ユーザー認証待ち
- `authorized` → 認証完了（`isAuthorized()`）
- `access_denied` → ユーザー拒否（`isAccessDenied()`）

`CibaGrantService` は `OAuthTokenCreationService` を実装し、Token Endpoint での `urn:openid:params:grant-type:ciba` グラントを処理する。

## 通知モード実装詳細

| モード | 仕組み | 主要パラメータ |
|--------|--------|--------------|
| Poll | クライアントが `interval` 秒ごとにトークンエンドポイントをポーリング | `interval`（デフォルト5秒） |
| Push | 認証完了時にクライアントの `notification_endpoint` へ POST | `client_notification_token` |
| Ping | 認証完了時に簡易通知 → クライアントがトークン取得 | Poll + Push の組み合わせ |
