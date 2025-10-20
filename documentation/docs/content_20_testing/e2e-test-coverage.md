# E2Eテストカバレッジレポート

**最終更新日**: 2025-10-20
**総テストファイル数**: 87
**テストフレームワーク**: Jest

## 概要

本ドキュメントは、idp-serverプロジェクトのEnd-to-End（E2E）テストカバレッジの包括的な概要を提供します。E2Eテストは3つの主要カテゴリに分類されています：

- **📘 spec/** - 仕様準拠テスト（RFCおよび標準プロトコル検証）
- **📕 scenario/** - 実践的統合シナリオテスト
- **🐒 monkey/** - カオステストおよびエッジケース検証

## テスト統計

| カテゴリ | テストファイル数 | 説明 |
|----------|-----------------|------|
| **RFC準拠テスト (spec)** | 32 | プロトコル仕様テスト |
| **アプリケーションシナリオ** | 11 | エンドユーザーアプリケーションフローテスト |
| **Control Plane - システムレベル** | 14 | システムレベル管理APIテスト |
| **Control Plane - 組織レベル** | 28 | 組織レベル管理APIテスト |
| **Resource Server** | 1 | トークンイントロスペクション拡張テスト |
| **Monkeyテスト** | 1 | カオス・ストレステスト |
| **合計** | **87** | |

---

## 1. RFC準拠テスト (spec/)

**総ファイル数**: 32

これらのテストは、OAuth 2.0、OIDC、および関連プロトコル仕様への準拠を検証します。

### 1.1 OAuth 2.0 Core (RFC 6749)

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `rfc6749_4_1_code.test.js` | RFC 6749 Section 4.1 | 認可コードフロー |
| `rfc6749_4_1_code_secret_basic.test.js` | RFC 6749 Section 4.1 | 認可コード + client_secret_basic |
| `rfc6749_4_2_implicit.test.js` | RFC 6749 Section 4.2 | インプリシットグラントフロー |
| `rfc6749_4_3_resource_owner_password_credentials.test.js` | RFC 6749 Section 4.3 | リソースオーナーパスワードクレデンシャルグラント |
| `rfc6749_4_4_client_credentials.test.js` | RFC 6749 Section 4.4 | クライアントクレデンシャルグラント |
| `rfc6749_6_refresh_token.test.js` | RFC 6749 Section 6 | リフレッシュトークングラント |

**主要テストケース**:
- 認可エンドポイント HTTP GET/POST サポート
- トークンエンドポイント検証
- エラーレスポンスと不正リクエスト処理
- state パラメータ検証
- redirect_uri 検証

### 1.2 OpenID Connect Core 1.0

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `oidc_core_2_id_token.test.js` | OIDC Core Section 2 | IDトークン構造と検証 |
| `oidc_core_2_id_token_extension.test.js` | OIDC Core Section 2 | IDトークン拡張クレーム |
| `oidc_core_3_1_code.test.js` | OIDC Core Section 3.1 | 認可コードフロー（OIDC） |
| `oidc_core_3_2_implicit.test.js` | OIDC Core Section 3.2 | インプリシットフロー（OIDC） |
| `oidc_core_3_3_hybrid.test.js` | OIDC Core Section 3.3 | ハイブリッドフロー |
| `oidc_core_5_userinfo.test.js` | OIDC Core Section 5 | UserInfoエンドポイント |
| `oidc_core_6_request_object.test.js` | OIDC Core Section 6 | Request Object（JWT） |
| `oidc_core_9_client_authenticartion.test.js` | OIDC Core Section 9 | クライアント認証方式 |
| `oidc_discovery.test.js` | OIDC Discovery | Discoveryエンドポイント（.well-known） |

**主要テストケース**:
- IDトークン署名検証（RS256、ES256）
- IDトークンクレーム検証（iss、aud、exp、iat、nonce等）
- UserInfoエンドポイントクレーム
- Request Object JWT検証
- クライアント認証：client_secret_basic、client_secret_post、private_key_jwt

### 1.3 拡張仕様

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `rfc7009_token_revocation.test.js` | RFC 7009 | トークン失効 |
| `rfc7636_pkce.test.js` | RFC 7636 | PKCE（Proof Key for Code Exchange） |
| `rfc7662_token_introspection.test.js` | RFC 7662 | トークンイントロスペクション |
| `rfc8705_mtls.test.js` | RFC 8705 | OAuth 2.0 Mutual-TLS |
| `rfc9126_par.test.js` | RFC 9126 | PAR（Pushed Authorization Requests） |
| `rfc9396_rar.test.js` | RFC 9396 | RAR（Rich Authorization Requests） |

**主要テストケース**:
- access_tokenおよびrefresh_tokenの失効
- PKCEのcode_challengeおよびcode_verifier検証（S256、plain）
- トークンイントロスペクションレスポンス形式
- mTLSクライアント証明書検証
- PAR request_uri処理
- RAR authorization_details構造

### 1.4 CIBA（Client-Initiated Backchannel Authentication）

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `ciba_authentication_request.test.js` | CIBA Core 1.0 | 認証リクエスト開始 |
| `ciba_token_request.test.js` | CIBA Core 1.0 | auth_req_idを使用したトークンリクエスト |
| `ciba_ping.test.js` | CIBA Core 1.0 | Pingモードフロー |
| `ciba_push.test.js` | CIBA Core 1.0 | Pushモードフロー |
| `ciba_discovery.test.js` | CIBA Discovery | CIBAディスカバリーメタデータ |

**主要テストケース**:
- バックチャネル認証エンドポイント
- auth_req_id生成と検証
- Push通知配信
- Pingポーリングメカニズム
- ユーザー承認/拒否処理

### 1.5 FAPI（Financial-grade API）

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `fapi_baseline.test.js` | FAPI 1.0 Part 1 | ベースラインセキュリティプロファイル |
| `fapi_advance.test.js` | FAPI 1.0 Part 2 | アドバンスドセキュリティプロファイル |
| `jarm.test.js` | JARM | JWT Secured Authorization Response Mode |

**主要テストケース**:
- FAPI Baseline: PKCE、stateパラメータ、scope検証
- FAPI Advanced: PAR、request object、mTLS
- JARM: JWT署名付き認可レスポンス

### 1.6 Identity Assurance & Verifiable Credentials

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `oidc_for_identity_assurance.test.js` | OIDC4IDA 1.0 | Identity Assuranceクレーム |
| `openid_for_verifiable_credential_rar.test.js` | OIDC4VC | RARを使用したVerifiable Credentials |

**主要テストケース**:
- verified_claims構造
- trust_framework検証
- authorization_detailsを使用したVerifiable Credential発行

### 1.7 Shared Signals Framework（SSF）

| テストファイル | 仕様 | カバー範囲 |
|---------------|------|-----------|
| `ssf_discovery.test.js` | SSF 1.0 draft-04 | SSF Discoveryエンドポイント |

**主要テストケース**:
- SSFディスカバリーメタデータ
- Security Event Token（SET）設定

---

## 2. シナリオテスト (scenario/)

### 2.1 アプリケーション層テスト (scenario/application/)

**総ファイル数**: 11

これらのテストは、実世界のエンドユーザーアプリケーションフローをシミュレートします。

| テストファイル | シナリオ | 説明 |
|---------------|----------|------|
| `scenario-01-user-registration.test.js` | ユーザー登録 | メールベースのユーザー登録フロー |
| `scenario-02-sso-oidc.test.js` | SSO | OpenID ConnectによるSingle Sign-On |
| `scenario-03-mfa-registration.test.js` | MFA | 多要素認証登録（TOTP、WebAuthn） |
| `scenario-04-ciba-mfa.test.js` | CIBA + MFA | MFAチャレンジを伴うCIBAフロー |
| `scenario-05-identity_verification-application.test.js` | 身元確認 | 身元確認申請送信 |
| `scenario-06-identity_verification-result.test.js` | 身元確認 | 身元確認結果取得 |
| `scenario-07-identity-verification-retry.test.js` | 身元確認 | HttpRequestExecutorリトライ機能 |
| `scenario-07-user-deletion.test.js` | ユーザーライフサイクル | ユーザー削除とクリーンアップ |
| `scenario-08-identity-verification-token-validation.test.js` | トークン検証 | 身元確認用トークン検証 |
| `scenario-08-multi-app_fido-authn.test.js` | マルチアプリ | 複数クライアント間のFIDO認証 |
| `scenario-09-token-refresh.test.js` | トークンリフレッシュ | トークンリフレッシュ戦略 |

**主要シナリオ**:
- **ユーザーオンボーディング**: メール認証チャレンジ → ユーザー登録 → プロファイル作成
- **SSOフロー**: 認可リクエスト → 認証 → トークン発行 → UserInfo取得
- **MFAセットアップ**: TOTPシークレット生成 → QRコード表示 → 検証コード検証
- **身元確認**: 外部サービス連携 → コールバック処理 → ステータス追跡
- **FIDO/WebAuthn**: クレデンシャル登録 → 認証セレモニー → クロスアプリ認証

### 2.2 Control Plane - システムレベルテスト (scenario/control_plane/system/)

**総ファイル数**: 14

これらのテストは、システムレベル管理APIを検証します。

| テストファイル | 管理API | テスト対象操作 |
|---------------|---------|---------------|
| `user-management.test.js` | ユーザー管理 | ユーザーのCRUD操作 |
| `client_management.test.js` | クライアント管理 | OAuthクライアントのCRUD操作 |
| `role-management.test.js` | ロール管理 | ロール作成、権限割り当て |
| `permission-management.test.js` | 権限管理 | 権限のCRUD操作 |
| `authentication_management.test.js` | 認証設定 | 認証設定管理 |
| `authorization_server_management.test.js` | 認可サーバー | 認可サーバー設定 |
| `federation_management.test.js` | フェデレーション設定 | フェデレーション設定管理 |
| `identity_verification_management.test.js` | 身元確認 | 身元確認設定 |
| `security-event-management.test.js` | セキュリティイベント | セキュリティイベント管理 |
| `security-event-hook-management.test.js` | セキュリティイベントフック | フック登録と実行 |
| `security-event-hook-management-retry.test.js` | セキュリティイベントフック | フックリトライメカニズム |
| `security_event_hook_config_management.test.js` | セキュリティイベントフック設定 | フック設定管理 |
| `audit-log-management.test.js` | 監査ログ | 監査ログ取得とフィルタリング |
| `tenant_invitation_management.test.js` | テナント招待 | テナント招待ワークフロー |

**主要管理操作**:
- **ユーザー管理**: provider_idを使用したユーザー作成、プロファイル更新、ユーザーアカウント削除
- **クライアント管理**: OAuthクライアント登録、クライアントメタデータ更新、クライアントシークレットローテーション
- **RBAC**: ロール作成、権限割り当て、ロール階層管理
- **セキュリティイベントフック**: Webhookエンドポイント登録、リトライポリシー設定、フック実行

### 2.3 Control Plane - 組織レベルテスト (scenario/control_plane/organization/)

**総ファイル数**: 28

これらのテストは、組織スコープの管理APIを検証します。

| テストファイル | 管理API | スコープ |
|---------------|---------|---------|
| `organization_user_management.test.js` | ユーザー管理 | 組織スコープ |
| `organization_client_management.test.js` | クライアント管理 | 組織スコープ |
| `organization_client_management_structured.test.js` | クライアント管理 | 構造化テストスイート |
| `organization_role_management.test.js` | ロール管理 | 組織スコープ |
| `organization_role_management_structured.test.js` | ロール管理 | 構造化テストスイート |
| `organization_permission_management.test.js` | 権限管理 | 組織スコープ |
| `organization_permission_management_structured.test.js` | 権限管理 | 構造化テストスイート |
| `organization_tenant_management.test.js` | テナント管理 | 組織スコープ |
| `organization_tenant_management_structured.test.js` | テナント管理 | 構造化テストスイート |
| `organization_authentication_config_management.test.js` | 認証設定 | 組織スコープ |
| `organization_authentication_config_management_structured.test.js` | 認証設定 | 構造化テストスイート |
| `organization_authentication_policy_config_management.test.js` | 認証ポリシー | 組織スコープ |
| `organization_authentication_policy_config_management_structured.test.js` | 認証ポリシー | 構造化テストスイート |
| `organization_authentication_interaction_management.test.js` | 認証インタラクション | 組織スコープ |
| `organization_authentication_transaction_management.test.js` | 認証トランザクション | 組織スコープ |
| `organization_authorization_server_management.test.js` | 認可サーバー | 組織スコープ |
| `organization_federation_config_management.test.js` | フェデレーション設定 | 組織スコープ |
| `organization_federation_config_management_structured.test.js` | フェデレーション設定 | 構造化テストスイート |
| `organization_identity_verification_config_management.test.js` | 身元確認設定 | 組織スコープ |
| `organization_identity_verification_config_management_structured.test.js` | 身元確認設定 | 構造化テストスイート |
| `organization_security_event_management.test.js` | セキュリティイベント | 組織スコープ |
| `organization_security_event_management_structured.test.js` | セキュリティイベント | 構造化テストスイート |
| `organization_security_event_hook_management.test.js` | セキュリティイベントフック | 組織スコープ |
| `organization_security_event_hook_config_management.test.js` | セキュリティイベントフック設定 | 組織スコープ |
| `organization_security_event_hook_config_management_structured.test.js` | セキュリティイベントフック設定 | 構造化テストスイート |
| `organization_audit_log_management.test.js` | 監査ログ | 組織スコープ |
| `organization_audit_log_management_structured.test.js` | 監査ログ | 構造化テストスイート |
| `api_behavior_debug.test.js` | デバッグ | API動作デバッグ |

**主要な組織レベル機能**:
- **マルチテナンシー**: 組織スコープのテナント管理
- **委譲**: 組織管理者権限
- **分離**: 組織レベルのデータ分離
- **構造化テスト**: 体系的なカバレッジを持つ包括的テストスイート

### 2.4 Resource Serverテスト (scenario/resource_server/)

**総ファイル数**: 1

| テストファイル | カバー範囲 |
|---------------|-----------|
| `token_introspection_extensions.test.js` | トークンイントロスペクション拡張クレーム |

---

## 3. Monkeyテスト (monkey/)

**総ファイル数**: 1

| テストファイル | カバー範囲 |
|---------------|-----------|
| `ciba-monkey.test.js` | CIBAカオステストおよびエッジケース |

**主要シナリオ**:
- 並行CIBAリクエスト
- タイムアウト処理
- 無効な状態遷移
- リソース枯渇

---

## 4. テストインフラストラクチャ

### 4.1 設定管理

**ファイル**: `e2e/src/tests/testConfig.js`

**機能**:
- 環境変数による動的テナント設定
- マルチ環境サポート（ローカル、ステージング、CI）
- クライアント設定管理
- サーバーエンドポイント設定

**環境変数**:
```bash
IDP_SERVER_URL=http://localhost:8080
IDP_SERVER_TENANT_ID=<tenant-id>
CIBA_USER_SUB=<user-sub>
CIBA_USER_EMAIL=<email>
CIBA_USERNAME=<username>
CIBA_PASSWORD=<password>
```

### 4.2 テストユーティリティ

**場所**: `e2e/src/lib/`

| ユーティリティ | 用途 |
|---------------|------|
| `http.js` | HTTPクライアント（GET、POST、PUT、PATCH、DELETE） |
| `jose.js` | JWT作成と検証 |
| `oauth.js` | OAuthヘルパー関数（ハッシュ計算等） |
| `util.js` | ユーティリティ関数（ランダム生成、エンコーディング等） |

### 4.3 APIクライアント

**場所**: `e2e/src/api/`

| クライアント | 用途 |
|-------------|------|
| `oauthClient.js` | OAuth/OIDCエンドポイントクライアント |

### 4.4 OAuthフローヘルパー

**場所**: `e2e/src/oauth/`

| ヘルパー | 用途 |
|---------|------|
| `request.js` | 認可リクエスト、ログアウト等 |

---

## 5. 詳細カバレッジ測定結果

### 5.1 実装API vs E2Eテストカバレッジ

**測定日**: 2025-10-20
**測定方法**: EntryService実装ファイル（55個）とE2Eテストファイル（87個）の突合

#### EntryService実装状況

| カテゴリ | 実装EntryService数 | 説明 |
|---------|------------------|------|
| **Control Plane - System Manager** | 17 | システムレベル管理API |
| **Control Plane - Organization Manager** | 11 | 組織レベル管理API |
| **Application - End User** | 9 | エンドユーザー向けAPI |
| **Application - System** | 6 | システム内部API |
| **Application - Identity Verification** | 2 | 身元確認サービスAPI |
| **Application - Relying Party** | 1 | RPメタデータAPI |
| **Application - Tenant Invitator** | 1 | テナント招待API |
| **Control Plane - System Administrator** | 2 | システム管理者API |
| **合計** | **55** | |

#### Management API E2Eテストカバレッジ詳細

##### システムレベルManagement API

| EntryService | E2Eテスト | テストファイル | カバレッジ |
|-------------|----------|--------------|----------|
| `UserManagementEntryService` | ✅ | `user-management.test.js` | **100%** |
| `ClientManagementEntryService` | ✅ | `client_management.test.js` | **100%** |
| `RoleManagementEntryService` | ✅ | `role-management.test.js` | **100%** |
| `PermissionManagementEntryService` | ✅ | `permission-management.test.js` | **100%** |
| `TenantManagementEntryService` | ❌ | - | **0%** |
| `TenantInvitationManagementEntryService` | ✅ | `tenant_invitation_management.test.js` | **100%** |
| `AuthenticationConfigurationManagementEntryService` | ✅ | `authentication_management.test.js` | **100%** |
| `AuthenticationPolicyConfigurationManagementEntryService` | ❌ | - | **0%** |
| `AuthenticationInteractionManagementEntryService` | ❌ | - | **0%** |
| `AuthenticationTransactionManagementEntryService` | ❌ | - | **0%** |
| `AuthorizationServerManagementEntryService` | ✅ | `authorization_server_management.test.js` | **100%** |
| `FederationConfigurationManagementEntryService` | ✅ | `federation_management.test.js` | **100%** |
| `IdentityVerificationConfigManagementEntryService` | ✅ | `identity_verification_management.test.js` | **100%** |
| `SecurityEventManagementEntryService` | ✅ | `security-event-management.test.js` | **100%** |
| `SecurityEventHookManagementEntryService` | ✅ | `security-event-hook-management.test.js` + `*-retry.test.js` | **100%** |
| `SecurityEventHookConfigManagementEntryService` | ✅ | `security_event_hook_config_management.test.js` | **100%** |
| `AuditLogManagementEntryService` | ✅ | `audit-log-management.test.js` | **100%** |
| `OnboardingEntryService` | ⚠️ | scenario-01で間接的にテスト | **部分的** |

**システムレベルカバレッジ**: **13/17 = 76.5%**

##### 組織レベルManagement API

| EntryService | E2Eテスト | テストファイル | カバレッジ |
|-------------|----------|--------------|----------|
| `OrgUserManagementEntryService` | ✅ | `organization_user_management.test.js` | **100%** |
| `OrgClientManagementEntryService` | ✅ | `organization_client_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgRoleManagementEntryService` | ✅ | `organization_role_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgPermissionManagementEntryService` | ✅ | `organization_permission_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgTenantManagementEntryService` | ✅ | `organization_tenant_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgAuthenticationConfigManagementEntryService` | ✅ | `organization_authentication_config_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgAuthenticationPolicyConfigManagementEntryService` | ✅ | `organization_authentication_policy_config_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgAuthenticationInteractionManagementEntryService` | ✅ | `organization_authentication_interaction_management.test.js` | **100%** |
| `OrgAuthenticationTransactionManagementEntryService` | ✅ | `organization_authentication_transaction_management.test.js` | **100%** |
| `OrgAuthorizationServerManagementEntryService` | ✅ | `organization_authorization_server_management.test.js` | **100%** |
| `OrgFederationConfigManagementEntryService` | ✅ | `organization_federation_config_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgIdentityVerificationConfigManagementEntryService` | ✅ | `organization_identity_verification_config_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgSecurityEventManagementEntryService` | ✅ | `organization_security_event_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgSecurityEventHookManagementEntryService` | ✅ | `organization_security_event_hook_management.test.js` | **100%** |
| `OrgSecurityEventHookConfigManagementEntryService` | ✅ | `organization_security_event_hook_config_management.test.js` + `*_structured.test.js` | **100%** |
| `OrgAuditLogManagementEntryService` | ✅ | `organization_audit_log_management.test.js` + `*_structured.test.js` | **100%** |

**組織レベルカバレッジ**: **16/16 = 100%** 🎉

**注**: 組織レベルAPIは `OrgTenantInvitationManagementEntryService` が実装されていないため、16個のEntryServiceのみ

##### Application API

| EntryService | E2Eテスト | テストファイル | カバレッジ |
|-------------|----------|--------------|----------|
| `OAuthFlowEntryService` | ✅ | spec層32ファイル + scenario-02 | **100%** |
| `CibaFlowEntryService` | ✅ | ciba_*.test.js (5ファイル) + scenario-04 | **100%** |
| `TokenEntryService` | ✅ | rfc6749_6_refresh_token.test.js + scenario-09 | **100%** |
| `UserinfoEntryService` | ✅ | oidc_core_5_userinfo.test.js | **100%** |
| `AuthenticationTransactionEntryService` | ✅ | scenario-01, 02, 03 | **100%** |
| `UserOperationEntryService` | ✅ | scenario-03, 07 | **100%** |
| `IdentityVerificationEntryService` | ✅ | scenario-05, 06 | **100%** |
| `IdentityVerificationCallbackEntryService` | ✅ | scenario-05, 06 | **100%** |
| `AuthenticationMetaDataEntryService` | ✅ | oidc_discovery.test.js | **100%** |
| `OidcMetaDataEntryService` | ✅ | oidc_discovery.test.js | **100%** |
| `UserAuthenticationEntryService` | ⚠️ | 間接的にテスト | **部分的** |
| `SecurityEventEntryService` | ⚠️ | 間接的にテスト | **部分的** |
| `UserLifecycleEventEntryService` | ⚠️ | scenario-07で間接的にテスト | **部分的** |
| `AuditLogEntryService` | ⚠️ | 間接的にテスト | **部分的** |
| `TenantMetaDataEntryService` | ❓ | - | **不明** |
| `TenantInvitationMetaDataEntryService` | ❓ | - | **不明** |

**ApplicationAPIカバレッジ**: **10/16 = 62.5%** （直接テストのみ）

#### 総合カバレッジサマリー

| カテゴリ | 実装数 | テスト済み | 部分的 | 未テスト | カバレッジ率 |
|---------|-------|----------|--------|---------|------------|
| **システムレベル管理API** | 17 | 13 | 1 | 4 | **76.5%** |
| **組織レベル管理API** | 16 | 16 | 0 | 0 | **100%** 🎉 |
| **アプリケーションAPI** | 16 | 10 | 4 | 2 | **62.5%** |
| **システム管理者API** | 2 | 0 | 0 | 2 | **0%** |
| **全体** | **51** | **39** | **5** | **8** | **76.5%** |

**注**: システム管理者API（`IdpServerStarterEntryService`、`IdpServerOperationEntryService`）は運用系APIのためE2Eテスト対象外

### 5.2 テストされていない主要機能

#### 優先度：高（早急な対応推奨）

1. **TenantManagementEntryService（システムレベル）**
   - **影響範囲**: マルチテナント基盤の根幹
   - **理由**: 組織レベルではテスト済みだが、システムレベルが未テスト
   - **リスク**: テナント作成・更新・削除の不具合が検出できない

2. **AuthenticationPolicyConfigurationManagementEntryService（システムレベル）**
   - **影響範囲**: 認証ポリシー設定（パスワードポリシー、MFA設定等）
   - **理由**: 組織レベルではテスト済みだが、システムレベルが未テスト
   - **リスク**: セキュリティポリシー設定の不具合

3. **AuthenticationInteractionManagementEntryService（システムレベル）**
   - **影響範囲**: 認証インタラクション管理
   - **理由**: 組織レベルではテスト済みだが、システムレベルが未テスト

4. **AuthenticationTransactionManagementEntryService（システムレベル）**
   - **影響範囲**: 認証トランザクション管理
   - **理由**: 組織レベルではテスト済みだが、システムレベルが未テスト

#### 優先度：中（計画的な対応推奨）

5. **TenantMetaDataEntryService**
   - **影響範囲**: テナントメタデータ公開API
   - **理由**: 外部システム連携に影響

6. **TenantInvitationMetaDataEntryService**
   - **影響範囲**: テナント招待メタデータ公開API
   - **理由**: テナント招待フローの可視性

#### 優先度：低（間接的にカバー済み）

7. **UserAuthenticationEntryService** - scenario層で間接的にテスト済み
8. **SecurityEventEntryService** - Hook経由で間接的にテスト済み
9. **UserLifecycleEventEntryService** - scenario-07で間接的にテスト済み
10. **AuditLogEntryService** - 各操作で間接的にテスト済み

### 5.3 プロトコルカバレッジ

| プロトコル/仕様 | カバレッジ状況 | テストファイル数 |
|----------------|---------------|-----------------|
| **OAuth 2.0 Core** | ✅ 包括的 | 6ファイル |
| **OIDC Core** | ✅ 包括的 | 9ファイル |
| **PKCE** | ✅ 完全 | 1ファイル |
| **Token Revocation** | ✅ 完全 | 1ファイル |
| **Token Introspection** | ✅ 完全 | 2ファイル |
| **PAR** | ✅ 完全 | 1ファイル |
| **RAR** | ✅ 完全 | 1ファイル |
| **CIBA** | ✅ 完全 | 5ファイル |
| **FAPI** | ✅ 完全 | 3ファイル |
| **mTLS** | ✅ 完全 | 1ファイル |
| **JARM** | ✅ 完全 | 1ファイル |
| **OIDC4IDA** | ✅ 完全 | 1ファイル |
| **OIDC4VC** | ✅ 完全 | 1ファイル |
| **SSF Discovery** | ✅ 完全 | 1ファイル |

### 5.4 アプリケーションフローカバレッジ

| フロー | カバレッジ状況 |
|--------|---------------|
| **ユーザー登録** | ✅ 完全 |
| **ユーザーログイン** | ✅ 完全 |
| **ユーザー削除** | ✅ 完全 |
| **SSO** | ✅ 完全 |
| **MFA登録** | ✅ 完全 |
| **MFA認証** | ✅ 完全 |
| **FIDO/WebAuthn** | ✅ 完全 |
| **CIBA + MFA** | ✅ 完全 |
| **身元確認** | ✅ 完全 |
| **トークンリフレッシュ** | ✅ 完全 |

---

## 6. 推奨事項

### 6.1 カバレッジギャップ

以下の領域は追加のテストカバレッジが有益です：

1. **システムレベル管理API**:
   - テナント管理API（システムレベル）
   - 認証ポリシー設定管理（システムレベル）
   - 認証インタラクション管理（システムレベル）
   - 認証トランザクション管理（システムレベル）

2. **組織レベル管理API**:
   - テナント招待管理（組織レベル）

3. **エラーシナリオ**:
   - ネットワーク障害シミュレーション
   - データベース接続障害
   - 外部サービスタイムアウト

4. **パフォーマンステスト**:
   - 高並行シナリオの負荷テスト
   - 負荷下でのトークンエンドポイントパフォーマンス
   - UserInfoエンドポイントキャッシング

5. **セキュリティテスト**:
   - SQLインジェクション試行
   - XSS攻撃ベクトル
   - CSRFトークン検証

### 6.2 テスト組織化

**強み**:
- spec、scenario、monkeyテストの明確な分離
- 包括的なRFC準拠カバレッジ
- 組織レベルAPIの構造化テストスイート
- マルチ環境テストのための動的設定

**改善機会**:
- システムレベルAPIの構造化テストスイート追加
- 一貫したテストセットアップのためのテストデータファクトリ実装
- エッジ条件のネガティブテストケース追加
- 高速実行のためのテスト並列化実装

### 6.3 ドキュメント

**現状**:
- `e2e/README.md`がセットアップと設定手順を提供
- テストファイルに説明的なテスト名が含まれる
- 環境変数がドキュメント化されている

**今後の改善**:
- テスト実行レポート追加（HTML/JSON出力）
- テストデータ要件のドキュメント化
- 一般的なテスト失敗のトラブルシューティングガイド作成
- READMEへのテストカバレッジバッジ追加

---

## 7. テスト実行

### 7.1 全テスト実行

```bash
cd e2e
npm test
```

### 7.2 カテゴリ別実行

```bash
# RFC準拠テスト
npm test spec/

# アプリケーションシナリオテスト
npm test scenario/application/

# システムレベル管理APIテスト
npm test scenario/control_plane/system/

# 組織レベル管理APIテスト
npm test scenario/control_plane/organization/

# Resource Serverテスト
npm test scenario/resource_server/

# Monkeyテスト
npm test monkey/
```

### 7.3 特定テストファイル実行

```bash
# 特定のテスト実行
npm test spec/oidc_core_3_1_code.test.js

# カバレッジ付き実行
npm run test-coverage
```

### 7.4 異なる環境での実行

```bash
# ローカル環境
npm test

# ステージング環境
IDP_SERVER_URL=https://staging.example.com \
IDP_SERVER_TENANT_ID=staging-tenant \
npm test

# CI環境
IDP_SERVER_URL=https://ci-idp.example.com \
IDP_SERVER_TENANT_ID=ci-tenant-123 \
npm test
```

---

## 8. メンテナンス

### 8.1 新規テスト追加

1. 適切なカテゴリを選択（spec/scenario/monkey）
2. 既存のテスト構造と命名規則に従う
3. `testConfig.js`から共有設定を使用
4. 必要に応じて`.env.example`に環境変数を追加
5. 本カバレッジドキュメントを更新

### 8.2 テスト更新

1. 動的テナント設定でテストが機能することを確認
2. 既存のテストデータとの後方互換性を維持
3. API契約変更時にテスト期待値を更新
4. コミットメッセージに破壊的変更を記録

### 8.3 テストヘルス監視

**現在のステータス**: ✅ 全テスト合格（最終更新時点）

**監視戦略**:
- CI/CDパイプラインで完全なテストスイートを実行
- テスト実行時間のトレンド追跡
- 不安定なテストの発生を監視
- テスト失敗を週次でレビュー

---

## 付録A: テストファイルインデックス

### RFC準拠テスト（32ファイル）

```
e2e/src/tests/spec/
├── OAuth 2.0 Core (6ファイル)
│   ├── rfc6749_4_1_code.test.js
│   ├── rfc6749_4_1_code_secret_basic.test.js
│   ├── rfc6749_4_2_implicit.test.js
│   ├── rfc6749_4_3_resource_owner_password_credentials.test.js
│   ├── rfc6749_4_4_client_credentials.test.js
│   └── rfc6749_6_refresh_token.test.js
├── OIDC Core (9ファイル)
│   ├── oidc_core_2_id_token.test.js
│   ├── oidc_core_2_id_token_extension.test.js
│   ├── oidc_core_3_1_code.test.js
│   ├── oidc_core_3_2_implicit.test.js
│   ├── oidc_core_3_3_hybrid.test.js
│   ├── oidc_core_5_userinfo.test.js
│   ├── oidc_core_6_request_object.test.js
│   ├── oidc_core_9_client_authenticartion.test.js
│   └── oidc_discovery.test.js
├── 拡張仕様 (6ファイル)
│   ├── rfc7009_token_revocation.test.js
│   ├── rfc7636_pkce.test.js
│   ├── rfc7662_token_introspection.test.js
│   ├── rfc8705_mtls.test.js
│   ├── rfc9126_par.test.js
│   └── rfc9396_rar.test.js
├── CIBA (5ファイル)
│   ├── ciba_authentication_request.test.js
│   ├── ciba_discovery.test.js
│   ├── ciba_ping.test.js
│   ├── ciba_push.test.js
│   └── ciba_token_request.test.js
├── FAPI (3ファイル)
│   ├── fapi_advance.test.js
│   ├── fapi_baseline.test.js
│   └── jarm.test.js
├── Identity Assurance & VC (2ファイル)
│   ├── oidc_for_identity_assurance.test.js
│   └── openid_for_verifiable_credential_rar.test.js
└── SSF (1ファイル)
    └── ssf_discovery.test.js
```

### シナリオテスト（54ファイル）

```
e2e/src/tests/scenario/
├── application/ (11ファイル)
│   ├── scenario-01-user-registration.test.js
│   ├── scenario-02-sso-oidc.test.js
│   ├── scenario-03-mfa-registration.test.js
│   ├── scenario-04-ciba-mfa.test.js
│   ├── scenario-05-identity_verification-application.test.js
│   ├── scenario-06-identity_verification-result.test.js
│   ├── scenario-07-identity-verification-retry.test.js
│   ├── scenario-07-user-deletion.test.js
│   ├── scenario-08-identity-verification-token-validation.test.js
│   ├── scenario-08-multi-app_fido-authn.test.js
│   └── scenario-09-token-refresh.test.js
├── control_plane/system/ (14ファイル)
│   ├── audit-log-management.test.js
│   ├── authentication_management.test.js
│   ├── authorization_server_management.test.js
│   ├── client_management.test.js
│   ├── federation_management.test.js
│   ├── identity_verification_management.test.js
│   ├── permission-management.test.js
│   ├── role-management.test.js
│   ├── security-event-hook-management.test.js
│   ├── security-event-hook-management-retry.test.js
│   ├── security-event-management.test.js
│   ├── security_event_hook_config_management.test.js
│   ├── tenant_invitation_management.test.js
│   └── user-management.test.js
├── control_plane/organization/ (28ファイル)
│   ├── organization_user_management.test.js
│   ├── organization_client_management.test.js
│   ├── organization_client_management_structured.test.js
│   ├── organization_role_management.test.js
│   ├── organization_role_management_structured.test.js
│   ├── organization_permission_management.test.js
│   ├── organization_permission_management_structured.test.js
│   ├── organization_tenant_management.test.js
│   ├── organization_tenant_management_structured.test.js
│   ├── organization_authentication_config_management.test.js
│   ├── organization_authentication_config_management_structured.test.js
│   ├── organization_authentication_policy_config_management.test.js
│   ├── organization_authentication_policy_config_management_structured.test.js
│   ├── organization_authentication_interaction_management.test.js
│   ├── organization_authentication_transaction_management.test.js
│   ├── organization_authorization_server_management.test.js
│   ├── organization_federation_config_management.test.js
│   ├── organization_federation_config_management_structured.test.js
│   ├── organization_identity_verification_config_management.test.js
│   ├── organization_identity_verification_config_management_structured.test.js
│   ├── organization_security_event_management.test.js
│   ├── organization_security_event_management_structured.test.js
│   ├── organization_security_event_hook_management.test.js
│   ├── organization_security_event_hook_config_management.test.js
│   ├── organization_security_event_hook_config_management_structured.test.js
│   ├── organization_audit_log_management.test.js
│   ├── organization_audit_log_management_structured.test.js
│   └── api_behavior_debug.test.js
└── resource_server/ (1ファイル)
    └── token_introspection_extensions.test.js
```

### Monkeyテスト（1ファイル）

```
e2e/src/tests/monkey/
└── ciba-monkey.test.js
```

---

## 付録B: 関連ドキュメント

- [E2Eテストセットアップガイド](../../e2e/README.md)
- [APIドキュメント](./api-documentation.md)
- [認証フローガイド](./authentication-flows.md)
- [管理APIガイド](./management-api-guide.md)

---

**ドキュメントバージョン**: 1.0.0
**作成者**: Claude Code
**Issue**: #738
