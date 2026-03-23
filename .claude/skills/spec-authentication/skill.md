---
name: spec-authentication
description: 認証機能（Authentication Policy, MFA）の開発・修正を行う際に使用。認証ポリシー、パスワード、OTP、FIDO2、条件付き認証実装時に役立つ。
---

# 認証（Authentication）機能 開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/03-application-plane/04-authentication.md` - 認証実装ガイド
- `documentation/docs/content_06_developer-guide/05-configuration/authentication-policy.md` - 認証ポリシー設定
- `documentation/docs/content_06_developer-guide/05-configuration/authn/` - 認証方式別設定ガイド
  - `password.md`, `sms.md`, `email.md`, `fido2.md`, `fido-uaf.md`
- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md` - 認証ポリシー概念
- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-02-mfa.md` - MFA概念
- `documentation/docs/content_06_developer-guide/03-application-plane/06-ciba-flow.md` - CIBAフロー（LoginHint解決）

## 機能概要

認証機能は、ユーザーの本人確認を行う層。
- **多様な認証方式**: Password, Email OTP, SMS OTP, FIDO2/WebAuthn, FIDO-UAF
- **条件付き認証**: scope/ACR/clientベースでポリシー切替
- **Step-up認証**: 高セキュリティ操作時の追加認証
- **MFA**: 多要素認証のオーケストレーション

## モジュール構成

```
libs/
├── idp-server-core/                         # 認証コア
│   └── .../openid/authentication/
│       ├── AuthenticationInteractor.java   # 認証Interactor IF
│       ├── AuthenticationTransaction.java  # 認証トランザクション
│       ├── AuthenticationTransactionCommandRepository.java
│       └── AuthenticationTransactionQueryRepository.java
│
├── idp-server-authentication-interactors/   # 認証Interactor実装
│   └── .../authentication/interactors/
│       ├── password/
│       │   └── PasswordAuthenticationInteractor.java
│       ├── sms/
│       │   └── SmsAuthenticationInteractor.java
│       ├── email/
│       │   └── EmailAuthenticationInteractor.java
│       ├── fido2/
│       │   ├── Fido2AuthenticationInteractor.java
│       │   └── Fido2RegistrationInteractor.java
│       ├── fidouaf/
│       │   └── FidoUafAuthenticationInteractor.java
│       └── ... (各認証方式の実装)
│
└── idp-server-control-plane/                # 管理API
    └── .../management/authentication/
        └── AuthenticationPolicyManagementApi.java
```

## 認証ポリシー設定

認証ポリシーは設定ベースで管理:

```java
public class AuthenticationConfiguration {
    String type;  // password, sms, email, fido2, fido_uaf, etc.
    Map<String, Object> payload;
    // 設定内容は type により異なる
}
```

## 認証Interactorパターン

`idp-server-core/openid/authentication/` 内:

```java
public interface AuthenticationInteractor {

    AuthenticationInteractionType type();

    String method();

    /**
     * 認証インタラクションを実行
     */
    AuthenticationInteractionRequestResult interact(
        Tenant tenant,
        AuthenticationTransaction transaction,
        AuthenticationInteractionType type,
        AuthenticationInteractionRequest request,
        RequestAttributes requestAttributes,
        UserQueryRepository userQueryRepository
    );
}
```

### パスワード認証実装例（概念的）

`idp-server-authentication-interactors/` モジュール内のInteractorは、
interact()メソッドを実装し、AuthenticationInteractionRequestResultを返します。

パスワード検証の概念:
- トランザクションとリクエストを受け取る
- ブルートフォースチェック（CacheStoreによるRedisカウンター）
- ユーザーリポジトリからユーザー情報を取得
- パスワードエンコーダーで検証
- 成功時にカウンターリセット
- 成功/失敗の結果をAuthenticationInteractionRequestResultで返却

詳細は `documentation/docs/content_06_developer-guide/05-configuration/authn/password.md` のブルートフォース対策セクションを参照。

## FIDO2/WebAuthn認証

`idp-server-webauthn4j-adapter/` モジュール内:

WebAuthn Assertion検証をwebauthn4jライブラリを使用して実行し、
公開鍵暗号方式により認証を実現します。

## E2Eテスト

```
e2e/src/tests/
├── spec/
│   ├── oidc_core_3_1_code.test.js           # Authorization Code Flow
│   ├── rfc6749_4_3_resource_owner_password_credentials.test.js  # パスワードグラント
│   └── ... (各OIDCフロー仕様テスト)
│
├── scenario/application/
│   ├── scenario-01-user-registration.test.js  # ユーザー登録
│   ├── scenario-03-mfa-registration.test.js   # MFA登録
│   └── scenario-04-ciba-mfa.test.js           # CIBA MFA
│
├── usecase/mfa/
│   ├── mfa-01-password-reset-email-auth.test.js
│   └── mfa-03-fido-uaf-device-registration-acr-policy.test.js
│
└── monkey/
    ├── password-authentication-monkey.test.js
    └── sms-authentication-monkey.test.js
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava
./gradlew :libs:idp-server-authentication-interactors:compileJava
./gradlew :libs:idp-server-webauthn4j-adapter:compileJava

# テスト
cd e2e && npm test -- spec/oidc_core_3_1_code.test.js
cd e2e && npm test -- scenario/application/scenario-03-mfa-registration.test.js
```

## トラブルシューティング

### 認証Interactorが見つからない
- `AuthenticationConfiguration.type` が正しいか確認
- 対応するInteractor実装が登録されているか確認

