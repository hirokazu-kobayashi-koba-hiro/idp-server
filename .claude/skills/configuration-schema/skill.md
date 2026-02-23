---
name: configuration-schema
description: 設定JSONスキーマリファレンス。テナント設定、認可サーバー設定、クライアント設定のフィールド一覧と有効な値を確認する際に使用。
---

# 設定JSON スキーマリファレンス

## 設定クラス間の関連

```
AuthorizationServerConfiguration
├─ extension: AuthorizationServerExtensionConfiguration
└─ credentialIssuerMetadata: VerifiableCredentialConfiguration
   └─ credentialsSupported: List<VerifiableCredentialsSupportConfiguration>

ClientConfiguration
└─ extension: ClientExtensionConfiguration
   └─ availableFederations: List<AvailableFederation>

Tenant
├─ uiConfiguration: UIConfiguration
├─ corsConfiguration: CorsConfiguration
├─ sessionConfiguration: SessionConfiguration
├─ identityPolicyConfig: TenantIdentityPolicy
│  ├─ passwordPolicyConfig: PasswordPolicyConfig
│  └─ authenticationDeviceRule: AuthenticationDeviceRule
├─ securityEventLogConfiguration: SecurityEventLogConfiguration
└─ securityEventUserAttributeConfiguration: SecurityEventUserAttributeConfiguration
```

---

## 1. AuthorizationServerConfiguration（認可サーバー設定）

**パス**: `libs/idp-server-core/.../oauth/configuration/AuthorizationServerConfiguration.java`

### 標準OIDC Discoveryフィールド

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `issuer` | String | トークン発行者URI |
| `authorization_endpoint` | String | 認可エンドポイントURL |
| `token_endpoint` | String | トークンエンドポイントURL |
| `userinfo_endpoint` | String | UserInfoエンドポイントURL |
| `jwks` | String | JWKS (inline) |
| `jwks_uri` | String | JWKS URIエンドポイント |
| `registration_endpoint` | String | クライアント登録エンドポイント |
| `end_session_endpoint` | String | セッション終了エンドポイント |
| `pushed_authorization_request_endpoint` | String | PARエンドポイント |
| `revocation_endpoint` | String | トークン取り消しエンドポイント |
| `introspection_endpoint` | String | トークンイントロスペクションエンドポイント |
| `scopes_supported` | List | サポートスコープ一覧 |
| `response_types_supported` | List | サポートレスポンスタイプ |
| `response_modes_supported` | List | サポートレスポンスモード |
| `grant_types_supported` | List | サポートグラント種別 |
| `acr_values_supported` | List | サポートACR値 |
| `subject_types_supported` | List | サポートサブジェクト型 |
| `token_endpoint_auth_methods_supported` | List | トークンエンドポイント認証方式 |
| `claims_supported` | List | サポートクレーム一覧 |
| `code_challenge_methods_supported` | List | PKCE対応チャレンジメソッド |
| `enabled` | boolean | 設定有効化フラグ (default: true) |

### 署名/暗号化アルゴリズム

| JSONキー | 対象 |
|---------|------|
| `id_token_signing_alg_values_supported` | ID Token署名 |
| `id_token_encryption_alg_values_supported` | ID Token暗号化alg |
| `id_token_encryption_enc_values_supported` | ID Token暗号化enc |
| `userinfo_signing_alg_values_supported` | UserInfo署名 |
| `userinfo_encryption_alg_values_supported` | UserInfo暗号化alg |
| `userinfo_encryption_enc_values_supported` | UserInfo暗号化enc |
| `request_object_signing_alg_values_supported` | Request Object署名 |
| `request_object_encryption_alg_values_supported` | Request Object暗号化alg |
| `request_object_encryption_enc_values_supported` | Request Object暗号化enc |
| `authorization_signing_alg_values_supported` | Authorization Response署名 |
| `authorization_encryption_alg_values_supported` | Authorization Response暗号化alg |
| `authorization_encryption_enc_values_supported` | Authorization Response暗号化enc |

### フラグ

| JSONキー | default | 説明 |
|---------|---------|------|
| `claims_parameter_supported` | true | claimsパラメータ対応 |
| `request_parameter_supported` | true | requestパラメータ対応 |
| `request_uri_parameter_supported` | true | request_uriパラメータ対応 |
| `require_request_uri_registration` | true | request_uri登録必須 |
| `require_signed_request_object` | false | Request Object署名必須 |
| `tls_client_certificate_bound_access_tokens` | false | Certificate Bound Access Tokens |
| `authorization_response_iss_parameter_supported` | false | issパラメータ対応 |
| `verified_claims_supported` | false | 身元確認済みクレーム対応 |

