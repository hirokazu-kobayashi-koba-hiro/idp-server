# 検証済み機能マトリクス

**idp-server** が提供する機能を、10 個の[ユースケーステンプレート](../content_02_quickstart/quickstart-03-common-use-cases.md)で実際にセットアップ・動作確認した結果に基づいて整理しています。

各テンプレートは `config/templates/use-cases/` に配置されており、`setup.sh` → `verify.sh` で動作確認できます。

## テンプレート一覧

| 略称 | テンプレート | 想定シナリオ |
|------|------------|------------|
| **PW** | `login-password-only` | 最小構成のパスワードログイン |
| **Social** | `login-social` | Google 等ソーシャルログイン |
| **MFA-E** | `mfa-email` | パスワード + Email OTP |
| **MFA-S** | `mfa-sms` | パスワード + SMS OTP（外部API連携） |
| **FIDO2** | `passwordless-fido2` | WebAuthn/Passkey によるパスワードレス |
| **ExtPW** | `external-password-auth` | パスワード認証を外部APIに委譲 |
| **eKYC** | `ekyc` | 身元確認（本人確認）フロー |
| **3rd** | `third-party` | Web/Mobile/M2M マルチクライアント |
| **FAPI** | `financial-grade` | FAPI Advanced + mTLS + CIBA |
| **CIBA** | `ciba` | FIDO-UAF + CIBA バックチャネル認証 |

---

## 1. 認証

| 機能 | 説明 | PW | Social | MFA-E | MFA-S | FIDO2 | ExtPW | eKYC | 3rd | FAPI | CIBA |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| パスワード認証 | ユーザー名+パスワードによるログイン | o | o | o | o | o | — | o | o | — | o |
| 外部パスワード認証 | パスワード検証を外部HTTPサービスに委譲 | — | — | — | — | — | o | — | — | — | — |
| Email OTP | メールでワンタイムパスワードを送信・検証 | — | — | o | — | — | — | — | — | o | — |
| SMS OTP | SMSでワンタイムパスワードを送信・検証（外部API） | — | — | — | o | — | — | — | — | o | — |
| FIDO2 / WebAuthn | Passkey・生体認証によるパスワードレス認証 | — | — | — | — | o | — | — | — | o | — |
| FIDO-UAF | FIDO-UAF プロトコルによるデバイス認証 | — | — | — | — | — | — | — | — | — | o |
| ソーシャルログイン | Google 等 外部OIDC IdPによるフェデレーション | — | o | — | — | — | — | — | — | — | — |
| ユーザー自己登録 | 認可フロー内でのユーザーセルフサインアップ | o | o | o | o | o | — | o | o | — | o |

### 認証ポリシー

全テンプレートで **認証ポリシー** により認証フローを制御しています。テンプレートで検証済みのポリシーパターン:

| パターン | 説明 | 検証テンプレート |
|---------|------|---------------|
| パスワードのみ | 単一要素認証 | PW, Social, eKYC, 3rd, ExtPW |
| MFA（AND条件） | パスワード + OTP の両方が必要 | MFA-E, MFA-S |
| MFA（OR条件） | パスワード or OTP のどちらかで成功 | MFA-E（VERIFY-CONFIG-CHANGES パターン6-3） |
| 条件付きMFA | 特定スコープ要求時のみMFAを強制 | MFA-E（パターン6-2: `transfers` スコープ） |
| 段階的認証 | ステップ順序の変更（Email→PW / PW→Email） | MFA-E（パターン6-1） |
| FIDO2 or パスワード | パスワードレス移行期間の併用 | FIDO2 |
| 多段階ポリシー | セキュリティレベル別に3段階のポリシー | FAPI |
| CIBA専用ポリシー | バックチャネル認証用の独立ポリシー | CIBA, FAPI |

### パスワードポリシー

全テンプレート共通のベースライン:

| 設定項目 | 値 |
|---------|-----|
| 最小文字数 | 8 |
| 最大文字数 | 72 |
| 失敗回数上限 | 5回 |
| ロックアウト時間 | 15分 (900秒) |

---

## 2. 認可

