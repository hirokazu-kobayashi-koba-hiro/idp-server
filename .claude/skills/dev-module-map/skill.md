---
name: dev-module-map
description: プロジェクト全体像の把握に使用。Gradleモジュール一覧、依存階層、認可/トークンリクエストのEnd-to-Endフローを確認する際に参照。
---

# モジュールマップ & リクエストフロー

## モジュール一覧（24モジュール）

### コア層

| モジュール | 責務 |
|-----------|------|
| **idp-server-platform** | 基盤インフラ。マルチテナント、ロギング、型定義、JSON/JOSE処理、トランザクション管理 |
| **idp-server-core** | OIDCコアエンジン。OAuth/OIDC仕様実装、プロトコルハンドラー、バリデーター |
| **idp-server-control-plane** | 管理API契約定義。テナント、クライアント、ユーザー、認証ポリシーのAPI仕様 |

### 拡張モジュール

| モジュール | 責務 |
|-----------|------|
| **idp-server-core-extension-pkce** | PKCE（Proof Key for Code Exchange）検証 |
| **idp-server-core-extension-fapi** | FAPI準拠機能（ID Token署名要件、レスポンス暗号化） |
| **idp-server-core-extension-ciba** | CIBA（Client Initiated Backchannel Authentication）フロー |
| **idp-server-core-extension-fapi-ciba** | FAPI + CIBA 複合拡張 |
| **idp-server-core-extension-ida** | 身元確認（Identity Document Analysis）機能 |
| **idp-server-core-extension-verifiable-credentials** | W3C Verifiable Credentials 発行・検証 |

### 認証・フェデレーション

| モジュール | 責務 |
|-----------|------|
| **idp-server-authentication-interactors** | 認証方式実装（Password, SMS, Email, FIDO2, FIDO-UAF, Cancel等） |
| **idp-server-webauthn4j-adapter** | WebAuthn4jライブラリ統合（FIDO2 登録/認証/登録解除） |
| **idp-server-federation-oidc** | 外部OIDCプロバイダーとのフェデレーション（SSO） |

### セキュリティイベント

| モジュール | 責務 |
|-----------|------|
| **idp-server-security-event-framework** | セキュリティイベント基盤（イベント定義、SSF対応） |
| **idp-server-security-event-hooks** | イベントフック実装（Slack, Webhook, SSF） |

### 通知・外部サービス

| モジュール | 責務 |
|-----------|------|
| **idp-server-notification-fcm-adapter** | Firebase Cloud Messaging プッシュ通知 |
| **idp-server-notification-apns-adapter** | Apple Push Notification Service 通知 |
| **idp-server-email-aws-adapter** | Amazon SES メール送信 |

### アダプター・インフラ

| モジュール | 責務 |
|-----------|------|
| **idp-server-core-adapter** | 永続化実装（PostgreSQL/MySQL両対応、キャッシュ） |
| **idp-server-database** | Flywayマイグレーション、スキーマ定義 |
| **idp-server-springboot-adapter** | Spring Boot統合（Controller, Filter, Session, EventListener） |

### オーケストレーション・エントリーポイント

| モジュール | 責務 |
|-----------|------|
| **idp-server-use-cases** | EntryService層。全APIのオーケストレーション、テナント対応プロキシ |
| **app** | Spring Boot起動エントリーポイント。全モジュール統合 |

---

## 依存階層

```
Layer 1 [基盤]
  idp-server-platform

Layer 2 [コア]
  idp-server-core  →  platform

Layer 3 [拡張・認証・イベント]
  core-extension-pkce         →  platform + core
  core-extension-fapi         →  platform + core
  core-extension-ciba         →  platform + core
  core-extension-fapi-ciba    →  platform + core + fapi + ciba
  core-extension-ida          →  platform + core
  core-extension-vc           →  platform + core
  authentication-interactors  →  platform + core
  security-event-framework    →  platform + core
  security-event-hooks        →  platform + core
  federation-oidc             →  platform + core

Layer 4 [アダプター]
  core-adapter         →  platform + core + extensions + control-plane
  webauthn4j-adapter   →  platform + core + authentication-interactors
  notification-*       →  platform + core + authentication-interactors
  email-aws-adapter    →  platform + authentication-interactors
  control-plane        →  platform + core + ida

Layer 5 [ユースケース]
  use-cases            →  platform + core + 全extension + 全Layer3

Layer 6 [Spring Boot統合]
  springboot-adapter   →  platform + core + control-plane + core-adapter
                          + use-cases + webauthn4j + notification-*

Layer 7 [起動]
  app                  →  springboot-adapter + 全アダプター
```

**原則**: 上位層は下位層に依存。同一層のモジュール間は依存しない（core-extension-fapi-cibaを除く）。

---

## リクエストフロー

### A. 認可リクエスト（Authorization Request）