### CIBA

| JSONキー | 説明 |
|---------|------|
| `backchannel_token_delivery_modes_supported` | トークン配信モード |
| `backchannel_authentication_endpoint` | Backchannel認証エンドポイント |
| `backchannel_authentication_request_signing_alg_values_supported` | リクエスト署名アルゴリズム |
| `backchannel_user_code_parameter_supported` | user_codeパラメータ対応 (default: false) |

### mTLS

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `mtls_endpoint_aliases` | Map | mTLS用エンドポイント別名 |

---

## 2. AuthorizationServerExtensionConfiguration（拡張設定）

**パス**: `libs/idp-server-core/.../oauth/configuration/AuthorizationServerExtensionConfiguration.java`

`extension` フィールド内にネスト。

### トークン設定

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `access_token_type` | String | `opaque` | アクセストークン型（opaque/jwt） |
| `access_token_duration` | long | 1800 | アクセストークン有効期限（秒） |
| `refresh_token_duration` | long | 3600 | リフレッシュトークン有効期限（秒） |
| `refresh_token_strategy` | String | `FIXED` | リフレッシュトークン戦略（FIXED/EXTENDS） |
| `rotate_refresh_token` | boolean | true | リフレッシュトークンローテーション |
| `id_token_duration` | long | 3600 | ID Token有効期限（秒） |
| `id_token_strict_mode` | boolean | false | ID Token厳密モード |
| `token_signed_key_id` | String | - | トークン署名用キーID |
| `id_token_signed_key_id` | String | - | ID Token署名用キーID |

### 有効期限

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `authorization_code_valid_duration` | int | 600 | 認可コード有効期限（秒） |
| `default_max_age` | long | 86400 | デフォルト最大認証経過時間（秒） |
| `authorization_response_duration` | long | 60 | Authorization Response有効期限（秒） |
| `oauth_authorization_request_expires_in` | int | 1800 | OAuth認可リクエスト有効期限（秒） |
| `pushed_authorization_request_expires_in` | int | 90 | PAR有効期限（秒） |

### FAPI/スコープ

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `fapi_baseline_scopes` | List | FAPI Baselineスコープ |
| `fapi_advance_scopes` | List | FAPI Advanceスコープ |
| `required_identity_verification_scopes` | List | 身元確認必須スコープ |

### CIBA

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `backchannel_authentication_request_expires_in` | int | 300 | CIBAリクエスト有効期限（秒） |
| `backchannel_authentication_polling_interval` | int | 5 | ポーリング間隔（秒） |
| `required_backchannel_auth_user_code` | boolean | false | user_code必須 |
| `backchannel_auth_user_code_type` | String | `password` | user_codeの型 |
| `default_ciba_authentication_interaction_type` | String | `authentication-device-notification` | デフォルトCIBA認証方式 |

### クレーム制御

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `custom_claims_scope_mapping` | boolean | false | カスタムクレームスコープマッピング |
| `access_token_selective_user_custom_properties` | boolean | false | ATでユーザーカスタムプロパティ選択可能化 |
| `access_token_verified_claims` | boolean | false | ATに身元確認済みクレーム含有 |
| `access_token_selective_verified_claims` | boolean | false | ATで身元確認済みクレーム選択可能化 |

---

## 3. ClientConfiguration（クライアント設定）

**パス**: `libs/idp-server-core/.../oauth/configuration/client/ClientConfiguration.java`

### 基本

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `client_id` | String | クライアント識別子 |
| `client_id_alias` | String | クライアントIDエイリアス |
| `client_secret` | String | クライアントシークレット |
| `client_name` | String | クライアント名 |
| `client_uri` | String | クライアントURI |
| `logo_uri` | String | ロゴURI |
| `contacts` | List | 連絡先メールアドレス |
| `tos_uri` | String | 利用規約URI |
| `policy_uri` | String | プライバシーポリシーURI |
| `software_id` | String | ソフトウェアID |
| `software_version` | String | ソフトウェアバージョン |
| `application_type` | String | アプリケーション型 (default: web) |
| `enabled` | boolean | 有効化フラグ (default: true) |

### OAuth/OIDC

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `redirect_uris` | List | リダイレクトURI一覧 |
| `post_logout_redirect_uris` | List | ログアウト後リダイレクトURI一覧 |
| `token_endpoint_auth_method` | String | トークンエンドポイント認証方式 |
| `grant_types` | List | グラント型一覧 |
| `response_types` | List | レスポンス型一覧 |
| `scope` | String | 要求スコープ（スペース区切り） |
| `jwks_uri` | String | JWKS URI |
| `jwks` | String | JWKS (inline) |
| `request_uris` | List | Request URIレジストリ |