| 機能 | 説明 | PW | Social | MFA-E | MFA-S | FIDO2 | ExtPW | eKYC | 3rd | FAPI | CIBA |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Authorization Code | 認可コードフロー | o | o | o | o | o | o | o | o | o | — |
| Refresh Token | リフレッシュトークンによる更新 | o | o | o | o | o | o | o | o | o | — |
| Client Credentials | M2Mサービス間認証 | — | — | — | — | — | — | — | o | — | — |
| CIBA (Poll) | バックチャネル認証（ポーリング） | — | — | — | — | — | — | — | — | o | o |
| CIBA (Ping) | バックチャネル認証（Ping通知） | — | — | — | — | — | — | — | — | o | — |
| JWT Bearer Grant | デバイスシークレットによるトークン取得 | — | — | — | — | — | — | — | — | — | o |
| SSO | セッション共有による自動ログイン | o | o | o | o | o | o | o | o | o | — |

---

## 3. トークン・クレーム

| 機能 | 説明 | PW | Social | MFA-E | MFA-S | FIDO2 | ExtPW | eKYC | 3rd | FAPI | CIBA |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| ID Token | ユーザー認証情報を含むJWT発行 | o | o | o | o | o | o | o | o | o | o |
| UserInfo | アクセストークンでユーザー属性取得 | o | o | o | o | o | o | o | o | o | o |
| カスタムスコープ | 業務固有スコープの定義 | — | — | — | — | — | — | o | o | o | o |
| カスタムクレーム | `claims:*` スコープによるクレーム拡張 | — | — | — | — | — | — | — | — | — | o |
| verified_claims | 身元確認済み属性の発行 | — | — | — | — | — | — | o | — | — | — |
| Refresh Token Rotation | リフレッシュトークンの自動ローテーション | — | — | — | — | — | — | — | o | — | — |
| Opaque Access Token | 不透明アクセストークン（JWT以外） | — | — | — | — | — | — | — | o | — | — |
| Token Introspection | アクセストークンの有効性検証 | — | — | — | — | — | — | — | o | o | — |

### トークン有効期限の設定例

| テンプレート | Access Token | ID Token | Refresh Token |
|------------|:----------:|:-------:|:------------:|
| ベースライン（PW等） | デフォルト | デフォルト | デフォルト |
| 3rd | 1800秒（30分） | 3600秒（1時間） | 604800秒（7日） |
| FAPI | 300秒（5分） | 300秒（5分） | 2592000秒（30日） |

---

## 4. クライアント認証

| 認証方式 | 説明 | 検証テンプレート |
|---------|------|---------------|
| `client_secret_post` | ボディでシークレット送信 | PW, Social, MFA-E, MFA-S, FIDO2, eKYC, CIBA |
| `client_secret_basic` | Basic認証ヘッダー | 3rd（Web/M2M） |
| `none`（Public Client + PKCE） | シークレットなし、PKCE必須 | 3rd（Mobile） |
| `private_key_jwt` | JWT署名によるクライアント認証 | FAPI |
| `tls_client_auth` | mTLS証明書によるクライアント認証 | FAPI |
| `self_signed_tls_client_auth` | 自己署名mTLS証明書 | FAPI |

---

## 5. セキュリティ

| 機能 | 説明 | 検証テンプレート |
|------|------|---------------|
| FAPI Advanced | mTLS + 署名リクエスト + PAR + JARM | FAPI |
| PAR（Pushed Authorization Request） | 認可リクエストの事前登録（60秒有効） | FAPI |
| JARM | 認可レスポンスのJWT署名 | FAPI |
| mTLS Certificate-Bound Token | 証明書バインドアクセストークン | FAPI |
| 署名リクエストオブジェクト必須 | `require_signed_request_object: true` | FAPI |
| Issuerレスポンスパラメータ | `authorization_response_iss_parameter_supported` | FAPI |
| ブルートフォース防止（パスワード） | N回失敗でアカウントロック | 全テンプレート |
| ブルートフォース防止（OTP） | リトライ回数制限 + 有効期限 | MFA-E, MFA-S |
| ワイルドカードマッピング禁止 | 外部APIレスポンスの明示的フィールドマッピング | MFA-E, MFA-S |

---

## 6. 外部サービス連携

| 連携パターン | 説明 | 検証テンプレート |
|------------|------|---------------|
| 認証API委譲 | パスワード認証を外部HTTPサービスに委譲 | ExtPW |
| SMS送信API | OTP送信・検証を外部APIに委譲 | MFA-S |
| Email送信API | OTP送信・検証を外部APIに委譲（http_request切替） | MFA-E（パターン5） |
| OAuth認証付き外部API | `auth_type: "oauth2"` + `oauth_authorization` | MFA-E（パターン5-3）, MFA-S |
| eKYCサービス連携 | 身元確認プロセスの外部API委譲 | eKYC |
| フェデレーション（OIDC） | Google等 外部IdPとの認証連携 | Social |
| http_request_store | チャレンジ→検証間のデータ引き継ぎ | MFA-S, MFA-E（パターン5-2） |