```
GET /{tenant-id}/view/v1/authorizations?response_type=code&client_id=...&...
│
├─ [Spring Boot] OAuthController.get()
│  パラメータ変換、RequestAttributes抽出
│
├─ [UseCase] OAuthFlowEntryService.request()
│  Tenant取得、OAuthRequestParameters作成
│  OAuthProtocols から protocol 取得
│
├─ [Core] OAuthProtocol.request()
│  └─ AuthorizationRequestHandler.handle()
│     ├─ AuthorizationRequestValidator.validate()     ← 入力形式チェック
│     ├─ ClientConfiguration 取得
│     ├─ AuthorizationServerConfiguration 取得
│     ├─ Verifier群:
│     │  ├─ AuthorizationRequestVerifier.verify()     ← プロトコル検証
│     │  ├─ ClientRegistrationVerifier.verify()       ← クライアント検証
│     │  ├─ RedirectUriVerifier.verify()              ← リダイレクトURI
│     │  ├─ ScopeVerifier.verify()                    ← スコープ
│     │  ├─ ResponseTypeVerifier.verify()             ← レスポンスタイプ
│     │  └─ (拡張: PkceVerifier, FapiVerifier 等)
│     ├─ AuthorizationRequestContext 作成
│     └─ AuthorizationRequestRepository.save()
│
└─ [Response]
   ├─ OK → redirect: /signin/index.html?id={authz_id}&tenant_id={tenant_id}
   ├─ OK_SESSION_ENABLE → redirect: /signin（SSO既存セッション）
   ├─ OK_ACCOUNT_CREATION → redirect: /signup
   ├─ NO_INTERACTION_OK → redirect to redirect_uri（同意済み）
   └─ Error → エラーレスポンス
```

### B. トークンリクエスト（Token Request）

```
POST /{tenant-id}/v1/tokens
Content-Type: application/x-www-form-urlencoded
Authorization: Basic {base64(client_id:client_secret)}
│
├─ [Spring Boot] TokenV1Api.request()
│  パラメータ変換、RequestAttributes抽出
│
├─ [UseCase] TokenEntryService.request()
│  Tenant取得、TokenRequest作成
│  TokenProtocols から protocol 取得
│
├─ [Core] TokenProtocol.request()
│  └─ TokenRequestHandler.handle()
│     ├─ TokenRequestValidator.validate()             ← grant_type必須等
│     ├─ ClientAuthentication:
│     │  └─ ClientAuthenticationHandler.authenticate()
│     │     client_secret_basic / client_secret_post /
│     │     private_key_jwt / tls_client_auth 等
│     ├─ Grant Type別処理（OAuthTokenCreationServices）:
│     │  ├─ authorization_code → AuthorizationCodeGrantService
│     │  ├─ refresh_token     → RefreshTokenGrantService
│     │  ├─ client_credentials → ClientCredentialsGrantService
│     │  └─ jwt-bearer        → JwtBearerGrantService
│     └─ OAuthTokenCommandRepository.register()
│
└─ [Response]
   ├─ 200 OK: {"access_token":"...","token_type":"Bearer","expires_in":3600,...}
   └─ Error: 400/401 + {"error":"...","error_description":"..."}
```

### C. 管理API（Control Plane）

```
POST /organizations/{org-id}/v1/tenants
Authorization: Bearer {admin_token}
│
├─ [Spring Boot] OrganizationTenantManagementV1Api.create()
│
├─ [UseCase] OrgTenantManagementEntryService.create()
│  権限チェック、テナント作成コマンド実行
│
├─ [Core/Adapter]
│  ├─ TenantCommandRepository.save()
│  ├─ AuthorizationServerConfiguration 自動初期化
│  └─ JWKS 自動生成（RS256鍵ペア）
│
└─ [Response] 201 Created + Tenant JSON
```

---

## 主要インターフェース

### Application Plane（OAuth/OIDC API）

| インターフェース | メソッド | 実装 |
|----------------|---------|------|
| **OAuthFlowApi** | `request()`, `interact()`, `authorize()`, `deny()`, `logout()` | OAuthFlowEntryService |
| **TokenApi** | `request()`, `inspect()`, `revoke()` | TokenEntryService |

### Protocol（コア処理委譲）

| インターフェース | 役割 |
|----------------|------|
| **OAuthProtocol** | 認可リクエスト処理。`request()`, `authorize()`, `interactAuthentication()` |
| **TokenProtocol** | トークンリクエスト処理。`request()` |

### マルチテナント対応

```
Controller → TenantAwareEntryServiceProxy → EntryService → Handler
             ↑ tenantIdentifier で Tenant解決
```

**TenantAwareEntryServiceProxy**: すべてのEntryServiceをラップし、tenantIdentifier → Tenant変換を一元管理。

---

## 新機能追加時のモジュール選定ガイド

| やりたいこと | 触るモジュール |
|-------------|---------------|
| 新しいOAuth/OIDC仕様の実装 | `core` + `core-extension-*`（新規） |
| 新しい認証方式の追加 | `authentication-interactors` |
| 管理APIの追加 | `control-plane`（契約） + `use-cases`（EntryService） + `springboot-adapter`（Controller） + `core-adapter`（永続化） |
| 新しい通知チャネル | `notification-*-adapter`（新規） |
| DBスキーマ変更 | `database`（Flyway） + `core-adapter`（SQL） |
| 外部IdP連携追加 | `federation-oidc` |
| セキュリティイベントフック追加 | `security-event-hooks` |
| 新しいVerifiable Credential形式 | `core-extension-verifiable-credentials` |

$ARGUMENTS