### 暗号化

| JSONキー | 説明 |
|---------|------|
| `id_token_encrypted_response_alg` | ID Token暗号化アルゴリズム |
| `id_token_encrypted_response_enc` | ID Token暗号化enc値 |
| `authorization_signed_response_alg` | Authorization Response署名アルゴリズム |
| `authorization_encrypted_response_alg` | Authorization Response暗号化アルゴリズム |
| `authorization_encrypted_response_enc` | Authorization Response暗号化enc値 |

### mTLS

| JSONキー | 説明 |
|---------|------|
| `tls_client_auth_subject_dn` | 証明書Subject DN |
| `tls_client_auth_san_dns` | DNS SAN |
| `tls_client_auth_san_uri` | URI SAN |
| `tls_client_auth_san_ip` | IP SAN |
| `tls_client_auth_san_email` | Email SAN |
| `tls_client_certificate_bound_access_tokens` | Certificate Bound AT (default: false) |

### CIBA

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `backchannel_token_delivery_mode` | String | `poll` | トークン配信モード |
| `backchannel_client_notification_endpoint` | String | - | 通知エンドポイント |
| `backchannel_authentication_request_signing_alg` | String | - | リクエスト署名アルゴリズム |
| `backchannel_user_code_parameter` | boolean | false | user_codeパラメータ対応 |

### ログアウト

| JSONキー | default | 説明 |
|---------|---------|------|
| `backchannel_logout_uri` | - | Backchannel Logout URI |
| `backchannel_logout_session_required` | false | セッション指定必須 |
| `frontchannel_logout_uri` | - | Frontchannel Logout URI |
| `frontchannel_logout_session_required` | false | セッション指定必須 |

---

## 4. ClientExtensionConfiguration（クライアント拡張設定）

**パス**: `libs/idp-server-core/.../oauth/configuration/client/ClientExtensionConfiguration.java`

`extension` フィールド内にネスト。

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `access_token_duration` | Long | (サーバー設定継承) | クライアント個別AT有効期限 |
| `refresh_token_duration` | Long | (サーバー設定継承) | クライアント個別RT有効期限 |
| `refresh_token_strategy` | String | (サーバー設定継承) | FIXED/EXTENDS |
| `rotate_refresh_token` | Boolean | (サーバー設定継承) | RTローテーション |
| `id_token_duration` | Long | (サーバー設定継承) | クライアント個別IDToken有効期限 |
| `supported_jar` | boolean | false | JAR対応 |
| `available_federations` | List | - | 利用可能フェデレーション |
| `default_ciba_authentication_interaction_type` | String | `authentication-device-notification-no-action` | CIBAデフォルト認証方式 |
| `ciba_require_rar` | boolean | false | CIBA RAR必須 |
| `custom_properties` | Map | - | カスタムプロパティ |

---

## 5. Tenant（テナント設定）

**パス**: `libs/idp-server-platform/.../multi_tenancy/tenant/Tenant.java`

| JSONキー | 型 | 説明 |
|---------|-----|------|
| `id` | String | テナント識別子（UUID） |
| `name` | String | テナント名 |
| `type` | String | テナント型（ADMIN/ORGANIZER/PUBLIC） |
| `domain` | String | テナントドメイン |
| `authorization_provider` | Object | 認可プロバイダー |
| `attributes` | Object | テナント属性（タイムゾーン等） |
| `features` | Object | テナント機能フラグ |
| `main_organization_id` | String | メイン組織ID |
| `enabled` | boolean | 有効化フラグ (default: true) |
| `ui_config` | UIConfiguration | UI設定 |
| `cors_config` | CorsConfiguration | CORS設定 |
| `session_config` | SessionConfiguration | セッション設定 |
| `identity_policy_config` | TenantIdentityPolicy | 身元確認ポリシー |
| `security_event_log_config` | Object | セキュリティイベントログ設定 |
| `security_event_user_config` | Object | セキュリティイベントユーザー属性設定 |

---

## 6. UIConfiguration（UI設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `base_url` | String | - | UIホスティング基本URL |
| `signup_page` | String | `/auth-views/signup/index.html` | サインアップページパス |
| `signin_page` | String | `/auth-views/signin/index.html` | サインインページパス |

---