### 外部連携で検証済みの設定パターン

| パターン | 内容 | 落とし穴 |
|---------|------|---------|
| `auth_type: "oauth2"` 必須 | `oauth_authorization` と同じオブジェクトに必須 | 無いとOAuth認証が実行されない |
| 内部/外部でマッピングパスが異なる | 内部: `$` 直接、外部: `$.execution_http_request.response_body` | パスを間違えるとマッピングが空になる |
| `$.interaction` vs `$.previous_interaction` | 送信ボディでは `$.interaction.xxx` | `$.previous_interaction` ではない |
| レスポンスのワイルドカード禁止 | `"to": "*"` は内部データ漏洩リスク | `verification_code` 等がクライアントに露出 |

---

## 7. デバイス管理

| 機能 | 説明 | 検証テンプレート |
|------|------|---------------|
| FIDO2デバイス登録 | WebAuthn によるパスキー登録 | FIDO2, FAPI |
| FIDO2デバイス認証 | 登録済みパスキーでの認証 | FIDO2, FAPI |
| FIDO-UAFデバイス登録 | 自動デバイス登録 + device_secret発行 | CIBA |
| デバイス数上限 | 最大登録デバイス数の制限（例: 5台） | FIDO2, CIBA |
| Device Secret (JWT Bearer) | デバイスシークレットによるトークン取得 | CIBA |

---

## 8. 身元確認（eKYC）

| 機能 | 説明 | 検証テンプレート |
|------|------|---------------|
| 身元確認申請 | eKYC フロー開始（apply） | eKYC |
| 審査結果取得 | 外部サービスからの結果コールバック（evaluate-result） | eKYC |
| verified_claims 発行 | 身元確認済み属性の ID Token / UserInfo への反映 | eKYC |
| スコープトリガー | 特定スコープ要求時のみ身元確認を開始 | eKYC（`transfers` スコープ） |
| モック動作 | `mock/no_action` モードでローカル検証 | eKYC |

---

## 9. マルチクライアント

`third-party` テンプレートで3種類のクライアントパターンを検証済み:

| クライアント種別 | 認証方式 | Grant Type | PKCE | 用途 |
|---------------|---------|-----------|:----:|------|
| Web（Confidential） | `client_secret_basic` | authorization_code + refresh_token | — | Webアプリケーション |
| Mobile（Public） | `none` | authorization_code + refresh_token | 必須 | モバイルアプリ |
| M2M | `client_secret_basic` | client_credentials | — | サービス間連携 |

---

## 10. テナント・セッション管理

全テンプレートで共通:

| 機能 | 設定 |
|------|------|
| マルチテナント | 組織 → テナント の階層構造 |
| セッションタイムアウト | 24時間（86400秒） |
| 管理API | Management API でテナント/認可サーバー/クライアント/認証設定を CRUD |
| 設定のフル置換更新 | PUT API による設定全体の置換（差分更新ではない） |

---

## 対応プロトコル

テンプレートで実際に検証済みのプロトコル:

| プロトコル | 検証テンプレート |
|-----------|---------------|
| OAuth 2.0 Authorization Code (RFC 6749) | 全テンプレート |
| OAuth 2.0 Client Credentials | 3rd |
| OAuth 2.0 JWT Bearer Grant (RFC 7523) | CIBA |
| OpenID Connect Core 1.0 | 全テンプレート |
| OpenID Connect Discovery | 全テンプレート |
| CIBA (Poll / Ping) | CIBA, FAPI |
| FAPI 1.0 Advanced | FAPI |
| OpenID for Identity Assurance (verified_claims) | eKYC |
| Token Introspection (RFC 7662) | 3rd, FAPI |
| Token Revocation (RFC 7009) | 全テンプレート |

---

## カスタムスコープの設定例

| スコープ | 用途 | テンプレート |
|---------|------|------------|
| `transfers` | 送金操作（条件付きMFA / eKYCトリガー） | MFA-E, eKYC, FAPI |
| `identity_verification_application` | 身元確認申請権限 | eKYC |
| `api:read` / `api:write` | カスタムAPIアクセス制御 | 3rd |
| `claims:authentication_devices` | 認証デバイス情報クレーム | CIBA |
| `read` / `account` / `write` | FAPI Baseline/Advanced スコープ | FAPI |
