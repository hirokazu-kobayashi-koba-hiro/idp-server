# idp-serverの全体像

idp-serverは、OAuth 2.0 / OpenID Connect準拠のマルチテナント型アイデンティティプラットフォームです。

このページでは、idp-serverの構造と主要概念の関係を俯瞰します。

---

## アーキテクチャ

idp-serverは**2つのプレーン**で構成されています。

```
┌───────────────────────────────────────────────────┐
│                   idp-server                       │
│                                                   │
│  ┌─────────────────────┐  ┌────────────────────┐  │
│  │ Application Plane   │  │  Control Plane     │  │
│  │                     │  │                    │  │
│  │ ・認証（ログイン）    │  │ ・テナント管理      │  │
│  │ ・認可（同意）       │  │ ・クライアント管理   │  │
│  │ ・トークン発行       │  │ ・認証設定          │  │
│  │ ・ユーザー情報提供    │  │ ・ユーザー管理      │  │
│  │                     │  │                    │  │
│  │ 利用者: エンドユーザー│  │ 利用者: 管理者      │  │
│  └─────────────────────┘  └────────────────────┘  │
└───────────────────────────────────────────────────┘
```

| プレーン | 役割 | 利用者 |
|---------|------|--------|
| **Application Plane** | 認証・認可・トークン発行 | エンドユーザー、クライアントアプリ |
| **Control Plane** | 設定・管理 | テナント管理者、システム管理者 |

詳細: [アプリケーションプレーン](./01-foundation/concept-01-application-plane.md) | [コントロールプレーン](./01-foundation/concept-02-control-plane.md)

---

## 主要概念の関係

```
組織（Organization）
 └── テナント（Tenant） ← 認証・認可の独立した単位
      ├── クライアント（Client） ← アプリケーション（Web/モバイル/M2M）
      ├── ユーザー（User） ← ログインする人
      ├── 認証設定 ← パスワード、MFA、FIDO2、SMS等の認証方式
      ├── 認証ポリシー ← どの認証方式をどう組み合わせるか
      └── 認可サーバー設定 ← トークン有効期限、スコープ等
```

### テナントとは

テナントは**認証・認可の独立した単位**です。テナントごとに認証方式、パスワードポリシー、セッション設定等をすべて独立して設定できます。

1つのidp-serverで複数テナントを運用できるため、「本番 / ステージング」の分離や「顧客ごとのIdP提供」が可能です。

詳細: [マルチテナント](./01-foundation/concept-03-multi-tenant.md)

### クライアントとは

クライアントは、テナントに所属する**アプリケーション**です。Webアプリ、モバイルアプリ、バックエンドサービス等がOAuth 2.0/OIDCの仕組みを使ってユーザーの認証・認可を行います。

詳細: [クライアント](./01-foundation/concept-04-client.md)

---

## ログインの基本的な流れ

ユーザーがアプリにログインする際の基本フローです。

```
ユーザー        クライアントアプリ        idp-server
  │                   │                    │
  │  ログインクリック   │                    │
  │ ────────────────→ │                    │
  │                   │  認可リクエスト      │
  │                   │ ─────────────────→ │
  │                   │                    │
  │  ログイン画面表示   │                    │
  │ ←─────────────────────────────────── │
  │                   │                    │
  │  認証情報入力      │                    │
  │ ──────────────────────────────────→ │
  │                   │                    │
  │                   │  認可コード発行      │
  │                   │ ←───────────────── │
  │                   │                    │
  │                   │  トークンリクエスト   │
  │                   │ ─────────────────→ │
  │                   │                    │
  │                   │  トークン発行       │
  │                   │  (AT + IDT + RT)   │
  │                   │ ←───────────────── │
  │                   │                    │
  │  ログイン完了      │                    │
  │ ←──────────────── │                    │
```

このフローの各段階で、以下の概念が関わります:

| 段階 | 関連する概念 |
|------|------------|
| 認可リクエスト | [クライアント](./01-foundation/concept-04-client.md)、スコープ |
| 認証（ログイン） | [認証ポリシー](./03-authentication-authorization/concept-01-authentication-policy.md)、[MFA](./03-authentication-authorization/concept-02-mfa.md) |
| トークン発行 | [トークン管理](./04-tokens-claims/concept-02-token-management.md)、[IDトークン](./04-tokens-claims/concept-01-id-token.md) |
| ログイン後 | [セッション管理](./03-authentication-authorization/concept-03-session-management.md) |

---

## 概念マップ

各コンセプトページがカバーする範囲と、推奨する読み順です。

### まず読む（基本）

| 概念 | 内容 |
|------|------|
| [マルチテナント](./01-foundation/concept-03-multi-tenant.md) | テナント・組織の構造、データ分離 |
| [クライアント](./01-foundation/concept-04-client.md) | アプリケーションの種類と設定 |
| [認証ポリシー](./03-authentication-authorization/concept-01-authentication-policy.md) | 認証方式の組み合わせとルール |
| [トークン管理](./04-tokens-claims/concept-02-token-management.md) | アクセストークン、リフレッシュトークンの管理 |

### 次に読む（認証を深める）

| 概念 | 内容 |
|------|------|
| [MFA](./03-authentication-authorization/concept-02-mfa.md) | 多要素認証 |
| [パスワードレス認証](./03-authentication-authorization/concept-07-passwordless.md) | FIDO2/WebAuthn/Passkey |
| [フェデレーション](./03-authentication-authorization/concept-08-federation.md) | 外部IdP連携（Google等） |
| [セッション管理](./03-authentication-authorization/concept-03-session-management.md) | SSO、セッション有効期限 |
| [ID管理](./02-identity-management/concept-01-id-management.md) | ユーザー登録・管理 |
| [パスワードポリシー](./02-identity-management/concept-02-password-policy.md) | パスワードの強度・有効期限 |

### 高度な機能

| 概念 | 内容 |
|------|------|
| [FAPI](./03-authentication-authorization/concept-06-fapi.md) | 金融グレードセキュリティ |
| [身元確認済みID](./05-advanced-id/concept-01-id-verified.md) | eKYC/本人確認 |
| [デバイスクレデンシャル](./03-authentication-authorization/concept-10-device-credential.md) | CIBA向けデバイス管理 |

### 運用・拡張

| 概念 | 内容 |
|------|------|
| [コントロールプレーン](./01-foundation/concept-02-control-plane.md) | 管理APIの構造 |
| [セキュリティイベント](./06-security-extensions/concept-01-security-events.md) | 通知・フック |
| [監査ログ](./07-operations/concept-01-audit-compliance.md) | 操作履歴・コンプライアンス |

---

**最終更新**: 2026-03-13