## 7. CorsConfiguration（CORS設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `allow_origins` | List | [] | 許可オリジン一覧 |
| `allow_headers` | String | `Authorization, Content-Type, Accept, x-device-id` | 許可ヘッダ |
| `allow_methods` | String | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | 許可HTTPメソッド |
| `allow_credentials` | boolean | true | 認証情報許可 |

---

## 8. SessionConfiguration（セッション設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `cookie_name` | String | (テナント固有名生成) | クッキー名 |
| `cookie_domain` | String | - | クッキードメイン |
| `cookie_same_site` | String | `Lax` | SameSite属性 |
| `use_secure_cookie` | boolean | true | Secureフラグ |
| `use_http_only_cookie` | boolean | true | HttpOnlyフラグ |
| `cookie_path` | String | `/` | クッキーパス |
| `timeout_seconds` | int | 3600 | セッションタイムアウト（秒） |
| `switch_policy` | String | `SWITCH_ALLOWED` | セッション切り替えポリシー |

**switch_policy の有効値**:
- `STRICT` — セッション切り替え不可
- `SWITCH_ALLOWED` — セッション切り替え許可
- `MULTI_SESSION` — 複数セッション許可

---

## 9. TenantIdentityPolicy（身元確認ポリシー）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `identity_unique_key_type` | UniqueKeyType | `EMAIL_OR_EXTERNAL_USER_ID` | ユーザー一意性キー型 |
| `password_policy` | PasswordPolicyConfig | - | パスワードポリシー |
| `authentication_device_rule` | AuthenticationDeviceRule | - | 認証デバイスルール |

**UniqueKeyType の有効値**:
- `USERNAME`, `USERNAME_OR_EXTERNAL_USER_ID`
- `EMAIL`, `EMAIL_OR_EXTERNAL_USER_ID`（推奨）
- `PHONE`, `PHONE_OR_EXTERNAL_USER_ID`
- `EXTERNAL_USER_ID`

---

## 10. PasswordPolicyConfig（パスワードポリシー）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `min_length` | int | 8 | 最小文字数 |
| `max_length` | int | 72 | 最大文字数（BCrypt制限） |
| `require_uppercase` | boolean | false | 大文字必須 |
| `require_lowercase` | boolean | false | 小文字必須 |
| `require_number` | boolean | false | 数字必須 |
| `require_special_char` | boolean | false | 特殊文字必須 |
| `custom_regex` | String | - | カスタム正規表現検証 |
| `custom_regex_error_message` | String | - | カスタム検証エラーメッセージ |
| `max_history` | int | 0 | パスワード履歴保存数 |
| `max_attempts` | int | 5 | ブルートフォース最大試行回数 |
| `lockout_duration_seconds` | int | 900 | アカウントロック期間（秒＝15分） |

---

## 11. AuthenticationDeviceRule（認証デバイスルール）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `max_devices` | int | 5 | 最大デバイス登録数/ユーザー |
| `required_identity_verification` | boolean | false | デバイス登録時身元確認必須 |
| `authentication_type` | String | `none` | デバイス認証型（none/bearer_jwt） |
| `issue_device_secret` | boolean | false | デバイスシークレット発行 |
| `device_secret_algorithm` | String | `HS256` | JWTアルゴリズム（HS256/HS384/HS512） |
| `device_secret_expires_in_seconds` | Long | - | デバイスシークレット有効期限（秒） |

---

## 12. AuthenticationConfiguration（認証設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `id` | String | - | 認証設定ID（UUID） |
| `type` | String | - | 認証設定タイプ |
| `attributes` | Map | - | 認証属性マップ |
| `metadata` | Map | - | メタデータマップ |
| `interactions` | Map | - | 認証インタラクション設定 |
| `enabled` | boolean | true | 有効化フラグ |

---

## 13. AuthenticationPolicyConfiguration（認証ポリシー設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `id` | String | - | ポリシーID（UUID） |
| `flow` | String | - | フロー（authorization/ciba等） |
| `policies` | List | - | 認証ポリシー一覧（優先度順） |
| `enabled` | boolean | true | 有効化フラグ |

---

## 14. FederationConfiguration（フェデレーション設定）

| JSONキー | 型 | default | 説明 |
|---------|-----|---------|------|
| `id` | String | - | フェデレーション設定ID |
| `type` | String | - | フェデレーション型 |
| `sso_provider` | String | - | SSOプロバイダー |
| `payload` | Map | - | フェデレーション設定ペイロード |
| `enabled` | boolean | true | 有効化フラグ |

$ARGUMENTS