### MFA登録がブロックされる
- Pre-authentication: ユーザーが認証済みか確認
- ACR Policy: 現在のACRが要求レベルを満たすか確認

### 認証トランザクションが期限切れ
- `AuthenticationTransaction` の有効期限（デフォルト: 5分）を確認
- Redis等のセッションストレージが正常か確認

---

## AuthenticationInteractor Factory パターン

認証方式は Plugin アーキテクチャで拡張可能。各認証方式は `AuthenticationInteractorFactory` を実装し、`META-INF/services/` で登録される。

```java
public interface AuthenticationInteractorFactory {
  AuthenticationInteractor create(AuthenticationDependencyContainer container);
}
```

`AuthenticationDependencyContainer` から依存関係を `resolve()` して Interactor を組み立てる。

**探索起点**: `libs/idp-server-authentication-interactors/`

---

## WebAuthn4j アダプター

FIDO2/WebAuthn は `AuthenticationExecutorFactory` を実装した個別の Executor クラス群で構成:
- `WebAuthn4jRegistrationChallengeExecutor` - 登録チャレンジ生成
- `WebAuthn4jAuthenticationExecutor` - 認証
- `WebAuthn4jRegistrationExecutor` - 登録
- `WebAuthn4jDeregistrationExecutor` - 登録解除

**探索起点**: `libs/idp-server-webauthn4j-adapter/`

---

## Federation パターン

外部 IdP 連携は `FederationInteractor` インターフェースで実装。

```
SPA → idp-server（request） → 外部 IdP 認可エンドポイント
外部 IdP → idp-server（callback） → ユーザー紐づけ → AuthenticationTransaction 更新
```

- `FederationInteractorFactory` で `FederationType` に応じた Interactor を生成
- `request()`: 外部 IdP の認可 URL を生成しリダイレクト
- `callback()`: 外部 IdP からのコールバックを処理、ユーザー紐づけ
- アカウントリンキング: 外部 IdP ユーザーと内部ユーザーの紐づけ

**探索起点**: `libs/idp-server-federation-oidc/`

---

## OperationType 列挙型

認証インタラクションの操作種別:

| 値 | 用途 |
|----|------|
| `CHALLENGE` | チャレンジ生成（SMS/Email/WebAuthn） |
| `AUTHENTICATION` | 認証実行 |
| `REGISTRATION` | デバイス/クレデンシャル登録 |
| `DENY` | 認証拒否 |
| `DE_REGISTRATION` | 登録解除 |
| `NO_ACTION` | 操作なし |
| `UNKNOWN` | 不明 |

## 認証パターンの分類

### Challenge-Response 型
SMS, Email, WebAuthn, Device — チャレンジ生成 → ユーザー応答 → 検証の2ステップ

### Single-Step 型
Password, External Token, Cancel — 1回のリクエストで完結

## 認証方式パッケージ一覧

```
authentication/interactors/
├── cancel/            # 認証キャンセル
├── device/            # デバイス認証（CIBA）
├── email/             # Email OTP
├── external_token/    # 外部トークン
├── fidouaf/           # FIDO-UAF
├── initial_registration/ # 初期登録
├── password/          # パスワード
├── plugin/            # Plugin 定義
├── sms/               # SMS OTP
└── webauthn/          # WebAuthn/FIDO2
```

## パスワードポリシー

テナント設定 `identity_policy_config.password_policy` で制御。

| 設定 | 説明 |
|------|------|
| `min_length` | 最小文字数 |
| `require_uppercase` | 大文字必須 |
| `require_lowercase` | 小文字必須 |
| `require_number` | 数字必須 |
| `max_attempts` | ロックまでの最大失敗回数 |
| `lockout_duration_seconds` | ロック期間（秒） |

## アカウントロック

パスワード認証の失敗回数がしきい値に達するとアカウントをロックする仕組み。3つの設定が連携して動作する:

1. **テナント設定**: `password_policy.max_attempts` + `lockout_duration_seconds`
2. **認証ポリシー `failure_conditions`**: 失敗回数の条件（例: `$.password-authentication.failure_count >= 3`）
3. **認証ポリシー `lock_conditions`**: ロック判定条件（`failure_conditions` と同じ条件を設定）

ロック中は正しいパスワードでも認証が拒否される。`lockout_duration_seconds` 経過後に自動解除。

## SMS/Email OTP認証フロー

2ステップのChallenge-Response型:

1. **チャレンジ送信**: `sms-authentication-challenge` / `email-authentication-challenge` でOTPコードを送信
2. **検証**: `sms-authentication` / `email-authentication` でコードを検証

### OTP動作特性

- **再チャレンジで旧コード無効化**: 新しいチャレンジを送信すると、前のOTPコードは無効になる
- **Email OTP有効期限**: `expire_seconds` で設定可能（期限切れコードは検証失敗）
- **no_actionモード**: ローカル開発用。Management APIの `authentication-interactions/{id}/sms-authentication-challenge` で検証コードを取得可能

### MFA時のID Token amrクレーム

MFA認証完了後、ID Tokenの `amr` クレームに使用された全認証方式が含まれる:

```json
{
  "amr": ["sms", "pwd"]  // SMS OTP + パスワード
}
```

## RFC 8176 AMR (Authentication Methods References)

| 認証方式 | AMR 値 |
|---------|--------|
| Password | `pwd` |
| SMS | `sms` |
| WebAuthn/FIDO2 | `fido` |
| FIDO-UAF | `fido` |
